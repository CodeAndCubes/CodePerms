package com.mrleonardos.codeperms.internal.store;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codecore.api.config.ConfigFile;
import com.mrleonardos.codecore.api.config.ConfigService;
import com.mrleonardos.codecore.api.util.Scheduler;
import com.mrleonardos.codeperms.api.PermsApi;
import com.mrleonardos.codeperms.api.manage.ChangeEvent;
import com.mrleonardos.codeperms.api.model.ChangeCause;
import com.mrleonardos.codeperms.api.model.Snapshot;
import com.mrleonardos.codeperms.api.store.ChangeBatch;
import com.mrleonardos.codeperms.api.store.OperationResult;
import com.mrleonardos.codeperms.api.store.PermissionStore;
import com.mrleonardos.codeperms.internal.Ceilings;
import com.mrleonardos.codeperms.internal.MainSettings;
import com.mrleonardos.codeperms.internal.PermsSettings;
import com.mrleonardos.codeperms.internal.engine.ExpiryHeap;

public final class SingleWriterImpl implements SingleWriter {

    public static final long CLOCK_JUMP_MILLIS = 5L * 60L * 1000L;
    public static final long AWAIT_MILLIS = 10_000L;
    public static final String AUTHOR = "codeperms";
    public static final String WRITER_THREAD = "codeperms-writer";

    private final PermissionStore builtin;
    private final String configured;
    private final Lookup lookup;
    private final Scheduler scheduler;
    private final Logger log;
    private final LongSupplier clock;
    private final int autosaveTicks;
    private final int scanTicks;

    private final ReentrantLock lock = new ReentrantLock();
    private final ReentrantLock writeLock = new ReentrantLock();
    private final ThreadLocal<Boolean> onWriterThread = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private final LinkedBlockingQueue<Long> saves = new LinkedBlockingQueue<>();
    private final ExpiryHeap heap = new ExpiryHeap();
    private final ClockWatch watch;

    private volatile Snapshot current;
    private volatile Snapshot saved;
    private volatile boolean running;
    private volatile boolean dumpUnsupported;
    private volatile long awaitMillis = AWAIT_MILLIS;
    private volatile Sink sink = Sink.QUIET;
    private volatile Thread writer;
    private volatile PermissionStore resolved;

    public static SingleWriterImpl create(ConfigService configs, MainSettings main, Ceilings ceilings,
        Scheduler scheduler, Logger log) {
        ConfigFile<PermsSettings> settings = configs.open(PermsSettings.spec());
        PermissionStore builtin = new JsonPermissionStore(
            main,
            configs.open(GroupsStore.spec()),
            configs.open(PlayersStore.spec()),
            ceilings,
            log);
        return new SingleWriterImpl(
            builtin,
            main.provider(),
            PermsApi::store,
            scheduler,
            log,
            System::currentTimeMillis,
            main.autosaveTicks(),
            settings.get()
                .scanTicks());
    }

    /** Занято ли имя шва хранилища: встроенное json или провайдер из реестра. От этого зависит заявка на роль. */
    public static boolean storageRegistered(String configured, Lookup lookup) {
        return JsonPermissionStore.ID.equals(configured) || lookup.store(configured)
            .isPresent();
    }

    static PermissionStore resolveProvider(String configured, Lookup lookup, PermissionStore builtin, Logger log) {
        Optional<PermissionStore> foreign = lookup.store(configured);
        if (foreign.isPresent()) {
            log.info("Permissions storage provider is {}", configured);
            return foreign.get();
        }
        if (JsonPermissionStore.ID.equals(configured)) {
            return builtin;
        }
        throw new IllegalStateException(
            "Storage provider " + configured
                + " from [storage] provider is not registered;"
                + " the role claim had to refuse this name, check the seam check on the adapter");
    }

    public SingleWriterImpl(PermissionStore store, Scheduler scheduler, Logger log, LongSupplier clock,
        int autosaveTicks, int scanTicks) {
        this(store, null, null, scheduler, log, clock, autosaveTicks, scanTicks);
    }

    public SingleWriterImpl(PermissionStore builtin, String configured, Lookup lookup, Scheduler scheduler, Logger log,
        LongSupplier clock, int autosaveTicks, int scanTicks) {
        this.builtin = builtin;
        this.configured = configured;
        this.lookup = lookup;
        this.scheduler = scheduler;
        this.log = log;
        this.clock = clock;
        this.autosaveTicks = Math.max(1, autosaveTicks);
        this.scanTicks = Math.max(1, scanTicks);
        this.watch = new ClockWatch(clock, System::nanoTime);
    }

