package com.mrleonardos.codeperms.api.resolve;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Ответ на вопрос «разрешено ли» с объяснением.
 *
 * <p>
 * Держит всех кандидатов в порядке силы и того, кто решил исход. Пустой список кандидатов значит, что
 * ни одно правило не подошло, и это запрет: права по умолчанию закрыты.
 */
public final class Resolution {

    private static final Resolution NONE = new Resolution(Collections.<Candidate>emptyList(), null);

    private final List<Candidate> candidates;
    private final Candidate winner;

    private Resolution(List<Candidate> candidates, Candidate winner) {
        this.candidates = candidates;
        this.winner = winner;
    }

    /**
     * Собрать ответ из применимых кандидатов. Побеждает сильнейший по {@link
     * Candidate#STRONGEST_FIRST}, его запрет или разрешение и есть ответ.
     */
    public static Resolution of(List<Candidate> found) {
        if (found.isEmpty()) {
            return NONE;
        }
        List<Candidate> sorted = new ArrayList<>(found);
        Collections.sort(sorted, Candidate.STRONGEST_FIRST);
        return new Resolution(Collections.unmodifiableList(sorted), sorted.get(0));
    }

    /** Ни одно правило не подошло: запрет без кандидатов. */
    public static Resolution none() {
        return NONE;
    }

    /** true значит разрешено. */
    public boolean allowed() {
        return winner != null && !winner.deny();
    }

    /** Кандидат, который решил исход, или пустой ответ, если никто не подошёл. */
    public Optional<Candidate> winner() {
        return Optional.ofNullable(winner);
    }

    /** Все применимые кандидаты от сильнейшего к слабейшему. */
    public List<Candidate> candidates() {
        return candidates;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Resolution)) {
            return false;
        }
        Resolution that = (Resolution) other;
        return allowed() == that.allowed() && candidates.equals(that.candidates);
    }

    @Override
    public int hashCode() {
        return (allowed() ? 1 : 0) * 31 + candidates.hashCode();
    }

    @Override
    public String toString() {
        if (winner == null) {
            return "denied, no candidates";
        }
        return (allowed() ? "allowed" : "denied") + " by " + winner;
    }
}
