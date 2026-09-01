package com.mrleonardos.codeperms.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import net.minecraft.command.ICommandSender;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.junit.jupiter.api.Test;

import com.mrleonardos.codecore.api.command.CommandContext;
import com.mrleonardos.codeperms.api.model.ContextSet;
import com.mrleonardos.codeperms.api.model.GroupRecord;
import com.mrleonardos.codeperms.api.model.NodeEntry;
import com.mrleonardos.codeperms.api.model.Snapshot;
import com.mrleonardos.codeperms.api.model.UserRecord;
import com.mrleonardos.codeperms.internal.command.PermsSubjects;
import com.mrleonardos.codeperms.internal.engine.ResolverImpl;

class CorePermissionServiceTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final long NOW = 20_000L;
    private static final Function<ICommandSender, UUID> THIS_PLAYER = sender -> PLAYER;

    @Test
    void nonPlayerSenderIsAllowedWithoutReadingTheModel() {
        AtomicInteger reads = new AtomicInteger();
        CorePermissionService service = service(Snapshot.empty(), reads, sender -> null);

        assertTrue(service.has((ICommandSender) null, "codechat.create"));
        assertEquals(0, reads.get(), "хранилище не читается для консоли и командных блоков");
    }

    @Test
    void playerSenderIsCheckedByHisIdentifier() {
        Snapshot snapshot = Snapshot.builder()
            .group(group("default", 0, nodes("codechat.*")))
            .user(member(PLAYER, "default"))
            .build();
        CorePermissionService service = service(snapshot, new AtomicInteger(), THIS_PLAYER);

        assertTrue(service.has((ICommandSender) null, "codechat.channel.global.read"));
        assertFalse(service.has((ICommandSender) null, "economy.pay"));
    }

    @Test
    void hasWalksPersonalNodesThenGroups() {
        Snapshot snapshot = Snapshot.builder()
            .group(group("default", 0, nodes("codechat.*")))
            .user(member(PLAYER, "default", "-codechat.create"))
            .build();
        CorePermissionService service = service(snapshot, new AtomicInteger(), THIS_PLAYER);

        assertFalse(service.has(PLAYER, "codechat.create"), "личный запрет сильнее группового разрешения");
        assertTrue(service.has(PLAYER, "codechat.channel.global.read"));
    }

    @Test
    void contextsComeFromTheSubjectDirectory() {
        Snapshot snapshot = Snapshot.builder()
            .group(inNether("default", "codechat.format"))
            .user(member(PLAYER, "default"))
            .build();
        FixedSubjects subjects = new FixedSubjects(world("overworld"));
        CorePermissionService service = new CorePermissionService(
            () -> snapshot,
            new ResolverImpl(() -> NOW),
            subjects,
            THIS_PLAYER);

        assertFalse(service.has(PLAYER, "codechat.format"), "правило другого мира не применяется");
        assertTrue(subjects.reads.get() > 0);

        subjects.contexts = world("nether");
        assertTrue(service.has(PLAYER, "codechat.format"));
    }

    @Test
    void groupFollowsPrimaryThenWeightThenDefault() {
        Snapshot snapshot = Snapshot.builder()
            .group(group("junior", 10, Collections.<NodeEntry>emptyList()))
            .group(group("senior", 50, Collections.<NodeEntry>emptyList()))
            .group(group("player", 0, Collections.<NodeEntry>emptyList()))
            .user(inGroups(PLAYER, "junior", "junior", "senior"))
            .defaultGroup("player")
            .build();

        assertEquals(
            "junior",
            service(snapshot, new AtomicInteger(), THIS_PLAYER).group(PLAYER),
            "primary старше веса");

        Snapshot withoutPrimary = Snapshot.builder()
            .from(snapshot)
            .user(inGroups(PLAYER, null, "junior", "senior"))
            .build();
        assertEquals(
            "senior",
            service(withoutPrimary, new AtomicInteger(), THIS_PLAYER).group(PLAYER),
            "без primary решает вес");

        Snapshot stranger = Snapshot.builder()
            .group(group("player", 0, Collections.<NodeEntry>emptyList()))
            .defaultGroup("player")
            .build();
        assertEquals("player", service(stranger, new AtomicInteger(), THIS_PLAYER).group(PLAYER));
    }

    @Test
    void metaFallsBackWhenNobodyHoldsTheKey() {
        Snapshot snapshot = Snapshot.builder()
            .group(metaGroup("default", "&7"))
            .user(member(PLAYER, "default"))
            .build();
        CorePermissionService service = service(snapshot, new AtomicInteger(), THIS_PLAYER);

        assertEquals("&7", service.meta(PLAYER, "prefix", ""));
        assertEquals("none", service.meta(PLAYER, "suffix", "none"));
    }

    @Test
    void checksAreExplainedAtDebugWhenTheFlagIsOn() {
        Snapshot snapshot = Snapshot.builder()
            .group(group("default", 0, nodes("codechat.create")))
            .user(member(PLAYER, "default"))
            .build();
        org.apache.logging.log4j.core.Logger log = (org.apache.logging.log4j.core.Logger) CodePermsMod.LOG;
        CapturingAppender appender = new CapturingAppender();
        Level before = log.getLevel();
        log.addAppender(appender);
        log.setLevel(Level.DEBUG);
        try {
            CorePermissionService service = new CorePermissionService(
                () -> snapshot,
                new ResolverImpl(() -> NOW),
                new FixedSubjects(ContextSet.empty()),
                THIS_PLAYER,
                () -> true);

            assertTrue(service.has(PLAYER, "codechat.create"));
            assertTrue(
                appender.lines.stream()
                    .anyMatch(line -> line.contains("codechat.create") && line.contains("allowed")),
                "проверка должна попадать в debug: " + appender.lines);

            appender.lines.clear();
            CorePermissionService quiet = new CorePermissionService(
                () -> snapshot,
                new ResolverImpl(() -> NOW),
                new FixedSubjects(ContextSet.empty()),
                THIS_PLAYER,
                () -> false);

            assertTrue(quiet.has(PLAYER, "codechat.create"));
            assertTrue(appender.lines.isEmpty(), "выключенный флаг не пишет проверку в лог");
        } finally {
            log.removeAppender(appender);
            log.setLevel(before);
        }
    }

    /** Подставной приёмник debug-записей: ловит отформатированные сообщения. */
    private static final class CapturingAppender extends AbstractAppender {

        private final List<String> lines = new ArrayList<>();

        CapturingAppender() {
            super("capturing", null, null, true);
            start();
        }

        @Override
        public void append(LogEvent event) {
            lines.add(
                event.getMessage()
                    .getFormattedMessage());
        }
    }

    private CorePermissionService service(Snapshot snapshot, AtomicInteger reads,
        Function<ICommandSender, UUID> players) {
        return new CorePermissionService(() -> {
            reads.incrementAndGet();
            return snapshot;
        }, new ResolverImpl(() -> NOW), new FixedSubjects(ContextSet.empty()), players);
    }

    private static ContextSet world(String name) {
        return ContextSet.builder()
            .put("world", name)
            .build();
    }

    private static GroupRecord group(String id, int weight, List<NodeEntry> nodes) {
        return GroupRecord
            .of(id, id, weight, Collections.<String>emptyList(), nodes, Collections.<String, String>emptyMap());
    }

    private static GroupRecord inNether(String id, String node) {
        NodeEntry entry = NodeEntry.of("codechat.format", true, world("nether"), 0L);
        return GroupRecord.of(
            id,
            id,
            0,
            Collections.<String>emptyList(),
            Collections.singletonList(entry),
            Collections.<String, String>emptyMap());
    }

    private static GroupRecord metaGroup(String id, String prefix) {
        return GroupRecord.of(
            id,
            id,
            0,
            Collections.<String>emptyList(),
            Collections.<NodeEntry>emptyList(),
            Collections.singletonMap("prefix", prefix));
    }

    private static List<NodeEntry> nodes(String... raw) {
        List<NodeEntry> entries = new ArrayList<>();
        for (String node : raw) {
            entries.add(NodeEntry.parse(node));
        }
        return entries;
    }

    private static UserRecord member(UUID player, String groupId, String... nodes) {
        return UserRecord.of(
            player,
            "Steve",
            groupId,
            Collections.singletonList(UserRecord.Grant.permanent(groupId)),
            nodes(nodes),
            Collections.<String, String>emptyMap());
    }

    private static UserRecord inGroups(UUID player, String primary, String... groupIds) {
        List<UserRecord.Grant> grants = new ArrayList<>();
        for (String groupId : groupIds) {
            grants.add(UserRecord.Grant.permanent(groupId));
        }
        return UserRecord.of(
            player,
            "Steve",
            primary,
            grants,
            Collections.<NodeEntry>emptyList(),
            Collections.<String, String>emptyMap());
    }

    private static final class FixedSubjects implements PermsSubjects {

        private final AtomicInteger reads = new AtomicInteger();
        private ContextSet contexts;

        private FixedSubjects(ContextSet contexts) {
            this.contexts = contexts;
        }

        @Override
        public Optional<UUID> subjectOf(CommandContext context) {
            reads.incrementAndGet();
            return Optional.of(PLAYER);
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
            reads.incrementAndGet();
            return contexts;
        }
    }
}
