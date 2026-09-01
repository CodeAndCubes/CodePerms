package com.mrleonardos.codeperms.internal;

import com.mrleonardos.codecore.api.config.AuditSettings;
import com.mrleonardos.codecore.api.config.ConfigRoles;
import com.mrleonardos.codecore.api.config.ConfigService;
import com.mrleonardos.codecore.api.config.StorageSettings;

public final class MainSettings {

    public static final String DEFAULT_GROUP = "player";
    public static final String OP_GROUP = "admin";
    public static final String DEFAULT_PROVIDER = "json";
    public static final int TICKS_PER_SECOND = 20;

    private static final String DEFAULT_GROUP_KEY = "permissions.defaultGroup";
    private static final String OP_GROUP_KEY = "permissions.opGroup";

    private final ConfigService configs;

    public MainSettings(ConfigService configs) {
        this.configs = configs;
    }

    public String defaultGroup() {
        return name(
            configs.main()
                .string(DEFAULT_GROUP_KEY, DEFAULT_GROUP),
            DEFAULT_GROUP);
    }

    public String opGroup() {
        return name(
            configs.main()
                .string(OP_GROUP_KEY, OP_GROUP),
            OP_GROUP);
    }

    public String provider() {
        String named = storage().provider();
        return named == null || named.trim()
            .isEmpty() ? DEFAULT_PROVIDER : named.trim();
    }

    public int autosaveTicks() {
        return Math.max(1, storage().autosaveSeconds()) * TICKS_PER_SECOND;
    }

    public boolean logChanges() {
        return audit().logChanges();
    }

    public boolean logChecks() {
        return audit().logChecks();
    }

    private StorageSettings storage() {
        return configs.storage(ConfigRoles.PERMISSIONS);
    }

    private AuditSettings audit() {
        return configs.audit(ConfigRoles.PERMISSIONS);
    }

    private static String name(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }
}
