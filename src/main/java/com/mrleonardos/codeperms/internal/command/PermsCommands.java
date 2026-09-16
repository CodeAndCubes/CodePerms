package com.mrleonardos.codeperms.internal.command;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import com.mrleonardos.codecore.api.command.ArgumentTypes;
import com.mrleonardos.codecore.api.command.CommandContext;
import com.mrleonardos.codecore.api.command.CommandMessages;
import com.mrleonardos.codecore.api.command.CommandNode;
import com.mrleonardos.codecore.api.command.CommandService;
import com.mrleonardos.codecore.api.util.Durations;
import com.mrleonardos.codeperms.api.PermsLimits;
import com.mrleonardos.codeperms.api.manage.PermsAdmin;
import com.mrleonardos.codeperms.api.model.ChangeCause;
import com.mrleonardos.codeperms.api.model.GroupRecord;
import com.mrleonardos.codeperms.api.model.NodeEntry;
import com.mrleonardos.codeperms.api.model.Snapshot;
import com.mrleonardos.codeperms.api.model.TrackRecord;
import com.mrleonardos.codeperms.api.model.UserRecord;
import com.mrleonardos.codeperms.api.store.OperationResult;
import com.mrleonardos.codeperms.internal.store.SingleWriter;

public final class PermsCommands {

    private static final String GROUP_ARGUMENT = "group";
    private static final String PARENT_ARGUMENT = "parent";
    private static final String TARGET_ARGUMENT = "target";
    private static final String PLAYER_ARGUMENT = "player";
    private static final String NODE_ARGUMENT = "node";
    private static final String KEY_ARGUMENT = "key";
    private static final String VALUE_ARGUMENT = "value";
    private static final String WEIGHT_ARGUMENT = "weight";
    private static final String NAME_ARGUMENT = "name";
    private static final String EXPIRY_ARGUMENT = "expiry";
    private static final String TRACK_ARGUMENT = "track";
    private static final String FLAGS_ARGUMENT = "flags";
    private static final String PAGE_ARGUMENT = "page";

    private static final String DRY_RUN_FLAG = "--dry-run";
    private static final String FORCE_FLAG = "--force";
    private static final String EMPTY_MARKER = "-";

    private final SingleWriter writer;
    private final PermsAdmin admin;
    private final Supplier<PermsLimits> limits;
    private final PermsArguments arguments;
    private final PermsSubjects subjects;
    private final PermsPresents presents;
    private final PermsMaintenance maintenance;
    private final DebugView debug;
    private CommandNode root;

    public PermsCommands(SingleWriter writer, PermsAdmin admin, Supplier<PermsLimits> limits, PermsArguments arguments,
        PermsSubjects subjects, PermsPresents presents, PermsMaintenance maintenance, DebugView debug) {
        this.writer = writer;
        this.admin = admin;
        this.limits = limits;
        this.arguments = arguments;
        this.subjects = subjects;
        this.presents = presents;
        this.maintenance = maintenance;
        this.debug = debug;
    }

    public void register(CommandService commands) {
        commands.register(root());
    }

    /**
     * Фактическое дерево команд, каким его увидит сервер.
     *
     * <p>
     * Дерево строится один раз: повторный вызов отдаёт тот же объект, поэтому имя корня для карточек
     * спрашивается у зарегистрированного дерева, а не у параллельной строки в коде. Смена корня
     * меняет команды и клики вместе.
     */
    public CommandNode root() {
        if (root == null) {
            root = buildRoot();
        }
        return root;
    }

    /** Имя фактического корня: из него собираются клики карточек. */
    public String rootName() {
        return root().name();
    }

    private CommandNode buildRoot() {
        return CommandNode.literal("perms")
            .usage(PermsMessages.USAGE_ROOT)
            .child(
                CommandNode.literal("me")
                    .permission(PermsPermissions.ME)
                    .usage(PermsMessages.USAGE_ME)
                    .executes(this::me))
            .child(groupBranch())
            .child(playerBranch())
            .child(trackBranch())
            .child(
                CommandNode.literal("import")
                    .permission(PermsPermissions.IMPORT)
                    .usage(PermsMessages.USAGE_IMPORT)
                    .optionalArg(FLAGS_ARGUMENT, ArgumentTypes.text())
                    .executes(this::importFromCore))
            .child(
                CommandNode.literal("export")
                    .permission(PermsPermissions.EXPORT)
                    .usage(PermsMessages.USAGE_EXPORT)
                    .executes(this::export))
            .child(
                CommandNode.literal("reload")
                    .permission(PermsPermissions.RELOAD)
                    .usage(PermsMessages.USAGE_RELOAD)
                    .executes(this::reload))
            .child(
                CommandNode.literal("debug")
                    .permission(PermsPermissions.DEBUG)
                    .usage(PermsMessages.USAGE_DEBUG)
                    .arg(PLAYER_ARGUMENT, arguments.player())
                    .arg(NODE_ARGUMENT, arguments.node())
                    .executes(this::debugView));
    }

