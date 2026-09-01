package com.mrleonardos.codeperms.api.model;

import java.util.Objects;

import com.mrleonardos.codeperms.api.PermsLimits;

/**
 * Одно право субъекта: нода, разрешение или запрет, контексты и срок.
 *
 * <p>
 * В файлах и командах нода пишется строкой: {@code codechat.channel.*} разрешает,
 * {@code -codechat.create} запрещает. Формат строки совпадает с форматом файла ядра, поэтому права из
 * него переносятся без преобразований. Контексты и срок в строковой форме не выражаются, для них есть
 * объектная запись в json.
 */
public final class NodeEntry {

    /** Разделитель сегментов ноды, тот же, что в правиле сопоставления. */
    public static final char SEPARATOR = '.';

    private static final char NEGATION = '-';

    private final String node;
    private final boolean value;
    private final ContextSet contexts;
    private final long expiresAt;

    private NodeEntry(String node, boolean value, ContextSet contexts, long expiresAt) {
        this.node = node;
        this.value = value;
        this.contexts = contexts;
        this.expiresAt = expiresAt;
    }

    /** Разрешение без контекстов и без срока. */
    public static NodeEntry allow(String node) {
        return of(node, true, ContextSet.empty(), 0L);
    }

    /** Запрет без контекстов и без срока. */
    public static NodeEntry deny(String node) {
        return of(node, false, ContextSet.empty(), 0L);
    }

    /**
     * Правило целиком.
     *
     * @param node      нода, звёздочки разрешены так же, как в файлах ядра
     * @param value     true разрешает, false запрещает
     * @param contexts  пустой набор значит «действует везде»
     * @param expiresAt метка истечения в миллисекундах, ноль значит бессрочно
     * @throws IllegalArgumentException если нода пустая, держит пробелы или пустые сегменты
     */
    public static NodeEntry of(String node, boolean value, ContextSet contexts, long expiresAt) {
        Objects.requireNonNull(contexts, "contexts");
        if (expiresAt < 0) {
            throw new IllegalArgumentException("Expiry must not be negative: " + expiresAt);
        }
        validateShape(node);
        return new NodeEntry(node, value, contexts, expiresAt);
    }

    /**
     * Строковая форма из файла или команды с заводскими потолками.
     *
     * @throws IllegalArgumentException если строка пустая или нода не вписывается в потолки
     */
    public static NodeEntry parse(String raw) {
        return parse(raw, PermsLimits.defaults());
    }

    /**
     * Строковая форма из файла или команды с указанными потолками.
     *
     * @param raw запись вида {@code codechat.channel.*} или {@code -codechat.create}
     * @throws IllegalArgumentException если строка пустая или нода не вписывается в потолки
     */
    public static NodeEntry parse(String raw, PermsLimits limits) {
        Objects.requireNonNull(limits, "limits");
        String text = Objects.requireNonNull(raw, "raw")
            .trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Node must not be empty");
        }
        boolean value = true;
        if (text.charAt(0) == NEGATION) {
            value = false;
            text = text.substring(1);
        }
        validateShape(text);
        if (text.length() > limits.nodeLength()) {
            throw new IllegalArgumentException("Node is longer than " + limits.nodeLength() + " characters");
        }
        if (segments(text) > limits.nodeSegments()) {
            throw new IllegalArgumentException("Node has more than " + limits.nodeSegments() + " segments");
        }
        return new NodeEntry(text, value, ContextSet.empty(), 0L);
    }

    /** Нода правила, звёздочки в ней допустимы. */
    public String node() {
        return node;
    }

    /** true разрешает, false запрещает. */
    public boolean value() {
        return value;
    }

    /** Пустой набор значит «действует везде». */
    public ContextSet contexts() {
        return contexts;
    }

    /** Метка истечения в миллисекундах, ноль значит бессрочно. */
    public long expiresAt() {
        return expiresAt;
    }

    /** Правда ли правило бессрочное. */
    public boolean permanent() {
        return expiresAt == 0L;
    }

    /** Истёкшее правило пропускается целиком, как будто его нет. */
    public boolean expiredAt(long nowMillis) {
        return !permanent() && nowMillis >= expiresAt;
    }

    /** Строковая форма: нода с минусом для запрета. Контексты и срок в ней не выражаются. */
    public String toShortString() {
        return (value ? "" : NEGATION) + node;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof NodeEntry)) {
            return false;
        }
        NodeEntry that = (NodeEntry) other;
        return value == that.value && expiresAt == that.expiresAt
            && node.equals(that.node)
            && contexts.equals(that.contexts);
    }

    @Override
    public int hashCode() {
        return ((node.hashCode() * 31 + (value ? 1 : 0)) * 31 + contexts.hashCode()) * 31
            + (int) (expiresAt ^ (expiresAt >>> 32));
    }

    @Override
    public String toString() {
        return toShortString();
    }

    private static void validateShape(String node) {
        Objects.requireNonNull(node, "node");
        if (node.isEmpty()) {
            throw new IllegalArgumentException("Node must not be empty");
        }
        for (int index = 0; index < node.length(); index++) {
            char symbol = node.charAt(index);
            if (Character.isWhitespace(symbol)) {
                throw new IllegalArgumentException("Node must not hold whitespace: " + node);
            }
        }
        String[] parts = node.split("\\" + SEPARATOR, -1);
        for (String part : parts) {
            if (part.isEmpty()) {
                throw new IllegalArgumentException("Node must not hold empty segments: " + node);
            }
        }
    }

    private static int segments(String node) {
        return node.split("\\" + SEPARATOR, -1).length;
    }
}
