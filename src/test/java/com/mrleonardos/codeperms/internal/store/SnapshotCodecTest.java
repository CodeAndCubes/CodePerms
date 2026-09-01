package com.mrleonardos.codeperms.internal.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mrleonardos.codeperms.api.PermsLimits;
import com.mrleonardos.codeperms.api.model.ContextSet;
import com.mrleonardos.codeperms.api.model.GroupRecord;
import com.mrleonardos.codeperms.api.model.NodeEntry;
import com.mrleonardos.codeperms.api.model.Snapshot;
import com.mrleonardos.codeperms.api.model.UserRecord;

class SnapshotCodecTest {

    private static final Logger LOG = LogManager.getLogger(SnapshotCodecTest.class);
    private static final UUID PLAYER = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    private final SnapshotCodec codec = new SnapshotCodec(PermsLimits.defaults());

    @Test
    void plainNodeIsEncodedAsString() {
        JsonElement encoded = codec.encodeNode(NodeEntry.parse("-codechat.create"));

        assertTrue(encoded.isJsonPrimitive());
        assertEquals("-codechat.create", encoded.getAsString());
    }

    @Test
    void nodeWithContextAndExpiryIsEncodedAsObject() {
        JsonElement encoded = codec.encodeNode(
            NodeEntry.of(
                "codechat.format",
                false,
                ContextSet.builder()
                    .put("world", "nether")
                    .build(),
                5000L));

        assertTrue(encoded.isJsonObject());
        JsonObject data = encoded.getAsJsonObject();
        assertEquals(
            "codechat.format",
            data.get("node")
                .getAsString());
        assertEquals(
            Boolean.FALSE,
            data.get("value")
                .getAsBoolean());
        assertEquals(
            "nether",
            data.get("contexts")
                .getAsJsonObject()
                .get("world")
                .getAsString());
        assertEquals(
            5000L,
            data.get("expiresAt")
                .getAsLong());
    }

    @Test
    void stringFormReadsAsAllowanceWithoutContextsOrExpiry() {
        JsonArray nodes = new JsonArray();
        nodes.add(new JsonPrimitive("codechat.channel.*"));

        SnapshotCodec.DecodedGroups decoded = codec.readGroups(fileWithNodes("player", nodes), LOG);

        NodeEntry node = decoded.groups()
            .get(0)
            .nodes()
            .get(0);
        assertTrue(node.value());
        assertTrue(
            node.contexts()
                .isEmpty());
        assertTrue(node.permanent());
    }

    @Test
    void objectFormReadsWithContextAndExpiry() {
        JsonArray nodes = new JsonArray();
        JsonObject node = new JsonObject();
        node.addProperty("node", "codechat.format");
        node.addProperty("value", false);
        JsonObject contexts = new JsonObject();
        contexts.addProperty("world", "nether");
        node.add("contexts", contexts);
        node.addProperty("expiresAt", 5000L);
        nodes.add(node);

        SnapshotCodec.DecodedGroups decoded = codec.readGroups(fileWithNodes("player", nodes), LOG);

        NodeEntry read = decoded.groups()
            .get(0)
            .nodes()
            .get(0);
        assertFalse(read.value());
        assertEquals(
            "nether",
            read.contexts()
                .asMap()
                .get("world"));
        assertEquals(5000L, read.expiresAt());
    }

    @Test
    void starNodeSurvivesReading() {
        JsonArray nodes = new JsonArray();
        nodes.add(new JsonPrimitive("*"));

        SnapshotCodec.DecodedGroups decoded = codec.readGroups(fileWithNodes("admin", nodes), LOG);

        assertEquals(
            Arrays.asList("*"),
            nodes(
                decoded.groups()
                    .get(0)
                    .nodes()));
        assertEquals(0, decoded.dropped());
    }