    private CommandNode groupBranch() {
        return CommandNode.literal("group")
            .usage(PermsMessages.USAGE_GROUP)
            .child(
                CommandNode.literal("list")
                    .permission(PermsPermissions.GROUP_LIST)
                    .optionalArg(PAGE_ARGUMENT, ArgumentTypes.integer(1, Integer.MAX_VALUE))
                    .executes(this::groupList))
            .child(
                CommandNode.literal("info")
                    .permission(PermsPermissions.GROUP_INFO)
                    .arg(GROUP_ARGUMENT, arguments.groupId())
                    .optionalArg(PAGE_ARGUMENT, ArgumentTypes.integer(1, Integer.MAX_VALUE))
                    .executes(this::groupInfo))
            .child(
                CommandNode.literal("create")
                    .permission(PermsPermissions.GROUP_CREATE)
                    .arg(GROUP_ARGUMENT, ArgumentTypes.word())
                    .optionalArg(WEIGHT_ARGUMENT, ArgumentTypes.integer(0, Integer.MAX_VALUE))
                    .optionalArg(NAME_ARGUMENT, ArgumentTypes.text())
                    .executes(this::groupCreate))
            .child(
                CommandNode.literal("delete")
                    .permission(PermsPermissions.GROUP_DELETE)
                    .arg(GROUP_ARGUMENT, arguments.groupId())
                    .executes(this::groupDelete))
            .child(
                CommandNode.literal("rename")
                    .permission(PermsPermissions.GROUP_RENAME)
                    .arg(GROUP_ARGUMENT, arguments.groupId())
                    .arg(TARGET_ARGUMENT, ArgumentTypes.word())
                    .executes(this::groupRename))
            .child(
                CommandNode.literal("copy")
                    .permission(PermsPermissions.GROUP_COPY)
                    .arg(GROUP_ARGUMENT, arguments.groupId())
                    .arg(TARGET_ARGUMENT, ArgumentTypes.word())
                    .executes(this::groupCopy))
            .child(
                CommandNode.literal("setweight")
                    .permission(PermsPermissions.GROUP_WEIGHT)
                    .arg(GROUP_ARGUMENT, arguments.groupId())
                    .arg(WEIGHT_ARGUMENT, ArgumentTypes.integer(0, Integer.MAX_VALUE))
                    .executes(this::groupWeight))
            .child(
                CommandNode.literal("parent")
                    .permission(PermsPermissions.GROUP_EDIT)
                    .child(
                        CommandNode.literal("add")
                            .arg(GROUP_ARGUMENT, arguments.groupId())
                            .arg(PARENT_ARGUMENT, arguments.groupId())
                            .executes(this::parentAdd))
                    .child(
                        CommandNode.literal("remove")
                            .arg(GROUP_ARGUMENT, arguments.groupId())
                            .arg(PARENT_ARGUMENT, arguments.groupId())
                            .executes(this::parentRemove)))
            .child(
                CommandNode.literal("node")
                    .permission(PermsPermissions.GROUP_EDIT)
                    .child(
                        CommandNode.literal("set")
                            .arg(GROUP_ARGUMENT, arguments.groupId())
                            .arg(NODE_ARGUMENT, arguments.node())
                            .optionalArg(EXPIRY_ARGUMENT, arguments.expiry())
                            .executes(this::groupNodeSet))
                    .child(
                        CommandNode.literal("remove")
                            .arg(GROUP_ARGUMENT, arguments.groupId())
                            .arg(NODE_ARGUMENT, arguments.node())
                            .executes(this::groupNodeRemove)))
            .child(
                CommandNode.literal("meta")
                    .permission(PermsPermissions.GROUP_EDIT)
                    .child(
                        CommandNode.literal("set")
                            .arg(GROUP_ARGUMENT, arguments.groupId())
                            .arg(KEY_ARGUMENT, ArgumentTypes.word())
                            .arg(VALUE_ARGUMENT, ArgumentTypes.text())
                            .executes(this::groupMetaSet))
                    .child(
                        CommandNode.literal("remove")
                            .arg(GROUP_ARGUMENT, arguments.groupId())
                            .arg(KEY_ARGUMENT, ArgumentTypes.word())
                            .executes(this::groupMetaRemove)));
    }

