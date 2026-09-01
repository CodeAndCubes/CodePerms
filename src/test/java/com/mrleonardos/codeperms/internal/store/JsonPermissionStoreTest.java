package com.mrleonardos.codeperms.internal.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mrleonardos.codeperms.api.PermsLimits;
import com.mrleonardos.codeperms.api.model.ChangeCause;
import com.mrleonardos.codeperms.api.model.ContextSet;
import com.mrleonardos.codeperms.api.model.GroupRecord;
import com.mrleonardos.codeperms.api.model.NodeEntry;
import com.mrleonardos.codeperms.api.model.Snapshot;
import com.mrleonardos.codeperms.api.model.TrackRecord;
import com.mrleonardos.codeperms.api.model.UserRecord;
import com.mrleonardos.codeperms.api.store.ChangeBatch;
import com.mrleonardos.codeperms.internal.PermsSettings;

class JsonPermissionStoreTest {

    private static final Logger LOG = LogManager.getLogger(JsonPermissionStoreTest.class);
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @TempDir
    Path root;

    private StubConfigService configs;

    @BeforeEach
    void setUp() {
        configs = new StubConfigService(root);
    }

    @Test
    void firstRunCreatesWorkingExample() {
        Snapshot snapshot = store().load();

        assertEquals(
            Arrays.asList("player", "admin"),
            new ArrayList<>(
                snapshot.groups()
                    .keySet()));
        assertNotNull(snapshot.track("main"));
        assertTrue(
            snapshot.users()
                .isEmpty());
        assertEquals("player", snapshot.defaultGroup());
        assertEquals("admin", snapshot.opGroup());
        assertTrue(Files.isRegularFile(configs.pathOf("codeperms", "groups")));
        assertTrue(Files.isRegularFile(configs.pathOf("codeperms", "players")));
        assertTrue(Files.isRegularFile(configs.pathOf("codeperms", "config")));
    }

    @Test
    void roundTripKeepsNodesContextsAndExpiry() {
        Snapshot written = snapshot();
        store().save(written);

        Snapshot read = freshStore().load();

        assertEquals(written.groups(), read.groups());
        assertEquals(written.users(), read.users());
        assertEquals(written.tracks(), read.tracks());
    }

    @Test
    void factoryAdminGroupKeepsItsStarNode() {
        Snapshot snapshot = store().load();

        assertEquals(
            Arrays.asList("*"),
            strings(
                snapshot.group("admin")
                    .get()
                    .nodes()));
    }

    @Test
    void roundTripKeepsTheStarNode() {
        Snapshot written = Snapshot.builder()
            .revision(3L)
            .group(
                GroupRecord.of(
                    "admin",
                    "",
                    100,
                    Arrays.asList("player"),
                    Arrays.asList(NodeEntry.parse("*")),
                    new LinkedHashMap<String, String>()))
            .build();
        store().save(written);

        Snapshot read = freshStore().load();

        assertEquals(
            Arrays.asList("*"),
            strings(
                read.group("admin")
                    .get()
                    .nodes()));
    }

    @Test
    void groupReferencesAreNormalizedWhileReading() {
        JsonObject file = new JsonObject();
        JsonArray groups = new JsonArray();
        groups.add(declaration("player", "codechat.create"));
        JsonObject admin = declaration("admin", "*");
        JsonArray inherits = new JsonArray();
        inherits.add(new com.google.gson.JsonPrimitive(" Player "));
        admin.add("inherits", inherits);
        groups.add(admin);
        JsonArray tracks = new JsonArray();
        JsonObject track = new JsonObject();
        track.addProperty("name", "main");
        JsonArray ladder = new JsonArray();
        ladder.add(new com.google.gson.JsonPrimitive("PLAYER"));
        ladder.add(new com.google.gson.JsonPrimitive("admin"));
        track.add("groups", ladder);
        tracks.add(track);
        file.add("groups", groups);
        file.add("tracks", tracks);
        StubConfigService.Json.write(file(), file);

        Snapshot snapshot = store().load();

        assertEquals(
            Arrays.asList("player"),
            snapshot.group("admin")
                .get()
                .inherits());
        assertEquals(
            Arrays.asList("player", "admin"),
            snapshot.track("main")
                .get()
                .groups());
    }

