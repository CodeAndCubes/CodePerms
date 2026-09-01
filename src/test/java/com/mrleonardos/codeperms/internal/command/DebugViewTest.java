package com.mrleonardos.codeperms.internal.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mrleonardos.codeperms.api.model.ContextSet;
import com.mrleonardos.codeperms.api.model.GroupRecord;
import com.mrleonardos.codeperms.api.model.NodeEntry;
import com.mrleonardos.codeperms.api.model.Snapshot;
import com.mrleonardos.codeperms.api.model.UserRecord;
import com.mrleonardos.codeperms.internal.engine.ResolverImpl;

class DebugViewTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final String NODE = "codechat.chat.staff";

    private DebugView view;
    private TestCommandContext context;

    @BeforeEach
    void setUp() {
        view = new DebugView(new ResolverImpl(() -> 1_000_000L));
        context = new TestCommandContext();
    }

    @Test
    void tableExplainsTheDecidingSourceAndTheWinner() {
        Snapshot snapshot = Snapshot.builder()
            .group(group("moderator", "Moderator", 10, "codechat.chat.*"))
            .user(member("moderator", "-" + NODE))
            .build();

        view.show(context, snapshot, PLAYER, NODE, ContextSet.empty(), "Steve");

        assertEquals(
            3,
            context.sent()
                .size());
        assertEquals(
            PermsMessages.DEBUG_HEADER,
            context.sent()
                .get(0).key);
        assertEquals(
            NODE,
            context.sent()
                .get(0).arguments.get(0));
        assertEquals(
            "Steve",
            context.sent()
                .get(0).arguments.get(1));

        assertEquals(
            PermsMessages.DEBUG_ROW_DENY,
            context.sent()
                .get(1).key);
        assertEquals(
            "player",
            context.sent()
                .get(1).arguments.get(0));
        assertEquals(
            "-" + NODE,
            context.sent()
                .get(1).arguments.get(1));

        assertEquals(PermsMessages.DEBUG_WINNER_DENY, context.last().key);
        assertTrue(context.last().error);
        assertEquals("player", context.last().arguments.get(0));
    }

    @Test
    void tableStaysWithTheGroupWhenPersonalNodesStaySilent() {
        Snapshot snapshot = Snapshot.builder()
            .group(group("moderator", "Moderator", 10, "codechat.chat.*"))
            .user(member("moderator"))
            .build();

        view.show(context, snapshot, PLAYER, "codechat.chat.staff", ContextSet.empty(), "Steve");

        assertEquals(
            3,
            context.sent()
                .size());
        assertEquals(
            PermsMessages.DEBUG_ROW_ALLOW,
            context.sent()
                .get(1).key);
        assertEquals(
            "group:moderator",
            context.sent()
                .get(1).arguments.get(0));
        assertEquals(
            "codechat.chat.*",
            context.sent()
                .get(1).arguments.get(1));
        assertEquals(
            0,
            context.sent()
                .get(1).arguments.get(2));
        assertEquals(
            2,
            context.sent()
                .get(1).arguments.get(3));
        assertEquals(PermsMessages.DEBUG_WINNER_ALLOW, context.last().key);
        assertFalse(context.last().error);
    }

    @Test
    void silenceIsExplainedAsClosedByDefault() {
        Snapshot snapshot = Snapshot.builder()
            .defaultGroup("default")
            .build();

        view.show(context, snapshot, PLAYER, NODE, ContextSet.empty(), "Steve");

        assertEquals(
            2,
            context.sent()
                .size());
        assertEquals(PermsMessages.DEBUG_EMPTY, context.last().key);
        assertFalse(context.last().error);
    }

    @Test
    void contextMatchesAreCounted() {
        Snapshot snapshot = Snapshot.builder()
            .group(
                GroupRecord.of(
                    "default",
                    "Default",
                    0,
                    Collections.<String>emptyList(),
                    Collections.singletonList(
                        NodeEntry.of(
                            NODE,
                            true,
                            ContextSet.builder()
                                .put("world", "nether")
                                .build(),
                            0L)),
                    Collections.<String, String>emptyMap()))
            .user(member("default"))
            .build();

        view.show(
            context,
            snapshot,
            PLAYER,
            NODE,
            ContextSet.builder()
                .put("world", "nether")
                .build(),
            "Steve");

        assertEquals(
            PermsMessages.DEBUG_ROW_ALLOW,
            context.sent()
                .get(1).key);
        assertEquals(
            1,
            context.sent()
                .get(1).arguments.get(2));
        assertEquals(PermsMessages.DEBUG_WINNER_ALLOW, context.last().key);
        assertFalse(context.last().error);
    }

    private static GroupRecord group(String id, String displayName, int weight, String node) {
        return GroupRecord.of(
            id,
            displayName,
            weight,
            Collections.<String>emptyList(),
            Collections.singletonList(NodeEntry.parse(node)),
            Collections.<String, String>emptyMap());
    }

    private static UserRecord member(String groupId, String... nodes) {
        return UserRecord.of(
            PLAYER,
            "Steve",
            groupId,
            Collections.singletonList(UserRecord.Grant.permanent(groupId)),
            nodes(nodes),
            Collections.<String, String>emptyMap());
    }

    private static java.util.List<NodeEntry> nodes(String... raw) {
        java.util.List<NodeEntry> entries = new java.util.ArrayList<>();
        for (String value : raw) {
            entries.add(NodeEntry.parse(value));
        }
        return entries;
    }
}
