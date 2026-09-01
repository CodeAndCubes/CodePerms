package com.mrleonardos.codeperms.internal.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SchemaMigrationsTest {

    @Test
    void firstVersionIsCurrentForEveryFile() {
        assertEquals(1, SchemaMigrations.SETTINGS_VERSION);
        assertEquals(1, SchemaMigrations.GROUPS_VERSION);
        assertEquals(1, SchemaMigrations.PLAYERS_VERSION);
    }

    @Test
    void chainsAreEmptyWhileFirstVersionIsCurrent() {
        assertTrue(
            SchemaMigrations.settingsChain()
                .isEmpty());
        assertTrue(
            SchemaMigrations.groupsChain()
                .isEmpty());
        assertTrue(
            SchemaMigrations.playersChain()
                .isEmpty());
    }
}