    private PermissionStore provider() {
        PermissionStore known = resolved;
        if (known != null) {
            return known;
        }
        synchronized (this) {
            if (resolved == null) {
                resolved = lookup == null ? builtin : resolveProvider(configured, lookup, builtin, log);
            }
            return resolved;
        }
    }

    @Override
    public Snapshot snapshot() {
        Snapshot snapshot = current;
        if (snapshot != null) {
            return snapshot;
        }
        lock.lock();
        try {
            if (current == null) {
                Snapshot loaded = provider().load();
                current = loaded;
                saved = loaded;
                heap.rebuild(loaded);
            }
            return current;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public OperationResult commit(Snapshot next, ChangeBatch batch) {
        if (next == null || batch == null) {
            return OperationResult.success();
        }
        if (onWriterThread.get()
            .booleanValue()) {
            return apply(next, batch);
        }
        CompletableFuture<OperationResult> done = new CompletableFuture<>();
        AtomicBoolean claimed = new AtomicBoolean();
        scheduler.onMainThread(() -> {
            if (!claimed.compareAndSet(false, true)) {
                return;
            }
            onWriterThread.set(Boolean.TRUE);
            try {
                done.complete(apply(next, batch));
            } catch (RuntimeException failure) {
                done.completeExceptionally(failure);
            } finally {
                onWriterThread.set(Boolean.FALSE);
            }
        });
        return await(done, claimed);
    }

    public void sink(Sink registered) {
        sink = registered == null ? Sink.QUIET : registered;
    }

    public ExpiryHeap expiry() {
        return heap;
    }

    void awaitMillis(long millis) {
        awaitMillis = millis;
    }

    public boolean unsaved() {
        Snapshot snapshot = current;
        return !dumpUnsupported && snapshot != null && snapshot.revision() != revisionOf(saved);
    }

    public PermissionStore store() {
        return provider();
    }

    public void start() {
        lock.lock();
        try {
            current = provider().load();
            saved = current;
            heap.rebuild(current);
            watch.check(clock.getAsLong());
            running = true;
        } finally {
            lock.unlock();
        }
        scheduleAutosave();
        scheduleScan();
    }

    public void stop() {
        running = false;
        Snapshot snapshot = current;
        if (unsaved()) {
            write(snapshot);
        }
        Thread worker;
        synchronized (this) {
            worker = writer;
            writer = null;
            notifyAll();
        }
        if (worker != null) {
            worker.interrupt();
        }
    }

    public OperationResult reload(Runnable rereadFiles) {
        lock.lock();
        writeLock.lock();
        try {
            Snapshot pending = current;
            if (unsaved()) {
                OperationResult written = write(pending);
                if (!written.successful()) {
                    return written;
                }
            }
            if (rereadFiles != null) {
                rereadFiles.run();
            }
            Snapshot loaded = provider().load();
            current = loaded;
            saved = loaded;
            heap.rebuild(loaded);
            return OperationResult.success();
        } finally {
            writeLock.unlock();
            lock.unlock();
        }
    }

    public OperationResult scanExpiry() {
        return exclusive(this::scanOnce);
    }

    public void flushNow() {
        Snapshot snapshot = current;
        if (!unsaved()) {
            return;
        }
        exclusive(() -> write(snapshot));
    }

    @Override
    public void flush() {
        Snapshot snapshot = current;
        if (!unsaved()) {
            return;
        }
        if (onWriterThread.get()
            .booleanValue()) {
            write(snapshot);
            return;
        }
        saves.offer(Long.valueOf(snapshot.revision()));
        wake();
    }

    private OperationResult apply(Snapshot next, ChangeBatch batch) {
        OperationResult applied;
        lock.lock();
        try {
            Snapshot live = current;
            if (live != null && next.revision() != live.revision() + 1L) {
                log.warn(
                    "Change was built on revision {} while the model holds {}, it is refused",
                    Long.valueOf(next.revision() - 1L),
                    Long.valueOf(live.revision()));
                return OperationResult.failure(
                    OperationResult.Failure.STALE_SNAPSHOT,
                    "the change was built on revision " + (next.revision() - 1L)
                        + " while the model holds "
                        + live.revision());
            }
            if (watch.check(clock.getAsLong())) {
                log.warn(
                    "System clock jumped by more than {} ms, timed permissions may expire early",
                    Long.valueOf(CLOCK_JUMP_MILLIS));
            }
            OperationResult stored = provider().apply(batch);
            if (!stored.successful()) {
                log.warn(
                    "Provider {} refused the change: {} {}",
                    provider().id(),
                    stored.failure()
                        .orElse(null),
                    stored.message()
                        .orElse(""));
                return stored;
            }
            current = next;
            heap.rebuild(next);
            applied = OperationResult.success();
        } catch (RuntimeException failure) {
            log.error("Change was not applied, the last snapshot stays: {}", failure.toString(), failure);
            return OperationResult.failure(OperationResult.Failure.PROVIDER_FAILED, failure.toString());
        } finally {
            lock.unlock();
        }
        announce(batch, new ArrayList<ChangeEvent>());
        return applied;
    }

    private void announce(ChangeBatch batch, List<ChangeEvent> events) {
        try {
            sink.accept(batch, events);
        } catch (RuntimeException failure) {
            log.error("Change was applied, but the listeners were not told: {}", failure.toString(), failure);
        }
    }

    private OperationResult exclusive(java.util.function.Supplier<OperationResult> action) {
        onWriterThread.set(Boolean.TRUE);
        try {
            return action.get();
        } finally {
            onWriterThread.set(Boolean.FALSE);
        }
    }

    private OperationResult scanOnce() {
        long now = clock.getAsLong();
        if (heap.isEmpty() || heap.nextExpiry() > now) {
            return OperationResult.success();
        }
        return applyExpiry(prune(now));
    }

    private Pruned prune(long now) {
        ExpiryHeap.Prune pruned = heap.prune(current, now);
        if (!pruned.changed()) {
            return null;
        }
        ChangeBatch batch = expiryBatch(pruned.snapshot(), pruned.subjects());
        return new Pruned(pruned.snapshot(), batch, events(pruned.subjects(), batch));
    }

    private OperationResult applyExpiry(Pruned pruned) {
        if (pruned == null) {
            return OperationResult.success();
        }
        lock.lock();
        try {
            Snapshot live = current;
            if (live != null && pruned.snapshot.revision() != live.revision() + 1L) {
                log.warn(
                    "Expiry was built on revision {} while the model holds {}, it waits for the next scan",
                    Long.valueOf(pruned.snapshot.revision() - 1L),
                    Long.valueOf(live.revision()));
                return OperationResult.failure(
                    OperationResult.Failure.STALE_SNAPSHOT,
                    "expiry was built on revision " + (pruned.snapshot.revision() - 1L)
                        + " while the model holds "
                        + live.revision());
            }
            OperationResult stored = provider().apply(pruned.batch);
            if (!stored.successful()) {
                log.warn(
                    "Expiry was not stored: {} {}",
                    stored.failure()
                        .orElse(null),
                    stored.message()
                        .orElse(""));
                return stored;
            }
            current = pruned.snapshot;
            heap.rebuild(current);
        } catch (RuntimeException failure) {
            log.error("Expiry was not applied, the last snapshot stays: {}", failure.toString(), failure);
            return OperationResult.failure(OperationResult.Failure.PROVIDER_FAILED, failure.toString());
        } finally {
            lock.unlock();
        }
        announce(pruned.batch, pruned.events);
        return OperationResult.success();
    }

    private ChangeBatch expiryBatch(Snapshot snapshot, List<ChangeEvent.Subject> subjects) {
        ChangeBatch.Builder builder = ChangeBatch.builder(ChangeCause.EXPIRY, AUTHOR);
        for (ChangeEvent.Subject subject : subjects) {
            if (subject.isGroup()) {
                snapshot.group(subject.groupId())
                    .ifPresent(builder::upsert);
            } else {
                snapshot.user(subject.playerId())
                    .ifPresent(builder::upsert);
            }
        }
        return builder.build();
    }

    private List<ChangeEvent> events(List<ChangeEvent.Subject> subjects, ChangeBatch batch) {
        List<ChangeEvent.Subject> nodes = new ArrayList<>();
        List<ChangeEvent.Subject> membership = new ArrayList<>();
        for (ChangeEvent.Subject subject : subjects) {
            (subject.isPlayer() ? membership : nodes).add(subject);
        }
        List<ChangeEvent> events = new ArrayList<>();
        if (!nodes.isEmpty()) {
            events.add(ChangeEvent.of(ChangeEvent.Kind.NODES, nodes, batch.cause()));
        }
        if (!membership.isEmpty()) {
            events.add(ChangeEvent.of(ChangeEvent.Kind.MEMBERSHIP, membership, batch.cause()));
        }
        return events;
    }

    private OperationResult await(CompletableFuture<OperationResult> done, AtomicBoolean claimed) {
        try {
            return done.get(awaitMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread()
                .interrupt();
            return abandon(done, claimed, "interrupted while waiting");
        } catch (TimeoutException timeout) {
            return abandon(done, claimed, "the main thread did not take the change within " + awaitMillis + " ms");
        } catch (Exception failure) {
            return OperationResult.failure(OperationResult.Failure.PROVIDER_FAILED, failure.toString());
        }
    }

    private OperationResult abandon(CompletableFuture<OperationResult> done, AtomicBoolean claimed, String reason) {
        if (claimed.compareAndSet(false, true)) {
            log.warn("Change was dropped from the queue: {}", reason);
            return OperationResult.failure(OperationResult.Failure.TIMEOUT, reason);
        }
        try {
            return done.get(awaitMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread()
                .interrupt();
            return OperationResult.failure(OperationResult.Failure.PROVIDER_FAILED, "interrupted while waiting");
        } catch (Exception failure) {
            return OperationResult.failure(OperationResult.Failure.PROVIDER_FAILED, failure.toString());
        }
    }

    private void scheduleAutosave() {
        if (running) {
            scheduler.afterTicks(autosaveTicks, this::autosave);
        }
    }

    private void autosave() {
        Snapshot snapshot = current;
        if (unsaved()) {
            saves.offer(Long.valueOf(snapshot.revision()));
            wake();
        }
        scheduleAutosave();
    }

    private void scheduleScan() {
        if (running) {
            scheduler.afterTicks(scanTicks, this::tickScan);
        }
    }

    private void tickScan() {
        try {
            exclusive(this::scanOnce);
        } catch (RuntimeException failure) {
            log.error("Expiry scan failed, the next one still runs: {}", failure.toString(), failure);
        } finally {
            scheduleScan();
        }
    }

    private static long revisionOf(Snapshot snapshot) {
        return snapshot == null ? 0L : snapshot.revision();
    }

    private void wake() {
        synchronized (this) {
            if (writer == null) {
                Thread worker = new Thread(this::drain, WRITER_THREAD);
                worker.setDaemon(true);
                writer = worker;
                worker.start();
            }
            notifyAll();
        }
    }

    private void drain() {
        while (running || !saves.isEmpty()) {
            Long pending = null;
            synchronized (this) {
                while (saves.isEmpty() && running) {
                    try {
                        wait(1000L);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread()
                            .interrupt();
                        return;
                    }
                }
                pending = saves.poll();
            }
            if (pending == null) {
                continue;
            }
            write(current);
        }
    }

    private OperationResult write(Snapshot snapshot) {
        writeLock.lock();
        try {
            OperationResult written = provider().save(snapshot);
            if (written.successful()) {
                saved = snapshot;
                return written;
            }
            if (written.failure()
                .orElse(null) == OperationResult.Failure.UNSUPPORTED) {
                saved = snapshot;
                if (!dumpUnsupported) {
                    dumpUnsupported = true;
                    log.info(
                        "Provider {} keeps the state itself and takes no full dump, the background flush is off",
                        provider().id());
                }
                return OperationResult.success();
            }
            log.error(
                "Snapshot {} was not saved: {} {}",
                Long.valueOf(snapshot.revision()),
                written.failure()
                    .orElse(null),
                written.message()
                    .orElse(""));
            return written;
        } finally {
            writeLock.unlock();
        }
    }

    public interface Lookup {

        Optional<PermissionStore> store(String id);
    }

    public interface Sink {

        Sink QUIET = new Sink() {

            @Override
            public void accept(ChangeBatch batch, List<ChangeEvent> events) {}
        };

        void accept(ChangeBatch batch, List<ChangeEvent> events);
    }

    private static final class Pruned {

        final Snapshot snapshot;
        final ChangeBatch batch;
        final List<ChangeEvent> events;

        Pruned(Snapshot snapshot, ChangeBatch batch, List<ChangeEvent> events) {
            this.snapshot = snapshot;
            this.batch = batch;
            this.events = events;
        }
    }

    static final class ClockWatch {

        private final LongSupplier wall;
        private final LongSupplier monotonic;
        private long lastWall;
        private long lastMonotonic;
        private boolean primed;

        ClockWatch(LongSupplier wall, LongSupplier monotonic) {
            this.wall = wall;
            this.monotonic = monotonic;
        }

        boolean check(long now) {
            long monotonicNow = monotonic.getAsLong();
            if (!primed) {
                primed = true;
                lastWall = now;
                lastMonotonic = monotonicNow;
                return false;
            }
            long expected = lastWall + (monotonicNow - lastMonotonic) / 1_000_000L;
            long drift = now - expected;
            lastWall = now;
            lastMonotonic = monotonicNow;
            return Math.abs(drift) > CLOCK_JUMP_MILLIS;
        }
    }
}
