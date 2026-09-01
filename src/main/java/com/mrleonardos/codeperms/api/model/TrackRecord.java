package com.mrleonardos.codeperms.api.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Трек: упорядоченный список групп от младшей к старшей.
 *
 * <p>
 * По треку работают повышение и понижение: один шаг двигает игрока на соседнюю группу. Трек это
 * порядок, а не право: никаких нод сам он не держит.
 */
public final class TrackRecord {

    private final String name;
    private final List<String> groups;

    private TrackRecord(String name, List<String> groups) {
        this.name = name;
        this.groups = groups;
    }

    /**
     * Собрать трек.
     *
     * @param name   имя трека
     * @param groups идентификаторы групп от младшей к старшей, повторов быть не должно
     * @throws IllegalArgumentException если имя пустое, список пуст или группа в нём повторяется
     */
    public static TrackRecord of(String name, List<String> groups) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(groups, "groups");
        String normalized = name.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Track name must not be empty");
        }
        if (groups.isEmpty()) {
            throw new IllegalArgumentException("Track " + normalized + " needs at least one group");
        }
        Set<String> seen = new HashSet<>();
        for (String groupId : groups) {
            Objects.requireNonNull(groupId, "groupId");
            if (!seen.add(groupId)) {
                throw new IllegalArgumentException("Track " + normalized + " holds group " + groupId + " twice");
            }
        }
        return new TrackRecord(normalized, Collections.unmodifiableList(new ArrayList<>(groups)));
    }

    /** Имя трека. */
    public String name() {
        return name;
    }

    /** Группы от младшей к старшей. */
    public List<String> groups() {
        return groups;
    }

    /** Номер группы в треке или -1, если группы в треке нет. */
    public int position(String groupId) {
        return groups.indexOf(groupId);
    }

    /** Соседняя группа выше по треку. */
    public Optional<String> next(String groupId) {
        return neighbour(groupId, 1);
    }

    /** Соседняя группа ниже по треку. */
    public Optional<String> previous(String groupId) {
        return neighbour(groupId, -1);
    }

    private Optional<String> neighbour(String groupId, int step) {
        int index = position(groupId);
        if (index < 0) {
            return Optional.empty();
        }
        int target = index + step;
        if (target < 0 || target >= groups.size()) {
            return Optional.empty();
        }
        return Optional.of(groups.get(target));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TrackRecord)) {
            return false;
        }
        TrackRecord that = (TrackRecord) other;
        return name.equals(that.name) && groups.equals(that.groups);
    }

    @Override
    public int hashCode() {
        return name.hashCode() * 31 + groups.hashCode();
    }

    @Override
    public String toString() {
        return name + ":" + groups;
    }
}
