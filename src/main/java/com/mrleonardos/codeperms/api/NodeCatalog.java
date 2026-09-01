package com.mrleonardos.codeperms.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Каталог нод для автодополнения.
 *
 * <p>
 * Мод сам рассказывает о своих правах: нода и ключ перевода с описанием. Команды дополняют по
 * каталогу, поэтому администратор видит, что вообще можно выдать, а не помнит ноды наизусть.
 *
 * <p>
 * Регистрацию делают на инициализации мода. Повторная запись той же ноды заменяет прежнее описание.
 */
public final class NodeCatalog {

    private final Map<String, Entry> entries = new TreeMap<>();

    /**
     * Рассказать о ноде.
     *
     * @param node           нода, звёздочки в ней допустимы: каталог описывает и семейства вроде
     *                       {@code codechat.channel.*}
     * @param descriptionKey ключ перевода с описанием того, что нода разрешает
     */
    public synchronized void register(String node, String descriptionKey) {
        Entry entry = Entry.of(node, descriptionKey);
        entries.put(entry.node(), entry);
    }

    /** Забыть ноду. */
    public synchronized void unregister(String node) {
        Objects.requireNonNull(node, "node");
        entries.remove(node);
    }

    /** Описания в алфавитном порядке нод. */
    public synchronized List<Entry> entries() {
        return new ArrayList<>(entries.values());
    }

    /**
     * Дополнение по началу ноды.
     *
     * @param prefix уже набранный текст, регистр не важен
     * @param limit  сколько подсказок вернуть
     */
    public synchronized List<String> suggest(String prefix, int limit) {
        Objects.requireNonNull(prefix, "prefix");
        if (limit <= 0) {
            return Collections.emptyList();
        }
        String lowered = prefix.toLowerCase(Locale.ROOT);
        List<String> found = new ArrayList<>();
        for (String node : entries.keySet()) {
            if (node.toLowerCase(Locale.ROOT)
                .startsWith(lowered)) {
                found.add(node);
                if (found.size() == limit) {
                    break;
                }
            }
        }
        return found;
    }

    /** Описание ноды. */
    public static final class Entry {

        private final String node;
        private final String descriptionKey;

        private Entry(String node, String descriptionKey) {
            this.node = node;
            this.descriptionKey = descriptionKey;
        }

        /**
         * Собрать описание.
         *
         * @throws IllegalArgumentException если нода или ключ описания пустые
         */
        public static Entry of(String node, String descriptionKey) {
            String normalizedNode = normalize(node, "node");
            String normalizedKey = normalize(descriptionKey, "description key");
            return new Entry(normalizedNode, normalizedKey);
        }

        /** Нода, возможно со звёздочкой. */
        public String node() {
            return node;
        }

        /** Ключ перевода с описанием. */
        public String descriptionKey() {
            return descriptionKey;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Entry)) {
                return false;
            }
            Entry that = (Entry) other;
            return node.equals(that.node) && descriptionKey.equals(that.descriptionKey);
        }

        @Override
        public int hashCode() {
            return node.hashCode() * 31 + descriptionKey.hashCode();
        }

        @Override
        public String toString() {
            return node + " (" + descriptionKey + ")";
        }

        private static String normalize(String value, String what) {
            Objects.requireNonNull(value, what);
            String normalized = value.trim();
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException("Catalog " + what + " must not be empty");
            }
            return normalized;
        }
    }
}
