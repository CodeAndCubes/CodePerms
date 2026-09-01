package com.mrleonardos.codeperms.internal.admin;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

import com.mrleonardos.codeperms.api.PermsLimits;
import com.mrleonardos.codeperms.api.manage.ChangeEvent;
import com.mrleonardos.codeperms.api.manage.PermsAdmin;
import com.mrleonardos.codeperms.api.model.ChangeCause;
import com.mrleonardos.codeperms.api.model.ContextSet;
import com.mrleonardos.codeperms.api.model.GroupRecord;
import com.mrleonardos.codeperms.api.model.NodeEntry;
import com.mrleonardos.codeperms.api.model.Snapshot;
import com.mrleonardos.codeperms.api.model.TrackRecord;
import com.mrleonardos.codeperms.api.model.UserRecord;
import com.mrleonardos.codeperms.api.resolve.Resolution;
import com.mrleonardos.codeperms.api.store.OperationResult;
import com.mrleonardos.codeperms.internal.engine.ResolverImpl;
import com.mrleonardos.codeperms.internal.store.SingleWriter;

public final class PermsAdminImpl implements PermsAdmin {

    private final SingleWriter writer;
    private final ChangeCoalescer coalescer;
    private final PermsLimits limits;
    private final ResolverImpl resolver;
    private final LongSupplier clock;

    public PermsAdminImpl(SingleWriter writer, ChangeCoalescer coalescer, PermsLimits limits) {
        this(writer, coalescer, limits, new ResolverImpl(System::currentTimeMillis));
    }

    public PermsAdminImpl(SingleWriter writer, ChangeCoalescer coalescer, PermsLimits limits, LongSupplier clock) {
        this(writer, coalescer, limits, new ResolverImpl(clock), clock);
    }

    public PermsAdminImpl(SingleWriter writer, ChangeCoalescer coalescer, PermsLimits limits, ResolverImpl resolver) {
        this(writer, coalescer, limits, resolver, System::currentTimeMillis);
    }

