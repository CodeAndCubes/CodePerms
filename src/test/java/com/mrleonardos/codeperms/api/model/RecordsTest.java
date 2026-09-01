package com.mrleonardos.codeperms.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class RecordsTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void groupKeepsLowercaseId() {
        GroupRecord group = GroupRecord.of("vip", "VIP", 10, list("player"), nodes("codechat.*"), meta("prefix", "&6"));
        assertEquals("vip", group.id());
        assertEquals("VIP", group.displayName());
        assertEquals(10, group.weight());
        assertEquals(
            1,
            group.inherits()
                .size());
        assertEquals(
            "&6",
            group.meta()
                .get("prefix"));
        assertEquals("vip#10", group.toString());
    }

    @Test
    void groupWithoutDisplayNameFallsBackToId() {
        assertEquals(
            "vip",
            GroupRecord.of("vip", null, 0, list(), nodes(), meta())
                .displayName());
        assertEquals(
            "vip",
            GroupRecord.of("vip", "  ", 0, list(), nodes(), meta())
                .displayName());
    }

    @Test
    void groupIdIsChecked() {
        assertThrows(NullPointerException.class, () -> GroupRecord.of(null, null, 0, list(), nodes(), meta()));
        assertThrows(IllegalArgumentException.class, () -> GroupRecord.of("", null, 0, list(), nodes(), meta()));
        assertThrows(IllegalArgumentException.class, () -> GroupRecord.of("VIP", null, 0, list(), nodes(), meta()));
        assertThrows(IllegalArgumentException.class, () -> GroupRecord.of(" vip ", null, 0, list(), nodes(), meta()));
    }

    @Test
    void userKeepsNameAndPrimary() {
        UserRecord user = UserRecord.of(
            PLAYER,
            "Steve",
            "vip",
            grants(UserRecord.Grant.permanent("player"), UserRecord.Grant.of("vip", 1000L)),
            nodes("-codechat.create"),
            meta("prefix", "&c"));
        assertEquals(PLAYER, user.uuid());
        assertEquals("Steve", user.name());
        assertEquals("vip", user.primary());
        assertEquals(
            "&c",
            user.meta()
                .get("prefix"));
        assertEquals("Steve", user.toString());
    }

    @Test
    void userWithoutNameFallsBackToUuid() {
        UserRecord user = UserRecord.of(PLAYER, null, null, grants(), nodes(), meta());
        assertNull(user.name());
        assertNull(user.primary());
        assertEquals(PLAYER.toString(), user.toString());
    }

    @Test
    void expiredGrantsDropOut() {
        UserRecord user = UserRecord.of(
            PLAYER,
            "Steve",
            null,
            grants(
                UserRecord.Grant.permanent("player"),
                UserRecord.Grant.of("vip", 1000L),
                UserRecord.Grant.of("premium", 2000L)),
            nodes(),
            meta());
        assertTrue(user.memberOf("player", 500L));
        assertTrue(user.memberOf("vip", 999L));
        assertFalse(user.memberOf("vip", 1000L));
        assertFalse(user.memberOf("premium", 3000L));
        assertFalse(user.memberOf("admin", 500L));
        assertEquals(
            3,
            user.groups()
                .size());
        assertEquals(
            1,
            user.activeGroupIds(2000L)
                .size());
        assertThrows(
            UnsupportedOperationException.class,
            () -> user.activeGroupIds(1L)
                .add("admin"));
    }

    @Test
    void grantIsChecked() {
        assertEquals(
            "vip",
            UserRecord.Grant.permanent("vip")
                .groupId());
        assertEquals(
            0L,
            UserRecord.Grant.permanent("vip")
                .expiresAt());
        assertTrue(
            UserRecord.Grant.permanent("vip")
                .permanent());
        assertTrue(
            UserRecord.Grant.of("vip", 10L)
                .expiredAt(10L));
        assertFalse(
            UserRecord.Grant.of("vip", 10L)
                .expiredAt(9L));
        assertThrows(NullPointerException.class, () -> UserRecord.Grant.permanent(null));
        assertThrows(IllegalArgumentException.class, () -> UserRecord.Grant.permanent("  "));
        assertThrows(IllegalArgumentException.class, () -> UserRecord.Grant.of("vip", -1L));
    }

    @Test
    void trackMovesByNeighbours() {
        TrackRecord track = TrackRecord.of("ladder", list("player", "vip", "premium"));
        assertEquals("ladder", track.name());
        assertEquals(
            3,
            track.groups()
                .size());
        assertEquals(1, track.position("vip"));
        assertEquals(-1, track.position("admin"));
        assertEquals(
            "vip",
            track.next("player")
                .get());
        assertEquals(
            "premium",
            track.next("vip")
                .get());
        assertFalse(
            track.next("premium")
                .isPresent());
        assertEquals(
            "player",
            track.previous("vip")
                .get());
        assertFalse(
            track.previous("player")
                .isPresent());
        assertFalse(
            track.next("admin")
                .isPresent());
        assertFalse(
            track.previous("admin")
                .isPresent());
        assertEquals("ladder:[player, vip, premium]", track.toString());
    }

    @Test
    void trackIsChecked() {
        assertThrows(NullPointerException.class, () -> TrackRecord.of(null, list("player")));
        assertThrows(IllegalArgumentException.class, () -> TrackRecord.of("  ", list("player")));
        assertThrows(IllegalArgumentException.class, () -> TrackRecord.of("ladder", list()));
        assertThrows(IllegalArgumentException.class, () -> TrackRecord.of("ladder", list("vip", "vip")));
        assertThrows(NullPointerException.class, () -> TrackRecord.of("ladder", list((String) null)));
    }

    @Test
    void changeCauseIsFinite() {
        assertEquals(5, ChangeCause.values().length);
        for (ChangeCause cause : ChangeCause.values()) {
            assertEquals(cause, ChangeCause.valueOf(cause.name()));
        }
    }

    private static List<String> list(String... values) {
        List<String> list = new ArrayList<>();
        for (String value : values) {
            list.add(value);
        }
        return list;
    }

    private static List<UserRecord.Grant> grants(UserRecord.Grant... values) {
        List<UserRecord.Grant> list = new ArrayList<>();
        for (UserRecord.Grant value : values) {
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

    private static Map<String, String> meta(String key, String value) {
        Map<String, String> map = new java.util.LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    private static Map<String, String> meta() {
        return new java.util.LinkedHashMap<>();
    }
}
