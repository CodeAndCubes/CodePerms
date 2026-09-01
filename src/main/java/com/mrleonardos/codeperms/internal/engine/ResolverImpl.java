package com.mrleonardos.codeperms.internal.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import com.mrleonardos.codeperms.api.model.ContextSet;
import com.mrleonardos.codeperms.api.model.GroupRecord;
import com.mrleonardos.codeperms.api.model.NodeEntry;
import com.mrleonardos.codeperms.api.model.Snapshot;
import com.mrleonardos.codeperms.api.model.UserRecord;
import com.mrleonardos.codeperms.api.resolve.Candidate;
import com.mrleonardos.codeperms.api.resolve.NodePatterns;
import com.mrleonardos.codeperms.api.resolve.Resolution;

public final class ResolverImpl {

    static final String PLAYER_SOURCE = "player";
    static final String GROUP_SOURCE = "group:";
    static final String DEFAULT_SOURCE = "default";

    private static final Comparator<Source> FIRST_STRONGEST = new Comparator<Source>() {

        @Override
        public int compare(Source left, Source right) {
            int byPriority = Integer.compare(right.priority, left.priority);
            if (byPriority != 0) {
                return byPriority;
            }
            int byDepth = Integer.compare(left.depth, right.depth);
            if (byDepth != 0) {
                return byDepth;
            }
            return left.id.compareTo(right.id);
        }
    };

    private final LongSupplier clock;
    private final Supplier<List<NodeEntry>> defaultNodes;

    public ResolverImpl() {
        this(System::currentTimeMillis);
    }

    public ResolverImpl(LongSupplier clock) {
        this(clock, Collections.<NodeEntry>emptyList());
    }