    @Test
    void duplicateGroupIdentifierKeepsFirstDeclaration() {
        JsonArray groups = new JsonArray();
        groups.add(declaration("player", "codechat.create"));
        groups.add(declaration("player", "codechat.channel.*"));
        StubConfigService.Json.write(file(), objectWithGroups(groups));

        Snapshot snapshot = store().load();

        assertEquals(
            1,
            snapshot.groups()
                .size());
        assertEquals(
            Arrays.asList("codechat.create"),
            strings(
                snapshot.group("player")
                    .get()
                    .nodes()));
    }

    @Test
    void brokenGroupsFileIsSetAsideAndReplacedWithDefaults() throws Exception {
        Path groups = file();
        Files.createDirectories(groups.getParent());
        Files.write(groups, Arrays.asList("{ \"groups\": ["));

        Snapshot snapshot = store().load();

        assertTrue(Files.isRegularFile(groups.resolveSibling("groups.json.broken")));
        assertEquals(
            Arrays.asList("player", "admin"),
            new ArrayList<>(
                snapshot.groups()
                    .keySet()));
    }

    @Test
    void applyPatchesStateAndKeepsRevisionMoving() {
        JsonPermissionStore store = store();
        store.load();

        store.apply(
            ChangeBatch.builder(ChangeCause.COMMAND, "console")
                .upsert(
                    GroupRecord.of(
                        "vip",
                        "",
                        10,
                        new ArrayList<String>(),
                        new ArrayList<NodeEntry>(),
                        new LinkedHashMap<String, String>()))
                .build());

        assertEquals(
            Arrays.asList("player", "admin", "vip"),
            new ArrayList<>(
                store.state()
                    .groups()
                    .keySet()));
        assertEquals(
            1L,
            store.state()
                .revision());
    }

    @Test
    void saveWritesEncodedNodesToTheFile() throws Exception {
        store().save(snapshot());

        String text = new String(Files.readAllBytes(file()), StandardCharsets.UTF_8);
        assertTrue(text.contains("codechat.channel.*"));
        assertTrue(text.contains("expiresAt"));
        assertTrue(text.contains("contexts"));
    }

    private JsonObject objectWithGroups(JsonArray groups) {
        JsonObject data = new JsonObject();
        data.add("groups", groups);
        return data;
    }

    private JsonObject declaration(String id, String node) {
        JsonObject entry = new JsonObject();
        entry.addProperty("id", id);
        JsonArray nodes = new JsonArray();
        nodes.add(new com.google.gson.JsonPrimitive(node));
        entry.add("nodes", nodes);
        return entry;
    }

    private Path file() {
        return configs.pathOf("codeperms", "groups");
    }

    private Snapshot snapshot() {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("prefix", "&7");
        GroupRecord moderator = GroupRecord.of(
            "moderator",
            "",
            50,
            Arrays.asList("player"),
            Arrays.asList(
                NodeEntry.of("codechat.channel.*", true, ContextSet.empty(), 0L),
                NodeEntry.of(
                    "codechat.format",
                    false,
                    ContextSet.builder()
                        .put("world", "nether")
                        .build(),
                    1500L)),
            meta);
        UserRecord player = UserRecord.of(
            PLAYER,
            "Steve",
            "moderator",
            Arrays.asList(UserRecord.Grant.of("vip", 9000L)),
            Arrays.asList(NodeEntry.parse("-codechat.create")),
            new LinkedHashMap<String, String>());
        return Snapshot.builder()
            .revision(7L)
            .defaultGroup("player")
            .opGroup("admin")
            .group(moderator)
            .track(TrackRecord.of("main", Arrays.asList("player", "moderator")))
            .user(player)
            .build();
    }

    private List<String> strings(List<NodeEntry> nodes) {
        List<String> values = new ArrayList<>();
        for (NodeEntry node : nodes) {
            values.add(node.toShortString());
        }
        return values;
    }

    private JsonPermissionStore store() {
        return new JsonPermissionStore(
            configs.open(PermsSettings.spec()),
            configs.open(JsonGroupsStore.spec()),
            configs.open(JsonPlayersStore.spec()),
            PermsLimits.defaults(),
            LOG);
    }

    private JsonPermissionStore freshStore() {
        return new JsonPermissionStore(
            new StubConfigService(root).open(PermsSettings.spec()),
            new StubConfigService(root).open(JsonGroupsStore.spec()),
            new StubConfigService(root).open(JsonPlayersStore.spec()),
            PermsLimits.defaults(),
            LOG);
    }
}
