package com.mrleonardos.codeperms.internal.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mrleonardos.codeperms.api.PermsLimits;
import com.mrleonardos.codeperms.api.manage.ChangeEvent;
import com.mrleonardos.codeperms.api.manage.PermissionsListener;
import com.mrleonardos.codeperms.api.model.ChangeCause;
import com.mrleonardos.codeperms.api.model.ContextSet;
import com.mrleonardos.codeperms.api.model.GroupRecord;
import com.mrleonardos.codeperms.api.model.NodeEntry;
import com.mrleonardos.codeperms.api.model.Snapshot;
import com.mrleonardos.codeperms.api.model.TrackRecord;
import com.mrleonardos.codeperms.api.model.UserRecord;
import com.mrleonardos.codeperms.api.resolve.Resolution;
import com.mrleonardos.codeperms.api.store.ChangeBatch;
import com.mrleonardos.codeperms.api.store.OperationResult;
import com.mrleonardos.codeperms.internal.store.SingleWriter;

class PermsAdminImplTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final long NOW = 1_000_000L;
    private static final String CREATE = "codechat.create";

    private FakeWriter writer;
    private ChangeCoalescer coalescer;
    private RecordingListener listener;
    private PermsAdminImpl admin;

    @BeforeEach
    void setUp() {
        writer = new FakeWriter();
        coalescer = new ChangeCoalescer(LogManager.getLogger("codeperms-test"));
        listener = new RecordingListener();
        coalescer.register(0, listener);
        admin = new PermsAdminImpl(writer, coalescer, PermsLimits.defaults(), () -> NOW);
    }

    @Test
    void createGroupBuildsSnapshotBumpsRevisionAndWritesAudit() {
        OperationResult result = admin.createGroup("VIP", "Very Important", 10, ChangeCause.COMMAND, "console");

        assertTrue(result.successful());
        assertEquals(
            1L,
            writer.snapshot()
                .revision());
        GroupRecord created = writer.snapshot()
            .group("vip")
            .get();
        assertEquals("Very Important", created.displayName());
        assertEquals(10, created.weight());
        assertEquals(1, writer.batches.size());
        ChangeBatch batch = writer.batches.get(0);
        assertEquals(ChangeCause.COMMAND, batch.cause());
        assertEquals("console", batch.author());
        assertEquals(
            "vip",
            batch.changes()
                .get(0)
                .subject());
    }

    @Test
    void previousSnapshotStaysUntouched() {
        Snapshot before = writer.snapshot();

        admin.createGroup("vip", "", 0, ChangeCause.COMMAND, "console");

        assertTrue(
            before.groups()
                .isEmpty());
        assertEquals(0L, before.revision());
        assertTrue(
            writer.snapshot()
                .group("vip")
                .isPresent());
    }

    @Test
    void mutationRaisesEventWithCauseAndSubject() {
        admin.setPlayerNode(PLAYER, NodeEntry.parse(CREATE), ChangeCause.API, "clans");

        coalescer.dispatch();
        assertEquals(1, listener.events.size());
        ChangeEvent event = listener.events.get(0);
        assertEquals(ChangeEvent.Kind.NODES, event.kind());
        assertEquals(ChangeCause.API, event.cause());
        assertTrue(
            event.subjects()
                .contains(ChangeEvent.Subject.player(PLAYER)));
    }

    @Test
    void missingGroupIsRejectedWithoutTouchingTheModel() {
        OperationResult result = admin.deleteGroup("vip", ChangeCause.COMMAND, "console");

        assertEquals(
            OperationResult.Failure.NOT_FOUND,
            result.failure()
                .get());
        assertEquals(
            0L,
            writer.snapshot()
                .revision());
        assertTrue(writer.batches.isEmpty());
    }

    @Test
    void duplicateGroupIsRejected() {
        admin.createGroup("vip", "", 0, ChangeCause.COMMAND, "console");

        assertEquals(
            OperationResult.Failure.ALREADY_EXISTS,
            admin.createGroup("vip", "", 1, ChangeCause.COMMAND, "console")
                .failure()
                .get());
    }

    @Test
    void ceilingsOfTheModelAreEnforced() {
        PermsAdminImpl tight = new PermsAdminImpl(
            writer,
            coalescer,
            PermsLimits.builder()
                .groups(1)
                .groupIdLength(3)
                .nodesPerSubject(1)
                .metaValueLength(3)
                .metaKeysPerSubject(1)
                .build(),
            () -> NOW);

        admin.createGroup("vip", "", 0, ChangeCause.COMMAND, "console");

        assertEquals(
            OperationResult.Failure.LIMIT_REACHED,
            tight.createGroup("second", "", 0, ChangeCause.COMMAND, "console")
                .failure()
                .get());
        assertEquals(
            OperationResult.Failure.LIMIT_REACHED,
            tight.createGroup("toolong", "", 0, ChangeCause.COMMAND, "console")
                .failure()
                .get());
        assertTrue(
            tight.setGroupNode("vip", NodeEntry.parse(CREATE), ChangeCause.COMMAND, "console")
                .successful());
        assertEquals(
            OperationResult.Failure.LIMIT_REACHED,
            tight.setGroupNode("vip", NodeEntry.parse("codechat.other"), ChangeCause.COMMAND, "console")
                .failure()
                .get());
        assertEquals(
            OperationResult.Failure.LIMIT_REACHED,
            tight.setGroupMeta("vip", "prefix", "toolong", ChangeCause.COMMAND, "console")
                .failure()
                .get());
        assertTrue(
            tight.setGroupMeta("vip", "prefix", "&7", ChangeCause.COMMAND, "console")
                .successful());
        assertEquals(
            OperationResult.Failure.LIMIT_REACHED,
            tight.setGroupMeta("vip", "suffix", "&c", ChangeCause.COMMAND, "console")
                .failure()
                .get());
    }

    @Test
    void inheritanceCycleIsRejected() {
        admin.createGroup("a", "", 0, ChangeCause.COMMAND, "console");
        admin.createGroup("b", "", 0, ChangeCause.COMMAND, "console");
        admin.addParent("a", "b", ChangeCause.COMMAND, "console");

        assertEquals(
            OperationResult.Failure.INHERITANCE_CYCLE,
            admin.addParent("b", "a", ChangeCause.COMMAND, "console")
                .failure()
                .get());
    }

    @Test
    void renameCarriesInheritsGrantsAndPrimaryAlong() {
        admin.createGroup("junior", "", 10, ChangeCause.COMMAND, "console");
        admin.createGroup("senior", "", 20, ChangeCause.COMMAND, "console");
        admin.addParent("senior", "junior", ChangeCause.COMMAND, "console");
        admin.addPlayerGroup(PLAYER, "junior", 0L, ChangeCause.COMMAND, "console");
        admin.setPrimaryGroup(PLAYER, "junior", ChangeCause.COMMAND, "console");

        OperationResult result = admin.renameGroup("junior", "member", ChangeCause.COMMAND, "console");

        assertTrue(result.successful());
        Snapshot snapshot = writer.snapshot();
        assertFalse(
            snapshot.group("junior")
                .isPresent());
        assertEquals(
            "member",
            snapshot.group("senior")
                .get()
                .inherits()
                .get(0));
        UserRecord user = snapshot.user(PLAYER)
            .get();
        assertEquals("member", user.primary());
        assertEquals(
            "member",
            user.groups()
                .get(0)
                .groupId());
    }

    @Test
    void renameCarriesTracksAlong() {
        admin.createGroup("junior", "", 10, ChangeCause.COMMAND, "console");
        admin.createGroup("senior", "", 20, ChangeCause.COMMAND, "console");
        writer.seed(
            Snapshot.builder()
                .revision(
                    writer.snapshot()
                        .revision())
                .from(writer.snapshot())
                .track(TrackRecord.of("main", Arrays.asList("junior", "senior")))
                .build());

        assertTrue(
            admin.renameGroup("junior", "member", ChangeCause.COMMAND, "console")
                .successful());

        assertEquals(
            Arrays.asList("member", "senior"),
            writer.snapshot()
                .track("main")
                .get()
                .groups());
    }

    @Test
    void groupNamedByTheConfigIsNeitherRenamedNorDeleted() {
        writer.seed(
            Snapshot.builder()
                .defaultGroup("player")
                .opGroup("admin")
                .group(group("player", 0))
                .group(group("admin", 100))
                .build());

        assertEquals(
            OperationResult.Failure.IN_USE,
            admin.renameGroup("player", "member", ChangeCause.COMMAND, "console")
                .failure()
                .get());
        assertEquals(
            OperationResult.Failure.IN_USE,
            admin.deleteGroup("admin", ChangeCause.COMMAND, "console")
                .failure()
                .get());
        assertTrue(
            writer.snapshot()
                .group("player")
                .isPresent());
        assertTrue(
            writer.snapshot()
                .group("admin")
                .isPresent());
        assertTrue(writer.batches.isEmpty());
    }

    @Test
    void deleteStripsEveryReferenceToTheGroup() {
        writer.seed(
            Snapshot.builder()
                .group(group("player", 0))
                .group(GroupRecord.of("moderator", "moderator", 10, Arrays.asList("player"), nodes(), meta()))
                .group(group("vip", 5))
                .track(TrackRecord.of("main", Arrays.asList("player", "moderator")))
                .track(TrackRecord.of("solo", Arrays.asList("player")))
                .user(
                    UserRecord.of(
                        PLAYER,
                        "Steve",
                        "player",
                        Arrays.asList(UserRecord.Grant.permanent("player"), UserRecord.Grant.permanent("vip")),
                        nodes(),
                        meta()))
                .build());

        assertTrue(
            admin.deleteGroup("player", ChangeCause.COMMAND, "console")
                .successful());

        Snapshot snapshot = writer.snapshot();
        assertFalse(
            snapshot.group("player")
                .isPresent());
        assertTrue(
            snapshot.group("moderator")
                .get()
                .inherits()
                .isEmpty());
        assertEquals(
            Arrays.asList("moderator"),
            snapshot.track("main")
                .get()
                .groups());
        assertFalse(
            snapshot.track("solo")
                .isPresent());
        UserRecord user = snapshot.user(PLAYER)
            .get();
        assertNull(user.primary());
        assertEquals(Arrays.asList("vip"), user.activeGroupIds(NOW));
    }

    @Test
    void allowReplacesTheDenyOfTheSameNode() {
        admin.createGroup("vip", "", 0, ChangeCause.COMMAND, "console");
        admin.setGroupNode("vip", NodeEntry.parse("-" + CREATE), ChangeCause.COMMAND, "console");

        admin.setGroupNode("vip", NodeEntry.parse(CREATE), ChangeCause.COMMAND, "console");

        List<NodeEntry> held = writer.snapshot()
            .group("vip")
            .get()
            .nodes();
        assertEquals(1, held.size());
        assertTrue(
            held.get(0)
                .value());
    }

    @Test
    void rulesWithDifferentContextsLiveSideBySide() {
        ContextSet nether = ContextSet.builder()
            .put("world", "nether")
            .build();
        admin.createGroup("vip", "", 0, ChangeCause.COMMAND, "console");
        admin.setGroupNode("vip", NodeEntry.parse(CREATE), ChangeCause.COMMAND, "console");

        admin.setGroupNode("vip", NodeEntry.of(CREATE, false, nether, 0L), ChangeCause.COMMAND, "console");

        assertEquals(
            2,
            writer.snapshot()
                .group("vip")
                .get()
                .nodes()
                .size());
    }

    @Test
    void moveKeepsTheExpiryAndThePrimaryInOneCommit() {
        writer.seed(
            Snapshot.builder()
                .group(group("player", 0))
                .group(group("vip", 10))
                .user(
                    UserRecord.of(
                        PLAYER,
                        "Steve",
                        "player",
                        Arrays.asList(UserRecord.Grant.of("player", NOW + 7200_000L)),
                        nodes(),
                        meta()))
                .build());

        assertTrue(
            admin.movePlayerGroup(PLAYER, "player", "vip", ChangeCause.COMMAND, "console")
                .successful());

        UserRecord user = writer.snapshot()
            .user(PLAYER)
            .get();
        assertEquals(1, writer.batches.size());
        assertEquals(Arrays.asList("vip"), user.activeGroupIds(NOW));
        assertEquals(
            NOW + 7200_000L,
            user.groups()
                .get(0)
                .expiresAt());
        assertEquals("vip", user.primary());
    }

    @Test
    void refusedMoveLeavesThePlayerInTheOldGroup() {
        writer.seed(
            Snapshot.builder()
                .group(group("player", 0))
                .group(group("vip", 10))
                .user(
                    UserRecord.of(
                        PLAYER,
                        "Steve",
                        null,
                        Arrays.asList(UserRecord.Grant.permanent("player")),
                        nodes(),
                        meta()))
                .build());
        writer.failure = OperationResult.failure(OperationResult.Failure.PROVIDER_FAILED, "store is down");

        assertEquals(
            OperationResult.Failure.PROVIDER_FAILED,
            admin.movePlayerGroup(PLAYER, "player", "vip", ChangeCause.COMMAND, "console")
                .failure()
                .get());

        assertEquals(
            Arrays.asList("player"),
            writer.snapshot()
                .user(PLAYER)
                .get()
                .activeGroupIds(NOW));
    }

    @Test
    void moveOfAGroupThePlayerDoesNotHoldIsRefused() {
        writer.seed(
            Snapshot.builder()
                .group(group("player", 0))
                .group(group("vip", 10))
                .build());

        assertEquals(
            OperationResult.Failure.NOT_FOUND,
            admin.movePlayerGroup(PLAYER, "player", "vip", ChangeCause.COMMAND, "console")
                .failure()
                .get());
        assertEquals(
            OperationResult.Failure.NOT_FOUND,
            admin.movePlayerGroup(PLAYER, "player", "ghost", ChangeCause.COMMAND, "console")
                .failure()
                .get());
    }

    @Test
    void sameRuleIsReplacedInsteadOfAppended() {
        admin.createGroup("vip", "", 0, ChangeCause.COMMAND, "console");
        admin.setGroupNode("vip", NodeEntry.parse(CREATE), ChangeCause.COMMAND, "console");

        admin.setGroupNode(
            "vip",
            NodeEntry.of(CREATE, true, ContextSet.empty(), NOW + 1000L),
            ChangeCause.COMMAND,
            "console");

        List<NodeEntry> nodes = writer.snapshot()
            .group("vip")
            .get()
            .nodes();
        assertEquals(1, nodes.size());
        assertEquals(
            NOW + 1000L,
            nodes.get(0)
                .expiresAt());
    }

    @Test
    void removingGroupNodeDropsEveryVariantOfIt() {
        admin.createGroup("vip", "", 0, ChangeCause.COMMAND, "console");
        admin.setGroupNode("vip", NodeEntry.parse(CREATE), ChangeCause.COMMAND, "console");
        admin.setGroupNode("vip", NodeEntry.parse("-" + CREATE), ChangeCause.COMMAND, "console");

        OperationResult result = admin.removeGroupNode("vip", CREATE, ChangeCause.COMMAND, "console");

        assertTrue(result.successful());
        assertTrue(
            writer.snapshot()
                .group("vip")
                .get()
                .nodes()
                .isEmpty());
        assertEquals(
            OperationResult.Failure.NOT_FOUND,
            admin.removeGroupNode("vip", CREATE, ChangeCause.COMMAND, "console")
                .failure()
                .get());
    }

    @Test
    void playerRecordsAreCreatedOnFirstEdit() {
        OperationResult result = admin.setPlayerMeta(PLAYER, "prefix", "&c", ChangeCause.COMMAND, "console");

        assertTrue(result.successful());
        UserRecord user = writer.snapshot()
            .user(PLAYER)
            .get();
        assertEquals(
            "&c",
            user.meta()
                .get("prefix"));
        assertNull(user.name());
    }

    @Test
    void addGroupGrantHoldsAbsoluteExpiry() {
        admin.createGroup("vip", "", 0, ChangeCause.COMMAND, "console");

        OperationResult result = admin.addPlayerGroup(PLAYER, "vip", NOW + 5000L, ChangeCause.COMMAND, "console");

        assertTrue(result.successful());
        assertEquals(
            NOW + 5000L,
            writer.snapshot()
                .user(PLAYER)
                .get()
                .groups()
                .get(0)
                .expiresAt());
        assertEquals(
            OperationResult.Failure.NOT_FOUND,
            admin.addPlayerGroup(PLAYER, "ghost", 0L, ChangeCause.COMMAND, "console")
                .failure()
                .get());
    }

    @Test
    void cleanupDropsOnlyExpiredEntries() {
        admin.createGroup("vip", "", 0, ChangeCause.COMMAND, "console");
        admin.addPlayerGroup(PLAYER, "vip", NOW - 1L, ChangeCause.COMMAND, "console");
        admin.setPlayerNode(
            PLAYER,
            NodeEntry.of(CREATE, true, ContextSet.empty(), NOW + 5000L),
            ChangeCause.COMMAND,
            "console");

        OperationResult result = admin.cleanupPlayer(PLAYER, ChangeCause.EXPIRY, "codeperms");

        assertTrue(result.successful());
        UserRecord user = writer.snapshot()
            .user(PLAYER)
            .get();
        assertTrue(
            user.groups()
                .isEmpty());
        assertEquals(
            1,
            user.nodes()
                .size());
    }

    @Test
    void cleanupWithoutExpiredWorkChangesNothing() {
        admin.setPlayerNode(PLAYER, NodeEntry.parse(CREATE), ChangeCause.COMMAND, "console");
        Snapshot before = writer.snapshot();

        OperationResult result = admin.cleanupPlayer(PLAYER, ChangeCause.EXPIRY, "codeperms");

        assertTrue(result.successful());
        assertSame(before, writer.snapshot());
        assertEquals(1, writer.batches.size());
    }

    @Test
    void providerFailureKeepsTheLastGoodSnapshotAndSkipsTheEvent() {
        admin.createGroup("vip", "", 0, ChangeCause.COMMAND, "console");
        coalescer.dispatch();
        listener.events.clear();
        Snapshot before = writer.snapshot();
        writer.failure = OperationResult.failure(OperationResult.Failure.PROVIDER_FAILED, "store is down");

        OperationResult result = admin.createGroup("mod", "", 0, ChangeCause.COMMAND, "console");

        assertEquals(
            OperationResult.Failure.PROVIDER_FAILED,
            result.failure()
                .get());
        assertSame(before, writer.snapshot());
        coalescer.dispatch();
        assertTrue(listener.events.isEmpty());
        assertEquals(2, writer.batches.size());
    }

    @Test
    void metaStackFollowsTheSourceOrderThroughTheApi() {
        admin.createGroup("base", "", 0, ChangeCause.COMMAND, "console");
        admin.setGroupMeta("base", "prefix", "&7", ChangeCause.COMMAND, "console");
        admin.createGroup("top", "", 10, ChangeCause.COMMAND, "console");
        admin.setGroupMeta("top", "prefix", "&c", ChangeCause.COMMAND, "console");
        admin.addParent("top", "base", ChangeCause.COMMAND, "console");
        admin.addPlayerGroup(PLAYER, "top", 0L, ChangeCause.COMMAND, "console");

        assertEquals(Arrays.asList("&c", "&7"), admin.metaStack(PLAYER, "prefix"));
        assertTrue(
            admin.metaStack(PLAYER, "suffix")
                .isEmpty());
    }

    @Test
    void explainNamesTheWinnerAndEveryCandidate() {
        admin.createGroup("vip", "", 10, ChangeCause.COMMAND, "console");
        admin.setGroupNode("vip", NodeEntry.parse(CREATE), ChangeCause.COMMAND, "console");
        admin.addPlayerGroup(PLAYER, "vip", 0L, ChangeCause.COMMAND, "console");

        Resolution allowed = admin.explain(PLAYER, CREATE, ContextSet.empty());

        assertTrue(allowed.allowed());
        assertEquals(
            "group:vip",
            allowed.winner()
                .get()
                .source());
        assertEquals(
            CREATE,
            allowed.winner()
                .get()
                .entry()
                .node());

        admin.setPlayerNode(PLAYER, NodeEntry.parse("-" + CREATE), ChangeCause.API, "clans");

        assertFalse(
            admin.explain(PLAYER, CREATE, ContextSet.empty())
                .allowed());
        assertTrue(
            admin.explain(PLAYER, "codechat.missing", ContextSet.empty())
                .candidates()
                .isEmpty());
    }

    private static GroupRecord group(String id, int weight) {
        return GroupRecord.of(id, id, weight, Collections.<String>emptyList(), nodes(), meta());
    }

    private static List<NodeEntry> nodes() {
        return Collections.<NodeEntry>emptyList();
    }

    private static Map<String, String> meta() {
        return Collections.<String, String>emptyMap();
    }

    private static final class FakeWriter implements SingleWriter {

        private Snapshot current = Snapshot.empty();
        private final List<ChangeBatch> batches = new ArrayList<>();
        private OperationResult failure = OperationResult.success();

        void seed(Snapshot snapshot) {
            current = snapshot;
        }

        @Override
        public Snapshot snapshot() {
            return current;
        }

        @Override
        public OperationResult commit(Snapshot next, ChangeBatch batch) {
            batches.add(batch);
            OperationResult result = failure;
            if (result.successful()) {
                current = next;
            }
            return result;
        }

        @Override
        public void flush() {}
    }

    private static final class RecordingListener implements PermissionsListener {

        private final List<ChangeEvent> events = new ArrayList<>();

        @Override
        public void onChange(ChangeEvent event) {
            events.add(event);
        }
    }
}
