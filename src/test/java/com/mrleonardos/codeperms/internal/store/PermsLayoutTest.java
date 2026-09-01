package com.mrleonardos.codeperms.internal.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mrleonardos.codecore.api.config.ConfigService;
import com.mrleonardos.codeperms.TestConfigs;
import com.mrleonardos.codeperms.internal.PermsSettings;

class PermsLayoutTest {

    private static final List<String> MOVED_KEYS = Arrays
        .asList("provider", "autosaveSeconds", "logChanges", "logChecks", "defaultGroup", "opGroup", "servicePriority");

    private static final Set<String> OWN_KEYS = new LinkedHashSet<>(
        Arrays.asList(
            "schemaVersion",
            "applyOps",
            "defaultNodes",
            "nodeLength",
            "nodeSegments",
            "nodesPerSubject",
            "groupIdLength",
            "groups",
            "metaValueLength",
            "metaKeysPerSubject",
            "trackNameLength",
            "scanTicks",
            "offlineCacheSize"));

    @TempDir
    Path root;

    @BeforeEach
    void openEveryFile() {
        ConfigService configs = TestConfigs.of(root);
        configs.open(PermsSettings.spec());
        configs.open(GroupsStore.spec());
        configs.open(PlayersStore.spec());
    }

    @Test
    void firstRunCreatesThreeFilesInThePermissionsDirectory() {
        assertTrue(Files.isRegularFile(permissions().resolve("perms.toml")));
        assertTrue(Files.isRegularFile(permissions().resolve("perms-groups.toml")));
        assertTrue(Files.isRegularFile(permissions().resolve("perms-players.json")));
    }

    @Test
    void ownerIsPartOfEveryNameSoTheCoreFileFitsInTheSameDirectory() {
        TestConfigs.write(permissions().resolve(CoreGroupsImporter.CORE_FILE), "# файл встроенной реализации ядра");

        List<String> names = new ArrayList<>();
        for (Path file : listing(permissions())) {
            names.add(
                file.getFileName()
                    .toString());
        }

        assertEquals(Arrays.asList("core-groups.toml", "perms-groups.toml", "perms-players.json", "perms.toml"), names);
    }

    @Test
    void settingsFileHoldsTheRareValuesAndNothingElse() {
        assertEquals(OWN_KEYS, keysOf(TestConfigs.read(permissions().resolve("perms.toml"))));
    }

    @Test
    void whatMovedToTheMainFileIsGoneFromTheSettingsFile() {
        String text = TestConfigs.read(permissions().resolve("perms.toml"));

        for (String key : MOVED_KEYS) {
            assertFalse(keysOf(text).contains(key), "ключ " + key + " остался в perms.toml");
        }
    }

    @Test
    void everyFieldOfTheSettingsClassIsDescribedInTheFile() {
        String text = TestConfigs.read(permissions().resolve("perms.toml"));

        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")
                || trimmed.startsWith("[")
                || trimmed.startsWith("schemaVersion")) {
                continue;
            }
            assertTrue(described(text, trimmed), "у ключа " + trimmed + " нет строки описания над ним");
        }
    }

    private static boolean described(String text, String line) {
        String[] lines = text.split("\n");
        for (int index = 1; index < lines.length; index++) {
            if (lines[index].trim()
                .equals(line)) {
                return lines[index - 1].trim()
                    .startsWith("#");
            }
        }
        return false;
    }

    private static Set<String> keysOf(String text) {
        Set<String> keys = new LinkedHashSet<>();
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            int equals = trimmed.indexOf('=');
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("[") || equals < 0) {
                continue;
            }
            keys.add(
                trimmed.substring(0, equals)
                    .trim());
        }
        return keys;
    }

    private static List<Path> listing(Path directory) {
        List<Path> files = new ArrayList<>();
        try (java.util.stream.Stream<Path> stream = Files.list(directory)) {
            stream.sorted()
                .forEach(files::add);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException(failure);
        }
        return files;
    }

    private Path permissions() {
        return TestConfigs.permissions(root);
    }
}
