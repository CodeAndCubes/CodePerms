package com.mrleonardos.codeperms.api.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Игрок: ник, основная группа, выдачи групп, личные ноды и мета.
 *
 * <p>
 * Ник денормализуется в запись, чтобы оффлайн-игрок находился по имени из команды. Групп у игрока
 * может быть несколько, каждая выдача несёт свой срок.
 */
public final class UserRecord {

    private final UUID uuid;
    private final String name;
    private final String primary;
    private final List<Grant> groups;
    private final List<NodeEntry> nodes;
    private final Map<String, String> meta;

    private UserRecord(UUID uuid, String name, String primary, List<Grant> groups, List<NodeEntry> nodes,
        Map<String, String> meta) {
        this.uuid = uuid;
        this.name = name;
        this.primary = primary;
        this.groups = groups;
        this.nodes = nodes;
        this.meta = meta;
    }

    /**
     * Собрать запись игрока.
     *
     * @param name    последний известный ник или null, если он ещё неизвестен
     * @param primary идентификатор основной группы или null
     * @param groups  выдачи групп со сроками
     * @param nodes   личные ноды
     * @param meta    личная мета
     */
    public static UserRecord of(UUID uuid, String name, String primary, List<Grant> groups, List<NodeEntry> nodes,
        Map<String, String> meta) {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(groups, "groups");
        Objects.requireNonNull(nodes, "nodes");
        Objects.requireNonNull(meta, "meta");
        return new UserRecord(
            uuid,
            name,
            primary,
            Collections.unmodifiableList(new ArrayList<>(groups)),
            Collections.unmodifiableList(new ArrayList<>(nodes)),
            Collections.unmodifiableMap(new LinkedHashMap<>(meta)));
    }

    /** Идентификатор игрока. */
    public UUID uuid() {
        return uuid;
    }

    /** Последний известный ник или null. */
    public String name() {
        return name;
    }

    /** Идентификатор основной группы или null, если игрок сам её не выбирал. */
    public String primary() {
        return primary;
    }

    /** Выдачи групп со сроками. */
    public List<Grant> groups() {
        return groups;
    }

    /** Личные ноды. */
    public List<NodeEntry> nodes() {
        return nodes;
    }

    /** Личная мета. */
    public Map<String, String> meta() {
        return meta;
    }

    /** Состоит ли игрок в группе прямо сейчас, то есть выдача не истекла. */
    public boolean memberOf(String groupId, long nowMillis) {
        return activeGroupIds(nowMillis).contains(groupId);
    }

    /** Идентификаторы групп с действующей выдачей, в порядке записи. */
    public List<String> activeGroupIds(long nowMillis) {
        List<String> ids = new ArrayList<>();
        for (Grant grant : groups) {
            if (!grant.expiredAt(nowMillis)) {
                ids.add(grant.groupId());
            }
        }
        return Collections.unmodifiableList(ids);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UserRecord)) {
            return false;
        }
        UserRecord that = (UserRecord) other;
        return uuid.equals(that.uuid) && Objects.equals(name, that.name)
            && Objects.equals(primary, that.primary)
            && groups.equals(that.groups)
            && nodes.equals(that.nodes)
            && meta.equals(that.meta);
    }

    @Override
    public int hashCode() {
        return (((uuid.hashCode() * 31 + Objects.hashCode(name)) * 31 + Objects.hashCode(primary)) * 31
            + groups.hashCode()) * 31 + nodes.hashCode();
    }

    @Override
    public String toString() {
        return name != null ? name : uuid.toString();
    }

    /** Выдача группы игроку: какая группа и до какого момента. */
    public static final class Grant {

        private final String groupId;
        private final long expiresAt;

        private Grant(String groupId, long expiresAt) {
            this.groupId = groupId;
            this.expiresAt = expiresAt;
        }

        /** Бессрочная выдача. */
        public static Grant permanent(String groupId) {
            return of(groupId, 0L);
        }

        /**
         * Выдача с сроком.
         *
         * @param expiresAt метка истечения в миллисекундах, ноль значит бессрочно
         * @throws IllegalArgumentException если идентификатор группы пустой
         */
        public static Grant of(String groupId, long expiresAt) {
            Objects.requireNonNull(groupId, "groupId");
            String normalized = groupId.trim()
                .toLowerCase(Locale.ROOT);
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException("Group id must not be empty");
            }
            if (expiresAt < 0) {
                throw new IllegalArgumentException("Expiry must not be negative: " + expiresAt);
            }
            return new Grant(normalized, expiresAt);
        }

        /** Идентификатор группы. */
        public String groupId() {
            return groupId;
        }

        /** Метка истечения в миллисекундах, ноль значит бессрочно. */
        public long expiresAt() {
            return expiresAt;
        }

        /** Правда ли выдача бессрочная. */
        public boolean permanent() {
            return expiresAt == 0L;
        }

        /** Истекшая выдача снимается при первой проверке. */
        public boolean expiredAt(long nowMillis) {
            return !permanent() && nowMillis >= expiresAt;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Grant)) {
                return false;
            }
            Grant that = (Grant) other;
            return expiresAt == that.expiresAt && groupId.equals(that.groupId);
        }

        @Override
        public int hashCode() {
            return (groupId.hashCode() * 31 + (int) (expiresAt ^ (expiresAt >>> 32)));
        }

        @Override
        public String toString() {
            return permanent() ? groupId : groupId + "@" + expiresAt;
        }
    }
}