    /**
     * Источник {@code default} замыкает разрешение: применяется, когда личные ноды, группы и группа по
     * умолчанию ничего не ответили. Поставщик вызывается на каждом {@link #resolve}, поэтому должен
     * отдавать закэшированный список, а не читать конфиг заново.
     *
     * @param defaultNodes поставщик узлов по умолчанию, может вернуть {@code null} или пустой список
     */
    public ResolverImpl(LongSupplier clock, Supplier<List<NodeEntry>> defaultNodes) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.defaultNodes = Objects.requireNonNull(defaultNodes, "defaultNodes");
    }

    /**
     * Вариант с готовым списком узлов по умолчанию, семантика как у поставщика выше.
     *
     * @param defaultNodes узлы по умолчанию, список не копируется и не должен меняться после передачи
     */
    public ResolverImpl(LongSupplier clock, List<NodeEntry> defaultNodes) {
        this.clock = Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(defaultNodes, "defaultNodes");
        this.defaultNodes = () -> defaultNodes;
    }

    public Resolution resolve(Snapshot snapshot, UUID player, String node, ContextSet contexts) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(contexts, "contexts");
        long now = clock.getAsLong();
        for (Source source : sources(snapshot, player, now)) {
            List<Candidate> found = new ArrayList<>();
            for (NodeEntry entry : source.group != null ? source.group.nodes() : source.personalNodes) {
                if (entry.expiredAt(now) || !NodePatterns.matches(entry.node(), node)
                    || !entry.contexts()
                        .matches(contexts)) {
                    continue;
                }
                found.add(Candidate.of(source.name, entry, contexts));
            }
            if (!found.isEmpty()) {
                return Resolution.of(found);
            }
        }
        return Resolution.none();
    }

    public String group(Snapshot snapshot, UUID player) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(player, "player");
        long now = clock.getAsLong();
        UserRecord user = snapshot.user(player)
            .orElse(null);
        if (user != null && user.primary() != null && user.memberOf(user.primary(), now)) {
            return user.primary();
        }
        String best = null;
        int bestWeight = 0;
        for (String groupId : user != null ? user.activeGroupIds(now) : Collections.<String>emptyList()) {
            GroupRecord group = snapshot.group(groupId)
                .orElse(null);
            if (group == null) {
                continue;
            }
            if (best == null || group.weight() > bestWeight
                || (group.weight() == bestWeight && groupId.compareTo(best) < 0)) {
                best = groupId;
                bestWeight = group.weight();
            }
        }
        if (best != null) {
            return best;
        }
        return snapshot.defaultGroup();
    }

    public Optional<String> meta(Snapshot snapshot, UUID player, String key) {
        List<String> values = metaValues(snapshot, player, key);
        return values.isEmpty() ? Optional.<String>empty() : Optional.of(values.get(0));
    }

    public List<String> metaValues(Snapshot snapshot, UUID player, String key) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(key, "key");
        long now = clock.getAsLong();
        UserRecord user = snapshot.user(player)
            .orElse(null);
        List<GroupRecord> ordered = new ArrayList<>();
        for (Source source : sources(snapshot, user, now)) {
            if (source.group != null) {
                ordered.add(source.group);
            }
        }
        return MetaStack.values(user, ordered, key);
    }

    private List<Source> sources(Snapshot snapshot, UUID player, long now) {
        return sources(
            snapshot,
            snapshot.user(player)
                .orElse(null),
            now);
    }

    private List<Source> sources(Snapshot snapshot, UserRecord user, long now) {
        List<Source> sources = new ArrayList<>();
        if (user != null && !user.nodes()
            .isEmpty()) {
            sources.add(Source.personal(user.nodes()));
        }
        Set<String> visited = new HashSet<>();
        List<Source> granted = new ArrayList<>();
        if (user != null) {
            walk(snapshot, user.activeGroupIds(now), visited, granted);
        }
        List<Source> fallback = new ArrayList<>();
        walk(snapshot, Collections.singletonList(snapshot.defaultGroup()), visited, fallback);
        Collections.sort(granted, FIRST_STRONGEST);
        Collections.sort(fallback, FIRST_STRONGEST);
        sources.addAll(granted);
        sources.addAll(fallback);
        addFallback(sources);
        return sources;
    }

    private static void walk(Snapshot snapshot, List<String> roots, Set<String> visited, List<Source> found) {
        Deque<Pending> queue = new ArrayDeque<>();
        for (String groupId : roots) {
            queue.add(new Pending(groupId, 0));
        }
        while (!queue.isEmpty()) {
            Pending pending = queue.poll();
            if (pending.groupId == null || !visited.add(pending.groupId)) {
                continue;
            }
            GroupRecord group = snapshot.group(pending.groupId)
                .orElse(null);
            if (group == null) {
                continue;
            }
            found.add(Source.group(group, group.weight() - pending.depth, pending.depth));
            for (String parentId : group.inherits()) {
                if (!visited.contains(parentId)) {
                    queue.add(new Pending(parentId, pending.depth + 1));
                }
            }
        }
    }

    private void addFallback(List<Source> sources) {
        List<NodeEntry> fallback = defaultNodes.get();
        if (fallback != null && !fallback.isEmpty()) {
            sources.add(Source.fixed(DEFAULT_SOURCE, fallback));
        }
    }

    private static final class Pending {

        private final String groupId;
        private final int depth;

        private Pending(String groupId, int depth) {
            this.groupId = groupId;
            this.depth = depth;
        }
    }

    private static final class Source {

        private final String name;
        private final List<NodeEntry> personalNodes;
        private final GroupRecord group;
        private final int priority;
        private final int depth;
        private final String id;

        private static Source personal(List<NodeEntry> nodes) {
            return new Source(PLAYER_SOURCE, nodes, null, 0, 0, "");
        }

        private static Source group(GroupRecord group, int priority, int depth) {
            return new Source(
                GROUP_SOURCE + group.id(),
                Collections.<NodeEntry>emptyList(),
                group,
                priority,
                depth,
                group.id());
        }

        private static Source fixed(String name, List<NodeEntry> nodes) {
            return new Source(name, nodes, null, Integer.MIN_VALUE, 0, name);
        }

        private Source(String name, List<NodeEntry> personalNodes, GroupRecord group, int priority, int depth,
            String id) {
            this.name = name;
            this.personalNodes = personalNodes;
            this.group = group;
            this.priority = priority;
            this.depth = depth;
            this.id = id;
        }
    }
}