    private CommandNode playerBranch() {
        return CommandNode.literal("player")
            .usage(PermsMessages.USAGE_PLAYER)
            .child(
                CommandNode.literal("info")
                    .permission(PermsPermissions.PLAYER_INFO)
                    .arg(PLAYER_ARGUMENT, arguments.player())
                    .optionalArg(PAGE_ARGUMENT, ArgumentTypes.integer(1, Integer.MAX_VALUE))
                    .executes(this::playerInfo))
            .child(
                CommandNode.literal("setgroup")
                    .permission(PermsPermissions.PLAYER_SET_GROUP)
                    .arg(PLAYER_ARGUMENT, arguments.player())
                    .optionalArg(GROUP_ARGUMENT, arguments.groupId())
                    .executes(this::playerSetGroup))
            .child(
                CommandNode.literal("addgroup")
                    .permission(PermsPermissions.PLAYER_ADD_GROUP)
                    .arg(PLAYER_ARGUMENT, arguments.player())
                    .arg(GROUP_ARGUMENT, arguments.groupId())
                    .optionalArg(EXPIRY_ARGUMENT, arguments.expiry())
                    .executes(this::playerAddGroup))
            .child(
                CommandNode.literal("rmgroup")
                    .permission(PermsPermissions.PLAYER_REMOVE_GROUP)
                    .arg(PLAYER_ARGUMENT, arguments.player())
                    .arg(GROUP_ARGUMENT, arguments.groupId())
                    .executes(this::playerRemoveGroup))
            .child(
                CommandNode.literal("node")
                    .permission(PermsPermissions.PLAYER_NODE)
                    .child(
                        CommandNode.literal("set")
                            .arg(PLAYER_ARGUMENT, arguments.player())
                            .arg(NODE_ARGUMENT, arguments.node())
                            .optionalArg(EXPIRY_ARGUMENT, arguments.expiry())
                            .executes(this::playerNodeSet))
                    .child(
                        CommandNode.literal("remove")
                            .arg(PLAYER_ARGUMENT, arguments.player())
                            .arg(NODE_ARGUMENT, arguments.node())
                            .executes(this::playerNodeRemove)))
            .child(
                CommandNode.literal("meta")
                    .permission(PermsPermissions.PLAYER_META)
                    .child(
                        CommandNode.literal("set")
                            .arg(PLAYER_ARGUMENT, arguments.player())
                            .arg(KEY_ARGUMENT, ArgumentTypes.word())
                            .arg(VALUE_ARGUMENT, ArgumentTypes.text())
                            .executes(this::playerMetaSet))
                    .child(
                        CommandNode.literal("remove")
                            .arg(PLAYER_ARGUMENT, arguments.player())
                            .arg(KEY_ARGUMENT, ArgumentTypes.word())
                            .executes(this::playerMetaRemove)))
            .child(
                CommandNode.literal("cleanup")
                    .permission(PermsPermissions.PLAYER_CLEANUP)
                    .arg(PLAYER_ARGUMENT, arguments.player())
                    .executes(this::playerCleanup));
    }

    private CommandNode trackBranch() {
        return CommandNode.literal("track")
            .usage(PermsMessages.USAGE_TRACK)
            .child(
                CommandNode.literal("list")
                    .permission(PermsPermissions.TRACK_LIST)
                    .optionalArg(PAGE_ARGUMENT, ArgumentTypes.integer(1, Integer.MAX_VALUE))
                    .executes(this::trackList))
            .child(
                CommandNode.literal("info")
                    .permission(PermsPermissions.TRACK_INFO)
                    .arg(TRACK_ARGUMENT, arguments.trackName())
                    .executes(this::trackInfo))
            .child(
                CommandNode.literal("promote")
                    .permission(PermsPermissions.TRACK_PROMOTE)
                    .arg(PLAYER_ARGUMENT, arguments.player())
                    .optionalArg(TRACK_ARGUMENT, arguments.trackName())
                    .executes(this::trackPromote))
            .child(
                CommandNode.literal("demote")
                    .permission(PermsPermissions.TRACK_DEMOTE)
                    .arg(PLAYER_ARGUMENT, arguments.player())
                    .optionalArg(TRACK_ARGUMENT, arguments.trackName())
                    .executes(this::trackDemote));
    }

