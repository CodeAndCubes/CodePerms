package com.mrleonardos.codeperms.api;

import java.util.ArrayList;
import java.util.List;

/**
 * Потолки модели: сколько символов и элементов способна унести одна правка.
 *
 * <p>
 * Нужны там, где значение приходит от человека или от чужого мода: команда, вызов API, разбор файла.
 * Без потолка одна команда с длинным хвостом записывает в хранилище мегабайт мусора.
 *
 * <p>
 * Заводские значения это верхняя граница. Через конфиг потолки меняются только вниз:
 * {@link #loweredTo(PermsLimits)} берёт минимум по каждому полю, поэтому поднять потолок выше
 * заводского нельзя. Значение меньше единицы потолком быть не может, поэтому оно считается незаданным
 * и заменяется заводским с замечанием в {@link Builder#remarks()}: опечатка в конфиге не роняет мод и
 * не обесправливает сервер молча.
 */
public final class PermsLimits {

    /** Длина ноды в символах. */
    public static final int DEFAULT_NODE_LENGTH = 128;

    /** Число сегментов ноды, разделитель точка. */
    public static final int DEFAULT_NODE_SEGMENTS = 16;

    /** Сколько нод допускается у одной группы или игрока. */
    public static final int DEFAULT_NODES_PER_SUBJECT = 1024;

    /** Длина идентификатора группы в символах. */
    public static final int DEFAULT_GROUP_ID_LENGTH = 32;

    /** Сколько групп хранится всего. */
    public static final int DEFAULT_GROUPS = 512;

    /** Длина мета-значения в символах. */
    public static final int DEFAULT_META_VALUE_LENGTH = 256;

    /** Сколько мета-ключей допускается у одной группы или игрока. */
    public static final int DEFAULT_META_KEYS_PER_SUBJECT = 32;

    /** Длина имени трека в символах. */
    public static final int DEFAULT_TRACK_NAME_LENGTH = 32;

    private final int nodeLength;
    private final int nodeSegments;
    private final int nodesPerSubject;
    private final int groupIdLength;
    private final int groups;
    private final int metaValueLength;
    private final int metaKeysPerSubject;
    private final int trackNameLength;

    private PermsLimits(int nodeLength, int nodeSegments, int nodesPerSubject, int groupIdLength, int groups,
        int metaValueLength, int metaKeysPerSubject, int trackNameLength) {
        this.nodeLength = nodeLength;
        this.nodeSegments = nodeSegments;
        this.nodesPerSubject = nodesPerSubject;
        this.groupIdLength = groupIdLength;
        this.groups = groups;
        this.metaValueLength = metaValueLength;
        this.metaKeysPerSubject = metaKeysPerSubject;
        this.trackNameLength = trackNameLength;
    }

    /** Заводские потолки. */
    public static PermsLimits defaults() {
        return new PermsLimits(
            DEFAULT_NODE_LENGTH,
            DEFAULT_NODE_SEGMENTS,
            DEFAULT_NODES_PER_SUBJECT,
            DEFAULT_GROUP_ID_LENGTH,
            DEFAULT_GROUPS,
            DEFAULT_META_VALUE_LENGTH,
            DEFAULT_META_KEYS_PER_SUBJECT,
            DEFAULT_TRACK_NAME_LENGTH);
    }

    /** Начать собирать потолки из значений конфига. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Те же потолки, ужатые до указанных.
     *
     * <p>
     * По каждому полю берётся меньшее из двух значений, поэтому конфиг способен опустить потолок, но не
     * поднять его выше заводского.
     */
    public PermsLimits loweredTo(PermsLimits requested) {
        return new PermsLimits(
            Math.min(nodeLength, requested.nodeLength),
            Math.min(nodeSegments, requested.nodeSegments),
            Math.min(nodesPerSubject, requested.nodesPerSubject),
            Math.min(groupIdLength, requested.groupIdLength),
            Math.min(groups, requested.groups),
            Math.min(metaValueLength, requested.metaValueLength),
            Math.min(metaKeysPerSubject, requested.metaKeysPerSubject),
            Math.min(trackNameLength, requested.trackNameLength));
    }

    /** Длина ноды в символах. */
    public int nodeLength() {
        return nodeLength;
    }

    /** Число сегментов ноды. */
    public int nodeSegments() {
        return nodeSegments;
    }

    /** Сколько нод допускается у одного субъекта. */
    public int nodesPerSubject() {
        return nodesPerSubject;
    }

    /** Длина идентификатора группы в символах. */
    public int groupIdLength() {
        return groupIdLength;
    }

    /** Сколько групп хранится всего. */
    public int groups() {
        return groups;
    }

    /** Длина мета-значения в символах. */
    public int metaValueLength() {
        return metaValueLength;
    }

    /** Сколько мета-ключей допускается у одного субъекта. */
    public int metaKeysPerSubject() {
        return metaKeysPerSubject;
    }

    /** Длина имени трека в символах. */
    public int trackNameLength() {
        return trackNameLength;
    }

    /** Вписывается ли нода в потолки по длине и числу сегментов. */
    public boolean acceptsNode(String node) {
        if (node == null || node.isEmpty() || node.length() > nodeLength) {
            return false;
        }
        return splitSegments(node) <= nodeSegments;
    }

