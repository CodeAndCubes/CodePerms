package com.mrleonardos.codeperms.api.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Группа: ноды, мета, вес и родители.
 *
 * <p>
 * Вес задаёт старшинство: большее число значит более старшую группу. Родители наследуются цепочкой,
 * цикл при разборе отсекается множеством посещённых групп, поэтому проверка не зацикливается.
 */
public final class GroupRecord {

    private final String id;
    private final String displayName;
    private final int weight;
    private final List<String> inherits;
    private final List<NodeEntry> nodes;
    private final Map<String, String> meta;

    private GroupRecord(String id, String displayName, int weight, List<String> inherits, List<NodeEntry> nodes,
        Map<String, String> meta) {
        this.id = id;
        this.displayName = displayName;
        this.weight = weight;
        this.inherits = inherits;
        this.nodes = nodes;
        this.meta = meta;
    }

    /**
     * Собрать группу.
     *
     * @param id          идентификатор в нижнем регистре, он же ключ в хранилище
     * @param displayName имя для вывода человеку; пустое заменяется на идентификатор
     * @param weight      вес, большее число значит старше
     * @param inherits    идентификаторы родителей
     * @param nodes       ноды группы
     * @param meta        мета группы, карта строк
     * @throws IllegalArgumentException если идентификатор пустой или не в нижнем регистре
     */
    public static GroupRecord of(String id, String displayName, int weight, List<String> inherits,
        List<NodeEntry> nodes, Map<String, String> meta) {
        Objects.requireNonNull(inherits, "inherits");
        Objects.requireNonNull(nodes, "nodes");
        Objects.requireNonNull(meta, "meta");
        String normalizedId = normalizeId(id);
        String name = displayName == null || displayName.trim()
            .isEmpty() ? normalizedId : displayName;
        return new GroupRecord(
            normalizedId,
            name,
            weight,
            Collections.unmodifiableList(new ArrayList<>(inherits)),
            Collections.unmodifiableList(new ArrayList<>(nodes)),
            Collections.unmodifiableMap(new LinkedHashMap<>(meta)));
    }

    /** Идентификатор в нижнем регистре. */
    public String id() {
        return id;
    }

    /** Имя для вывода человеку. */
    public String displayName() {
        return displayName;
    }

    /** Вес, большее число значит старше. */
    public int weight() {
        return weight;
    }

    /** Идентификаторы родителей. */
    public List<String> inherits() {
        return inherits;
    }

    /** Ноды группы. */
    public List<NodeEntry> nodes() {
        return nodes;
    }

    /** Мета группы. */
    public Map<String, String> meta() {
        return meta;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GroupRecord)) {
            return false;
        }
        GroupRecord that = (GroupRecord) other;
        return weight == that.weight && id.equals(that.id)
            && displayName.equals(that.displayName)
            && inherits.equals(that.inherits)
            && nodes.equals(that.nodes)
            && meta.equals(that.meta);
    }

    @Override
    public int hashCode() {
        return ((((id.hashCode() * 31 + displayName.hashCode()) * 31 + weight) * 31 + inherits.hashCode()) * 31
            + nodes.hashCode()) * 31 + meta.hashCode();
    }

    @Override
    public String toString() {
        return id + "#" + weight;
    }

    private static String normalizeId(String id) {
        Objects.requireNonNull(id, "id");
        String normalized = id.trim()
            .toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Group id must not be empty");
        }
        if (!normalized.equals(id)) {
            throw new IllegalArgumentException("Group id must be lower case: " + id);
        }
        return normalized;
    }
}