    private void me(CommandContext context) {
        UUID self = subjects.subjectOf(context)
            .orElse(null);
        if (self == null) {
            context.replyError(CommandMessages.PLAYERS_ONLY);
            return;
        }
        Snapshot snapshot = writer.snapshot();
        context.reply(PermsMessages.ME_HEADER, nameOf(snapshot, self));
        UserRecord user = snapshot.user(self)
            .orElse(null);
        if (user == null) {
            context.reply(PermsMessages.ME_NO_GROUPS);
            return;
        }
        boolean held = false;
        for (UserRecord.Grant grant : user.groups()) {
            if (grant.expiredAt(now())) {
                continue;
            }
            held = true;
            if (grant.permanent()) {
                context.reply(PermsMessages.ME_GROUP_PERMANENT, grant.groupId());
            } else {
                context.reply(PermsMessages.ME_GROUP_TEMPORARY, grant.groupId(), Durations.format(remaining(grant)));
            }
        }
        if (!held) {
            context.reply(PermsMessages.ME_NO_GROUPS);
        }
        if (user.meta()
            .isEmpty()) {
            context.reply(PermsMessages.ME_NO_META);
            return;
        }
        for (Map.Entry<String, String> entry : user.meta()
            .entrySet()) {
            context.reply(PermsMessages.ME_META, entry.getKey(), entry.getValue());
        }
    }

    private void groupList(CommandContext context) {
        Snapshot snapshot = writer.snapshot();
        if (snapshot.groups()
            .isEmpty()) {
            context.reply(PermsMessages.GROUP_EMPTY);
            return;
        }
        presents.groupList(context, snapshot, viewer(context), context.getOrDefault(PAGE_ARGUMENT, 1), rootName());
    }

    private void groupInfo(CommandContext context) {
        String groupId = context.get(GROUP_ARGUMENT);
        Snapshot snapshot = writer.snapshot();
        GroupRecord group = snapshot.group(groupId)
            .orElse(null);
        if (group == null) {
            context.replyError(PermsMessages.FAILURE_NOT_FOUND, groupId);
            return;
        }
        presents
            .groupInfo(context, snapshot, group, viewer(context), context.getOrDefault(PAGE_ARGUMENT, 1), rootName());
    }

    private void groupCreate(CommandContext context) {
        String groupId = context.get(GROUP_ARGUMENT);
        int weight = context.getOrDefault(WEIGHT_ARGUMENT, 0);
        String displayName = context.getOrDefault(NAME_ARGUMENT, "");
        OperationResult result = admin.createGroup(groupId, displayName, weight, ChangeCause.COMMAND, author(context));
        reply(context, result, PermsMessages.GROUP_CREATED, groupId);
    }

    private void groupDelete(CommandContext context) {
        String groupId = context.get(GROUP_ARGUMENT);
        OperationResult result = admin.deleteGroup(groupId, ChangeCause.COMMAND, author(context));
        reply(context, result, PermsMessages.GROUP_DELETED, groupId);
    }

    private void groupRename(CommandContext context) {
        String groupId = context.get(GROUP_ARGUMENT);
        String target = context.get(TARGET_ARGUMENT);
        OperationResult result = admin.renameGroup(groupId, target, ChangeCause.COMMAND, author(context));
        reply(context, result, PermsMessages.GROUP_RENAMED, groupId, target);
    }

    private void groupCopy(CommandContext context) {
        String groupId = context.get(GROUP_ARGUMENT);
        String target = context.get(TARGET_ARGUMENT);
        OperationResult result = admin.copyGroup(groupId, target, ChangeCause.COMMAND, author(context));
        reply(context, result, PermsMessages.GROUP_COPIED, groupId, target);
    }

    private void groupWeight(CommandContext context) {
        String groupId = context.get(GROUP_ARGUMENT);
        int weight = context.get(WEIGHT_ARGUMENT);
        OperationResult result = admin.setWeight(groupId, weight, ChangeCause.COMMAND, author(context));
        reply(context, result, PermsMessages.GROUP_WEIGHT, groupId, weight);
    }

