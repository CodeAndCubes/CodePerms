package com.mrleonardos.codeperms.internal.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;
import com.mrleonardos.codeperms.api.PermsLimits;
import com.mrleonardos.codeperms.api.model.GroupRecord;
import com.mrleonardos.codeperms.api.model.Snapshot;
import com.mrleonardos.codeperms.internal.store.CoreJsonImporter.Result;
import com.mrleonardos.codeperms.internal.store.CoreJsonImporter.Status;

class CoreJsonImporterTest {

    private static final Logger LOG = LogManager.getLogger(CoreJsonImporterTest.class);

    @TempDir
    Path root;

    private Path coreFile;
    private Path exportDirectory;

    @BeforeEach
    void setUp() {
        coreFile = root.resolve("codecore")
            .resolve("permissions.json");
        exportDirectory = root.resolve("codeperms")
            .resolve("export");
    }

    @Test
    void missingSourceIsReported() {
        Result result = importer().run(Snapshot.empty(), false, false);

        assertEquals(Status.SOURCE_MISSING, result.status());
        assertFalse(result.imported());
    }

    @Test
    void importCarriesGroupsWeightsNodesMetaAndPlayers() throws Exception {
        write(coreFile(), fixture());

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
        assertEquals(
            "player",
            result.snapshot()
                .defaultGroup());
        GroupRecord admin = result.snapshot()
            .group("admin")
            .get();
        assertEquals(1, admin.weight());
        assertTrue(
            admin.nodes()
                .toString()
                .contains("*"));
        assertTrue(
            result.snapshot()
                .users()
                .containsKey(UUID_OF_STEVE));
        assertEquals(
            "player",
            result.snapshot()
                .users()
                .get(UUID_OF_STEVE)
                .primary());
    }

    @Test
    void markerRecordsHashAndSecondImportOfChangedSourceRefuses() throws Exception {
        write(coreFile(), fixture());
        CoreJsonImporter importer = importer();
        importer.mark(importer.run(Snapshot.empty(), false, false));

        writeJson(coreFile(), editedFixture());
        Result refused = importer.run(Snapshot.empty(), false, false);

        assertEquals(Status.SOURCE_CHANGED, refused.status());

        Result forced = importer.run(Snapshot.empty(), true, false);
        assertEquals(Status.IMPORTED, forced.status());
    }

    @Test
    void repeatedImportOfUnchangedSourceIsAllowed() throws Exception {
        write(coreFile(), fixture());
        CoreJsonImporter importer = importer();
        importer.mark(importer.run(Snapshot.empty(), false, false));

        assertEquals(
            Status.IMPORTED,
            importer.run(Snapshot.empty(), false, false)
                .status());
    }

    @Test
    void dryRunReportsWithoutWritingMarkerOrSnapshot() throws Exception {
        write(coreFile(), fixture());
        String before = new String(Files.readAllBytes(coreFile), StandardCharsets.UTF_8);

        Result result = importer().run(Snapshot.empty(), false, true);

        assertEquals(Status.IMPORTED, result.status());
        assertTrue(result.dryRun());
        assertFalse(result.imported());
        assertFalse(dataHasMarker());
        assertEquals(before, new String(Files.readAllBytes(coreFile), StandardCharsets.UTF_8));
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
        assertTrue(Files.isRegularFile(exportDirectory.resolve("permissions.json")));
        assertFalse(
            Files.exists(
                root.resolve("codecore")
                    .resolve("permissions.json")));
    }

    @Test
    void exportSkipsNodesTheCoreFormatCannotExpress() throws Exception {
        Snapshot snapshot = Snapshot.builder()
            .revision(1L)
            .defaultGroup("player")
            .group(
                com.mrleonardos.codeperms.api.model.GroupRecord.of(
                    "player",
                    "",
                    0,
                    Arrays.asList(""),
                    Arrays.asList(
                        com.mrleonardos.codeperms.api.model.NodeEntry.parse("codechat.create"),
                        com.mrleonardos.codeperms.api.model.NodeEntry.of(
                            "codechat.format",
                            true,
                            com.mrleonardos.codeperms.api.model.ContextSet.builder()
                                .put("world", "nether")
                                .build(),
                            0L)),
                    new java.util.LinkedHashMap<String, String>()))
            .build();

        Result result = importer().export(snapshot);

        assertEquals(
            1,
            result.counts()
                .nodesSkipped());
        String exported = new String(
            Files.readAllBytes(exportDirectory.resolve("permissions.json")),
            StandardCharsets.UTF_8);
        assertTrue(exported.contains("codechat.create"));
        assertFalse(exported.contains("codechat.format"));
    }

