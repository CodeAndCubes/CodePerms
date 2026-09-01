package com.mrleonardos.codeperms.internal.store;

import java.util.Collections;
import java.util.List;

import com.mrleonardos.codecore.api.config.Migration;

public final class SchemaMigrations {

    public static final String VERSION_FIELD = "schemaVersion";

    public static final int SETTINGS_VERSION = 1;
    public static final int GROUPS_VERSION = 1;
    public static final int PLAYERS_VERSION = 1;

    private static final List<Migration> SETTINGS_CHAIN = Collections.emptyList();
    private static final List<Migration> GROUPS_CHAIN = Collections.emptyList();
    private static final List<Migration> PLAYERS_CHAIN = Collections.emptyList();

    private SchemaMigrations() {}

    public static List<Migration> settingsChain() {
        return SETTINGS_CHAIN;
    }

    public static List<Migration> groupsChain() {
        return GROUPS_CHAIN;
    }

    public static List<Migration> playersChain() {
        return PLAYERS_CHAIN;
    }
}
