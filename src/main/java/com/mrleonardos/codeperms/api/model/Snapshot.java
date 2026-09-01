package com.mrleonardos.codeperms.api.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Неизменяемый слепок всех прав на один момент.
 *
 * <p>
 * Хранилище держит снимок за volatile-ссылку, поэтому читать его можно из любого потока без
 * блокировок. Мутация строит новый снимок и подменяет ссылку целиком, счётчик ревизии растёт на каждой
 * мутации: по нему видно, что состояние поменялось.
 */
public final class Snapshot {

    private static final Snapshot EMPTY = builder().build();

    private final long revision;
    private final String defaultGroup;
    private final String opGroup;
    private final Map<String, GroupRecord> groups;
    private final Map<UUID, UserRecord> users;
    private final Map<String, TrackRecord> tracks;
    private final boolean contextNodes;

    private Snapshot(long revision, String defaultGroup, String opGroup, Map<String, GroupRecord> groups,
        Map<UUID, UserRecord> users, Map<String, TrackRecord> tracks, boolean contextNodes) {
        this.revision = revision;
        this.defaultGroup = defaultGroup;
        this.opGroup = opGroup;
        this.groups = groups;
        this.users = users;
        this.tracks = tracks;
        this.contextNodes = contextNodes;
    }

    /** Пустой снимок без групп, игроков и треков. */
    public static Snapshot empty() {
        return EMPTY;
    }

    /** Начать собирать новый снимок. */
    public static Builder builder() {
        return new Builder();
    }

    /** Число мутаций с самого начала работы хранилища. */
    public long revision() {
        return revision;
    }

    /** Группа игрока без назначений или null, если настройка не задана. */
    public String defaultGroup() {
        return defaultGroup;
    }

    /** Группа оператора сервера или null, если настройка не задана. */
    public String opGroup() {
        return opGroup;
    }

    /** Группы по идентификатору. */
    public Map<String, GroupRecord> groups() {
        return groups;
    }

    /** Игроки по идентификатору. */
    public Map<UUID, UserRecord> users() {
        return users;
    }

    /** Треки по имени. */
    public Map<String, TrackRecord> tracks() {
        return tracks;
    }

    /** Группа по идентификатору. */
    public Optional<GroupRecord> group(String id) {
        return Optional.ofNullable(groups.get(id));
    }

    /** Игрок по идентификатору. */
    public Optional<UserRecord> user(UUID uuid) {
        return Optional.ofNullable(users.get(uuid));
    }

    /** Трек по имени. */
    public Optional<TrackRecord> track(String name) {
        return Optional.ofNullable(tracks.get(name));
    }

    /**
     * Есть ли в снимке хоть одно правило с контекстами.
     *
     * <p>
     * Сервер без контекстных правил не платит за сбор набора контекстов: он гасится этим флагом.
     */
    public boolean hasContextNodes() {
        return contextNodes;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Snapshot)) {
            return false;
        }
        Snapshot that = (Snapshot) other;
        return revision == that.revision && Objects.equals(defaultGroup, that.defaultGroup)
            && Objects.equals(opGroup, that.opGroup)
            && groups.equals(that.groups)
            && users.equals(that.users)
            && tracks.equals(that.tracks);
    }

    @Override
    public int hashCode() {
        return (((((int) (revision ^ (revision >>> 32))) * 31 + Objects.hashCode(defaultGroup)) * 31
            + Objects.hashCode(opGroup)) * 31 + groups.hashCode()) * 31 + users.hashCode();
    }

    @Override
    public String toString() {
        return "revision " + revision
            + ": "
            + groups.size()
            + " groups, "
            + users.size()
            + " players, "
            + tracks.size()
            + " tracks";
    }

    /** Сборщик нового снимка. */
    public static final class Builder {

        private long revision;
        private String defaultGroup;
        private String opGroup;
        private final Map<String, GroupRecord> groups = new LinkedHashMap<>();
        private final Map<UUID, UserRecord> users = new LinkedHashMap<>();
        private final Map<String, TrackRecord> tracks = new LinkedHashMap<>();

        private Builder() {}

        /** Ревизия снимка. По умолчанию ноль. */
        public Builder revision(long value) {
            this.revision = value;
            return this;
        }

        /** Группа игрока без назначений, null сбрасывает настройку. */
        public Builder defaultGroup(String value) {
            this.defaultGroup = value;
            return this;
        }

        /** Группа оператора сервера, null сбрасывает настройку. */
        public Builder opGroup(String value) {
            this.opGroup = value;
            return this;
        }

        /** Добавить группу или заменить прежнюю с тем же идентификатором. */
        public Builder group(GroupRecord record) {
            Objects.requireNonNull(record, "record");
            groups.put(record.id(), record);
            return this;
        }

        /** Добавить игрока или заменить прежнюю запись с тем же идентификатором. */
        public Builder user(UserRecord record) {
            Objects.requireNonNull(record, "record");
            users.put(record.uuid(), record);
            return this;
        }

        /** Добавить трек или заменить прежний с тем же именем. */
        public Builder track(TrackRecord record) {
            Objects.requireNonNull(record, "record");
            tracks.put(record.name(), record);
            return this;
        }

        /** Убрать группу, если она есть. */
        public Builder removeGroup(String id) {
            groups.remove(id);
            return this;
        }

        /** Убрать игрока, если он есть. */
        public Builder removeUser(UUID uuid) {
            users.remove(uuid);
            return this;
        }

        /** Убрать трек, если он есть. */
        public Builder removeTrack(String name) {
            tracks.remove(name);
            return this;
        }

        /** Скопировать всё из другого снимка, кроме ревизии и настроек по умолчанию. */
        public Builder from(Snapshot snapshot) {
            Objects.requireNonNull(snapshot, "snapshot");
            groups.putAll(snapshot.groups);
            users.putAll(snapshot.users);
            tracks.putAll(snapshot.tracks);
            return this;
        }

        /** Готовый снимок. */
        public Snapshot build() {
            return new Snapshot(
                revision,
                defaultGroup,
                opGroup,
                Collections.unmodifiableMap(new LinkedHashMap<>(groups)),
                Collections.unmodifiableMap(new LinkedHashMap<>(users)),
                Collections.unmodifiableMap(new LinkedHashMap<>(tracks)),
                holdsContextNodes());
        }

        private boolean holdsContextNodes() {
            for (GroupRecord group : groups.values()) {
                if (holdsContextNodes(group.nodes())) {
                    return true;
                }
            }
            for (UserRecord user : users.values()) {
                if (holdsContextNodes(user.nodes())) {
                    return true;
                }
            }
            return false;
        }

        private static boolean holdsContextNodes(List<NodeEntry> nodes) {
            for (NodeEntry entry : nodes) {
                if (!entry.contexts()
                    .isEmpty()) {
                    return true;
                }
            }
            return false;
        }
    }
}