    @Test
    void unreadableSourceIsReportedSeparately() throws Exception {
        Files.createDirectories(coreFile.getParent());
        Files.write(coreFile, Arrays.asList("not a json at all"));

        assertEquals(
            Status.SOURCE_UNREADABLE,
            importer().run(Snapshot.empty(), false, false)
                .status());
    }

    @Test
    void inheritedGroupIdsAreNormalized() throws Exception {
        write(
            coreFile(),
            "{" + "\"defaultGroup\": \"player\","
                + "\"groups\": {"
                + "  \"player\": {\"inherits\": [], \"nodes\": [\"codechat.create\"], \"meta\": {}},"
                + "  \"admin\": {\"inherits\": [\" PLAYER \"], \"nodes\": [\"*\"], \"meta\": {}}"
                + "},"
                + "\"players\": {}"
                + "}");

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
    void emptySourceIsMarkedSoTheSummaryStaysQuiet() throws Exception {
        write(coreFile(), "{\"groups\": {}, \"players\": {}}");
        CoreJsonImporter importer = importer();
        assertTrue(importer.pendingImport());

        Result result = importer.run(Snapshot.empty(), false, false);

        assertEquals(Status.NOTHING_TO_DO, result.status());
        assertFalse(result.imported());
        assertFalse(dataHasMarker());

        assertTrue(importer.mark(result));
        assertFalse(importer.pendingImport());
        assertTrue(dataHasMarker());
    }

    @Test
    void runLeavesTheCoreFileAloneUntilTheMarkIsAsked() throws Exception {
        write(coreFile(), fixture());
        String before = new String(Files.readAllBytes(coreFile), StandardCharsets.UTF_8);
        CoreJsonImporter importer = importer();

        Result result = importer.run(Snapshot.empty(), false, false);

        assertEquals(Status.IMPORTED, result.status());
        assertTrue(result.imported());
        assertEquals(before, new String(Files.readAllBytes(coreFile), StandardCharsets.UTF_8));
        assertTrue(importer.pendingImport());

        assertTrue(importer.mark(result));
        assertTrue(dataHasMarker());
        assertFalse(importer.pendingImport());
    }

    @Test
    void emptySourceInDryRunWritesNoMarker() throws Exception {
        write(coreFile(), "{\"groups\": {}, \"players\": {}}");

        Result result = importer().run(Snapshot.empty(), false, true);

        assertEquals(Status.NOTHING_TO_DO, result.status());
        assertFalse(dataHasMarker());
    }

    private boolean dataHasMarker() throws Exception {
        return new String(Files.readAllBytes(coreFile), StandardCharsets.UTF_8).contains("codepermsImport");
    }

    private CoreJsonImporter importer() {
        return new CoreJsonImporter(coreFile, exportDirectory, PermsLimits.defaults(), LOG);
    }

    private Path coreFile() {
        return coreFile;
    }

    private void write(Path target, String content) throws Exception {
        Files.createDirectories(target.getParent());
        Files.write(target, Arrays.asList(content.split("\n", -1)));
    }

    private JsonObject editedFixture() throws Exception {
        JsonObject data = StubConfigService.Json.read(coreFile())
            .getAsJsonObject();
        data.get("groups")
            .getAsJsonObject()
            .get("player")
            .getAsJsonObject()
            .get("nodes")
            .getAsJsonArray()
            .add(new com.google.gson.JsonPrimitive("codechat.delete"));
        return data;
    }

    private void writeJson(Path target, JsonObject data) throws Exception {
        Files.write(target, Arrays.asList(StubConfigService.Json.GSON.toJson(data)));
    }

    private String fixture() {
        return "{" + "\"schemaVersion\": 1,"
            + "\"defaultGroup\": \"player\","
            + "\"opGroup\": \"admin\","
            + "\"groups\": {"
            + "  \"player\": {\"inherits\": [], \"nodes\": [\"codechat.create\"], \"meta\": {\"prefix\": \"&7\"}},"
            + "  \"admin\": {\"inherits\": [\"player\"], \"nodes\": [\"*\"], \"meta\": {}}"
            + "},"
            + "\"players\": {"
            + "  \""
            + UUID_OF_STEVE
            + "\": {\"group\": \"player\", \"nodes\": [\"-codechat.delete\"], \"meta\": {}}"
            + "}"
            + "}";
    }

    private static final java.util.UUID UUID_OF_STEVE = java.util.UUID
        .fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

}
