package com.mrleonardos.codeperms.internal.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mrleonardos.codecore.api.command.ArgumentSpec;
import com.mrleonardos.codecore.api.command.ArgumentType;
import com.mrleonardos.codecore.api.command.CommandContext;
import com.mrleonardos.codecore.api.command.CommandMessages;
import com.mrleonardos.codecore.api.command.CommandNode;
import com.mrleonardos.codecore.api.command.CommandService;
import com.mrleonardos.codeperms.api.PermsLimits;
import com.mrleonardos.codeperms.api.model.ChangeCause;
import com.mrleonardos.codeperms.api.model.ContextSet;
import com.mrleonardos.codeperms.api.model.GroupRecord;
import com.mrleonardos.codeperms.api.model.NodeEntry;
import com.mrleonardos.codeperms.api.model.Snapshot;
import com.mrleonardos.codeperms.api.model.TrackRecord;
import com.mrleonardos.codeperms.api.model.UserRecord;
import com.mrleonardos.codeperms.api.store.ChangeBatch;
import com.mrleonardos.codeperms.api.store.OperationResult;
import com.mrleonardos.codeperms.internal.admin.ChangeCoalescer;
import com.mrleonardos.codeperms.internal.admin.PermsAdminImpl;
import com.mrleonardos.codeperms.internal.engine.ResolverImpl;
import com.mrleonardos.codeperms.internal.store.CoreGroupsImporter;
import com.mrleonardos.codeperms.internal.store.SingleWriter;

class PermsCommandsTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final String CREATE = "codechat.create";

    private FakeWriter writer;
    private PermsAdminImpl admin;
    private TestSubjects subjects;
    private RecordingPresents presents;
    private RecordingMaintenance maintenance;
    private PermsCommands commands;

    @BeforeEach
    void setUp() {
        writer = new FakeWriter();
        admin = new PermsAdminImpl(
            writer,
            new ChangeCoalescer(LogManager.getLogger("codeperms-test")),
            PermsLimits.defaults());
        subjects = new TestSubjects();
        presents = new RecordingPresents();
        maintenance = new RecordingMaintenance();
        commands = new PermsCommands(
            writer,
            admin,
            () -> PermsLimits.defaults(),
            arguments(),
            subjects,
            presents,
            maintenance,
            new DebugView(new ResolverImpl()));
    }

    @Test
    void rootIsOpenAndOnlyNamesTheBranches() {
        CommandNode root = commands.root();

        assertEquals("perms", root.name());
        assertNull(root.permissionNode());
        assertNull(root.action());
        assertEquals(
            Arrays.asList("me", "group", "player", "track", "import", "export", "reload", "debug"),
            names(root));
    }

    @Test
    void branchesCarryTheirOwnNodes() {
        CommandNode root = commands.root();
        CommandNode group = child(root, "group");
        CommandNode player = child(root, "player");
        CommandNode track = child(root, "track");

        assertEquals(PermsPermissions.ME, child(root, "me").permissionNode());
        assertEquals(PermsPermissions.GROUP_LIST, child(group, "list").permissionNode());
        assertEquals(PermsPermissions.GROUP_INFO, child(group, "info").permissionNode());
        assertEquals(PermsPermissions.GROUP_CREATE, child(group, "create").permissionNode());
        assertEquals(PermsPermissions.GROUP_DELETE, child(group, "delete").permissionNode());
        assertEquals(PermsPermissions.GROUP_RENAME, child(group, "rename").permissionNode());
        assertEquals(PermsPermissions.GROUP_COPY, child(group, "copy").permissionNode());
        assertEquals(PermsPermissions.GROUP_WEIGHT, child(group, "setweight").permissionNode());
        assertEquals(PermsPermissions.GROUP_EDIT, child(group, "parent").permissionNode());
        assertEquals(PermsPermissions.GROUP_EDIT, child(group, "node").permissionNode());
        assertEquals(PermsPermissions.GROUP_EDIT, child(group, "meta").permissionNode());
        assertEquals(PermsPermissions.PLAYER_INFO, child(player, "info").permissionNode());
        assertEquals(PermsPermissions.PLAYER_SET_GROUP, child(player, "setgroup").permissionNode());
        assertEquals(PermsPermissions.PLAYER_ADD_GROUP, child(player, "addgroup").permissionNode());
        assertEquals(PermsPermissions.PLAYER_REMOVE_GROUP, child(player, "rmgroup").permissionNode());
        assertEquals(PermsPermissions.PLAYER_NODE, child(player, "node").permissionNode());
        assertEquals(PermsPermissions.PLAYER_META, child(player, "meta").permissionNode());
        assertEquals(PermsPermissions.PLAYER_CLEANUP, child(player, "cleanup").permissionNode());
        assertEquals(PermsPermissions.TRACK_LIST, child(track, "list").permissionNode());
        assertEquals(PermsPermissions.TRACK_INFO, child(track, "info").permissionNode());
        assertEquals(PermsPermissions.TRACK_PROMOTE, child(track, "promote").permissionNode());
        assertEquals(PermsPermissions.TRACK_DEMOTE, child(track, "demote").permissionNode());
        assertEquals(PermsPermissions.IMPORT, child(root, "import").permissionNode());
        assertEquals(PermsPermissions.EXPORT, child(root, "export").permissionNode());
        assertEquals(PermsPermissions.RELOAD, child(root, "reload").permissionNode());
        assertEquals(PermsPermissions.DEBUG, child(root, "debug").permissionNode());
    }

    @Test
    void guestWithoutNodesSeesNoWorkingCommand() {
        CommandNode root = commands.root();

        assertTrue(visible(child(root, "group"), nothing).isEmpty());
        assertTrue(visible(child(root, "player"), nothing).isEmpty());
        assertTrue(visible(child(root, "track"), nothing).isEmpty());
        assertEquals(Arrays.asList("me", "group", "player", "track"), visible(root, owns(PermsPermissions.ME)));
    }

    @Test
    void moderatorWithOnlyPlayerInfoSeesNothingToChange() {
        CommandNode player = child(commands.root(), "player");

        assertEquals(Arrays.asList("info"), visible(player, owns(PermsPermissions.PLAYER_INFO)));
        assertEquals(
            Arrays.asList("info"),
            visible(player, owns(PermsPermissions.PLAYER_INFO).or(owns(PermsPermissions.ME))));
    }

    @Test
    void registerHandsTheTreeToTheCore() {
        RecordingCommandService service = new RecordingCommandService();

        commands.register(service);

        assertEquals("perms", service.registered.name());
    }

    @Test
    void createGroupGoesThroughTheAdminAndAnswers() {
        TestCommandContext context = new TestCommandContext().set("group", "vip")
            .set("weight", 10);

        execute(child(child(commands.root(), "group"), "create"), context);

        assertTrue(
            writer.snapshot()
                .group("vip")
                .isPresent());
        assertEquals(
            ChangeCause.COMMAND,
            writer.batches.get(0)
                .cause());
        assertEquals(
            "console",
            writer.batches.get(0)
                .author());
        assertTrue(
            context.last()
                .is(PermsMessages.GROUP_CREATED));
    }

    @Test
    void oversizedGroupIdAnswersWithTheCeiling() {
        StringBuilder identifier = new StringBuilder();
        for (int index = 0; index < 100; index++) {
            identifier.append('a');
        }
        TestCommandContext context = new TestCommandContext().set("group", identifier.toString());

        execute(child(child(commands.root(), "group"), "create"), context);

        assertTrue(context.last().error);
        assertTrue(
            context.last()
                .is(PermsMessages.FAILURE_LIMIT_REACHED));
        assertTrue(
            writer.snapshot()
                .groups()
                .isEmpty());
    }

    @Test
    void unknownGroupAnswersNotFound() {
        TestCommandContext context = new TestCommandContext().set("group", "nope");

        execute(child(child(commands.root(), "group"), "info"), context);

        assertTrue(context.last().error);
        assertTrue(
            context.last()
                .is(PermsMessages.FAILURE_NOT_FOUND));
    }

    @Test
    void denyPrefixReachesTheModel() {
        TestCommandContext context = new TestCommandContext().set("player", PLAYER.toString())
            .set("node", "-" + CREATE);

        execute(child(child(child(commands.root(), "player"), "node"), "set"), context);

        assertFalse(
            writer.snapshot()
                .user(PLAYER)
                .get()
                .nodes()
                .get(0)
                .value());
    }

    @Test
    void durationTurnsIntoAnAbsoluteDeadline() {
        TestCommandContext context = new TestCommandContext().set("player", PLAYER.toString())
            .set("node", CREATE)
            .set("expiry", "1h");

        execute(child(child(child(commands.root(), "player"), "node"), "set"), context);

        NodeEntry entry = writer.snapshot()
            .user(PLAYER)
            .get()
            .nodes()
            .get(0);
        assertFalse(entry.permanent());
        long left = entry.expiresAt() - System.currentTimeMillis();
        assertTrue(left > 3_500_000L && left < 3_700_000L, "срок должен быть около часа, а вышел " + left);
    }

    @Test
    void meNeedsAPlayer() {
        subjects.self = Optional.empty();

        TestCommandContext context = new TestCommandContext();
        execute(child(commands.root(), "me"), context);

        assertEquals(1, subjects.calls);
        assertTrue(context.last().error);
        assertTrue(
            context.last()
                .is(CommandMessages.PLAYERS_ONLY));
    }

    @Test
    void meListsGroupsMetaAndExpiry() {
        subjects.self = Optional.of(PLAYER);
        writer.seed(
            Snapshot.builder()
                .group(group("vip", 10))
                .user(
                    UserRecord.of(
                        PLAYER,
                        "Steve",
                        "vip",
                        Collections.singletonList(UserRecord.Grant.of("vip", System.currentTimeMillis() + 60_000L)),
                        Collections.<NodeEntry>emptyList(),
                        Collections.singletonMap("prefix", "&c")))
                .build());

        TestCommandContext context = new TestCommandContext();
        execute(child(commands.root(), "me"), context);

        assertEquals(
            PermsMessages.ME_HEADER,
            context.sent()
                .get(0).key);
        assertEquals(
            PermsMessages.ME_GROUP_TEMPORARY,
            context.sent()
                .get(1).key);
        assertEquals(PermsMessages.ME_META, context.last().key);
        assertEquals("prefix", context.last().arguments.get(0));
        assertEquals("&c", context.last().arguments.get(1));
    }

    @Test
    void importFlagsReachTheMaintenance() {
        maintenance.counts.groups = 2;
        maintenance.counts.players = 1;
        TestCommandContext dry = new TestCommandContext().set("flags", "--dry-run");
        execute(child(commands.root(), "import"), dry);

        assertTrue(maintenance.dryRun);
        assertFalse(maintenance.force);
        assertTrue(
            dry.last()
                .is(PermsMessages.IMPORT_DRY));

        TestCommandContext forced = new TestCommandContext().set("flags", "--force");
        execute(child(commands.root(), "import"), forced);
        assertTrue(maintenance.force);
        assertTrue(
            forced.last()
                .is(PermsMessages.IMPORT_DONE));

        maintenance.counts.groups = 0;
        maintenance.counts.players = 0;
        TestCommandContext empty = new TestCommandContext();
        execute(child(commands.root(), "import"), empty);
        assertTrue(
            empty.last()
                .is(PermsMessages.IMPORT_NOTHING));

        TestCommandContext broken = new TestCommandContext().set("flags", "--bogus");
        execute(child(commands.root(), "import"), broken);
        assertTrue(broken.last().error);
        assertTrue(
            broken.last()
                .is(PermsMessages.FAILURE_INVALID_VALUE));
    }

    @Test
    void importWithoutForceExplainsTheChangedSource() {
        maintenance.result = OperationResult.failure(OperationResult.Failure.INVALID_VALUE, "source changed");

        TestCommandContext context = new TestCommandContext().set("flags", "");
        execute(child(commands.root(), "import"), context);

        assertTrue(context.last().error);
        assertTrue(
            context.last()
                .is(PermsMessages.IMPORT_CHANGED));
    }

    @Test
    void promoteWalksTheTrack() {
        writer.seed(
            Snapshot.builder()
                .group(group("player", 0))
                .group(group("vip", 10))
                .track(TrackRecord.of("ladder", Arrays.asList("player", "vip")))
                .user(user("Steve", null, Collections.singletonList(UserRecord.Grant.permanent("player"))))
                .build());
        TestCommandContext context = new TestCommandContext().set("player", PLAYER.toString());

        execute(child(child(commands.root(), "track"), "promote"), context);

        assertEquals(
            "vip",
            writer.snapshot()
                .user(PLAYER)
                .get()
                .groups()
                .get(0)
                .groupId());
        assertTrue(
            context.last()
                .is(PermsMessages.TRACK_PROMOTED));
    }

    @Test
    void nodeRemoveTakesTheSameFormThatInfoShows() {
        writer.seed(
            Snapshot.builder()
                .group(group("vip", 10))
                .build());
        TestCommandContext set = new TestCommandContext().set("group", "vip")
            .set("node", "-" + CREATE);
        execute(child(child(child(commands.root(), "group"), "node"), "set"), set);

        TestCommandContext removed = new TestCommandContext().set("group", "vip")
            .set("node", "-" + CREATE);
        execute(child(child(child(commands.root(), "group"), "node"), "remove"), removed);

        assertFalse(removed.last().error, "снятие ноды с минусом обязано работать");
        assertTrue(
            writer.snapshot()
                .group("vip")
                .get()
                .nodes()
                .isEmpty());
    }

    @Test
    void promoteMovesThePlayerInOneCommit() {
        writer.seed(
            Snapshot.builder()
                .group(group("player", 0))
                .group(group("vip", 10))
                .track(TrackRecord.of("ladder", Arrays.asList("player", "vip")))
                .user(user("Steve", "player", Collections.singletonList(UserRecord.Grant.permanent("player"))))
                .build());
        TestCommandContext context = new TestCommandContext().set("player", PLAYER.toString());

        execute(child(child(commands.root(), "track"), "promote"), context);

        assertEquals(1, writer.batches.size());
        UserRecord user = writer.snapshot()
            .user(PLAYER)
            .get();
        assertEquals(Arrays.asList("vip"), user.activeGroupIds(System.currentTimeMillis()));
        assertEquals("vip", user.primary());
    }

    @Test
    void overflowingDurationIsRefusedInsteadOfWrappingAround() {
        CommandNode node = child(child(child(commands.root(), "player"), "node"), "set");
        ArgumentType<?> expiry = typeOf(node, "expiry");

        assertThrows(RuntimeException.class, () -> expiry.parse("99421d"));
        assertThrows(RuntimeException.class, () -> expiry.parse("4294967296"));

        long parsed = (Long) expiry.parse("1h");
        assertTrue(parsed > System.currentTimeMillis());
    }

    @Test
    void promoteIntoMissingGroupRefusesBeforeAnyChange() {
        writer.seed(
            Snapshot.builder()
                .group(group("player", 0))
                .track(TrackRecord.of("ladder", Arrays.asList("player", "vip")))
                .user(user("Steve", null, Collections.singletonList(UserRecord.Grant.permanent("player"))))
                .build());
        TestCommandContext context = new TestCommandContext().set("player", PLAYER.toString());

        execute(child(child(commands.root(), "track"), "promote"), context);

        assertTrue(context.last().error);
        assertTrue(
            context.last()
                .is(PermsMessages.FAILURE_NOT_FOUND));
        assertEquals(
            "player",
            writer.snapshot()
                .user(PLAYER)
                .get()
                .groups()
                .get(0)
                .groupId());
    }

    @Test
    void unknownTrackAnswerUnderstandably() {
        writer.seed(
            Snapshot.builder()
                .track(TrackRecord.of("ladder", Arrays.asList("player")))
                .user(user("Steve", null, Collections.singletonList(UserRecord.Grant.permanent("player"))))
                .build());
        TestCommandContext context = new TestCommandContext().set("player", PLAYER.toString())
            .set("track", "moderator");

        execute(child(child(commands.root(), "track"), "promote"), context);

        assertTrue(context.last().error);
        assertTrue(
            context.last()
                .is(PermsMessages.TRACK_UNKNOWN));
    }

    @Test
    void setgroupWithoutGroupClearsTheAssignment() {
        writer.seed(
            Snapshot.builder()
                .group(group("vip", 10))
                .user(user("Steve", "vip", Collections.singletonList(UserRecord.Grant.permanent("vip"))))
                .build());
        TestCommandContext context = new TestCommandContext().set("player", PLAYER.toString());

        execute(child(child(commands.root(), "player"), "setgroup"), context);

        assertNull(
            writer.snapshot()
                .user(PLAYER)
                .get()
                .primary());
        assertTrue(
            context.last()
                .is(PermsMessages.PLAYER_GROUP_CLEARED));
    }

    @Test
    void reloadReachesTheMaintenance() {
        TestCommandContext context = new TestCommandContext();

        execute(child(commands.root(), "reload"), context);

        assertTrue(maintenance.reloaded);
        assertTrue(
            context.last()
                .is(PermsMessages.RELOAD_DONE));
    }

    @Test
    void listsAndCardsGoThroughThePresents() {
        writer.seed(
            Snapshot.builder()
                .group(group("vip", 10))
                .group(group("player", 0))
                .track(TrackRecord.of("ladder", Arrays.asList("player", "vip")))
                .user(user("Steve", "vip", Collections.singletonList(UserRecord.Grant.permanent("vip"))))
                .build());
        TestCommandContext groups = new TestCommandContext().set("page", 2);
        execute(child(child(commands.root(), "group"), "list"), groups);
        TestCommandContext groupCard = new TestCommandContext().set("group", "vip");
        execute(child(child(commands.root(), "group"), "info"), groupCard);
        TestCommandContext playerCard = new TestCommandContext().set("player", PLAYER.toString());
        execute(child(child(commands.root(), "player"), "info"), playerCard);
        TestCommandContext playerNodes = new TestCommandContext().set("player", PLAYER.toString())
            .set("page", 3);
        execute(child(child(commands.root(), "player"), "info"), playerNodes);
        TestCommandContext tracks = new TestCommandContext();
        execute(child(child(commands.root(), "track"), "list"), tracks);
        TestCommandContext trackCard = new TestCommandContext().set("track", "ladder");
        execute(child(child(commands.root(), "track"), "info"), trackCard);

        assertEquals(
            Arrays.asList(
                "groupList:2:2",
                "groupInfo:vip:1",
                "playerInfo:Steve:1",
                "playerInfo:Steve:3",
                "trackList:1:1",
                "trackInfo:ladder"),
            presents.calls,
            "команды отдают вывод карточек слою present со своей страницей");
        assertEquals(commands.rootName(), presents.root, "клики получают имя фактического корня дерева");
    }

    private static void execute(CommandNode node, TestCommandContext context) {
        for (ArgumentSpec spec : node.arguments()) {
            if (!context.has(spec.name())) {
                continue;
            }
            Object raw = context.get(spec.name());
            context.put(
                spec.name(),
                spec.type()
                    .parse(String.valueOf(raw)));
        }
        node.action()
            .run(context);
    }

    private static final Predicate<String> nothing = held -> false;

    private static Predicate<String> owns(String node) {
        return held -> held.equals(node);
    }

    private static List<String> visible(CommandNode branch, Predicate<String> held) {
        List<String> names = new ArrayList<>();
        for (CommandNode child : branch.children()) {
            String permission = child.permissionNode();
            if (permission == null || held.test(permission)) {
                names.add(child.name());
            }
        }
        return names;
    }

    private static List<String> names(CommandNode node) {
        List<String> names = new ArrayList<>();
        for (CommandNode child : node.children()) {
            names.add(child.name());
        }
        return names;
    }

    private static ArgumentType<?> typeOf(CommandNode node, String argument) {
        for (ArgumentSpec spec : node.arguments()) {
            if (spec.name()
                .equals(argument)) {
                return spec.type();
            }
        }
        throw new AssertionError("No argument " + argument + " under " + node.name());
    }

    private static CommandNode child(CommandNode node, String name) {
        for (CommandNode child : node.children()) {
            if (child.name()
                .equals(name)) {
                return child;
            }
        }
        throw new AssertionError("No child " + name + " under " + node.name());
    }

    private static GroupRecord group(String id, int weight) {
        return GroupRecord.of(
            id,
            id,
            weight,
            Collections.<String>emptyList(),
            Collections.<NodeEntry>emptyList(),
            Collections.<String, String>emptyMap());
    }

    private static UserRecord user(String name, String primary, List<UserRecord.Grant> grants) {
        return UserRecord.of(
            PLAYER,
            name,
            primary,
            grants,
            Collections.<NodeEntry>emptyList(),
            Collections.<String, String>emptyMap());
    }

    private static PermsArguments arguments() {
        return new PermsArguments() {

            @Override
            public ArgumentType<String> groupId() {
                return raw -> raw;
            }

            @Override
            public ArgumentType<String> trackName() {
                return raw -> raw;
            }

            @Override
            public ArgumentType<UUID> player() {
                return UUID::fromString;
            }

            @Override
            public ArgumentType<String> node() {
                return raw -> raw;
            }

            @Override
            public ArgumentType<Long> expiry() {
                return PermsArguments.expiryType();
            }
        };
    }

    private static final class FakeWriter implements SingleWriter {

        private Snapshot current = Snapshot.empty();
        private final List<ChangeBatch> batches = new ArrayList<>();

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
            current = next;
            return OperationResult.success();
        }

        @Override
        public void flush() {}
    }

    private static final class TestSubjects implements PermsSubjects {

        private Optional<UUID> self = Optional.of(PLAYER);
        private int calls;

        @Override
        public Optional<UUID> subjectOf(CommandContext context) {
            calls++;
            return self;
        }

        @Override
        public String senderName(CommandContext context) {
            return "console";
        }

        @Override
        public Optional<String> playerName(UUID player) {
            return Optional.of("Steve");
        }

        @Override
        public ContextSet contexts(UUID player) {
            return ContextSet.empty();
        }
    }

    private static final class RecordingPresents implements PermsPresents {

        private final List<String> calls = new ArrayList<>();
        private String root;

        @Override
        public void groupList(CommandContext context, Snapshot snapshot, UUID viewer, int page, String root) {
            remember(root);
            calls.add(
                "groupList:" + snapshot.groups()
                    .size() + ":" + page);
        }

        @Override
        public void groupInfo(CommandContext context, Snapshot snapshot, GroupRecord group, UUID viewer, int page,
            String root) {
            remember(root);
            calls.add("groupInfo:" + group.id() + ":" + page);
        }

        @Override
        public void playerInfo(CommandContext context, Snapshot snapshot, UUID player, String name, UUID viewer,
            int page, String root) {
            remember(root);
            calls.add("playerInfo:" + name + ":" + page);
        }

        @Override
        public void trackList(CommandContext context, Snapshot snapshot, UUID viewer, int page, String root) {
            remember(root);
            calls.add(
                "trackList:" + snapshot.tracks()
                    .size() + ":" + page);
        }

        @Override
        public void trackInfo(CommandContext context, TrackRecord track, UUID viewer, String root) {
            remember(root);
            calls.add("trackInfo:" + track.name());
        }

        private void remember(String actualRoot) {
            root = actualRoot;
        }
    }

    private static final class RecordingMaintenance implements PermsMaintenance {

        private OperationResult result = OperationResult.success();
        private CoreGroupsImporter.Counts counts = new CoreGroupsImporter.Counts();
        private boolean dryRun;
        private boolean force;
        private boolean reloaded;

        @Override
        public OperationResult reload() {
            reloaded = true;
            return result;
        }

        @Override
        public Outcome importFromCore(boolean dryRun, boolean force) {
            this.dryRun = dryRun;
            this.force = force;
            return new Outcome(result, counts);
        }

        @Override
        public Outcome exportToCoreFormat() {
            return new Outcome(result, counts);
        }
    }

    private static final class RecordingCommandService implements CommandService {

        private CommandNode registered;

        @Override
        public void register(CommandNode root) {
            registered = root;
        }
    }
}
