package com.mrleonardos.codeperms.internal.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codeperms.api.model.ContextSet;
import com.mrleonardos.codeperms.api.model.GroupRecord;
import com.mrleonardos.codeperms.api.model.NodeEntry;
import com.mrleonardos.codeperms.api.model.Snapshot;
import com.mrleonardos.codeperms.api.model.UserRecord;
import com.mrleonardos.codeperms.api.resolve.Resolution;

class ResolverImplTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final long NOW = 1_000_000L;
    private static final String READ = "codechat.channel.global.read";
    private static final String CREATE = "codechat.create";

    private final ResolverImpl resolver = new ResolverImpl(() -> NOW);

    @Test
    void personalDenyBeatsGroupAllow() {
        Snapshot snapshot = snapshot(
            "default",
            Arrays.asList(group("default", 0, Collections.<String>emptyList(), "codechat.*")),
            user(PLAYER, "Steve", null, Arrays.asList("default"), "-" + CREATE));

        assertFalse(
            resolver.resolve(snapshot, PLAYER, CREATE, ContextSet.empty())
                .allowed());
        assertTrue(
            resolver.resolve(snapshot, PLAYER, READ, ContextSet.empty())
                .allowed());
    }

    @Test
    void groupSolvesWhenPersonalNodesStaySilent() {
        Snapshot snapshot = snapshot(
            "default",
            Arrays.asList(group("default", 0, Collections.<String>emptyList(), "codechat.*")),
            user(PLAYER, "Steve", null, Arrays.asList("default"), "other.thing"));

        assertTrue(
            resolver.resolve(snapshot, PLAYER, CREATE, ContextSet.empty())
                .allowed());
    }

    @Test
    void inheritedRulesReachThePlayer() {
        Snapshot snapshot = snapshot(
            "default",
            Arrays.asList(
                group("default", 0, Collections.<String>emptyList(), READ),
                group("moderator", 10, Arrays.asList("default"))),
            user(PLAYER, "Steve", null, Arrays.asList("moderator")));

        assertTrue(
            resolver.resolve(snapshot, PLAYER, READ, ContextSet.empty())
                .allowed());
    }

    @Test
    void inheritanceCycleDoesNotHang() {
        Snapshot snapshot = snapshot(
            null,
            Arrays.asList(group("a", 10, Arrays.asList("b"), CREATE), group("b", 10, Arrays.asList("a"))),
            user(PLAYER, "Steve", null, Arrays.asList("a")));

        assertTrue(
            resolver.resolve(snapshot, PLAYER, CREATE, ContextSet.empty())
                .allowed());
    }

    @Test
    void heavierGroupIsConsultedFirst() {
        Snapshot snapshot = snapshot(
            null,
            Arrays.asList(
                group("junior", 10, Collections.<String>emptyList(), CREATE),
                group("senior", 50, Collections.<String>emptyList(), "-" + CREATE)),
            user(PLAYER, "Steve", null, Arrays.asList("junior", "senior")));

        assertFalse(
            resolver.resolve(snapshot, PLAYER, CREATE, ContextSet.empty())
                .allowed());
    }

    @Test
    void shallowerGroupWinsTheTieOnWeight() {
        Snapshot snapshot = snapshot(
            null,
            Arrays.asList(
                group("parent", 10, Collections.<String>emptyList(), CREATE),
                group("child", 10, Arrays.asList("parent"), "-" + CREATE)),
            user(PLAYER, "Steve", null, Arrays.asList("child")));

        assertFalse(
            resolver.resolve(snapshot, PLAYER, CREATE, ContextSet.empty())
                .allowed());
    }

    @Test
    void smallerIdBreaksTheTieBetweenEqualGroups() {
        Snapshot snapshot = snapshot(
            null,
            Arrays.asList(
                group("alpha", 10, Collections.<String>emptyList(), "-" + CREATE),
                group("beta", 10, Collections.<String>emptyList(), CREATE)),
            user(PLAYER, "Steve", null, Arrays.asList("beta", "alpha")));

        assertFalse(
            resolver.resolve(snapshot, PLAYER, CREATE, ContextSet.empty())
                .allowed());
    }

    @Test
    void specificRuleBeatsBroadOneAndDenyBreaksTheTie() {
        Snapshot snapshot = snapshot(
            "default",
            Arrays.asList(group("default", 0, Collections.<String>emptyList(), "codechat.*", "-" + READ)),
            user(PLAYER, "Steve", null, Arrays.asList("default")));

        assertFalse(
            resolver.resolve(snapshot, PLAYER, READ, ContextSet.empty())
                .allowed());
        assertTrue(
            resolver.resolve(snapshot, PLAYER, CREATE, ContextSet.empty())
                .allowed());
    }

    @Test
    void midPatternCoversExactlyOneSegment() {
        Snapshot snapshot = snapshot(
            "default",
            Arrays.asList(group("default", 0, Collections.<String>emptyList(), "codechat.*.read")),
            user(PLAYER, "Steve", null, Arrays.asList("default")));

        assertTrue(
            resolver.resolve(snapshot, PLAYER, "codechat.channel.read", ContextSet.empty())
                .allowed());
        assertFalse(
            resolver.resolve(snapshot, PLAYER, "codechat.channel.global.read", ContextSet.empty())
                .allowed());
    }

    @Test
    void contextsNarrowTheRule() {
        NodeEntry netherOnly = NodeEntry.of(CREATE, true, context("world", "nether"), 0L);
        Snapshot snapshot = snapshot(
            "default",
            Arrays.asList(groupWithEntries("default", 0, Collections.<String>emptyList(), netherOnly)),
            user(PLAYER, "Steve", null, Arrays.asList("default")));

        assertFalse(
            resolver.resolve(snapshot, PLAYER, CREATE, context("world", "overworld"))
                .allowed());
        assertTrue(
            resolver.resolve(snapshot, PLAYER, CREATE, context("world", "nether"))
                .allowed());
    }

    @Test
    void matchedContextBeatsContextFreeRuleInsideOneSource() {
        NodeEntry plain = NodeEntry.parse("codechat.format");
        NodeEntry denied = NodeEntry.of("codechat.format", false, context("world", "nether"), 0L);
        Snapshot snapshot = snapshot(
            "default",
            Arrays.asList(groupWithEntries("default", 0, Collections.<String>emptyList(), plain, denied)),
            user(PLAYER, "Steve", null, Arrays.asList("default")));

        assertFalse(
            resolver.resolve(snapshot, PLAYER, "codechat.format", context("world", "nether"))
                .allowed());
        assertTrue(
            resolver.resolve(snapshot, PLAYER, "codechat.format", context("world", "overworld"))
                .allowed());
    }

    @Test
    void expiredNodeIsSkippedAndGroupAnswers() {
        NodeEntry expired = NodeEntry.of(CREATE, false, ContextSet.empty(), NOW - 1L);
        UserRecord user = UserRecord.of(
            PLAYER,
            "Steve",
            null,
            grants("default"),
            Arrays.asList(expired, NodeEntry.parse("spare.thing")),
            Collections.<String, String>emptyMap());
        Snapshot snapshot = snapshot(
            "default",
            Arrays.asList(group("default", 0, Collections.<String>emptyList(), CREATE)),
            user);

        Resolution answer = resolver.resolve(snapshot, PLAYER, CREATE, ContextSet.empty());
        assertTrue(answer.allowed());
        assertEquals(
            "group:default",
            answer.winner()
                .get()
                .source());
        assertEquals(
            "player",
            resolver.resolve(snapshot, PLAYER, "spare.thing", ContextSet.empty())
                .winner()
                .get()
                .source());
        assertTrue(
            resolver.resolve(snapshot, PLAYER, "codechat.missing", ContextSet.empty())
                .candidates()
                .isEmpty());
    }

    @Test
    void defaultGroupStaysTheLastGroupSource() {
        Snapshot snapshot = snapshot(
            "player",
            Arrays.asList(
                group("player", 0, Collections.<String>emptyList(), READ),
                group("vip", 10, Collections.<String>emptyList(), CREATE)),
            user(PLAYER, "Steve", null, Arrays.asList("vip")));

        Resolution answer = resolver.resolve(snapshot, PLAYER, READ, ContextSet.empty());

        assertTrue(answer.allowed());
        assertEquals(
            "group:player",
            answer.winner()
                .get()
                .source());
    }

    @Test
    void grantedGroupIsAskedBeforeTheDefaultGroupEvenWhenItWeighsLess() {
        Snapshot snapshot = snapshot(
            "heavy",
            Arrays.asList(
                group("heavy", 100, Collections.<String>emptyList(), CREATE),
                group("light", 1, Collections.<String>emptyList(), "-" + CREATE)),
            user(PLAYER, "Steve", null, Arrays.asList("light")));

        assertFalse(
            resolver.resolve(snapshot, PLAYER, CREATE, ContextSet.empty())
                .allowed());
    }

    @Test
    void defaultGroupMetaClosesTheStack() {
        Map<String, String> base = Collections.singletonMap("prefix", "&7");
        Map<String, String> vip = Collections.singletonMap("prefix", "&c");
        Snapshot snapshot = snapshot(
            "player",
            Arrays.asList(
                GroupRecord.of(
                    "player",
                    "player",
                    0,
                    Collections.<String>emptyList(),
                    Collections.<NodeEntry>emptyList(),
                    base),
                GroupRecord
                    .of("vip", "vip", 10, Collections.<String>emptyList(), Collections.<NodeEntry>emptyList(), vip)),
            user(PLAYER, "Steve", null, Arrays.asList("vip")));

        assertEquals(Arrays.asList("&c", "&7"), resolver.metaValues(snapshot, PLAYER, "prefix"));
    }

    @Test
    void unknownPlayerFallsBackToDefaultGroup() {
        Snapshot snapshot = snapshot(
            "default",
            Arrays.asList(group("default", 0, Collections.<String>emptyList(), CREATE)),
            null);

        assertEquals("default", resolver.group(snapshot, PLAYER));
        assertTrue(
            resolver.resolve(snapshot, PLAYER, CREATE, ContextSet.empty())
                .allowed());
    }

    @Test
    void defaultGroupBringsItsParentsAlong() {
        Snapshot snapshot = snapshot(
            "moderator",
            Arrays.asList(
                group("player", 0, Collections.<String>emptyList(), READ),
                group("moderator", 10, Arrays.asList("player"))),
            user(PLAYER, "Steve", null, Collections.<String>emptyList()));

        Resolution answer = resolver.resolve(snapshot, PLAYER, READ, ContextSet.empty());

        assertTrue(answer.allowed());
        assertEquals(
            "group:player",
            answer.winner()
                .get()
                .source());
    }

    @Test
    void defaultGroupChainAnswersForPlayerWithoutRecord() {
        Map<String, String> playerMeta = Collections.singletonMap("prefix", "&7");
        Snapshot snapshot = Snapshot.builder()
            .defaultGroup("moderator")
            .group(GroupRecord.of("player", "player", 0, Collections.<String>emptyList(), entries(READ), playerMeta))
            .group(group("moderator", 10, Arrays.asList("player")))
            .build();

        assertTrue(
            resolver.resolve(snapshot, PLAYER, READ, ContextSet.empty())
                .allowed());
        assertEquals(Collections.singletonList("&7"), resolver.metaValues(snapshot, PLAYER, "prefix"));
    }

    @Test
    void groupPrefersPrimaryOverWeight() {
        Snapshot snapshot = snapshot(
            "default",
            Arrays.asList(
                group("junior", 10, Collections.<String>emptyList()),
                group("senior", 50, Collections.<String>emptyList())),
            user(PLAYER, "Steve", "junior", Arrays.asList("junior", "senior")));

        assertEquals("junior", resolver.group(snapshot, PLAYER));
    }

    @Test
    void groupFallsBackToWeightAndThenToDefault() {
        Snapshot snapshot = snapshot(
            "default",
            Arrays.asList(
                group("junior", 10, Collections.<String>emptyList()),
                group("senior", 50, Collections.<String>emptyList())),
            user(PLAYER, "Steve", null, Arrays.asList("junior", "senior")));

        assertEquals("senior", resolver.group(snapshot, PLAYER));
    }

    @Test
    void groupBreaksWeightTieBySmallerId() {
        Snapshot snapshot = snapshot(
            "default",
            Arrays.asList(
                group("beta", 10, Collections.<String>emptyList()),
                group("alpha", 10, Collections.<String>emptyList())),
            user(PLAYER, "Steve", null, Arrays.asList("beta", "alpha")));

        assertEquals("alpha", resolver.group(snapshot, PLAYER));
    }

    @Test
    void expiredGrantLeavesTheGroup() {
        List<UserRecord.Grant> grants = new ArrayList<>();
        grants.add(UserRecord.Grant.of("vip", NOW - 1L));
        UserRecord user = UserRecord.of(
            PLAYER,
            "Steve",
            "vip",
            grants,
            Collections.<NodeEntry>emptyList(),
            Collections.<String, String>emptyMap());
        Snapshot snapshot = snapshot(
            "default",
            Arrays.asList(
                group("vip", 50, Collections.<String>emptyList()),
                group("default", 0, Collections.<String>emptyList())),
            user);

        assertEquals("default", resolver.group(snapshot, PLAYER));
    }

    @Test
    void metaStackStartsWithPersonalValueAndFollowsSourceOrder() {
        Map<String, String> juniorMeta = Collections.singletonMap("prefix", "&7");
        Map<String, String> seniorMeta = Collections.singletonMap("prefix", "&c");
        Snapshot snapshot = snapshot(
            "default",
            Arrays.asList(
                GroupRecord.of(
                    "junior",
                    "junior",
                    10,
                    Collections.<String>emptyList(),
                    Collections.<NodeEntry>emptyList(),
                    juniorMeta),
                GroupRecord.of(
                    "senior",
                    "senior",
                    50,
                    Collections.<String>emptyList(),
                    Collections.<NodeEntry>emptyList(),
                    seniorMeta)),
            UserRecord.of(
                PLAYER,
                "Steve",
                null,
                grants("junior", "senior"),
                Collections.<NodeEntry>emptyList(),
                Collections.singletonMap("prefix", "&a")));

        assertEquals(
            "&a",
            resolver.meta(snapshot, PLAYER, "prefix")
                .get());
        assertEquals(Arrays.asList("&a", "&c", "&7"), resolver.metaValues(snapshot, PLAYER, "prefix"));
        assertFalse(
            resolver.meta(snapshot, PLAYER, "suffix")
                .isPresent());
    }

    @Test
    void defaultNodesAnswerWhenNothingElseMatches() {
        Snapshot snapshot = snapshot(
            "default",
            Arrays.asList(
                groupWithEntries(
                    "default",
                    0,
                    Collections.<String>emptyList(),
                    NodeEntry.of(CREATE, true, ContextSet.empty(), NOW - 1L))),
            user(PLAYER, "Steve", null, Collections.<String>emptyList()));
        ResolverImpl withDefaults = new ResolverImpl(() -> NOW, Collections.singletonList(NodeEntry.parse(CREATE)));

        assertTrue(
            withDefaults.resolve(snapshot, PLAYER, CREATE, ContextSet.empty())
                .allowed());
        assertEquals(
            ResolverImpl.DEFAULT_SOURCE,
            withDefaults.resolve(snapshot, PLAYER, CREATE, ContextSet.empty())
                .winner()
                .get()
                .source());
    }

    @Test
    void defaultNodesStayBehindEveryExplicitSource() {
        Snapshot snapshot = snapshot(
            null,
            Arrays.asList(group("senior", 50, Collections.<String>emptyList(), "-" + CREATE)),
            user(PLAYER, "Steve", null, Arrays.asList("senior")));
        ResolverImpl withDefaults = new ResolverImpl(() -> NOW, Collections.singletonList(NodeEntry.parse(CREATE)));

        assertFalse(
            withDefaults.resolve(snapshot, PLAYER, CREATE, ContextSet.empty())
                .allowed());
    }

    @Test
    void defaultNodesReachPlayersWithoutRecord() {
        Snapshot snapshot = snapshot(null, Collections.<GroupRecord>emptyList(), null);
        ResolverImpl withDefaults = new ResolverImpl(() -> NOW, Collections.singletonList(NodeEntry.parse(CREATE)));

        assertTrue(
            withDefaults.resolve(snapshot, PLAYER, CREATE, ContextSet.empty())
                .allowed());
        assertEquals(
            ResolverImpl.DEFAULT_SOURCE,
            withDefaults.resolve(snapshot, PLAYER, CREATE, ContextSet.empty())
                .winner()
                .get()
                .source());
    }

    private static List<UserRecord.Grant> grants(String... groupIds) {
        List<UserRecord.Grant> grants = new ArrayList<>();
        for (String groupId : groupIds) {
            grants.add(UserRecord.Grant.permanent(groupId));
        }
        return grants;
    }

    private static ContextSet context(String key, String value) {
        return ContextSet.builder()
            .put(key, value)
            .build();
    }

    private static List<NodeEntry> entries(String... raw) {
        List<NodeEntry> entries = new ArrayList<>();
        for (String value : raw) {
            entries.add(NodeEntry.parse(value));
        }
        return entries;
    }

    private static GroupRecord group(String id, int weight, List<String> inherits, String... nodes) {
        return GroupRecord.of(id, id, weight, inherits, entries(nodes), Collections.<String, String>emptyMap());
    }

    private static GroupRecord groupWithEntries(String id, int weight, List<String> inherits, NodeEntry... nodes) {
        return GroupRecord.of(id, id, weight, inherits, Arrays.asList(nodes), Collections.<String, String>emptyMap());
    }

    private static UserRecord user(UUID uuid, String name, String primary, List<String> groupIds, String... nodes) {
        return UserRecord.of(
            uuid,
            name,
            primary,
            grants(groupIds.toArray(new String[0])),
            entries(nodes),
            Collections.<String, String>emptyMap());
    }

    private static Snapshot snapshot(String defaultGroup, List<GroupRecord> groups, UserRecord user) {
        Snapshot.Builder builder = Snapshot.builder()
            .defaultGroup(defaultGroup);
        for (GroupRecord group : groups) {
            builder.group(group);
        }
        if (user != null) {
            builder.user(user);
        }
        return builder.build();
    }
}