    private void parentAdd(CommandContext context) {
        String groupId = context.get(GROUP_ARGUMENT);
        String parent = context.get(PARENT_ARGUMENT);
        OperationResult result = admin.addParent(groupId, parent, ChangeCause.COMMAND, author(context));
        reply(context, result, PermsMessages.GROUP_PARENT_ADDED, groupId, parent);
    }

    private void parentRemove(CommandContext context) {
        String groupId = context.get(GROUP_ARGUMENT);
        String parent = context.get(PARENT_ARGUMENT);
        OperationResult result = admin.removeParent(groupId, parent, ChangeCause.COMMAND, author(context));
        reply(context, result, PermsMessages.GROUP_PARENT_REMOVED, groupId, parent);
    }

    private void groupNodeSet(CommandContext context) {
        String groupId = context.get(GROUP_ARGUMENT);
        NodeEntry entry = nodeEntry(context, NODE_ARGUMENT);
        if (entry == null) {
            return;
        }
        OperationResult result = admin.setGroupNode(groupId, entry, ChangeCause.COMMAND, author(context));
        reply(context, result, PermsMessages.GROUP_NODE_SET, groupId, entry.toShortString());
    }

    private void groupNodeRemove(CommandContext context) {
        String groupId = context.get(GROUP_ARGUMENT);
        String node = nodeName(context, NODE_ARGUMENT);
        if (node == null) {
            return;
        }
        OperationResult result = admin.removeGroupNode(groupId, node, ChangeCause.COMMAND, author(context));
        reply(context, result, PermsMessages.GROUP_NODE_REMOVED, groupId, node);
    }

    private void groupMetaSet(CommandContext context) {
        String groupId = context.get(GROUP_ARGUMENT);
        String key = context.get(KEY_ARGUMENT);
        String value = context.get(VALUE_ARGUMENT);
        OperationResult result = admin.setGroupMeta(groupId, key, value, ChangeCause.COMMAND, author(context));
        reply(context, result, PermsMessages.GROUP_META_SET, groupId, key, value);
    }

    private void groupMetaRemove(CommandContext context) {
        String groupId = context.get(GROUP_ARGUMENT);
        String key = context.get(KEY_ARGUMENT);
        OperationResult result = admin.removeGroupMeta(groupId, key, ChangeCause.COMMAND, author(context));
        reply(context, result, PermsMessages.GROUP_META_REMOVED, groupId, key);
    }

    private void playerInfo(CommandContext context) {
        UUID player = context.get(PLAYER_ARGUMENT);
        Snapshot snapshot = writer.snapshot();
        presents.playerInfo(
            context,
            snapshot,
            player,
            nameOf(snapshot, player),
            viewer(context),
            context.getOrDefault(PAGE_ARGUMENT, 1),
            rootName());
    }

    private void playerSetGroup(CommandContext context) {
        UUID player = context.get(PLAYER_ARGUMENT);
        OperationResult result = admin
            .setPrimaryGroup(player, context.getOrDefault(GROUP_ARGUMENT, null), ChangeCause.COMMAND, author(context));
        if (result.successful() && !context.has(GROUP_ARGUMENT)) {
            context.reply(PermsMessages.PLAYER_GROUP_CLEARED, nameOf(writer.snapshot(), player));
            return;
        }
        reply(
            context,
            result,
            PermsMessages.PLAYER_GROUP_SET,
            nameOf(writer.snapshot(), player),
            context.getOrDefault(GROUP_ARGUMENT, EMPTY_MARKER));
    }

    private void playerAddGroup(CommandContext context) {
        UUID player = context.get(PLAYER_ARGUMENT);
        String groupId = context.get(GROUP_ARGUMENT);
        long expiresAt = context.getOrDefault(EXPIRY_ARGUMENT, 0L);
        OperationResult result = admin.addPlayerGroup(player, groupId, expiresAt, ChangeCause.COMMAND, author(context));
        if (result.successful() && expiresAt > 0) {
            context.reply(
                PermsMessages.PLAYER_GROUP_TEMPORARY,
                nameOf(writer.snapshot(), player),
                groupId,
                Durations.format((int) Math.min(Integer.MAX_VALUE, remaining(expiresAt))));
            return;
        }
        reply(context, result, PermsMessages.PLAYER_GROUP_ADDED, nameOf(writer.snapshot(), player), groupId);
    }