    @Test
    void groupReferencesAreNormalized() {
        JsonArray groups = new JsonArray();
        JsonObject admin = new JsonObject();
        admin.addProperty("id", "admin");
        JsonArray inherits = new JsonArray();
        inherits.add(new JsonPrimitive("Player"));
        inherits.add(new JsonPrimitive("   "));
        admin.add("inherits", inherits);
        groups.add(admin);
        JsonObject track = new JsonObject();
        track.addProperty("name", "main");
        JsonArray ladder = new JsonArray();
        ladder.add(new JsonPrimitive("ADMIN"));
        track.add("groups", ladder);
        PermsGroupsFile file = fileWithGroups(groups);
        file.tracks = tracks(track);

        SnapshotCodec.DecodedGroups decoded = codec.readGroups(file, LOG);

        assertEquals(
            Arrays.asList("player"),
            decoded.groups()
                .get(0)
                .inherits());
        assertEquals(
            Arrays.asList("admin"),
            decoded.tracks()
                .get(0)
                .groups());
    }

    @Test
    void unusableNodesAreSkipped() {
        JsonArray nodes = new JsonArray();
        JsonObject withoutNode = new JsonObject();
        withoutNode.addProperty("value", true);
        nodes.add(withoutNode);
        nodes.add(new JsonPrimitive(""));
        nodes.add(new JsonPrimitive("codechat.create"));

        SnapshotCodec.DecodedGroups decoded = codec.readGroups(fileWithNodes("player", nodes), LOG);

        assertEquals(
            Arrays.asList("codechat.create"),
            nodes(
                decoded.groups()
                    .get(0)
                    .nodes()));
        assertEquals(2, decoded.dropped());
    }

    @Test
    void recordsRoundTripThroughFiles() {
        Snapshot written = snapshot();

        PermsGroupsFile groupsFile = new PermsGroupsFile();
        JsonObject playersFile = new JsonObject();
        codec.writeGroups(groupsFile, groupsOf(written), tracksOf(written));
        codec.writePlayers(playersFile, usersOf(written));

        SnapshotCodec.DecodedGroups groups = codec.readGroups(groupsFile, LOG);
        SnapshotCodec.DecodedPlayers players = codec.readPlayers(playersFile, LOG);

        assertEquals(
            new java.util.ArrayList<>(
                written.groups()
                    .values()),
            groups.groups());
        assertEquals(
            new java.util.ArrayList<>(
                written.tracks()
                    .values()),
            groups.tracks());
        assertEquals(
            new java.util.ArrayList<>(
                written.users()
                    .values()),
            players.players());
        assertEquals(0, groups.dropped());
        assertEquals(0, players.dropped());
    }

    @Test
    void emptyPlayerIsNotWrittenToTheFile() {
        JsonObject playersFile = new JsonObject();
        codec.writePlayers(
            playersFile,
            Arrays.asList(
                UserRecord
                    .of(PLAYER, null, null, Arrays.asList(), Arrays.asList(), new LinkedHashMap<String, String>())));

        assertFalse(
            playersFile.get("players")
                .getAsJsonArray()
                .iterator()
                .hasNext());
    }

    @Test
    void playerWithoutReadableUuidIsSkipped() {
        JsonArray players = new JsonArray();
        players.add(new JsonPrimitive("not-an-object"));
        JsonObject entry = new JsonObject();
        entry.addProperty("uuid", "steve");
        players.add(entry);

        SnapshotCodec.DecodedPlayers decoded = codec.readPlayers(fileWithPlayers(players), LOG);

        assertTrue(
            decoded.players()
                .isEmpty());
        assertEquals(2, decoded.dropped());
    }

    @Test
    void duplicatePlayersKeepTheFirstDeclaration() {
        JsonArray players = new JsonArray();
        players.add(playerWithNodes("one"));
        players.add(playerWithNodes("two"));

        SnapshotCodec.DecodedPlayers decoded = codec.readPlayers(fileWithPlayers(players), LOG);

        assertEquals(
            1,
            decoded.players()
                .size());
        assertEquals(
            Arrays.asList("one"),
            nodes(
                decoded.players()
                    .get(0)
                    .nodes()));
    }

    @Test
    void groupCeilingHoldsBackExtraGroups() {
        JsonArray groups = new JsonArray();
        for (int index = 0; index < 4; index++) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", "group" + index);
            groups.add(entry);
        }
        PermsGroupsFile file = fileWithGroups(groups);

        SnapshotCodec limited = new SnapshotCodec(
            PermsLimits.builder()
                .groups(2)
                .build());
        SnapshotCodec.DecodedGroups decoded = limited.readGroups(file, LOG);

