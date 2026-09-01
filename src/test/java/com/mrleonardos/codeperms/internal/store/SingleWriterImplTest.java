package com.mrleonardos.codeperms.internal.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mrleonardos.codecore.api.config.ConfigService;
import com.mrleonardos.codecore.api.util.Scheduler;
import com.mrleonardos.codeperms.TestConfigs;
import com.mrleonardos.codeperms.api.PermsLimits;
import com.mrleonardos.codeperms.api.model.ChangeCause;
import com.mrleonardos.codeperms.api.model.Snapshot;
import com.mrleonardos.codeperms.api.store.ChangeBatch;
import com.mrleonardos.codeperms.api.store.OperationResult;
import com.mrleonardos.codeperms.api.store.PermissionStore;
import com.mrleonardos.codeperms.internal.MainSettings;

class SingleWriterImplTest {

    private static final Logger LOG = LogManager.getLogger(SingleWriterImplTest.class);
    private static final String FOREIGN_ID = "memory-test";

    @TempDir
    Path root;

    private ConfigService configs;

    @BeforeEach
    void setUp() {
        configs = TestConfigs.of(root);
    }

    @Test
    void commitSwapsSnapshotAndMarksDirty() throws Exception {
        SingleWriterImpl writer = writer(new MemoryStore());

        writer.commit(with(writer.snapshot(), PermsFixtures.group("vip", 10)), batch("vip"));

        assertEquals(
            "vip",
            writer.snapshot()
                .group("vip")
                .get()
                .id());
        assertTrue(writer.unsaved());

        writer.flush();
        awaitSaved(writer);
    }

    @Test
    void refusedChangeKeepsLastSnapshot() {
        MemoryStore store = new MemoryStore();
        store.refuse = true;
        SingleWriterImpl writer = writer(store);
        Snapshot before = writer.snapshot();

        OperationResult result = writer.commit(with(before, PermsFixtures.group("vip", 10)), batch("vip"));

        assertFalse(result.successful());
        assertSame(before, writer.snapshot());
    }

    @Test
    void thrownProviderKeepsLastSnapshotAndReportsFailure() {
        MemoryStore store = new MemoryStore();
        store.throwOnApply = true;
        SingleWriterImpl writer = writer(store);
        Snapshot before = writer.snapshot();

        OperationResult result = writer.commit(with(before, PermsFixtures.group("vip", 10)), batch("vip"));

        assertEquals(
            OperationResult.Failure.PROVIDER_FAILED,
            result.failure()
                .get());
        assertSame(before, writer.snapshot());
        assertEquals(1L, before.revision());
        assertEquals(0, store.applied.get());
    }

    @Test
    void snapshotBeforeStartAnswersFromTheStore() {
        MemoryStore store = new MemoryStore();
        SingleWriterImpl writer = new SingleWriterImpl(store, new TestScheduler(), LOG, () -> 1000L, 20, 200);

        Snapshot early = writer.snapshot();

        assertEquals("player", early.defaultGroup());
        assertTrue(
            early.group("player")
                .isPresent());
        assertFalse(writer.unsaved());
    }

    @Test
    void stopWritesPendingChanges() throws Exception {
        MemoryStore store = new MemoryStore();
        SingleWriterImpl writer = writer(store);

        writer.commit(with(writer.snapshot(), PermsFixtures.group("vip", 10)), batch("vip"));
        writer.stop();

        assertFalse(writer.unsaved());
        assertTrue(store.savedGroups.contains("vip"));
    }

    @Test
    void failedSaveKeepsSnapshotAndDirtyFlag() {
        MemoryStore store = new MemoryStore();
        store.failSave = true;
        SingleWriterImpl writer = writer(store);

        writer.commit(with(writer.snapshot(), PermsFixtures.group("vip", 10)), batch("vip"));
        writer.flushNow();

        assertTrue(writer.unsaved());
        assertTrue(
            writer.snapshot()
                .group("vip")
                .isPresent());
    }

