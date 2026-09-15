package com.mrleonardos.codeperms.internal.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mrleonardos.codeperms.TestConfigs;
import com.mrleonardos.codeperms.api.PermsLimits;
import com.mrleonardos.codeperms.api.model.ContextSet;
import com.mrleonardos.codeperms.api.model.GroupRecord;
import com.mrleonardos.codeperms.api.model.NodeEntry;
import com.mrleonardos.codeperms.api.model.Snapshot;
import com.mrleonardos.codeperms.internal.store.CoreGroupsImporter.Result;
import com.mrleonardos.codeperms.internal.store.CoreGroupsImporter.Status;

class CoreGroupsImporterTest {

    private static final Logger LOG = LogManager.getLogger(CoreGroupsImporterTest.class);
    private static final UUID STEVE = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    private static final String[] FIXTURE = { "schemaVersion = 1", "", "[groups.player]", "inherits = []",
        "nodes = [\"codechat.create\"]", "", "[groups.player.meta]", "prefix = \"&7\"", "", "[groups.admin]",
        "inherits = [\"player\"]", "nodes = [\"*\"]", "", "[players.069a79f4-44e9-4726-a5be-fca90e38aaf5]",
        "group = \"player\"", "nodes = [\"-codechat.delete\"]" };

    @TempDir
    Path root;

    @Test
    void missingSourceIsReported() {
        Result result = importer().run(Snapshot.empty(), false, false);

        assertEquals(Status.SOURCE_MISSING, result.status());
        assertFalse(result.imported());
    }

    @Test
    void importCarriesGroupsWeightsNodesMetaAndPlayers() {
        writeSource(FIXTURE);

        Result result = importer().run(Snapshot.empty(), false, false);

        assertEquals(Status.IMPORTED, result.status());
        assertEquals(
            2,
            result.counts()
                .groups());
        assertEquals(
            1,
            result.counts()
                .players());
        GroupRecord admin = result.snapshot()
            .group("admin")
            .get();
        assertEquals(1, admin.weight());
        assertTrue(
            admin.nodes()
                .toString()
                .contains("*"));
        assertEquals(
            "&7",
            result.snapshot()
                .group("player")
                .get()
                .meta()
                .get("prefix"));
        assertEquals(
            "player",
            result.snapshot()
                .users()
                .get(STEVE)
                .primary());
    }

    @Test
    void weightsFollowTheOrderOfGroupsInTheFile() {
        writeSource(
            "[groups.zeta]",
            "nodes = []",
            "",
            "[groups.alpha]",
            "nodes = []",
            "",
            "[groups.middle]",
            "nodes = []");

        Result result = importer().run(Snapshot.empty(), false, false);

        assertEquals(
            0,
            result.snapshot()
                .group("zeta")
                .get()
                .weight());
        assertEquals(
            1,
            result.snapshot()
                .group("alpha")
                .get()
                .weight());
        assertEquals(
            2,
            result.snapshot()
                .group("middle")
                .get()
                .weight());
    }

    @Test
    void markerRecordsHashAndSecondImportOfChangedSourceRefuses() {
        writeSource(FIXTURE);
        CoreGroupsImporter importer = importer();
        importer.mark(importer.run(Snapshot.empty(), false, false));

        TestConfigs.write(
            source(),
            TestConfigs.read(source())
                .replace("nodes = [\"*\"]", "nodes = [\"*\", \"codechat.delete\"]"));
        Result refused = importer.run(Snapshot.empty(), false, false);

        assertEquals(Status.SOURCE_CHANGED, refused.status());

        Result forced = importer.run(Snapshot.empty(), true, false);
        assertEquals(Status.IMPORTED, forced.status());
    }

    @Test
    void commentInTheSourceIsNotAChange() {
        writeSource(FIXTURE);
        CoreGroupsImporter importer = importer();
        importer.mark(importer.run(Snapshot.empty(), false, false));

        TestConfigs.write(source(), "# группы завёл админ\n" + TestConfigs.read(source()));

        assertEquals(
            Status.IMPORTED,
            importer.run(Snapshot.empty(), false, false)
                .status(),
            "значения секций те же, значит источник не менялся");
    }