    /** Вписывается ли идентификатор группы в потолок по длине. */
    public boolean acceptsGroupId(String id) {
        return id != null && !id.isEmpty() && id.length() <= groupIdLength;
    }

    /** Вписывается ли мета-значение в потолок по длине. */
    public boolean acceptsMetaValue(String value) {
        return value != null && value.length() <= metaValueLength;
    }

    /** Вписывается ли имя трека в потолок по длине. */
    public boolean acceptsTrackName(String name) {
        return name != null && !name.isEmpty() && name.length() <= trackNameLength;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PermsLimits)) {
            return false;
        }
        PermsLimits that = (PermsLimits) other;
        return nodeLength == that.nodeLength && nodeSegments == that.nodeSegments
            && nodesPerSubject == that.nodesPerSubject
            && groupIdLength == that.groupIdLength
            && groups == that.groups
            && metaValueLength == that.metaValueLength
            && metaKeysPerSubject == that.metaKeysPerSubject
            && trackNameLength == that.trackNameLength;
    }

    @Override
    public int hashCode() {
        return ((((((nodeLength * 31 + nodeSegments) * 31 + nodesPerSubject) * 31 + groupIdLength) * 31 + groups) * 31
            + metaValueLength) * 31 + metaKeysPerSubject) * 31 + trackNameLength;
    }

    @Override
    public String toString() {
        return "nodes " + nodesPerSubject
            + "x"
            + nodeLength
            + "ch/"
            + nodeSegments
            + ", groups "
            + groups
            + "x"
            + groupIdLength
            + "ch, meta "
            + metaKeysPerSubject
            + "x"
            + metaValueLength
            + "ch, tracks "
            + trackNameLength
            + "ch";
    }

    private static int splitSegments(String value) {
        return value.split("\\.", -1).length;
    }

    /** Сборщик потолков из значений конфига. */
    public static final class Builder {

        private final List<String> remarks = new ArrayList<>();

        private int nodeLength = DEFAULT_NODE_LENGTH;
        private int nodeSegments = DEFAULT_NODE_SEGMENTS;
        private int nodesPerSubject = DEFAULT_NODES_PER_SUBJECT;
        private int groupIdLength = DEFAULT_GROUP_ID_LENGTH;
        private int groups = DEFAULT_GROUPS;
        private int metaValueLength = DEFAULT_META_VALUE_LENGTH;
        private int metaKeysPerSubject = DEFAULT_META_KEYS_PER_SUBJECT;
        private int trackNameLength = DEFAULT_TRACK_NAME_LENGTH;

        private Builder() {}

        /** Длина ноды в символах. Значение выше заводского ужимается до заводского. */
        public Builder nodeLength(int value) {
            nodeLength = clamp(value, DEFAULT_NODE_LENGTH, "limits.nodeLength");
            return this;
        }

        /** Число сегментов ноды. Значение выше заводского ужимается до заводского. */
        public Builder nodeSegments(int value) {
            nodeSegments = clamp(value, DEFAULT_NODE_SEGMENTS, "limits.nodeSegments");
            return this;
        }

        /** Нод на субъекта. Значение выше заводского ужимается до заводского. */
        public Builder nodesPerSubject(int value) {
            nodesPerSubject = clamp(value, DEFAULT_NODES_PER_SUBJECT, "limits.nodesPerSubject");
            return this;
        }

        /** Длина идентификатора группы. Значение выше заводского ужимается до заводского. */
        public Builder groupIdLength(int value) {
            groupIdLength = clamp(value, DEFAULT_GROUP_ID_LENGTH, "limits.groupIdLength");
            return this;
        }

        /** Число групп. Значение выше заводского ужимается до заводского. */
        public Builder groups(int value) {
            groups = clamp(value, DEFAULT_GROUPS, "limits.groups");
            return this;
        }

        /** Длина мета-значения. Значение выше заводского ужимается до заводского. */
        public Builder metaValueLength(int value) {
            metaValueLength = clamp(value, DEFAULT_META_VALUE_LENGTH, "limits.metaValueLength");
            return this;
        }

        /** Мета-ключей на субъекта. Значение выше заводского ужимается до заводского. */
        public Builder metaKeysPerSubject(int value) {
            metaKeysPerSubject = clamp(value, DEFAULT_META_KEYS_PER_SUBJECT, "limits.metaKeysPerSubject");
            return this;
        }

        /** Длина имени трека. Значение выше заводского ужимается до заводского. */
        public Builder trackNameLength(int value) {
            trackNameLength = clamp(value, DEFAULT_TRACK_NAME_LENGTH, "limits.trackNameLength");
            return this;
        }

        /** Замечания о значениях конфига, которые пришлось заменить заводскими. */
        public List<String> remarks() {
            return new ArrayList<>(remarks);
        }

        /** Готовые потолки. */
        public PermsLimits build() {
            return new PermsLimits(
                nodeLength,
                nodeSegments,
                nodesPerSubject,
                groupIdLength,
                groups,
                metaValueLength,
                metaKeysPerSubject,
                trackNameLength);
        }

        private int clamp(int value, int factoryValue, String field) {
            if (value < 1) {
                remarks.add(
                    field + " = "
                        + value
                        + " carries no usable ceiling, the factory value "
                        + factoryValue
                        + " is used");
                return factoryValue;
            }
            return Math.min(value, factoryValue);
        }
    }
}