    private void playerRemoveGroup(CommandContext context) {
        UUID player = context.get(PLAYER_ARGUMENT);
        String groupId = context.get(GROUP_ARGUMENT);
        OperationResult result = admin.removePlayerGroup(player, groupId, ChangeCause.COMMAND, author(context));
        reply(context, result, PermsMessages.PLAYER_GROUP_REMOVED, nameOf(writer.snapshot(), player), groupId);
    }

    private void playerNodeSet(CommandContext context) {
        UUID player = context.get(PLAYER_ARGUMENT);
        NodeEntry entry = nodeEntry(context, NODE_ARGUMENT);
        if (entry == null) {
            return;
        }
        OperationResult result = admin.setPlayerNode(player, entry, ChangeCause.COMMAND, author(context));
        reply(context, result, PermsMessages.PLAYER_NODE_SET, nameOf(writer.snapshot(), player), entry.toShortString());
    }

    private void playerNodeRemove(CommandContext context) {
        UUID player = context.get(PLAYER_ARGUMENT);
        String node = nodeName(context, NODE_ARGUMENT);
        if (node == null) {
            return;
        }
        OperationResult result = admin.removePlayerNode(player, node, ChangeCause.COMMAND, author(context));
        reply(context, result, PermsMessages.PLAYER_NODE_REMOVED, nameOf(writer.snapshot(), player), node);
    }

    private void playerMetaSet(CommandContext context) {
        UUID player = context.get(PLAYER_ARGUMENT);
        String key = context.get(KEY_ARGUMENT);
        String value = context.get(VALUE_ARGUMENT);
        OperationResult result = admin.setPlayerMeta(player, key, value, ChangeCause.COMMAND, author(context));
        reply(context, result, PermsMessages.PLAYER_META_SET, nameOf(writer.snapshot(), player), key, value);
    }

    private void playerMetaRemove(CommandContext context) {
        UUID player = context.get(PLAYER_ARGUMENT);
        String key = context.get(KEY_ARGUMENT);
        OperationResult result = admin.removePlayerMeta(player, key, ChangeCause.COMMAND, author(context));
        reply(context, result, PermsMessages.PLAYER_META_REMOVED, nameOf(writer.snapshot(), player), key);
    }

    private void playerCleanup(CommandContext context) {
        UUID player = context.get(PLAYER_ARGUMENT);
        Snapshot before = writer.snapshot();
        OperationResult result = admin.cleanupPlayer(player, ChangeCause.COMMAND, author(context));
        if (!result.successful()) {
            replyFailure(context, result, nameOf(before, player));
            return;
        }
        Snapshot after = writer.snapshot();
        int removed = held(before, player) - held(after, player);
        reply(context, result, PermsMessages.PLAYER_CLEANED, nameOf(after, player), removed);
    }

    private void trackList(CommandContext context) {
        Snapshot snapshot = writer.snapshot();
        if (snapshot.tracks()
            .isEmpty()) {
            context.reply(PermsMessages.TRACK_EMPTY);
            return;
        }
        presents.trackList(context, snapshot, viewer(context), context.getOrDefault(PAGE_ARGUMENT, 1), rootName());
    }

    private void trackInfo(CommandContext context) {
        String name = context.get(TRACK_ARGUMENT);
        TrackRecord track = writer.snapshot()
            .track(name)
            .orElse(null);
        if (track == null) {
            context.replyError(PermsMessages.TRACK_UNKNOWN, name);
            return;
        }
        presents.trackInfo(context, track, viewer(context), rootName());
    }

    private void trackPromote(CommandContext context) {
        move(context, true);
    }

    private void trackDemote(CommandContext context) {
        move(context, false);
    }

    private void move(CommandContext context, boolean up) {
        UUID player = context.get(PLAYER_ARGUMENT);
        Snapshot snapshot = writer.snapshot();
        TrackRecord track = trackOf(context, snapshot);
        if (track == null) {
            return;
        }
        String playerName = nameOf(snapshot, player);
        String current = currentTrackGroup(snapshot, player, track, now());
        if (current == null) {
            context.replyError(PermsMessages.TRACK_NOT_MEMBER, playerName, track.name());
            return;
        }
        Optional<String> target = up ? track.next(current) : track.previous(current);
        if (!target.isPresent()) {
            context.replyError(up ? PermsMessages.TRACK_TOP : PermsMessages.TRACK_BOTTOM, playerName, track.name());
            return;
        }
        if (!snapshot.group(target.get())
            .isPresent()) {
            context.replyError(PermsMessages.FAILURE_NOT_FOUND, target.get());
            return;
        }
        OperationResult moved = admin
            .movePlayerGroup(player, current, target.get(), ChangeCause.COMMAND, author(context));
        reply(
            context,
            moved,
            up ? PermsMessages.TRACK_PROMOTED : PermsMessages.TRACK_DEMOTED,
            playerName,
            target.get());
    }

