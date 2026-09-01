package com.mrleonardos.codeperms.internal.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import com.mrleonardos.codeperms.api.manage.ChangeEvent;
import com.mrleonardos.codeperms.api.model.GroupRecord;
import com.mrleonardos.codeperms.api.model.NodeEntry;
import com.mrleonardos.codeperms.api.model.Snapshot;
import com.mrleonardos.codeperms.api.model.UserRecord;

public final class ExpiryHeap {

    private final Map<ChangeEvent.Subject, Long> earliest = new HashMap<>();
    private final PriorityQueue<Pending> queue = new PriorityQueue<>();

    public void rebuild(Snapshot snapshot) {
        earliest.clear();
        queue.clear();
        for (UserRecord user : snapshot.users()
            .values()) {
            for (NodeEntry node : user.nodes()) {
                add(ChangeEvent.Subject.player(user.uuid()), node.expiresAt());
            }
            for (UserRecord.Grant grant : user.groups()) {
                add(ChangeEvent.Subject.player(user.uuid()), grant.expiresAt());
            }
        }
        for (GroupRecord group : snapshot.groups()
            .values()) {
            for (NodeEntry node : group.nodes()) {
                add(ChangeEvent.Subject.group(group.id()), node.expiresAt());
            }
        }
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public long nextExpiry() {
        Pending soonest = queue.peek();
        return soonest == null ? Long.MAX_VALUE : soonest.expiresAt;
    }

    public Prune prune(Snapshot snapshot, long now) {
        List<ChangeEvent.Subject> touched = new ArrayList<>();
        List<UserRecord> users = new ArrayList<>();
        boolean usersChanged = false;
        for (UserRecord user : snapshot.users()
            .values()) {
            UserRecord pruned = withoutExpired(user, now);
            if (pruned == user) {
                continue;
            }
            users.add(pruned);
            touched.add(ChangeEvent.Subject.player(user.uuid()));
            usersChanged = true;
        }
        List<GroupRecord> groups = new ArrayList<>();
        for (GroupRecord group : snapshot.groups()
            .values()) {
            GroupRecord pruned = withoutExpired(group, now);
            if (pruned == group) {
                continue;
            }
            groups.add(pruned);
            touched.add(ChangeEvent.Subject.group(group.id()));
        }
        if (!usersChanged && groups.isEmpty()) {
            return Prune.unchanged(snapshot);
        }
        Snapshot.Builder builder = Snapshot.builder()
            .revision(snapshot.revision() + 1L)
            .defaultGroup(snapshot.defaultGroup())
            .opGroup(snapshot.opGroup())
            .from(snapshot);
        for (UserRecord user : users) {
            builder.user(user);
        }
        for (GroupRecord group : groups) {
            builder.group(group);
        }
        return new Prune(builder.build(), touched);
    }

    private UserRecord withoutExpired(UserRecord user, long now) {
        List<NodeEntry> nodes = aliveNodes(user.nodes(), now);
        List<UserRecord.Grant> grants = new ArrayList<>();
        boolean changed = false;
        for (UserRecord.Grant grant : user.groups()) {
            if (grant.expiredAt(now)) {
                changed = true;
                continue;
            }
            grants.add(grant);
        }
        if (!changed && nodes == user.nodes()) {
            return user;
        }
        return UserRecord.of(user.uuid(), user.name(), user.primary(), grants, nodes, user.meta());
    }

    private GroupRecord withoutExpired(GroupRecord group, long now) {
        List<NodeEntry> nodes = aliveNodes(group.nodes(), now);
        if (nodes == group.nodes()) {
            return group;
        }
        return GroupRecord.of(group.id(), group.displayName(), group.weight(), group.inherits(), nodes, group.meta());
    }

    private List<NodeEntry> aliveNodes(List<NodeEntry> nodes, long now) {
        List<NodeEntry> alive = new ArrayList<>();
        boolean changed = false;
        for (NodeEntry node : nodes) {
            if (node.expiredAt(now)) {
                changed = true;
                continue;
            }
            alive.add(node);
        }
        return changed ? alive : nodes;
    }

    private void add(ChangeEvent.Subject subject, long expiresAt) {
        if (expiresAt <= 0) {
            return;
        }
        Long known = earliest.get(subject);
        if (known != null && known.longValue() <= expiresAt) {
            return;
        }
        earliest.put(subject, Long.valueOf(expiresAt));
        queue.add(new Pending(expiresAt, subject));
    }

    private static final class Pending implements Comparable<Pending> {

        final long expiresAt;
        final ChangeEvent.Subject subject;

        Pending(long expiresAt, ChangeEvent.Subject subject) {
            this.expiresAt = expiresAt;
            this.subject = subject;
        }

        @Override
        public int compareTo(Pending other) {
            return Long.compare(expiresAt, other.expiresAt);
        }
    }

    public static final class Prune {

        private final Snapshot snapshot;
        private final List<ChangeEvent.Subject> subjects;

        static Prune unchanged(Snapshot snapshot) {
            return new Prune(snapshot, Collections.<ChangeEvent.Subject>emptyList());
        }

        Prune(Snapshot snapshot, List<ChangeEvent.Subject> subjects) {
            this.snapshot = snapshot;
            this.subjects = Collections.unmodifiableList(new ArrayList<>(subjects));
        }

        public Snapshot snapshot() {
            return snapshot;
        }

        public List<ChangeEvent.Subject> subjects() {
            return subjects;
        }

        public boolean changed() {
            return snapshot != null && !subjects.isEmpty();
        }
    }
}
