package com.mrleonardos.codeperms.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codecore.api.config.Comment;
import com.mrleonardos.codecore.api.config.ConfigRoles;
import com.mrleonardos.codecore.api.config.ConfigScope;
import com.mrleonardos.codecore.api.config.ConfigSpec;
import com.mrleonardos.codecore.api.config.Migration;
import com.mrleonardos.codeperms.api.PermsLimits;
import com.mrleonardos.codeperms.api.model.NodeEntry;
import com.mrleonardos.codeperms.internal.store.SchemaMigrations;

@Comment({ "Настройки CodePerms. Здесь лежит редкое, а общее для всей линейки в config/code/config.toml:",
    "провайдер хранилища и автосохранение в [storage], записи в лог в [audit],",
    "группа по умолчанию и группа операторов в [permissions].",
    "Группы, треки и игроки лежат рядом в perms-groups.toml и perms-players.json." })
public final class PermsSettings {

    public static final String MODID = "codeperms";
    public static final String GROUPS_FILE = "groups";
    public static final String PLAYERS_FILE = "players";
    public static final String EXPORT_DIRECTORY = "perms-export";

    public static final String DEFAULT_NODE = "codeperms.me";

    public static final int DEFAULT_SCAN_TICKS = 200;
    public static final int DEFAULT_OFFLINE_CACHE_SIZE = 512;

    private static final PermsSettings DEFAULTS = new PermsSettings();

    @Comment({ "Выдавать ли группу операторов тем, кто записан в ops.json.",
        "Имя самой группы стоит в config.toml, ключ opGroup секции [permissions]." })
    public boolean applyOps = true;

    @Comment({ "Правила, которые получает любой игрок до всяких групп.",
        "Минус в начале запрещает: \"-codeperms.me\" отбирает право у всех." })
    public List<String> defaultNodes = new ArrayList<>(Collections.singletonList(DEFAULT_NODE));

    public Limits limits = new Limits();
    public Expiry expiry = new Expiry();
    public Cache cache = new Cache();

    public static PermsSettings defaults() {
        return DEFAULTS;
    }

    public static ConfigSpec<PermsSettings> spec() {
        ConfigSpec.Builder<PermsSettings> builder = ConfigSpec.settings(MODID, PermsSettings.class)
            .role(ConfigRoles.PERMISSIONS)
            .scope(ConfigScope.SETTINGS)
            .schemaVersion(SchemaMigrations.SETTINGS_VERSION);
        for (Migration migration : SchemaMigrations.settingsChain()) {
            builder.migration(migration);
        }
        return builder.defaults(PermsSettings::defaults)
            .build();
    }

    public PermsLimits ceilings() {
        return ceilingsBuilder().build();
    }

    public PermsLimits ceilings(Logger log) {
        PermsLimits.Builder builder = ceilingsBuilder();
        PermsLimits ceilings = builder.build();
        for (String remark : builder.remarks()) {
            log.warn("Config ceiling is unusable: {}", remark);
        }
        return ceilings;
    }

    private PermsLimits.Builder ceilingsBuilder() {
        return PermsLimits.builder()
            .nodeLength(limits.nodeLength)
            .nodeSegments(limits.nodeSegments)
            .nodesPerSubject(limits.nodesPerSubject)
            .groupIdLength(limits.groupIdLength)
            .groups(limits.groups)
            .metaValueLength(limits.metaValueLength)
            .metaKeysPerSubject(limits.metaKeysPerSubject)
            .trackNameLength(limits.trackNameLength);
    }

    public List<NodeEntry> parsedDefaultNodes(PermsLimits ceilings, Logger log) {
        return parseDefaultNodes(ceilings, log);
    }

    private List<NodeEntry> parseDefaultNodes(PermsLimits ceilings, Logger log) {
        List<NodeEntry> parsed = new ArrayList<>();
        for (String raw : defaultNodes) {
            try {
                parsed.add(NodeEntry.parse(raw, ceilings));
            } catch (RuntimeException failure) {
                log.warn("Default node {} is invalid and was skipped: {}", raw, failure.getMessage());
            }
        }
        return parsed;
    }

    public int scanTicks() {
        return Math.max(1, expiry.scanTicks);
    }

    @Comment({ "Потолки, выше которых мод не поднимется даже по этому файлу.",
        "Опустить можно, поднять нет: они защищают память и время разбора файлов.",
        "Запись, которая в потолок не влезла, остаётся в файле нетронутой и в снимок не попадает." })
    public static final class Limits {

        @Comment("Длина одного правила в символах.")
        public int nodeLength = PermsLimits.DEFAULT_NODE_LENGTH;

        @Comment("Сколько точек считается правилом: codechat.channel.global.read это четыре сегмента.")
        public int nodeSegments = PermsLimits.DEFAULT_NODE_SEGMENTS;

        @Comment("Сколько правил читается у одной группы или одного игрока.")
        public int nodesPerSubject = PermsLimits.DEFAULT_NODES_PER_SUBJECT;

        @Comment("Длина имени группы.")
        public int groupIdLength = PermsLimits.DEFAULT_GROUP_ID_LENGTH;

        @Comment("Сколько групп читается из файла.")
        public int groups = PermsLimits.DEFAULT_GROUPS;

        @Comment("Длина значения меты: префикса, суффикса, лимита домов.")
        public int metaValueLength = PermsLimits.DEFAULT_META_VALUE_LENGTH;

        @Comment("Сколько ключей меты читается у одной группы или одного игрока.")
        public int metaKeysPerSubject = PermsLimits.DEFAULT_META_KEYS_PER_SUBJECT;

        @Comment("Длина имени трека.")
        public int trackNameLength = PermsLimits.DEFAULT_TRACK_NAME_LENGTH;
    }

    @Comment("Снятие выдач, у которых вышел срок.")
    public static final class Expiry {

        @Comment({ "Через сколько тиков сервер смотрит, чему вышел срок.", "200 тиков это десять секунд." })
        public int scanTicks = DEFAULT_SCAN_TICKS;
    }

    @Comment("Память под игроков, которых сейчас нет на сервере.")
    public static final class Cache {

        @Comment("Сколько ответов про операторов держать про запас.")
        public int offlineCacheSize = DEFAULT_OFFLINE_CACHE_SIZE;
    }
}
