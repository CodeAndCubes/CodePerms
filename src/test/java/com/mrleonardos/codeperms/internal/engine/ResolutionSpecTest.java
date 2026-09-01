package com.mrleonardos.codeperms.internal.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codeperms.api.model.ContextSet;
import com.mrleonardos.codeperms.api.model.NodeEntry;
import com.mrleonardos.codeperms.api.model.Snapshot;
import com.mrleonardos.codeperms.api.model.UserRecord;

class ResolutionSpecTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000009");
    private static final long NOW = 100_000L;
    private static final ResolverImpl RESOLVER = new ResolverImpl(() -> NOW);

    @Test
    void personalDenyBeatsGroupAllow() {
        Snapshot snapshot = Snapshot.builder()
            .revision(1L)
            .group(PermsTestGroups.group("player", 0, "codechat.create"))
            .user(userWithNodes("-codechat.create"))
            .build();

        assertFalse(
            RESOLVER.resolve(snapshot, PLAYER, "codechat.create", ContextSet.empty())
                .allowed());
    }

    @Test
    void groupDecidesWhenPersonalNodesAreSilent() {
        Snapshot snapshot = Snapshot.builder()
            .revision(1L)
            .group(PermsTestGroups.group("player", 0, "codechat.channel.*"))
            .user(userWithNodes("player", "codechat.other"))
            .build();

        assertTrue(
            RESOLVER.resolve(snapshot, PLAYER, "codechat.channel.global.write", ContextSet.empty())
                .allowed());
    }

    @Test
    void inheritanceCycleFinishesAndCountsEachGroupOnce() {
        Snapshot snapshot = Snapshot.builder()
            .revision(1L)
            .group(PermsTestGroups.parented("a", 10, "b", "codechat.a"))
            .group(PermsTestGroups.parented("b", 5, "a", "codechat.b"))
            .user(userIn("a", "b"))
            .build();

        assertTrue(
            RESOLVER.resolve(snapshot, PLAYER, "codechat.a", ContextSet.empty())
                .allowed());
        assertTrue(
            RESOLVER.resolve(snapshot, PLAYER, "codechat.b", ContextSet.empty())
                .allowed());
        assertEquals("a", RESOLVER.group(snapshot, PLAYER));
    }

    @Test
    void preciseDenyBeatsBroadAllowInsideOneSource() {
        Snapshot snapshot = Snapshot.builder()
            .revision(1L)
            .group(PermsTestGroups.group("staff", 10, "codechat.channel.*", "-codechat.channel.staff.read"))
            .user(userIn("staff"))
            .build();

        assertFalse(
            RESOLVER.resolve(snapshot, PLAYER, "codechat.channel.staff.read", ContextSet.empty())
                .allowed());
        assertTrue(
            RESOLVER.resolve(snapshot, PLAYER, "codechat.channel.staff.write", ContextSet.empty())
                .allowed());
    }

    @Test
    void middleWildcardCoversExactlyOneSegment() {
        Snapshot snapshot = Snapshot.builder()
            .revision(1L)
            .group(PermsTestGroups.group("reader", 0, "codechat.*.read"))
            .user(userIn("reader"))
            .build();

        assertTrue(
            RESOLVER.resolve(snapshot, PLAYER, "codechat.channel.read", ContextSet.empty())
                .allowed());
        assertFalse(
            RESOLVER.resolve(snapshot, PLAYER, "codechat.channel.global.read", ContextSet.empty())
                .allowed());
    }

    @Test
    void worldContextSkipsForeignNodes() {
        Snapshot snapshot = Snapshot.builder()
            .revision(1L)
            .group(PermsTestGroups.entries("nether", 0, node("codechat.format", contextOf("world", "nether"))))
            .user(userIn("nether"))
            .build();

        assertFalse(
            RESOLVER.resolve(
                snapshot,
                PLAYER,
                "codechat.format",
                ContextSet.builder()
                    .put("world", "overworld")
                    .build())
                .allowed());
        assertTrue(
            RESOLVER.resolve(
                snapshot,
                PLAYER,
                "codechat.format",
                ContextSet.builder()
                    .put("world", "nether")
                    .build())
                .allowed());
    }

    @Test
    void moreSpecificContextWinsInsideSource() {
        Snapshot snapshot = Snapshot.builder()
            .revision(1L)
            .group(
                PermsTestGroups.entries(
                    "nether",
                    0,
                    node("codechat.format", ContextSet.empty()),
                    denied("codechat.format", contextOf("world", "nether"))))
            .user(userIn("nether"))
            .build();

        assertFalse(
            RESOLVER.resolve(
                snapshot,
                PLAYER,
                "codechat.format",
                ContextSet.builder()
                    .put("world", "nether")
                    .build())
                .allowed());
        assertTrue(
            RESOLVER.resolve(snapshot, PLAYER, "codechat.format", ContextSet.empty())
                .allowed());
    }

    @Test
    void expiredNodeIsSkippedAndGroupAnswers() {
        Snapshot snapshot = Snapshot.builder()
            .revision(1L)
            .group(PermsTestGroups.group("player", 0, "codechat.create"))
            .user(
                UserRecord.of(
                    PLAYER,
                    "Steve",
                    null,
                    Collections.singletonList(UserRecord.Grant.permanent("player")),
                    Arrays.asList(NodeEntry.of("codechat.create", true, ContextSet.empty(), NOW - 1L)),
                    Collections.<String, String>emptyMap()))
            .build();

        assertTrue(
            RESOLVER.resolve(snapshot, PLAYER, "codechat.create", ContextSet.empty())
                .allowed());
    }

    @Test
    void primaryBeatsWeight() {
        Snapshot snapshot = Snapshot.builder()
            .revision(1L)
            .group(PermsTestGroups.group("light", 10))
            .group(PermsTestGroups.group("heavy", 50))
            .user(
                UserRecord.of(
                    PLAYER,
                    "Steve",
                    "light",
                    Arrays.asList(UserRecord.Grant.permanent("light"), UserRecord.Grant.permanent("heavy")),
                    Collections.<NodeEntry>emptyList(),
                    Collections.<String, String>emptyMap()))
            .build();

        assertEquals("light", RESOLVER.group(snapshot, PLAYER));
    }

    @Test
    void weightDecidesWithoutPrimary() {
        Snapshot snapshot = Snapshot.builder()
            .revision(1L)
            .group(PermsTestGroups.group("light", 10))
            .group(PermsTestGroups.group("heavy", 50))
            .user(userIn("light", "heavy"))
            .build();

        assertEquals("heavy", RESOLVER.group(snapshot, PLAYER));
    }

    @Test
    void defaultGroupAnswersWhenNothingIsGranted() {
        Snapshot snapshot = Snapshot.builder()
            .revision(1L)
            .defaultGroup("player")
            .group(PermsTestGroups.group("player", 0))
            .build();

        assertEquals("player", RESOLVER.group(snapshot, PLAYER));
    }

    @Test
    void metaStackStartsWithPersonalValueAndFollowsSourceOrder() {
        Snapshot snapshot = Snapshot.builder()
            .revision(1L)
            .group(PermsTestGroups.meta("light", 10, "prefix", "L"))
            .group(PermsTestGroups.meta("heavy", 50, "prefix", "H"))
            .user(
                UserRecord.of(
                    PLAYER,
                    "Steve",
                    null,
                    Arrays.asList(UserRecord.Grant.permanent("light"), UserRecord.Grant.permanent("heavy")),
                    Collections.<NodeEntry>emptyList(),
                    Collections.singletonMap("prefix", "P")))
            .build();

        assertEquals(Arrays.asList("P", "H", "L"), RESOLVER.metaValues(snapshot, PLAYER, "prefix"));
    }

    private UserRecord userIn(String... groups) {
        java.util.List<UserRecord.Grant> grants = new java.util.ArrayList<>();
        for (String group : groups) {
            grants.add(UserRecord.Grant.permanent(group));
        }
        return UserRecord.of(
            PLAYER,
            "Steve",
            null,
            grants,
            Collections.<NodeEntry>emptyList(),
            Collections.<String, String>emptyMap());
    }

    private UserRecord userWithNodes(String group, String... nodes) {
        return UserRecord.of(
            PLAYER,
            "Steve",
            null,
            Collections.singletonList(UserRecord.Grant.permanent(group)),
            PermsTestGroups.nodes(nodes),
            Collections.<String, String>emptyMap());
    }

    private NodeEntry node(String name, ContextSet contexts) {
        return NodeEntry.of(name, true, contexts, 0L);
    }

    private NodeEntry denied(String name, ContextSet contexts) {
        return NodeEntry.of(name, false, contexts, 0L);
    }

    private ContextSet contextOf(String key, String value) {
        return ContextSet.builder()
            .put(key, value)
            .build();
    }
}
