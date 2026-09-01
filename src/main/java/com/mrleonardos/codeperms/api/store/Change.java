package com.mrleonardos.codeperms.api.store;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.mrleonardos.codeperms.api.model.GroupRecord;
import com.mrleonardos.codeperms.api.model.TrackRecord;
import com.mrleonardos.codeperms.api.model.UserRecord;

/**
 * Одна правка в пакете: субъект целиком, а не отдельное поле.
 *
 * <p>
 * Хранилище получает конечное состояние группы, игрока или трека, поэтому ему не нужно знать, какая
 * именно команда что меняла. Пакет при этом остаётся прямым переводом в запись любого другого
 * хранилища.
 */
public final class Change {

    /** Кого касается правка. */
    public enum Kind {

        /** Группа. */
        GROUP,

        /** Игрок. */
        PLAYER,

        /** Трек: переименование и удаление группы правят его состав. */
        TRACK
    }

    /** Что сделать с субъектом. */
    public enum Operation {

        /** Записать состояние целиком. */
        UPSERT,

        /** Удалить субъекта. */
        REMOVE
    }

    private final Kind kind;
    private final Operation operation;
    private final String subject;
    private final GroupRecord group;
    private final UserRecord player;
    private final TrackRecord track;

    private Change(Kind kind, Operation operation, String subject, GroupRecord group, UserRecord player,
        TrackRecord track) {
        this.kind = kind;
        this.operation = operation;
        this.subject = subject;
        this.group = group;
        this.player = player;
        this.track = track;
    }

    /** Записать группу целиком. */
    public static Change upsertGroup(GroupRecord group) {
        Objects.requireNonNull(group, "group");
        return new Change(Kind.GROUP, Operation.UPSERT, group.id(), group, null, null);
    }

    /** Удалить группу. */
    public static Change removeGroup(String groupId) {
        Objects.requireNonNull(groupId, "groupId");
        return new Change(Kind.GROUP, Operation.REMOVE, groupId, null, null, null);
    }

    /** Записать игрока целиком. */
    public static Change upsertPlayer(UserRecord player) {
        Objects.requireNonNull(player, "player");
        return new Change(
            Kind.PLAYER,
            Operation.UPSERT,
            player.uuid()
                .toString(),
            null,
            player,
            null);
    }

    /** Удалить игрока. */
    public static Change removePlayer(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        return new Change(Kind.PLAYER, Operation.REMOVE, uuid.toString(), null, null, null);
    }

    /** Записать трек целиком. */
    public static Change upsertTrack(TrackRecord track) {
        Objects.requireNonNull(track, "track");
        return new Change(Kind.TRACK, Operation.UPSERT, track.name(), null, null, track);
    }

    /** Удалить трек. */
    public static Change removeTrack(String name) {
        Objects.requireNonNull(name, "name");
        return new Change(Kind.TRACK, Operation.REMOVE, name, null, null, null);
    }

    /** Кого касается правка. */
    public Kind kind() {
        return kind;
    }

    /** Что сделать с субъектом. */
    public Operation operation() {
        return operation;
    }

    /** Идентификатор субъекта: идентификатор группы или строка с uuid игрока. */
    public String subject() {
        return subject;
    }

    /** Новое состояние группы, если правка это upsert группы. */
    public Optional<GroupRecord> group() {
        return Optional.ofNullable(group);
    }

    /** Новое состояние игрока, если правка это upsert игрока. */
    public Optional<UserRecord> player() {
        return Optional.ofNullable(player);
    }

    /** Новое состояние трека, если правка это upsert трека. */
    public Optional<TrackRecord> track() {
        return Optional.ofNullable(track);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Change)) {
            return false;
        }
        Change that = (Change) other;
        return kind == that.kind && operation == that.operation && subject.equals(that.subject);
    }

    @Override
    public int hashCode() {
        return (kind.hashCode() * 31 + operation.hashCode()) * 31 + subject.hashCode();
    }

    @Override
    public String toString() {
        return operation + " " + kind + " " + subject;
    }
}