    private void importFromCore(CommandContext context) {
        boolean dryRun = false;
        boolean force = false;
        String tail = context.getOrDefault(FLAGS_ARGUMENT, "");
        for (String flag : tail.split(" ")) {
            if (flag.isEmpty()) {
                continue;
            }
            if (DRY_RUN_FLAG.equals(flag)) {
                dryRun = true;
            } else if (FORCE_FLAG.equals(flag)) {
                force = true;
            } else {
                context.replyError(PermsMessages.FAILURE_INVALID_VALUE, flag);
                return;
            }
        }
        PermsMaintenance.Outcome outcome = maintenance.importFromCore(dryRun, force);
        OperationResult result = outcome.result;
        if (!result.successful() && result.failure()
            .orElse(null) == OperationResult.Failure.UNREADABLE_SOURCE) {
            context.replyError(
                PermsMessages.IMPORT_UNREADABLE,
                result.message()
                    .orElse(""));
            return;
        }
        if (!result.successful() && !force
            && result.failure()
                .orElse(null) == OperationResult.Failure.INVALID_VALUE) {
            context.replyError(PermsMessages.IMPORT_CHANGED);
            return;
        }
        if (!result.successful()) {
            context.replyError(
                failureKey(result),
                result.message()
                    .orElse(""));
            return;
        }
        if (outcome.counts.groups() == 0 && outcome.counts.players() == 0) {
            context.reply(PermsMessages.IMPORT_NOTHING);
            return;
        }
        context.reply(
            dryRun ? PermsMessages.IMPORT_DRY : PermsMessages.IMPORT_DONE,
            outcome.counts.groups(),
            outcome.counts.players(),
            outcome.counts.nodes(),
            outcome.counts.meta());
    }

    private void export(CommandContext context) {
        PermsMaintenance.Outcome outcome = maintenance.exportToCoreFormat();
        if (!outcome.result.successful()) {
            context.replyError(
                failureKey(outcome.result),
                outcome.result.message()
                    .orElse(""));
            return;
        }
        context.reply(
            PermsMessages.EXPORT_DONE,
            outcome.counts.groups(),
            outcome.counts.players(),
            outcome.counts.nodes(),
            outcome.counts.meta());
    }

    private void reload(CommandContext context) {
        OperationResult result = maintenance.reload();
        if (!result.successful()) {
            context.replyError(
                failureKey(result),
                result.message()
                    .orElse(""));
            return;
        }
        Snapshot snapshot = writer.snapshot();
        context.reply(
            PermsMessages.RELOAD_DONE,
            snapshot.revision(),
            snapshot.groups()
                .size(),
            snapshot.tracks()
                .size(),
            snapshot.users()
                .size());
    }

    private void debugView(CommandContext context) {
        UUID player = context.get(PLAYER_ARGUMENT);
        String node = context.get(NODE_ARGUMENT);
        Snapshot snapshot = writer.snapshot();
        debug.show(context, snapshot, player, node, subjects.contexts(player), nameOf(snapshot, player));
    }

    private TrackRecord trackOf(CommandContext context, Snapshot snapshot) {
        if (context.has(TRACK_ARGUMENT)) {
            String name = context.get(TRACK_ARGUMENT);
            TrackRecord track = snapshot.track(name)
                .orElse(null);
            if (track == null) {
                context.replyError(PermsMessages.TRACK_UNKNOWN, name);
                return null;
            }
            return track;
        }
        if (snapshot.tracks()
            .size() == 1) {
            return snapshot.tracks()
                .values()
                .iterator()
                .next();
        }
        context.replyError(CommandMessages.MISSING_ARGUMENT, TRACK_ARGUMENT);
        return null;
    }

