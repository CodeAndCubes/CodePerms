package com.mrleonardos.codeperms.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class SnapshotTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void emptySnapshotHoldsNothing() {
        Snapshot snapshot = Snapshot.empty();
        assertEquals(0L, snapshot.revision());
        assertTrue(
            snapshot.groups()
                .isEmpty());
        assertTrue(
            snapshot.users()
                .isEmpty());
        assertTrue(
            snapshot.tracks()
                .isEmpty());
        assertFalse(snapshot.hasContextNodes());
        assertFalse(
            snapshot.group("default")
                .isPresent());
        assertSame(Snapshot.empty(), Snapshot.empty());
    }

    @Test
    void mapsHoldTogether() {
        Snapshot snapshot = Snapshot.builder()
            .group(GroupRecord.of("vip", "VIP", 10, list("player"), nodes("codechat.*"), emptyMeta()))
            .user(UserRecord.of(PLAYER, "Steve", "vip", grants(), nodes("codechat.create"), emptyMeta()))
            .track(TrackRecord.of("ladder", list("player", "vip")))
            .revision(7L)
            .defaultGroup("player")
            .opGroup("admin")
            .build();

        assertEquals(7L, snapshot.revision());
        assertEquals("player", snapshot.defaultGroup());
        assertEquals("admin", snapshot.opGroup());
        assertEquals(
            "vip",
            snapshot.group("vip")
                .get()
                .id());
        assertEquals(
            "Steve",
            snapshot.user(PLAYER)
                .get()
                .name());
        assertEquals(
            2,
            snapshot.track("ladder")
                .get()
                .groups()
                .size());

        assertThrows(
            UnsupportedOperationException.class,
            () -> snapshot.groups()
                .put("admin", null));
        assertThrows(
            UnsupportedOperationException.class,
            () -> snapshot.users()
                .put(PLAYER, null));
        assertThrows(
            UnsupportedOperationException.class,
            () -> snapshot.tracks()
                .put("ladder", null));
    }

    @Test
    void recordsInsideSnapshotHoldTogether() {
        List<NodeEntry> nodes = new ArrayList<>(nodes("codechat.*"));
        Snapshot snapshot = Snapshot.builder()
            .group(GroupRecord.of("vip", null, 10, list("player"), nodes, emptyMeta()))
            .build();

        nodes.add(NodeEntry.deny("codechat.create"));
        assertThrows(
            UnsupportedOperationException.class,
            () -> snapshot.group("vip")
                .get()
                .nodes()
                .add(NodeEntry.deny("codechat.create")));
        assertEquals(
            1,
            snapshot.group("vip")
                .get()
                .nodes()
                .size());
        assertThrows(
            UnsupportedOperationException.class,
            () -> snapshot.group("vip")
                .get()
                .inherits()
                .add("other"));
        assertThrows(
            UnsupportedOperationException.class,
            () -> snapshot.group("vip")
                .get()
                .meta()
                .put("prefix", "&7"));
    }

    @Test
    void contextNodesAreSpotted() {
        Snapshot withoutContexts = Snapshot.builder()
            .group(GroupRecord.of("vip", null, 10, list(), nodes("codechat.*"), emptyMeta()))
            .user(UserRecord.of(PLAYER, "Steve", null, grants(), nodes("-codechat.create"), emptyMeta()))
            .build();
        assertFalse(withoutContexts.hasContextNodes());

        Snapshot withContexts = Snapshot.builder()
            .group(GroupRecord.of("vip", null, 10, list(), nodes("codechat.*"), emptyMeta()))
            .user(
                UserRecord.of(
                    PLAYER,
                    "Steve",
                    null,
                    grants(),
                    entries(
                        NodeEntry.of(
                            "codechat.format",
                            false,
                            ContextSet.builder()
                                .put("world", "nether")
                                .build(),
                            0L)),
                    emptyMeta()))
            .build();
        assertTrue(withContexts.hasContextNodes());
    }

    @Test
    void revisionTellsSnapshotsApart() {
        Snapshot first = Snapshot.builder()
            .revision(1L)
            .group(GroupRecord.of("vip", null, 10, list(), nodes("codechat.*"), emptyMeta()))
            .build();
        Snapshot second = Snapshot.builder()
            .revision(2L)
            .group(GroupRecord.of("vip", null, 10, list(), nodes("codechat.*"), emptyMeta()))
            .build();
        assertNotEquals(first, second);
        assertEquals(2L, second.revision());
    }

    @Test
    void builderCopiesAnotherSnapshot() {
        Snapshot source = Snapshot.builder()
            .group(GroupRecord.of("vip", null, 10, list(), nodes("codechat.*"), emptyMeta()))
            .track(TrackRecord.of("ladder", list("player", "vip")))
            .build();

        Snapshot copy = Snapshot.builder()
            .from(source)
            .group(GroupRecord.of("admin", null, 100, list("vip"), nodes("*"), emptyMeta()))
            .removeTrack("ladder")
            .build();

        assertEquals(
            2,
            copy.groups()
                .size());
        assertFalse(
            copy.track("ladder")
                .isPresent());
        assertEquals(
            1,
            source.groups()
                .size());
        assertEquals(
            1,
            source.tracks()
                .size());
    }

    @Test
    void removalsAreQuiet() {
        Snapshot snapshot = Snapshot.builder()
            .removeGroup("missing")
            .removeUser(PLAYER)
            .removeTrack("missing")
            .build();
        assertTrue(
            snapshot.groups()
                .isEmpty());
    }

    private static List<String> list(String... values) {
        List<String> list = new ArrayList<>();
        for (String value : values) {
            list.add(value);
        }
        return list;
    }

    private static List<UserRecord.Grant> grants(String... groupIds) {
        List<UserRecord.Grant> list = new ArrayList<>();
        for (String groupId : groupIds) {
            list.add(UserRecord.Grant.permanent(groupId));
        }
        return list;
    }

    private static List<NodeEntry> entries(NodeEntry... values) {
        List<NodeEntry> list = new ArrayList<>();
        for (NodeEntry value : values) {
            list.add(value);
        }
        return list;
    }

    private static List<NodeEntry> nodes(String... values) {
        List<NodeEntry> list = new ArrayList<>();
        for (String value : values) {
            list.add(NodeEntry.parse(value));
        }
        return list;
    }

    private static Map<String, String> emptyMeta() {
        return new LinkedHashMap<>();
    }
}
