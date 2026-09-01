package com.mrleonardos.codeperms.api.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.mrleonardos.codeperms.api.model.ChangeCause;
import com.mrleonardos.codeperms.api.model.GroupRecord;
import com.mrleonardos.codeperms.api.model.TrackRecord;
import com.mrleonardos.codeperms.api.model.UserRecord;

/**
 * Пачка правок для хранилища.
 *
 * <p>
 * Носит причину, автора и список правок целых субъектов. Хранилище применяет пачку один раз, поэтому
 * частичного применения не бывает: либо вся пачка, либо отказ. По пачке же строится аудит.
 */
public final class ChangeBatch {

    private final ChangeCause cause;
    private final String author;
    private final List<Change> changes;

    private ChangeBatch(ChangeCause cause, String author, List<Change> changes) {
        this.cause = cause;
        this.author = author;
        this.changes = changes;
    }

    /**
     * Начать собирать пачку.
     *
     * @param author кто правит: имя игрока, {@code console} или идентификатор чужого мода
     */
    public static Builder builder(ChangeCause cause, String author) {
        return new Builder(cause, author);
    }

    /** Причина правки. */
    public ChangeCause cause() {
        return cause;
    }

    /** Кто правил. */
    public String author() {
        return author;
    }

    /** Правки в порядке применения. */
    public List<Change> changes() {
        return changes;
    }

    /** Пустых пачек не бывает, поэтому проверка нужна только для подстраховки. */
    public boolean isEmpty() {
        return changes.isEmpty();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ChangeBatch)) {
            return false;
        }
        ChangeBatch that = (ChangeBatch) other;
        return cause == that.cause && author.equals(that.author) && changes.equals(that.changes);
    }

    @Override
    public int hashCode() {
        return (cause.hashCode() * 31 + author.hashCode()) * 31 + changes.hashCode();
    }

    @Override
    public String toString() {
        return cause + " by " + author + ": " + changes.size() + " changes";
    }

    /** Сборщик пачки правок. */
    public static final class Builder {

        private final ChangeCause cause;
        private final String author;
        private final List<Change> changes = new ArrayList<>();

        private Builder(ChangeCause cause, String author) {
            this.cause = Objects.requireNonNull(cause, "cause");
            this.author = Objects.requireNonNull(author, "author");
        }

        /** Записать группу целиком. */
        public Builder upsert(GroupRecord group) {
            changes.add(Change.upsertGroup(group));
            return this;
        }

        /** Записать игрока целиком. */
        public Builder upsert(UserRecord player) {
            changes.add(Change.upsertPlayer(player));
            return this;
        }

        /** Удалить группу. */
        public Builder removeGroup(String groupId) {
            changes.add(Change.removeGroup(groupId));
            return this;
        }

        /** Удалить игрока. */
        public Builder removePlayer(UUID uuid) {
            changes.add(Change.removePlayer(uuid));
            return this;
        }

        /** Записать трек целиком. */
        public Builder upsert(TrackRecord track) {
            changes.add(Change.upsertTrack(track));
            return this;
        }

        /** Удалить трек. */
        public Builder removeTrack(String name) {
            changes.add(Change.removeTrack(name));
            return this;
        }

        /**
         * Готовая пачка.
         *
         * @throws IllegalArgumentException если в пачке ни одной правки
         */
        public ChangeBatch build() {
            if (changes.isEmpty()) {
                throw new IllegalArgumentException("Change batch needs at least one change");
            }
            return new ChangeBatch(cause, author, Collections.unmodifiableList(new ArrayList<>(changes)));
        }
    }
}
