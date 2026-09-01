package com.mrleonardos.codeperms.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codecore.api.config.ConfigScope;
import com.mrleonardos.codecore.api.config.ConfigSpec;
import com.mrleonardos.codecore.api.config.Migration;
import com.mrleonardos.codecore.api.service.ServicePriority;
import com.mrleonardos.codeperms.api.PermsLimits;
import com.mrleonardos.codeperms.api.model.NodeEntry;
import com.mrleonardos.codeperms.internal.store.SchemaMigrations;

public final class PermsSettings {

    public static final String MODID = "codeperms";
    public static final String SETTINGS_FILE = "config";
    public static final String GROUPS_FILE = "groups";
    public static final String PLAYERS_FILE = "players";
    public static final String EXPORT_DIRECTORY = "export";

    public static final String DEFAULT_PROVIDER = "json";
    public static final String DEFAULT_GROUP = "player";
    public static final String OP_GROUP = "admin";
    public static final String DEFAULT_NODE = "codeperms.me";
    public static final ServicePriority DEFAULT_SERVICE_PRIORITY = ServicePriority.ADDON;

    public static final int DEFAULT_AUTOSAVE_SECONDS = 30;
    public static final int DEFAULT_SCAN_TICKS = 200;
    public static final int DEFAULT_OFFLINE_CACHE_SIZE = 512;

    private static final PermsSettings DEFAULTS = new PermsSettings();

    public Storage storage = new Storage();
    public String defaultGroup = DEFAULT_GROUP;
    public String opGroup = OP_GROUP;
    public String servicePriority = DEFAULT_SERVICE_PRIORITY.name();
    public boolean applyOps = true;
    public List<String> defaultNodes = new ArrayList<>(Collections.singletonList(DEFAULT_NODE));
    public Limits limits = new Limits();
    public Expiry expiry = new Expiry();
    public Cache cache = new Cache();
    public int autosaveSeconds = DEFAULT_AUTOSAVE_SECONDS;
    public Audit audit = new Audit();

    public static PermsSettings defaults() {
        return DEFAULTS;
    }

    public static ConfigSpec<PermsSettings> spec() {
        ConfigSpec.Builder<PermsSettings> builder = ConfigSpec.of(MODID, SETTINGS_FILE, PermsSettings.class)
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

    public ServicePriority priority(Logger log) {
        String requested = servicePriority == null ? "" : servicePriority.trim();
        for (ServicePriority known : ServicePriority.values()) {
            if (known.name()
                .equalsIgnoreCase(requested)) {
                return known;
            }
        }
        log.warn(
            "servicePriority = {} is not one of {}, {} is used",
            servicePriority,
            Arrays.toString(ServicePriority.values()),
            DEFAULT_SERVICE_PRIORITY);
        return DEFAULT_SERVICE_PRIORITY;
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

    public String provider() {
        return storage.provider == null || storage.provider.trim()
            .isEmpty() ? DEFAULT_PROVIDER : storage.provider.trim();
    }

    public int autosaveTicks() {
        return Math.max(1, autosaveSeconds) * 20;
    }

    public int scanTicks() {
        return Math.max(1, expiry.scanTicks);
    }

    public static final class Storage {

        public String provider = DEFAULT_PROVIDER;
    }

    public static final class Limits {

        public int nodeLength = PermsLimits.DEFAULT_NODE_LENGTH;
        public int nodeSegments = PermsLimits.DEFAULT_NODE_SEGMENTS;
        public int nodesPerSubject = PermsLimits.DEFAULT_NODES_PER_SUBJECT;
        public int groupIdLength = PermsLimits.DEFAULT_GROUP_ID_LENGTH;
        public int groups = PermsLimits.DEFAULT_GROUPS;
        public int metaValueLength = PermsLimits.DEFAULT_META_VALUE_LENGTH;
        public int metaKeysPerSubject = PermsLimits.DEFAULT_META_KEYS_PER_SUBJECT;
        public int trackNameLength = PermsLimits.DEFAULT_TRACK_NAME_LENGTH;
    }

    public static final class Expiry {

        public int scanTicks = DEFAULT_SCAN_TICKS;
    }

    public static final class Cache {

        public int offlineCacheSize = DEFAULT_OFFLINE_CACHE_SIZE;
    }

    public static final class Audit {

        public boolean logChanges = true;
        public boolean logChecks = false;
    }
}