    @Test
    void repeatedImportOfUnchangedSourceIsAllowed() {
        writeSource(FIXTURE);
        CoreGroupsImporter importer = importer();
        importer.mark(importer.run(Snapshot.empty(), false, false));

        assertEquals(
            Status.IMPORTED,
            importer.run(Snapshot.empty(), false, false)
                .status());
    }

    @Test
    void dryRunReportsWithoutWritingMarkerOrSnapshot() {
        writeSource(FIXTURE);
        String before = TestConfigs.read(source());

        Result result = importer().run(Snapshot.empty(), false, true);

        assertEquals(Status.IMPORTED, result.status());
        assertTrue(result.dryRun());
        assertFalse(result.imported());
        assertFalse(marked());
        assertEquals(before, TestConfigs.read(source()));
    }

    @Test
    void exportLandsInOwnDirectoryInCoreFormat() {
        Snapshot snapshot = Snapshot.builder()
            .revision(3L)
            .defaultGroup("player")
            .opGroup("admin")
            .group(PermsFixtures.group("player", 0, "codechat.create"))
            .build();

        Result result = importer().export(snapshot);

        assertEquals(Status.EXPORTED, result.status());
        Path exported = TestConfigs.permissions(root)
            .resolve(com.mrleonardos.codeperms.internal.PermsSettings.EXPORT_DIRECTORY)
            .resolve(CoreGroupsImporter.CORE_FILE);
        assertTrue(Files.isRegularFile(exported));
        assertTrue(
            TestConfigs.read(exported)
                .contains("[groups.player]"));
        assertFalse(Files.exists(source()), "экспорт не трогает файл ядра");
    }

    @Test
    void exportedFileIsReadByTheImporterBack() {
        Snapshot snapshot = Snapshot.builder()
            .revision(3L)
            .group(
                GroupRecord.of(
                    "vip",
                    "",
                    5,
                    Arrays.asList("player"),
                    Arrays.asList(NodeEntry.parse("codechat.create")),
                    meta("prefix", "&7 \"вип\"")))
            .build();
        importer().export(snapshot);

        Path exported = TestConfigs.permissions(root)
            .resolve(com.mrleonardos.codeperms.internal.PermsSettings.EXPORT_DIRECTORY)
            .resolve(CoreGroupsImporter.CORE_FILE);
        TestConfigs.write(source(), TestConfigs.read(exported));
        Result result = importer().run(Snapshot.empty(), false, false);

        assertEquals(Status.IMPORTED, result.status());
        GroupRecord vip = result.snapshot()
            .group("vip")
            .get();
        assertEquals(Arrays.asList("player"), vip.inherits());
        assertEquals(
            "&7 \"вип\"",
            vip.meta()
                .get("prefix"));
    }

    @Test
    void exportSkipsNodesTheCoreFormatCannotExpress() {
        Snapshot snapshot = Snapshot.builder()
            .revision(1L)
            .group(
                GroupRecord.of(
                    "player",
                    "",
                    0,
                    Arrays.asList(""),
                    Arrays.asList(
                        NodeEntry.parse("codechat.create"),
                        NodeEntry.of(
                            "codechat.format",
                            true,
                            ContextSet.builder()
                                .put("world", "nether")
                                .build(),
                            0L)),
                    new LinkedHashMap<String, String>()))
            .build();

        Result result = importer().export(snapshot);

        assertEquals(
            1,
            result.counts()
                .nodesSkipped());
        String exported = TestConfigs.read(
            TestConfigs.permissions(root)
                .resolve(com.mrleonardos.codeperms.internal.PermsSettings.EXPORT_DIRECTORY)
                .resolve(CoreGroupsImporter.CORE_FILE));
        assertTrue(exported.contains("codechat.create"));
        assertFalse(exported.contains("codechat.format"));
    }