    public PermsAdminImpl(SingleWriter writer, ChangeCoalescer coalescer, PermsLimits limits, ResolverImpl resolver,
        LongSupplier clock) {
        this.writer = Objects.requireNonNull(writer, "writer");
        this.coalescer = Objects.requireNonNull(coalescer, "coalescer");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public OperationResult createGroup(String id, String displayName, int weight, ChangeCause cause, String author) {
        String groupId = normalizeId(id);
        if (groupId == null) {
            return invalid("Group id must not be empty");
        }
        if (!limits.acceptsGroupId(groupId)) {
            return tooLong("Group id is longer than " + limits.groupIdLength());
        }
        PendingChange pending = new PendingChange(writer.snapshot(), cause, author);
        if (pending.current()
            .group(groupId)
            .isPresent()) {
            return pending.reject(OperationResult.Failure.ALREADY_EXISTS, "Group " + groupId + " already exists");
        }
        if (pending.current()
            .groups()
            .size() >= limits.groups()) {
            return pending
                .reject(OperationResult.Failure.LIMIT_REACHED, "No room for more than " + limits.groups() + " groups");
        }
        pending.putGroup(
            GroupRecord.of(
                groupId,
                displayName,
                weight,
                Collections.<String>emptyList(),
                Collections.<NodeEntry>emptyList(),
                Collections.<String, String>emptyMap()));
        return commit(pending, event(ChangeEvent.Kind.GROUPS, cause, subject(groupId)));
    }

    @Override
    public OperationResult deleteGroup(String id, ChangeCause cause, String author) {
        String groupId = normalizeId(id);
        PendingChange pending = new PendingChange(writer.snapshot(), cause, author);
        GroupRecord source = groupOf(pending.current(), groupId);
        if (source == null) {
            return pending.reject(OperationResult.Failure.NOT_FOUND, "Group " + groupId + " is missing");
        }
        String field = settingNaming(pending.current(), source.id());
        if (field != null) {
            return inUse(pending, source.id(), field);
        }
        pending.removeGroup(source.id());
        return commit(pending, relink(pending, source.id(), null, cause));
    }

    @Override
    public OperationResult renameGroup(String id, String newId, ChangeCause cause, String author) {
        String groupId = normalizeId(id);
        String renamed = normalizeId(newId);
        if (renamed == null) {
            return invalid("Group id must not be empty");
        }
        if (!limits.acceptsGroupId(renamed)) {
            return tooLong("Group id is longer than " + limits.groupIdLength());
        }
        PendingChange pending = new PendingChange(writer.snapshot(), cause, author);
        GroupRecord source = groupOf(pending.current(), groupId);
        if (source == null) {
            return pending.reject(OperationResult.Failure.NOT_FOUND, "Group " + groupId + " is missing");
        }
        if (pending.current()
            .group(renamed)
            .isPresent()) {
            return pending.reject(OperationResult.Failure.ALREADY_EXISTS, "Group " + renamed + " already exists");
        }
        String field = settingNaming(pending.current(), source.id());
        if (field != null) {
            return inUse(pending, source.id(), field);
        }
        pending.putGroup(
            GroupRecord
                .of(renamed, source.displayName(), source.weight(), source.inherits(), source.nodes(), source.meta()));
        pending.removeGroup(source.id());
        return commit(pending, relink(pending, source.id(), renamed, cause));
    }

    @Override
    public OperationResult copyGroup(String source, String target, ChangeCause cause, String author) {
        String sourceId = normalizeId(source);
        String targetId = normalizeId(target);
        if (targetId == null) {
            return invalid("Group id must not be empty");
        }
        if (!limits.acceptsGroupId(targetId)) {
            return tooLong("Group id is longer than " + limits.groupIdLength());
        }
        PendingChange pending = new PendingChange(writer.snapshot(), cause, author);
        GroupRecord origin = groupOf(pending.current(), sourceId);
        if (origin == null) {
            return pending.reject(OperationResult.Failure.NOT_FOUND, "Group " + sourceId + " is missing");
        }
        if (pending.current()
            .group(targetId)
            .isPresent()) {
            return pending.reject(OperationResult.Failure.ALREADY_EXISTS, "Group " + targetId + " already exists");
        }
        if (pending.current()
            .groups()
            .size() >= limits.groups()) {
            return pending
                .reject(OperationResult.Failure.LIMIT_REACHED, "No room for more than " + limits.groups() + " groups");
        }
        pending.putGroup(
            GroupRecord
                .of(targetId, origin.displayName(), origin.weight(), origin.inherits(), origin.nodes(), origin.meta()));
        return commit(pending, event(ChangeEvent.Kind.GROUPS, cause, subject(targetId)));
    }

    @Override
    public OperationResult setWeight(String groupId, int weight, ChangeCause cause, String author) {
        PendingChange pending = new PendingChange(writer.snapshot(), cause, author);
        GroupRecord group = groupOf(pending.current(), groupId);
        if (group == null) {
            return pending.reject(OperationResult.Failure.NOT_FOUND, "Group " + groupId + " is missing");
        }
        pending.putGroup(withWeight(group, weight));
        return commit(pending, event(ChangeEvent.Kind.GROUPS, cause, subject(group.id())));
    }

    @Override
    public OperationResult addParent(String groupId, String parentId, ChangeCause cause, String author) {
        PendingChange pending = new PendingChange(writer.snapshot(), cause, author);
        GroupRecord group = groupOf(pending.current(), groupId);
        if (group == null) {
            return pending.reject(OperationResult.Failure.NOT_FOUND, "Group " + groupId + " is missing");
        }
        GroupRecord parent = groupOf(pending.current(), parentId);
        if (parent == null) {
            return pending.reject(OperationResult.Failure.NOT_FOUND, "Group " + parentId + " is missing");
        }
        if (group.inherits()
            .contains(parent.id())) {
            return pending.reject(
                OperationResult.Failure.INVALID_VALUE,
                "Group " + group.id() + " already inherits " + parent.id());
        }
        if (createsCycle(pending.current(), group.id(), parent.id())) {
            return pending.reject(
                OperationResult.Failure.INHERITANCE_CYCLE,
                "Group " + parent.id() + " already leads to " + group.id());
        }
        List<String> inherits = new ArrayList<>(group.inherits());
        inherits.add(parent.id());
        pending.putGroup(withInherits(group, inherits));
        return commit(pending, event(ChangeEvent.Kind.GROUPS, cause, subject(group.id())));
    }

    @Override
    public OperationResult removeParent(String groupId, String parentId, ChangeCause cause, String author) {
        PendingChange pending = new PendingChange(writer.snapshot(), cause, author);
        GroupRecord group = groupOf(pending.current(), groupId);
        if (group == null) {
            return pending.reject(OperationResult.Failure.NOT_FOUND, "Group " + groupId + " is missing");
        }
        String parent = normalizeId(parentId);
        if (!group.inherits()
            .contains(parent)) {
            return pending
                .reject(OperationResult.Failure.NOT_FOUND, "Group " + group.id() + " has no parent " + parent);
        }
        List<String> inherits = new ArrayList<>(group.inherits());
        inherits.remove(parent);
        pending.putGroup(withInherits(group, inherits));
        return commit(pending, event(ChangeEvent.Kind.GROUPS, cause, subject(group.id())));
    }

    @Override
    public OperationResult setGroupNode(String groupId, NodeEntry entry, ChangeCause cause, String author) {
        Objects.requireNonNull(entry, "entry");
        PendingChange pending = new PendingChange(writer.snapshot(), cause, author);
        GroupRecord group = groupOf(pending.current(), groupId);
        if (group == null) {
            return pending.reject(OperationResult.Failure.NOT_FOUND, "Group " + groupId + " is missing");
        }
        if (!limits.acceptsNode(entry.node())) {
            return tooLong("Node " + entry.node() + " does not fit the ceilings");
        }
        List<NodeEntry> updated = withNode(group.nodes(), entry, limits.nodesPerSubject());
        if (updated == null) {
            return pending.reject(
                OperationResult.Failure.LIMIT_REACHED,
                "Group " + group.id() + " holds already " + limits.nodesPerSubject() + " nodes");
        }
        pending.putGroup(withNodes(group, updated));
        return commit(pending, event(ChangeEvent.Kind.NODES, cause, subject(group.id())));
    }

    @Override
    public OperationResult removeGroupNode(String groupId, String node, ChangeCause cause, String author) {
        PendingChange pending = new PendingChange(writer.snapshot(), cause, author);
        GroupRecord group = groupOf(pending.current(), groupId);
        if (group == null) {
            return pending.reject(OperationResult.Failure.NOT_FOUND, "Group " + groupId + " is missing");
        }
        List<NodeEntry> updated = withoutNode(group.nodes(), node);
        if (updated.size() == group.nodes()
            .size()) {
            return pending.reject(OperationResult.Failure.NOT_FOUND, "Group " + group.id() + " holds no node " + node);
        }
        pending.putGroup(withNodes(group, updated));
        return commit(pending, event(ChangeEvent.Kind.NODES, cause, subject(group.id())));
    }

    @Override
    public OperationResult setGroupMeta(String groupId, String key, String value, ChangeCause cause, String author) {
        PendingChange pending = new PendingChange(writer.snapshot(), cause, author);
        GroupRecord group = groupOf(pending.current(), groupId);
        if (group == null) {
            return pending.reject(OperationResult.Failure.NOT_FOUND, "Group " + groupId + " is missing");
        }
        OperationResult checked = checkedMeta(group.meta(), key, value);
        if (!checked.successful()) {
            return checked;
        }
        pending.putGroup(withMeta(group, withValue(group.meta(), key, value)));
        return commit(pending, event(ChangeEvent.Kind.META, cause, subject(group.id())));
    }

    @Override
    public OperationResult removeGroupMeta(String groupId, String key, ChangeCause cause, String author) {
        PendingChange pending = new PendingChange(writer.snapshot(), cause, author);
        GroupRecord group = groupOf(pending.current(), groupId);
        if (group == null) {
            return pending.reject(OperationResult.Failure.NOT_FOUND, "Group " + groupId + " is missing");
        }
        if (!group.meta()
            .containsKey(key)) {
            return pending.reject(OperationResult.Failure.NOT_FOUND, "Group " + group.id() + " holds no meta " + key);
        }
        pending.putGroup(withMeta(group, withoutValue(group.meta(), key)));
        return commit(pending, event(ChangeEvent.Kind.META, cause, subject(group.id())));
    }

    @Override
    public OperationResult setPlayerNode(UUID player, NodeEntry entry, ChangeCause cause, String author) {
        Objects.requireNonNull(entry, "entry");
        PendingChange pending = new PendingChange(writer.snapshot(), cause, author);
        UserRecord user = userOf(pending.current(), player);
        if (!limits.acceptsNode(entry.node())) {
            return tooLong("Node " + entry.node() + " does not fit the ceilings");
        }
        List<NodeEntry> updated = withNode(user.nodes(), entry, limits.nodesPerSubject());
        if (updated == null) {
            return pending.reject(
                OperationResult.Failure.LIMIT_REACHED,
                "Player " + player + " holds already " + limits.nodesPerSubject() + " nodes");
        }
        pending.putPlayer(withNodes(user, updated));
        return commit(pending, event(ChangeEvent.Kind.NODES, cause, playerSubject(player)));
    }

    @Override
    public OperationResult removePlayerNode(UUID player, String node, ChangeCause cause, String author) {
        PendingChange pending = new PendingChange(writer.snapshot(), cause, author);
        UserRecord user = userOf(pending.current(), player);
        List<NodeEntry> updated = withoutNode(user.nodes(), node);
        if (updated.size() == user.nodes()
            .size()) {
            return pending.reject(OperationResult.Failure.NOT_FOUND, "Player holds no node " + node);
        }
        pending.putPlayer(withNodes(user, updated));
        return commit(pending, event(ChangeEvent.Kind.NODES, cause, playerSubject(player)));
    }

    @Override
    public OperationResult setPlayerMeta(UUID player, String key, String value, ChangeCause cause, String author) {
        PendingChange pending = new PendingChange(writer.snapshot(), cause, author);
        UserRecord user = userOf(pending.current(), player);
        OperationResult checked = checkedMeta(user.meta(), key, value);
        if (!checked.successful()) {
            return checked;
        }
        pending.putPlayer(withMeta(user, withValue(user.meta(), key, value)));
        return commit(pending, event(ChangeEvent.Kind.META, cause, playerSubject(player)));
    }

    @Override
    public OperationResult removePlayerMeta(UUID player, String key, ChangeCause cause, String author) {
        PendingChange pending = new PendingChange(writer.snapshot(), cause, author);
        UserRecord user = userOf(pending.current(), player);
        if (!user.meta()
            .containsKey(key)) {
            return pending.reject(OperationResult.Failure.NOT_FOUND, "Player holds no meta " + key);
        }
        pending.putPlayer(withMeta(user, withoutValue(user.meta(), key)));
        return commit(pending, event(ChangeEvent.Kind.META, cause, playerSubject(player)));
    }

    @Override
    public OperationResult setPrimaryGroup(UUID player, String groupId, ChangeCause cause, String author) {
        PendingChange pending = new PendingChange(writer.snapshot(), cause, author);
        String primary = null;
        if (groupId != null) {
            primary = normalizeId(groupId);
            if (groupOf(pending.current(), primary) == null) {
                return pending.reject(OperationResult.Failure.NOT_FOUND, "Group " + primary + " is missing");
            }
        }
        UserRecord user = userOf(pending.current(), player);
        pending.putPlayer(withPrimary(user, primary));
        return commit(pending, event(ChangeEvent.Kind.MEMBERSHIP, cause, playerSubject(player)));
    }

    @Override
    public OperationResult addPlayerGroup(UUID player, String groupId, long expiresAt, ChangeCause cause,
        String author) {
        if (expiresAt < 0) {
            return invalid("Expiry must not be negative: " + expiresAt);
        }
        PendingChange pending = new PendingChange(writer.snapshot(), cause, author);
        if (groupOf(pending.current(), groupId) == null) {
            return pending.reject(OperationResult.Failure.NOT_FOUND, "Group " + groupId + " is missing");
        }
        UserRecord user = userOf(pending.current(), player);
        List<UserRecord.Grant> grants = new ArrayList<>();
        boolean replaced = false;
        for (UserRecord.Grant grant : user.groups()) {
            if (grant.groupId()
                .equals(normalizeId(groupId))) {
                grants.add(UserRecord.Grant.of(grant.groupId(), expiresAt));
                replaced = true;
            } else {
                grants.add(grant);
            }
        }
        if (!replaced) {
            grants.add(UserRecord.Grant.of(groupId, expiresAt));
        }
        pending.putPlayer(withGroups(user, grants));
        return commit(pending, event(ChangeEvent.Kind.MEMBERSHIP, cause, playerSubject(player)));
    }

    @Override
    public OperationResult movePlayerGroup(UUID player, String fromGroupId, String toGroupId, ChangeCause cause,
        String author) {
        String from = normalizeId(fromGroupId);
        String to = normalizeId(toGroupId);
        if (from == null || to == null) {
            return invalid("Group id must not be empty");
        }
        PendingChange pending = new PendingChange(writer.snapshot(), cause, author);
        if (groupOf(pending.current(), to) == null) {
            return pending.reject(OperationResult.Failure.NOT_FOUND, "Group " + to + " is missing");
        }
        UserRecord user = userOf(pending.current(), player);
        List<UserRecord.Grant> grants = new ArrayList<>();
        Set<String> kept = new HashSet<>();
        boolean moved = false;
        for (UserRecord.Grant grant : user.groups()) {
            String target = grant.groupId()
                .equals(from) ? to : grant.groupId();
            moved |= grant.groupId()
                .equals(from);
            if (kept.add(target)) {
                grants.add(UserRecord.Grant.of(target, grant.expiresAt()));
            }
        }
        if (!moved) {
            return pending.reject(OperationResult.Failure.NOT_FOUND, "Player holds no group " + from);
        }
        String primary = from.equals(user.primary()) ? to : user.primary();
        pending.putPlayer(UserRecord.of(user.uuid(), user.name(), primary, grants, user.nodes(), user.meta()));
        return commit(pending, event(ChangeEvent.Kind.MEMBERSHIP, cause, playerSubject(player)));
    }

    @Override
    public OperationResult removePlayerGroup(UUID player, String groupId, ChangeCause cause, String author) {
        String normalized = normalizeId(groupId);
        PendingChange pending = new PendingChange(writer.snapshot(), cause, author);
        UserRecord user = userOf(pending.current(), player);
        List<UserRecord.Grant> grants = new ArrayList<>();
        for (UserRecord.Grant grant : user.groups()) {
            if (!grant.groupId()
                .equals(normalized)) {
                grants.add(grant);
            }
        }
        if (grants.size() == user.groups()
            .size()) {
            return pending.reject(OperationResult.Failure.NOT_FOUND, "Player holds no group " + normalized);
        }
        pending.putPlayer(withGroups(user, grants));
        return commit(pending, event(ChangeEvent.Kind.MEMBERSHIP, cause, playerSubject(player)));
    }

    @Override
    public OperationResult cleanupPlayer(UUID player, ChangeCause cause, String author) {
        PendingChange pending = new PendingChange(writer.snapshot(), cause, author);
        UserRecord user = userOf(pending.current(), player);
        long now = clock.getAsLong();

        List<UserRecord.Grant> grants = new ArrayList<>();
        boolean droppedGrants = false;
        for (UserRecord.Grant grant : user.groups()) {
            if (grant.expiredAt(now)) {
                droppedGrants = true;
                continue;
            }
            grants.add(grant);
        }
        List<NodeEntry> nodes = new ArrayList<>();
        boolean droppedNodes = false;
        for (NodeEntry entry : user.nodes()) {
            if (entry.expiredAt(now)) {
                droppedNodes = true;
                continue;
            }
            nodes.add(entry);
        }
        if (!droppedGrants && !droppedNodes) {
            return OperationResult.success();
        }
        pending.putPlayer(withGroups(withNodes(user, nodes), grants));
        List<ChangeEvent.Subject> subjects = new ArrayList<>();
        subjects.add(playerSubject(player));
        if (droppedGrants) {
            return commit(
                pending,
                ChangeEvent.of(ChangeEvent.Kind.MEMBERSHIP, subjects, cause),
                ChangeEvent.of(ChangeEvent.Kind.NODES, subjects, cause));
        }
        return commit(pending, ChangeEvent.of(ChangeEvent.Kind.NODES, subjects, cause));
    }

    @Override
    public List<String> metaStack(UUID player, String key) {
        return resolver.metaValues(current(), player, key);
    }

    @Override
    public Resolution explain(UUID player, String node, ContextSet contexts) {
        Objects.requireNonNull(contexts, "contexts");
        return resolver.resolve(current(), player, node, contexts);
    }

    private Snapshot current() {
        Snapshot snapshot = writer.snapshot();
        return snapshot == null ? Snapshot.empty() : snapshot;
    }

    private OperationResult commit(PendingChange pending, ChangeEvent... events) {
        if (pending.rejected()) {
            return pending.failure();
        }
        OperationResult result = writer.commit(pending.snapshot(), pending.batch());
        if (result.successful()) {
            for (ChangeEvent event : events) {
                coalescer.record(event);
            }
        }
        return result;
    }

    private static String settingNaming(Snapshot snapshot, String groupId) {
        if (groupId.equals(snapshot.defaultGroup())) {
            return "defaultGroup";
        }
        if (groupId.equals(snapshot.opGroup())) {
            return "opGroup";
        }
        return null;
    }

    private static OperationResult inUse(PendingChange pending, String groupId, String field) {
        return pending.reject(
            OperationResult.Failure.IN_USE,
            "Group " + groupId + " is named by " + field + " in config.toml, change the setting first");
    }

    private static ChangeEvent[] relink(PendingChange pending, String from, String to, ChangeCause cause) {
        List<ChangeEvent.Subject> groups = new ArrayList<>();
        List<ChangeEvent.Subject> players = new ArrayList<>();
        groups.add(subject(from));
        if (to != null) {
            groups.add(subject(to));
        }
        for (GroupRecord group : pending.current()
            .groups()
            .values()) {
            if (group.id()
                .equals(from)
                || !group.inherits()
                    .contains(from)) {
                continue;
            }
            pending.putGroup(withInherits(group, relinked(group.inherits(), from, to)));
            groups.add(subject(group.id()));
        }
        for (UserRecord user : pending.current()
            .users()
            .values()) {
            UserRecord updated = relinkedGrants(user, from, to);
            if (updated == user) {
                continue;
            }
            pending.putPlayer(updated);
            players.add(ChangeEvent.Subject.player(user.uuid()));
        }
        for (TrackRecord track : pending.current()
            .tracks()
            .values()) {
            if (track.position(from) < 0) {
                continue;
            }
            List<String> ordered = relinked(track.groups(), from, to);
            if (ordered.isEmpty()) {
                pending.removeTrack(track.name());
            } else {
                pending.putTrack(TrackRecord.of(track.name(), ordered));
            }
        }
        List<ChangeEvent> events = new ArrayList<>();
        events.add(ChangeEvent.of(ChangeEvent.Kind.GROUPS, groups, cause));
        if (!players.isEmpty()) {
            events.add(ChangeEvent.of(ChangeEvent.Kind.MEMBERSHIP, players, cause));
        }
        return events.toArray(new ChangeEvent[0]);
    }

    private static List<String> relinked(List<String> groupIds, String from, String to) {
        List<String> updated = new ArrayList<>();
        for (String held : groupIds) {
            String target = held.equals(from) ? to : held;
            if (target != null && !updated.contains(target)) {
                updated.add(target);
            }
        }
        return updated;
    }

    private OperationResult checkedMeta(Map<String, String> held, String key, String value) {
        if (key == null || key.isEmpty()) {
            return invalid("Meta key must not be empty");
        }
        for (int index = 0; index < key.length(); index++) {
            if (Character.isWhitespace(key.charAt(index))) {
                return invalid("Meta key must not hold whitespace: " + key);
            }
        }
        if (!limits.acceptsMetaValue(value)) {
            return tooLong("Meta value is longer than " + limits.metaValueLength());
        }
        if (!held.containsKey(key) && held.size() >= limits.metaKeysPerSubject()) {
            return tooLong("No room for more than " + limits.metaKeysPerSubject() + " meta keys");
        }
        return OperationResult.success();
    }

    private static List<NodeEntry> withNode(List<NodeEntry> nodes, NodeEntry entry, int ceiling) {
        List<NodeEntry> updated = new ArrayList<>();
        boolean replaced = false;
        for (NodeEntry held : nodes) {
            if (sameRule(held, entry)) {
                updated.add(entry);
                replaced = true;
            } else {
                updated.add(held);
            }
        }
        if (!replaced) {
            if (nodes.size() >= ceiling) {
                return null;
            }
            updated.add(entry);
        }
        return updated;
    }

    private static List<NodeEntry> withoutNode(List<NodeEntry> nodes, String node) {
        List<NodeEntry> updated = new ArrayList<>();
        for (NodeEntry held : nodes) {
            if (!held.node()
                .equals(node)) {
                updated.add(held);
            }
        }
        return updated;
    }

    private static boolean sameRule(NodeEntry left, NodeEntry right) {
        return left.node()
            .equals(right.node())
            && left.contexts()
                .equals(right.contexts());
    }

    private static Map<String, String> withValue(Map<String, String> meta, String key, String value) {
        Map<String, String> updated = new LinkedHashMap<>(meta);
        updated.put(key, value);
        return updated;
    }

    private static Map<String, String> withoutValue(Map<String, String> meta, String key) {
        Map<String, String> updated = new LinkedHashMap<>(meta);
        updated.remove(key);
        return updated;
    }

    private static GroupRecord withNodes(GroupRecord group, List<NodeEntry> nodes) {
        return GroupRecord.of(group.id(), group.displayName(), group.weight(), group.inherits(), nodes, group.meta());
    }

    private static GroupRecord withInherits(GroupRecord group, List<String> inherits) {
        return GroupRecord.of(group.id(), group.displayName(), group.weight(), inherits, group.nodes(), group.meta());
    }

    private static GroupRecord withWeight(GroupRecord group, int weight) {
        return GroupRecord.of(group.id(), group.displayName(), weight, group.inherits(), group.nodes(), group.meta());
    }

    private static GroupRecord withMeta(GroupRecord group, Map<String, String> meta) {
        return GroupRecord.of(group.id(), group.displayName(), group.weight(), group.inherits(), group.nodes(), meta);
    }

    private static UserRecord withNodes(UserRecord user, List<NodeEntry> nodes) {
        return UserRecord.of(user.uuid(), user.name(), user.primary(), user.groups(), nodes, user.meta());
    }

    private static UserRecord withMeta(UserRecord user, Map<String, String> meta) {
        return UserRecord.of(user.uuid(), user.name(), user.primary(), user.groups(), user.nodes(), meta);
    }

    private static UserRecord withPrimary(UserRecord user, String primary) {
        return UserRecord.of(user.uuid(), user.name(), primary, user.groups(), user.nodes(), user.meta());
    }

    private static UserRecord withGroups(UserRecord user, List<UserRecord.Grant> grants) {
        return UserRecord.of(user.uuid(), user.name(), user.primary(), grants, user.nodes(), user.meta());
    }

    private static UserRecord relinkedGrants(UserRecord user, String from, String to) {
        boolean touched = false;
        Set<String> kept = new HashSet<>();
        List<UserRecord.Grant> grants = new ArrayList<>();
        for (UserRecord.Grant grant : user.groups()) {
            if (!grant.groupId()
                .equals(from)) {
                if (kept.add(grant.groupId())) {
                    grants.add(grant);
                }
                continue;
            }
            touched = true;
            if (to != null && kept.add(to)) {
                grants.add(UserRecord.Grant.of(to, grant.expiresAt()));
            }
        }
        String primary = user.primary();
        if (from.equals(primary)) {
            primary = to;
            touched = true;
        }
        if (!touched) {
            return user;
        }
        return UserRecord.of(user.uuid(), user.name(), primary, grants, user.nodes(), user.meta());
    }

    private boolean createsCycle(Snapshot snapshot, String groupId, String parentId) {
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(parentId);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (!visited.add(current)) {
                continue;
            }
            if (current.equals(groupId)) {
                return true;
            }
            Optional<GroupRecord> group = snapshot.group(current);
            if (group.isPresent()) {
                queue.addAll(
                    group.get()
                        .inherits());
            }
        }
        return false;
    }

