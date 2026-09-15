package com.mrleonardos.codeperms.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mrleonardos.codeperms.TestConfigs;

class MainSettingsTest {

    @TempDir
    Path root;

    @Test
    void withoutTheMainFileTheFactoryValuesAnswer() {
        MainSettings main = settings();

        assertEquals("player", main.defaultGroup());
        assertEquals("admin", main.opGroup());
        assertEquals("json", main.provider());
        assertEquals(30 * 20, main.autosaveTicks());
        assertEquals(true, main.logChanges());
        assertEquals(false, main.logChecks());
    }

    @Test
    void everySharedValueComesFromTheMainFile() {
        TestConfigs.writeMain(
            root,
            "[owners]",
            "permissions = \"codeperms\"",
            "[storage]",
            "provider = \"sql\"",
            "autosaveSeconds = 5",
            "[audit]",
            "logChanges = false",
            "logChecks = true",
            "[permissions]",
            "defaultGroup = \"guest\"",
            "opGroup = \"owner\"");

        MainSettings main = settings();

        assertEquals("guest", main.defaultGroup());
        assertEquals("owner", main.opGroup());
        assertEquals("sql", main.provider());
        assertEquals(5 * 20, main.autosaveTicks());
        assertEquals(false, main.logChanges());
        assertEquals(true, main.logChecks());
    }

    @Test
    void permissionsMayKeepItsOwnStorageWhileTheRestStaysShared() {
        TestConfigs.writeMain(
            root,
            "[storage]",
            "provider = \"json\"",
            "autosaveSeconds = 30",
            "[storage.permissions]",
            "provider = \"sql\"");

        MainSettings main = settings();

        assertEquals("sql", main.provider());
        assertEquals(30 * 20, main.autosaveTicks(), "перекрытие меняет только названные ключи");
    }

    @Test
    void emptyGroupNameFallsBackToTheFactoryValue() {
        TestConfigs.writeMain(root, "[permissions]", "defaultGroup = \"\"", "opGroup = \"  \"");

        MainSettings main = settings();

        assertEquals("player", main.defaultGroup());
        assertEquals("admin", main.opGroup());
    }

    @Test
    void groupNamesFoldToLowerCaseLikeGroupIdsDo() {
        TestConfigs.writeMain(root, "[permissions]", "defaultGroup = \" Player \"", "opGroup = \"Admin\"");

        MainSettings main = settings();

        assertEquals("player", main.defaultGroup(), "defaultGroup обязан сверяться с id группы в нижнем регистре");
        assertEquals("admin", main.opGroup());
    }

    private MainSettings settings() {
        return new MainSettings(TestConfigs.of(root));
    }
}