    /** Группа игрока на треке: основная, если стоит на нём, иначе старшая из действующих. */
    public static String currentTrackGroup(Snapshot snapshot, UUID player, TrackRecord track, long nowMillis) {
        UserRecord user = snapshot.user(player)
            .orElse(null);
        if (user == null) {
            return null;
        }
        if (user.primary() != null && track.position(user.primary()) >= 0 && user.memberOf(user.primary(), nowMillis)) {
            return user.primary();
        }
        String best = null;
        int bestWeight = 0;
        for (String groupId : user.activeGroupIds(nowMillis)) {
            GroupRecord group = snapshot.group(groupId)
                .orElse(null);
            if (group == null || track.position(groupId) < 0) {
                continue;
            }
            if (best == null || group.weight() > bestWeight) {
                best = groupId;
                bestWeight = group.weight();
            }
        }
        return best;
    }

    private NodeEntry nodeEntry(CommandContext context, String argument) {
        NodeEntry parsed = parsedNode(context, argument);
        if (parsed == null) {
            return null;
        }
        long expiresAt = context.getOrDefault(EXPIRY_ARGUMENT, 0L);
        if (expiresAt > 0) {
            return NodeEntry.of(parsed.node(), parsed.value(), parsed.contexts(), expiresAt);
        }
        return parsed;
    }

    private String nodeName(CommandContext context, String argument) {
        NodeEntry parsed = parsedNode(context, argument);
        return parsed == null ? null : parsed.node();
    }

    private NodeEntry parsedNode(CommandContext context, String argument) {
        String raw = context.get(argument);
        try {
            return NodeEntry.parse(raw, limits.get());
        } catch (IllegalArgumentException invalid) {
            context.replyError(PermsMessages.FAILURE_INVALID_VALUE, raw);
            return null;
        }
    }

    private static void reply(CommandContext context, OperationResult result, String successKey, Object... arguments) {
        if (result.successful()) {
            context.reply(successKey, arguments);
            return;
        }
        replyFailure(context, result, arguments.length > 0 ? arguments[0] : "");
    }

    private static void replyFailure(CommandContext context, OperationResult result, Object subject) {
        if (subject != null && !String.valueOf(subject)
            .isEmpty()) {
            context.replyError(failureKey(result), subject);
            return;
        }
        context.replyError(
            failureKey(result),
            result.message()
                .orElse(""));
    }

    private static String failureKey(OperationResult result) {
        OperationResult.Failure failure = result.failure()
            .orElse(OperationResult.Failure.UNSUPPORTED);
        switch (failure) {
            case NOT_FOUND:
                return PermsMessages.FAILURE_NOT_FOUND;
            case ALREADY_EXISTS:
                return PermsMessages.FAILURE_ALREADY_EXISTS;
            case LIMIT_REACHED:
                return PermsMessages.FAILURE_LIMIT_REACHED;
            case INVALID_VALUE:
                return PermsMessages.FAILURE_INVALID_VALUE;
            case INHERITANCE_CYCLE:
                return PermsMessages.FAILURE_INHERITANCE_CYCLE;
            case IN_USE:
                return PermsMessages.FAILURE_IN_USE;
            case TIMEOUT:
                return PermsMessages.FAILURE_TIMEOUT;
            case STALE_SNAPSHOT:
                return PermsMessages.FAILURE_STALE;
            case UNREADABLE_SOURCE:
                return PermsMessages.IMPORT_UNREADABLE;
            case PROVIDER_FAILED:
                return PermsMessages.FAILURE_PROVIDER;
            default:
                return PermsMessages.FAILURE_UNSUPPORTED;
        }
    }

    private UUID viewer(CommandContext context) {
        return subjects.subjectOf(context)
            .orElse(null);
    }

    private String nameOf(Snapshot snapshot, UUID player) {
        Optional<String> known = subjects.playerName(player);
        if (known.isPresent()) {
            return known.get();
        }
        UserRecord user = snapshot.user(player)
            .orElse(null);
        if (user != null && user.name() != null) {
            return user.name();
        }
        return player.toString();
    }

    private static int held(Snapshot snapshot, UUID player) {
        UserRecord user = snapshot.user(player)
            .orElse(null);
        if (user == null) {
            return 0;
        }
        return user.groups()
            .size()
            + user.nodes()
                .size();
    }

    private static int remaining(UserRecord.Grant grant) {
        return remaining(grant.expiresAt());
    }

    private static int remaining(long expiresAt) {
        long seconds = (expiresAt - System.currentTimeMillis()) / 1000L;
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, seconds));
    }

    private String author(CommandContext context) {
        return subjects.senderName(context);
    }

    private static long now() {
        return System.currentTimeMillis();
    }
}
