package com.mrleonardos.codeperms.api.resolve;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;

import com.mrleonardos.codeperms.api.model.ContextSet;
import com.mrleonardos.codeperms.api.model.NodeEntry;

/**
 * Одна применимая нода одного источника.
 *
 * <p>
 * Кандидаты собираются в таблицу для {@code /perms debug} и решают, кто победил внутри источника.
 * Источник это строка вроде {@code player} для личных нод или {@code group:moderator} для группы.
 */
public final class Candidate {

    /** Сравнение по силе внутри одного источника. */
    public static final Comparator<Candidate> STRONGEST_FIRST = new Comparator<Candidate>() {

        @Override
        public int compare(Candidate left, Candidate right) {
            int byContexts = Integer.compare(right.contextMatches, left.contextMatches);
            if (byContexts != 0) {
                return byContexts;
            }
            int bySpecificity = Integer.compare(right.specificity, left.specificity);
            if (bySpecificity != 0) {
                return bySpecificity;
            }
            return Boolean.compare(right.deny, left.deny);
        }
    };

    private final String source;
    private final NodeEntry entry;
    private final int contextMatches;
    private final int specificity;
    private final boolean deny;

    private Candidate(String source, NodeEntry entry, int contextMatches, int specificity, boolean deny) {
        this.source = source;
        this.entry = entry;
        this.contextMatches = contextMatches;
        this.specificity = specificity;
        this.deny = deny;
    }

    /**
     * Кандидат из применимого правила.
     *
     * @param source имя источника, например {@code player} или {@code group:moderator}
     * @param query  набор контекстов запроса, против которого считаются совпавшие пары
     */
    public static Candidate of(String source, NodeEntry entry, ContextSet query) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(query, "query");
        int matches = 0;
        for (Map.Entry<String, String> pair : entry.contexts()
            .asMap()
            .entrySet()) {
            if (pair.getValue()
                .equals(
                    query.asMap()
                        .get(pair.getKey()))) {
                matches++;
            }
        }
        return new Candidate(source, entry, matches, NodePatterns.specificity(entry.node()), !entry.value());
    }

    /** Имя источника. */
    public String source() {
        return source;
    }

    /** Правило, которое подошло. */
    public NodeEntry entry() {
        return entry;
    }

    /** Сколько пар контекста правила нашлось в наборе запроса. */
    public int contextMatches() {
        return contextMatches;
    }

    /** Точность ноды: число сегментов без звёздочек. */
    public int specificity() {
        return specificity;
    }

    /** true значит запрет. */
    public boolean deny() {
        return deny;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Candidate)) {
            return false;
        }
        Candidate that = (Candidate) other;
        return contextMatches == that.contextMatches && specificity == that.specificity
            && deny == that.deny
            && source.equals(that.source)
            && entry.equals(that.entry);
    }

    @Override
    public int hashCode() {
        return (((source.hashCode() * 31 + entry.hashCode()) * 31 + contextMatches) * 31 + specificity) * 31
            + (deny ? 1 : 0);
    }

    @Override
    public String toString() {
        return source + " "
            + entry.toShortString()
            + " contexts="
            + contextMatches
            + " specificity="
            + specificity
            + (deny ? " deny" : " allow");
    }
}