    @Test
    void autosaveHandsTheSnapshotToTheBackgroundWriter() throws Exception {
        MemoryStore store = new MemoryStore();
        TestScheduler scheduler = new TestScheduler();
        SingleWriterImpl writer = writer(store, scheduler);

        writer.commit(with(writer.snapshot(), PermsFixtures.group("vip", 10)), batch("vip"));
        scheduler.drain(1);

        long deadline = System.currentTimeMillis() + 10_000L;
        while (writer.unsaved() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20L);
        }

        assertFalse(writer.unsaved());
        assertTrue(store.savedGroups.contains("vip"));
        writer.stop();
    }

    @Test
    void commitsFromOtherThreadsAreCarriedToTheMainThread() throws Exception {
        MemoryStore store = new MemoryStore();
        MainThread main = new MainThread();
        SingleWriterImpl writer = new SingleWriterImpl(store, main, LOG, () -> 1000L, 20, 200);
        writer.start();
        int threads = 8;
        int perThread = 25;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch done = new CountDownLatch(threads);
        List<OperationResult> answers = Collections.synchronizedList(new ArrayList<>());

        for (int index = 0; index < threads; index++) {
            final int base = index;
            pool.submit(() -> {
                try {
                    for (int step = 0; step < perThread; step++) {
                        String groupId = "g" + base + "_" + step;
                        answers.add(
                            writer.commit(with(writer.snapshot(), PermsFixtures.group(groupId, 1)), batch(groupId)));
                    }
                } finally {
                    done.countDown();
                }
            });
        }

        assertTrue(done.await(30, TimeUnit.SECONDS));
        pool.shutdownNow();
        main.stop();
        writer.stop();

        assertEquals(threads * perThread, answers.size());
        for (OperationResult answer : answers) {
            assertTrue(answer.successful(), "каждая правка обязана дойти: " + answer);
        }
        assertEquals(threads * perThread, store.applied.get());
        assertEquals(1, store.appliedOn.size(), "правки обязаны идти одним потоком, а шли " + store.appliedOn);
        assertEquals(
            MainThread.NAME,
            store.appliedOn.iterator()
                .next());
    }

    @Test
    void timedOutCommitIsDroppedFromTheQueueAndNeverReachesTheStore() throws Exception {
        MemoryStore store = new MemoryStore();
        DeferredScheduler scheduler = new DeferredScheduler();
        SingleWriterImpl writer = new SingleWriterImpl(store, scheduler, LOG, () -> 1000L, 20, 200);
        writer.start();
        writer.awaitMillis(50L);
        Snapshot before = writer.snapshot();

        OperationResult result = new Thread() {

            OperationResult answer;

            OperationResult call() throws InterruptedException {
                start();
                join(10_000L);
                return answer;
            }

            @Override
            public void run() {
                answer = writer.commit(with(before, PermsFixtures.group("vip", 10)), batch("vip"));
            }
        }.call();

        assertEquals(
            OperationResult.Failure.TIMEOUT,
            result.failure()
                .get());

        scheduler.runQueued();

        assertEquals(0, store.applied.get());
        assertSame(before, writer.snapshot());
    }

    @Test
    void sinkFailureLeavesTheAppliedChangeApplied() {
        MemoryStore store = new MemoryStore();
        SingleWriterImpl writer = writer(store);
        writer.sink((batch, events) -> { throw new IllegalStateException("the config is not loaded yet"); });

        OperationResult result = writer.commit(with(writer.snapshot(), PermsFixtures.group("vip", 10)), batch("vip"));

        assertTrue(result.successful());
        assertTrue(
            writer.snapshot()
                .group("vip")
                .isPresent());
        assertEquals(1, store.applied.get());
    }

    @Test
    void expiryScanSurvivesAThrowingProviderAndKeepsItsSchedule() {
        MemoryStore store = new MemoryStore();
        AtomicLong clock = new AtomicLong(10_000L);
        TestScheduler scheduler = new TestScheduler();
        SingleWriterImpl writer = new SingleWriterImpl(store, scheduler, LOG, clock::get, 20, 200);
        writer.start();
        Snapshot withTimed = with(writer.snapshot(), PermsFixtures.timedGroup("temp", 5, 11_000L));
        writer.commit(withTimed, batch("temp"));

        clock.set(12_000L);
        store.throwOnApply = true;
        int queued = scheduler.queued.size();
        scheduler.drain(queued);

        assertEquals(queued, scheduler.queued.size(), "и автосейв, и скан обязаны встать в очередь заново");
        assertFalse(
            writer.snapshot()
                .group("temp")
                .get()
                .nodes()
                .isEmpty(),
            "упавший провайдер не должен менять снимок");

        store.throwOnApply = false;
        scheduler.drain(scheduler.queued.size());

        assertTrue(
            writer.snapshot()
                .group("temp")
                .get()
                .nodes()
                .isEmpty());
    }

    @Test
    void providerWithoutFullDumpIsNotAskedTwice() {
        LeanStore store = new LeanStore();
        SingleWriterImpl writer = new SingleWriterImpl(store, new TestScheduler(), LOG, () -> 1000L, 20, 200);
        writer.start();

        writer.commit(with(writer.snapshot(), PermsFixtures.group("vip", 10)), batch("vip"));
        writer.flushNow();

        assertFalse(writer.unsaved());
        assertEquals(1, store.saveCalls.get());

        writer.commit(with(writer.snapshot(), PermsFixtures.group("mod", 5)), batch("mod"));
        writer.flushNow();
        writer.stop();

        assertEquals(1, store.saveCalls.get());
    }

    @Test
    void reloadFlushesUnsavedWorkBeforeReadingTheFilesAgain() {
        MemoryStore store = new MemoryStore();
        SingleWriterImpl writer = writer(store);
        writer.commit(with(writer.snapshot(), PermsFixtures.group("vip", 10)), batch("vip"));
        List<String> order = new ArrayList<>();
        store.onSave = () -> order.add("save");
        store.onLoad = () -> order.add("load");

        OperationResult result = writer.reload(() -> order.add("reread"));

        assertTrue(result.successful());
        assertEquals(Arrays.asList("save", "reread", "load"), order);
        assertTrue(store.savedGroups.contains("vip"));
        assertFalse(writer.unsaved());
    }

    @Test
    void reloadRefusesWhenTheUnsavedWorkCannotBeWritten() {
        MemoryStore store = new MemoryStore();
        SingleWriterImpl writer = writer(store);
        writer.commit(with(writer.snapshot(), PermsFixtures.group("vip", 10)), batch("vip"));
        store.failSave = true;
        List<String> order = new ArrayList<>();
        store.onLoad = () -> order.add("load");

        OperationResult result = writer.reload(() -> order.add("reread"));

        assertFalse(result.successful());
        assertTrue(order.isEmpty(), "перечитывание обязано не начинаться: " + order);
        assertTrue(
            writer.snapshot()
                .group("vip")
                .isPresent());
    }

    @Test
    void scanExpiryDropsTimedEntriesAndReportsCause() {
        MemoryStore store = new MemoryStore();
        AtomicLong clock = new AtomicLong(10_000L);
        SingleWriterImpl writer = new SingleWriterImpl(store, new TestScheduler(), LOG, clock::get, 20, 200);
        writer.start();
        Snapshot withTimed = with(writer.snapshot(), PermsFixtures.timedGroup("temp", 5, 11_000L));
        writer.commit(withTimed, batch("temp"));
        writer.expiry()
            .rebuild(withTimed);

        List<ChangeCause> causes = new ArrayList<>();
        writer.sink((batch, events) -> causes.add(batch.cause()));

        clock.set(12_000L);
        writer.scanExpiry();

        assertTrue(
            writer.snapshot()
                .group("temp")
                .isPresent());
        assertTrue(
            writer.snapshot()
                .group("temp")
                .get()
                .nodes()
                .isEmpty());
        assertEquals(ChangeCause.EXPIRY, causes.get(0));
    }

    @Test
    void scanExpiryMarksTheSnapshotDirtyAndReachesTheStore() {
        MemoryStore store = new MemoryStore();
        AtomicLong clock = new AtomicLong(10_000L);
        SingleWriterImpl writer = new SingleWriterImpl(store, new TestScheduler(), LOG, clock::get, 20, 200);
        writer.start();
        Snapshot withTimed = with(writer.snapshot(), PermsFixtures.timedGroup("temp", 5, 11_000L));
        writer.commit(withTimed, batch("temp"));
        writer.expiry()
            .rebuild(withTimed);
        writer.flushNow();
        assertFalse(writer.unsaved());

        clock.set(12_000L);
        writer.scanExpiry();

        assertTrue(writer.unsaved());
        writer.flushNow();
        assertTrue(
            store.lastSaved.group("temp")
                .get()
                .nodes()
                .isEmpty());
    }

    @Test
    void providerIsChosenByName() {
        MemoryStore foreign = new MemoryStore() {

            @Override
            public String id() {
                return FOREIGN_ID;
            }
        };
        SingleWriterImpl.Lookup lookup = id -> FOREIGN_ID.equals(id) ? java.util.Optional.<PermissionStore>of(foreign)
            : java.util.Optional.<PermissionStore>empty();
        PermissionStore builtin = builtin();

        assertSame(foreign, SingleWriterImpl.resolveProvider(FOREIGN_ID, lookup, builtin, LOG));
        assertSame(builtin, SingleWriterImpl.resolveProvider("json", lookup, builtin, LOG));
        assertSame(builtin, SingleWriterImpl.resolveProvider("unknown", lookup, builtin, LOG));
    }

    private void awaitSaved(SingleWriterImpl writer) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000L;
        while (writer.unsaved() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20L);
        }
    }

    private SingleWriterImpl writer(MemoryStore store) {
        return writer(store, new TestScheduler());
    }

    private SingleWriterImpl writer(MemoryStore store, TestScheduler scheduler) {
        SingleWriterImpl writer = new SingleWriterImpl(store, scheduler, LOG, () -> 1000L, 20, 200);
        writer.start();
        return writer;
    }

    private PermissionStore builtin() {
        return new JsonPermissionStore(
            configs.open(com.mrleonardos.codeperms.internal.PermsSettings.spec()),
            new MainSettings(configs),
            configs.open(GroupsStore.spec()),
            configs.open(PlayersStore.spec()),
            PermsLimits.defaults(),
            LOG);
    }

    @Test
    void providerNameComesFromTheMainFile() {
        TestConfigs.writeMain(root, "[storage]", "provider = \"nowhere\"");
        ConfigService named = TestConfigs.of(root);

        SingleWriterImpl writer = SingleWriterImpl.create(named, new MainSettings(named), new TestScheduler(), LOG);

        assertEquals(
            JsonPermissionStore.ID,
            writer.store()
                .id(),
            "имя из главного файла никто не занял, поднимаемся на встроенном json");
    }

    private Snapshot with(Snapshot current, com.mrleonardos.codeperms.api.model.GroupRecord group) {
        return Snapshot.builder()
            .revision(current.revision() + 1L)
            .defaultGroup(current.defaultGroup())
            .opGroup(current.opGroup())
            .from(current)
            .group(group)
            .build();
    }

    private ChangeBatch batch(String groupId) {
        return ChangeBatch.builder(ChangeCause.COMMAND, "console")
            .removeGroup(groupId)
            .build();
    }

    @Test
    void clockJumpIsDetectedAgainstMonotonicTime() {
        AtomicLong wall = new AtomicLong(1_000_000L);
        AtomicLong monotonic = new AtomicLong(0L);
        java.util.function.Supplier<SingleWriterImpl.ClockWatch> watch = () -> new SingleWriterImpl.ClockWatch(
            wall::get,
            monotonic::get);

        SingleWriterImpl.ClockWatch fresh = watch.get();
        assertFalse(fresh.check(1_000_000L));
        monotonic.set(1_000_000_000L);
        assertFalse(fresh.check(1_001_000L));

        SingleWriterImpl.ClockWatch jumpy = watch.get();
        assertFalse(jumpy.check(1_000_000L));
        monotonic.set(2_000_000_000L);
        assertTrue(jumpy.check(2_001_000L));
        monotonic.set(3_000_000_000L);
        assertFalse(jumpy.check(2_001_000L));
    }

    private static final class TestScheduler implements Scheduler {

        private final List<Runnable> queued = new ArrayList<>();

        @Override
        public void onMainThread(Runnable task) {
            task.run();
        }

        @Override
        public void afterTicks(int ticks, Runnable task) {
            queued.add(task);
        }

        void drain(int count) {
            for (int index = 0; index < count && !queued.isEmpty(); index++) {
                queued.remove(0)
                    .run();
            }
        }
    }

    private static final class DeferredScheduler implements Scheduler {

        private final List<Runnable> queued = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void onMainThread(Runnable task) {
            queued.add(task);
        }

        @Override
        public void afterTicks(int ticks, Runnable task) {}

        void runQueued() {
            List<Runnable> taken;
            synchronized (queued) {
                taken = new ArrayList<>(queued);
                queued.clear();
            }
            for (Runnable task : taken) {
                task.run();
            }
        }
    }

    private static final class MainThread implements Scheduler, AutoCloseable {

        static final String NAME = "test-main-thread";

        private final LinkedBlockingQueue<Runnable> queued = new LinkedBlockingQueue<>();
        private final Thread worker;
        private volatile boolean running = true;

        MainThread() {
            worker = new Thread(this::loop, NAME);
            worker.setDaemon(true);
            worker.start();
        }

        @Override
        public void onMainThread(Runnable task) {
            queued.offer(task);
        }

        @Override
        public void afterTicks(int ticks, Runnable task) {}

        @Override
        public void close() {
            stop();
        }

        void stop() {
            running = false;
            worker.interrupt();
        }

        private void loop() {
            while (running) {
                try {
                    Runnable task = queued.poll(50L, TimeUnit.MILLISECONDS);
                    if (task != null) {
                        task.run();
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread()
                        .interrupt();
                    return;
                }
            }
        }
    }

    private static final class LeanStore implements PermissionStore {

        private final AtomicLong saveCalls = new AtomicLong();

        @Override
        public String id() {
            return "lean";
        }

        @Override
        public Snapshot load() {
            return Snapshot.builder()
                .revision(1L)
                .defaultGroup("player")
                .group(PermsFixtures.group("player", 0))
                .build();
        }

        @Override
        public OperationResult apply(ChangeBatch batch) {
            return OperationResult.success();
        }

        @Override
        public OperationResult save(Snapshot snapshot) {
            saveCalls.incrementAndGet();
            return PermissionStore.super.save(snapshot);
        }
    }

    private static class MemoryStore implements PermissionStore {

        final List<String> savedGroups = new ArrayList<>();
        final Set<String> appliedOn = Collections.synchronizedSet(new LinkedHashSet<>());
        final AtomicLong applied = new AtomicLong();
        Snapshot lastSaved;
        Runnable onSave;
        Runnable onLoad;
        boolean refuse;
        volatile boolean throwOnApply;
        boolean failSave;

        @Override
        public String id() {
            return "json";
        }

        @Override
        public Snapshot load() {
            if (onLoad != null) {
                onLoad.run();
            }
            return Snapshot.builder()
                .revision(1L)
                .defaultGroup("player")
                .group(PermsFixtures.group("player", 0))
                .build();
        }

        @Override
        public OperationResult apply(ChangeBatch batch) {
            if (throwOnApply) {
                throw new IllegalStateException("provider is broken");
            }
            if (refuse) {
                return OperationResult.failure(OperationResult.Failure.PROVIDER_FAILED, "refused");
            }
            appliedOn.add(
                Thread.currentThread()
                    .getName());
            applied.incrementAndGet();
            return OperationResult.success();
        }

        @Override
        public OperationResult save(Snapshot snapshot) {
            if (failSave) {
                return OperationResult.failure(OperationResult.Failure.PROVIDER_FAILED, "disk is gone");
            }
            if (onSave != null) {
                onSave.run();
            }
            lastSaved = snapshot;
            savedGroups.addAll(
                snapshot.groups()
                    .keySet());
            return OperationResult.success();
        }
    }
}