        assertEquals(
            2,
            decoded.groups()
                .size());
        assertEquals(2, decoded.dropped());
    }

    @Test
    void groupsBeyondTheCeilingSurviveTheFirstWrite() {
        JsonArray groups = new JsonArray();
        for (int index = 0; index < 4; index++) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", "group" + index);
            groups.add(entry);
        }
        PermsGroupsFile file = fileWithGroups(groups);
        SnapshotCodec limited = new SnapshotCodec(
            PermsLimits.builder()
                .groups(2)
                .build());
        SnapshotCodec.DecodedGroups decoded = limited.readGroups(file, LOG);

        limited.writeGroups(file, decoded.groups(), decoded.tracks(), decoded.quarantine());

        assertEquals(
            2,
            decoded.quarantine()
                .records());
        assertEquals(Arrays.asList("group0", "group1", "group2", "group3"), groupIdsOf(file.groups));
    }

    @Test
    void nodesBeyondTheCeilingLeaveTheModelButStayInTheFile() {
        JsonArray nodes = new JsonArray();
        nodes.add(new JsonPrimitive("codechat.one"));
        nodes.add(new JsonPrimitive("codechat.two"));
        nodes.add(new JsonPrimitive("codechat.three"));
        PermsGroupsFile file = fileWithNodes("vip", nodes);
        SnapshotCodec limited = new SnapshotCodec(
            PermsLimits.builder()
                .nodesPerSubject(1)
                .build());

        SnapshotCodec.DecodedGroups decoded = limited.readGroups(file, LOG);

        assertEquals(
            Arrays.asList("codechat.one"),
            nodes(
                decoded.groups()
                    .get(0)
                    .nodes()));
        assertEquals(2, decoded.dropped());

        limited.writeGroups(file, decoded.groups(), decoded.tracks(), decoded.quarantine());

        assertEquals(Arrays.asList("codechat.one", "codechat.two", "codechat.three"), textsOf(nodesOf(file, 0)));
    }

    @Test
    void unreadableNodeOfALivingSubjectStaysInTheFile() {
        JsonArray nodes = new JsonArray();
        nodes.add(new JsonPrimitive("codechat.one"));
        nodes.add(new JsonPrimitive("codechat..broken"));
        PermsGroupsFile file = fileWithNodes("vip", nodes);

        SnapshotCodec.DecodedGroups decoded = codec.readGroups(file, LOG);
        codec.writeGroups(file, decoded.groups(), decoded.tracks(), decoded.quarantine());

        assertEquals(1, decoded.dropped());
        assertEquals(Arrays.asList("codechat.one", "codechat..broken"), textsOf(nodesOf(file, 0)));
    }

    @Test
    void metaBeyondTheCeilingLeavesTheModelButStaysInTheFile() {
        JsonObject meta = new JsonObject();
        meta.addProperty("prefix", "&7");
        meta.addProperty("suffix", "&c");
        JsonArray groups = new JsonArray();
        JsonObject group = new JsonObject();
        group.addProperty("id", "vip");
        group.add("meta", meta);
        groups.add(group);
        PermsGroupsFile file = fileWithGroups(groups);
        SnapshotCodec limited = new SnapshotCodec(
            PermsLimits.builder()
                .metaKeysPerSubject(1)
                .build());

        SnapshotCodec.DecodedGroups decoded = limited.readGroups(file, LOG);

        assertEquals(
            1,
            decoded.groups()
                .get(0)
                .meta()
                .size());
        assertEquals(1, decoded.dropped());

        limited.writeGroups(file, decoded.groups(), decoded.tracks(), decoded.quarantine());

        JsonObject written = file.groups.get(0)
            .getAsJsonObject()
            .get("meta")
            .getAsJsonObject();
        assertEquals(
            "&7",
            written.get("prefix")
                .getAsString());
        assertEquals(
            "&c",
            written.get("suffix")
                .getAsString());
    }

    @Test
    void trackNameFoldsToLowerCase() {
        JsonObject track = new JsonObject();
        track.addProperty("name", "Main");
        JsonArray members = new JsonArray();
        members.add(new JsonPrimitive("player"));
        track.add("groups", members);

        SnapshotCodec.DecodedGroups decoded = codec.readGroups(fileWithTracks(tracks(track)), LOG);

        assertEquals(
            "main",
            decoded.tracks()
                .get(0)
                .name());
    }

    @Test
    void writeLeavesOnlyWhatTheSnapshotHolds() {
        PermsGroupsFile file = fileWithGroups(new JsonArray());
        file.tracks = tracks(new JsonObject());

        codec.writeGroups(file, Arrays.asList(PermsFixtures.group("player", 0)), Arrays.asList());

        assertEquals(Arrays.asList("player"), groupIdsOf(file.groups));
        assertFalse(
            file.tracks.iterator()
                .hasNext(),
            "трек, которого нет в снимке, из файла уходит");
    }

    private static java.util.List<String> groupIdsOf(JsonArray groups) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (JsonElement element : groups) {
            ids.add(
                element.getAsJsonObject()
                    .get("id")
                    .getAsString());
        }
        return ids;
    }

    private static JsonArray nodesOf(PermsGroupsFile file, int index) {
        return file.groups.get(index)
            .getAsJsonObject()
            .get("nodes")
            .getAsJsonArray();
    }

    private static java.util.List<String> textsOf(JsonArray array) {
        java.util.List<String> texts = new java.util.ArrayList<>();
        for (JsonElement element : array) {
            texts.add(element.getAsString());
        }
        return texts;
    }

    private PermsGroupsFile fileWithNodes(String groupId, JsonArray nodes) {
        JsonArray groups = new JsonArray();
        JsonObject group = new JsonObject();
        group.addProperty("id", groupId);
        group.add("nodes", nodes);
        groups.add(group);
        return fileWithGroups(groups);
    }

    private PermsGroupsFile fileWithGroups(JsonArray groups) {
        PermsGroupsFile file = new PermsGroupsFile();
        file.groups = groups;
        return file;
    }

    private PermsGroupsFile fileWithTracks(JsonArray tracks) {
        PermsGroupsFile file = new PermsGroupsFile();
        file.tracks = tracks;
        return file;
    }

    private JsonArray tracks(JsonObject... entries) {
        JsonArray array = new JsonArray();
        for (JsonObject entry : entries) {
            array.add(entry);
        }
        return array;
    }

    private JsonObject fileWithPlayers(JsonArray players) {
        JsonObject file = new JsonObject();
        file.add("players", players);
        return file;
    }

    private JsonObject playerWithNodes(String node) {
        JsonObject entry = new JsonObject();
        entry.addProperty("uuid", PLAYER.toString());
        JsonArray nodes = new JsonArray();
        nodes.add(new JsonPrimitive(node));
        entry.add("nodes", nodes);
        return entry;
    }

    private Snapshot snapshot() {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("prefix", "&7");
        return Snapshot.builder()
            .revision(1L)
            .group(
                GroupRecord.of(
                    "moderator",
                    "",
                    50,
                    Arrays.asList("player"),
                    Arrays.asList(
                        NodeEntry.parse("codechat.channel.*"),
                        NodeEntry.of(
                            "codechat.format",
                            false,
                            ContextSet.builder()
                                .put("world", "nether")
                                .build(),
                            5000L)),
                    meta))
            .user(
                UserRecord.of(
                    PLAYER,
                    "Steve",
                    "moderator",
                    Arrays.asList(UserRecord.Grant.of("vip", 9000L)),
                    Arrays.asList(NodeEntry.parse("-codechat.create")),
                    meta))
            .build();
    }

    private java.util.List<GroupRecord> groupsOf(Snapshot snapshot) {
        return new java.util.ArrayList<>(
            snapshot.groups()
                .values());
    }

    private java.util.List<com.mrleonardos.codeperms.api.model.TrackRecord> tracksOf(Snapshot snapshot) {
        return new java.util.ArrayList<>(
            snapshot.tracks()
                .values());
    }

    private java.util.List<UserRecord> usersOf(Snapshot snapshot) {
        return new java.util.ArrayList<>(
            snapshot.users()
                .values());
    }

    private java.util.List<String> nodes(java.util.List<NodeEntry> entries) {
        java.util.List<String> values = new java.util.ArrayList<>();
        for (NodeEntry entry : entries) {
            values.add(entry.node());
        }
        return values;
    }
}