    private static GroupRecord groupOf(Snapshot snapshot, String groupId) {
        String normalized = normalizeId(groupId);
        return normalized == null ? null
            : snapshot.group(normalized)
                .orElse(null);
    }

    private static UserRecord userOf(Snapshot snapshot, UUID player) {
        return snapshot.user(player)
            .orElse(
                UserRecord.of(
                    player,
                    null,
                    null,
                    Collections.<UserRecord.Grant>emptyList(),
                    Collections.<NodeEntry>emptyList(),
                    Collections.<String, String>emptyMap()));
    }

    private static String normalizeId(String id) {
        if (id == null) {
            return null;
        }
        String normalized = id.trim()
            .toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private static ChangeEvent event(ChangeEvent.Kind kind, ChangeCause cause, ChangeEvent.Subject subject) {
        return ChangeEvent.of(kind, Collections.singletonList(subject), cause);
    }

    private static ChangeEvent.Subject subject(String groupId) {
        return ChangeEvent.Subject.group(normalizeId(groupId));
    }

    private static ChangeEvent.Subject playerSubject(UUID player) {
        return ChangeEvent.Subject.player(player);
    }

    private static OperationResult invalid(String message) {
        return OperationResult.failure(OperationResult.Failure.INVALID_VALUE, message);
    }

    private static OperationResult tooLong(String message) {
        return OperationResult.failure(OperationResult.Failure.LIMIT_REACHED, message);
    }
}