    @Test
    void unreadableSourceIsReportedAndTheFileStaysAsItWas() {
        writeSource("[groups.player", "nodes = []");
        String before = TestConfigs.read(source());

        assertEquals(
            Status.SOURCE_UNREADABLE,
            importer().run(Snapshot.empty(), false, false)
                .status());
        assertEquals(before, TestConfigs.read(source()), "чужой файл мод не переписывает");
    }

    @Test
    void inheritedGroupIdsAreNormalized() {
        writeSource(
            "[groups.player]",
            "nodes = [\"codechat.create\"]",
            "",
            "[groups.admin]",
            "inherits = [\" PLAYER \"]",
            "nodes = [\"*\"]");

        Result result = importer().run(Snapshot.empty(), false, false);

        assertEquals(Status.IMPORTED, result.status());
        assertEquals(
            Arrays.asList("player"),
            result.snapshot()
                .group("admin")
                .get()
                .inherits());
    }

    @Test
    void emptySourceIsMarkedSoTheSummaryStaysQuiet() {
        writeSource("# ещё ничего не завели");
        CoreGroupsImporter importer = importer();
        assertTrue(importer.pendingImport());

        Result result = importer.run(Snapshot.empty(), false, false);

        assertEquals(Status.NOTHING_TO_DO, result.status());
        assertFalse(result.imported());
        assertFalse(marked());

        assertTrue(importer.mark(result));
        assertFalse(importer.pendingImport());
        assertTrue(marked());
    }

    @Test
    void runLeavesTheCoreFileAloneUntilTheMarkIsAsked() {
        writeSource(FIXTURE);
        String before = TestConfigs.read(source());
        CoreGroupsImporter importer = importer();

        Result result = importer.run(Snapshot.empty(), false, false);

        assertEquals(Status.IMPORTED, result.status());
        assertTrue(result.imported());
        assertEquals(before, TestConfigs.read(source()));
        assertTrue(importer.pendingImport());

        assertTrue(importer.mark(result));
        assertTrue(marked());
        assertFalse(importer.pendingImport());
    }

    @Test
    void markerKeepsTheValuesAndTheCommentsOfTheSource() {
        writeSource(FIXTURE);
        TestConfigs.write(
            source(),
            TestConfigs.read(source())
                .replace("[groups.player]", "# файл завёл админ\n[groups.player]"));
        CoreGroupsImporter importer = importer();

        importer.mark(importer.run(Snapshot.empty(), false, false));

        String written = TestConfigs.read(source());
        assertTrue(written.contains("# файл завёл админ"), "строки человека остаются на месте: " + written);
        assertTrue(written.contains("codechat.create"), "значения секций остаются на месте");
        assertTrue(
            written.indexOf("[groups.player]") < written.indexOf("[groups.admin]"),
            "порядок групп в чужом файле мод не тасует: " + written);
        assertTrue(written.contains(CoreGroupsImporter.MARKER_HASH));
    }

    @Test
    void emptySourceInDryRunWritesNoMarker() {
        writeSource("# ещё ничего не завели");

        Result result = importer().run(Snapshot.empty(), false, true);

        assertEquals(Status.NOTHING_TO_DO, result.status());
        assertFalse(marked());
    }

    private boolean marked() {
        return TestConfigs.read(source())
            .contains(CoreGroupsImporter.MARKER_FIELD);
    }

    private CoreGroupsImporter importer() {
        return new CoreGroupsImporter(TestConfigs.of(root), () -> PermsLimits.defaults(), LOG);
    }

    private Path source() {
        return TestConfigs.permissions(root)
            .resolve(CoreGroupsImporter.CORE_FILE);
    }

    private void writeSource(String... lines) {
        TestConfigs.write(source(), lines);
    }

    private static LinkedHashMap<String, String> meta(String key, String value) {
        LinkedHashMap<String, String> meta = new LinkedHashMap<>();
        meta.put(key, value);
        return meta;
    }
}
