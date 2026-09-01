package com.mrleonardos.codeperms.api.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Неизменяемый набор пар ключ-значение в нижнем регистре.
 *
 * <p>
 * Бывает двух родов: набор запроса, то есть где игрок сейчас, и набор правила, то есть где оно
 * действует. Правило применимо, только если все его пары есть в наборе запроса. Отсюда два следствия:
 * пустой набор правила означает «действует везде», а мера частности правила равна числу пар.
 */
public final class ContextSet {

    private static final ContextSet EMPTY = new ContextSet(Collections.<String, String>emptyMap());

    private final Map<String, String> values;

    private ContextSet(Map<String, String> values) {
        this.values = values;
    }

    /** Набор без пар. */
    public static ContextSet empty() {
        return EMPTY;
    }

    /**
     * Набор из готовых пар.
     *
     * @throws IllegalArgumentException если ключ пустой или значение равно null
     */
    public static ContextSet of(Map<String, String> values) {
        Objects.requireNonNull(values, "values");
        if (values.isEmpty()) {
            return EMPTY;
        }
        return builder().putAll(values)
            .build();
    }

    /** Начать собирать набор. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Все пары этого набора есть в наборе запроса.
     *
     * <p>
     * Пустой набор совпадает с любым запросом.
     */
    public boolean matches(ContextSet query) {
        Objects.requireNonNull(query, "query");
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!entry.getValue()
                .equals(query.values.get(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }

    /** Сколько пар в наборе: мера частности правила. */
    public int specificity() {
        return values.size();
    }

    /** Есть ли в наборе хоть одна пара. */
    public boolean isEmpty() {
        return values.isEmpty();
    }

    /** Пары набора, менять их нельзя. */
    public Map<String, String> asMap() {
        return values;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ContextSet)) {
            return false;
        }
        return values.equals(((ContextSet) other).values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    @Override
    public String toString() {
        return values.toString();
    }

    /** Сборщик набора контекстов. */
    public static final class Builder {

        private final Map<String, String> values = new LinkedHashMap<>();

        /**
         * Добавить пару. Ключ и значение приводятся к нижнему регистру, поэтому {@code World} и
         * {@code world} это один контекст.
         *
         * @throws IllegalArgumentException если ключ пустой или значение равно null
         */
        public Builder put(String key, String value) {
            Objects.requireNonNull(value, "value");
            String normalizedKey = normalize(key);
            if (normalizedKey.isEmpty()) {
                throw new IllegalArgumentException("Context key must not be empty");
            }
            values.put(normalizedKey, value.toLowerCase(Locale.ROOT));
            return this;
        }

        /** Добавить несколько пар, правила те же, что у {@link #put(String, String)}. */
        public Builder putAll(Map<String, String> added) {
            for (Map.Entry<String, String> entry : added.entrySet()) {
                put(entry.getKey(), entry.getValue());
            }
            return this;
        }

        /** Готовый набор. */
        public ContextSet build() {
            if (values.isEmpty()) {
                return EMPTY;
            }
            return new ContextSet(Collections.unmodifiableMap(new LinkedHashMap<>(values)));
        }

        private static String normalize(String key) {
            Objects.requireNonNull(key, "key");
            return key.trim()
                .toLowerCase(Locale.ROOT);
        }
    }
}
