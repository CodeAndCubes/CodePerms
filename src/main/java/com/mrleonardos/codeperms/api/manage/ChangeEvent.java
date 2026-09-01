package com.mrleonardos.codeperms.api.manage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.mrleonardos.codeperms.api.model.ChangeCause;

/**
 * Коалесированное уведомление о правках за тик.
 *
 * <p>
 * Носит затронутых субъектов, вид изменения и причину. Пять правок одного игрока за тик дают одно
 * событие с одним субъектом.
 */
public final class ChangeEvent {

    /** Что именно поменялось. */
    public enum Kind {

        /** Личные ноды или ноды групп. */
        NODES,

        /** Состав, вес, имя или наследование групп. */
        GROUPS,

        /** Мета групп или игроков. */
        META,

        /** Выдачи групп игрокам и основная группа. */
        MEMBERSHIP
    }

    /** Затронутый субъект: группа или игрок. */
    public static final class Subject {

        private final String id;
        private final UUID uuid;

        private Subject(String id, UUID uuid) {
            this.id = id;
            this.uuid = uuid;
        }

        /** Субъект-группа. */
        public static Subject group(String groupId) {
            Objects.requireNonNull(groupId, "groupId");
            return new Subject(groupId, null);
        }

        /** Субъект-игрок. */
        public static Subject player(UUID playerId) {
            Objects.requireNonNull(playerId, "playerId");
            return new Subject(playerId.toString(), playerId);
        }

        /** Правда ли субъект это группа. */
        public boolean isGroup() {
            return uuid == null;
        }

        /** Правда ли субъект это игрок. */
        public boolean isPlayer() {
            return uuid != null;
        }

        /** Идентификатор группы, если субъект это группа. */
        public String groupId() {
            return isGroup() ? id : null;
        }

        /** Идентификатор игрока, если субъект это игрок. */
        public UUID playerId() {
            return uuid;
        }

        /** Идентификатор субъекта в виде строки, для журналов и подсказок. */
        public String key() {
            return id;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Subject)) {
                return false;
            }
            Subject that = (Subject) other;
            return isGroup() == that.isGroup() && id.equals(that.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        @Override
        public String toString() {
            return isGroup() ? "group:" + id : "player:" + id;
        }
    }

    private final Kind kind;
    private final Set<Subject> subjects;
    private final ChangeCause cause;

    private ChangeEvent(Kind kind, Set<Subject> subjects, ChangeCause cause) {
        this.kind = kind;
        this.subjects = subjects;
        this.cause = cause;
    }

    /**
     * Собрать событие.
     *
     * @param subjects затронутые субъекты, повторы убираются
     * @throws IllegalArgumentException если список субъектов пустой
     */
    public static ChangeEvent of(Kind kind, Collection<Subject> subjects, ChangeCause cause) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(subjects, "subjects");
        Objects.requireNonNull(cause, "cause");
        if (subjects.isEmpty()) {
            throw new IllegalArgumentException("Change event needs at least one subject");
        }
        return new ChangeEvent(kind, Collections.unmodifiableSet(new LinkedHashSet<>(subjects)), cause);
    }

    /** Что поменялось. */
    public Kind kind() {
        return kind;
    }

    /** Затронутые субъекты. */
    public Set<Subject> subjects() {
        return subjects;
    }

    /** Причина правки. */
    public ChangeCause cause() {
        return cause;
    }

    /** Идентификаторы затронутых субъектов в виде строк, для журналов. */
    public List<String> subjectKeys() {
        List<String> keys = new ArrayList<>();
        for (Subject subject : subjects) {
            keys.add(subject.key());
        }
        return keys;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ChangeEvent)) {
            return false;
        }
        ChangeEvent that = (ChangeEvent) other;
        return kind == that.kind && cause == that.cause && subjects.equals(that.subjects);
    }

    @Override
    public int hashCode() {
        return (kind.hashCode() * 31 + subjects.hashCode()) * 31 + cause.hashCode();
    }

    @Override
    public String toString() {
        return kind + " " + subjectKeys() + " by " + cause;
    }
}
