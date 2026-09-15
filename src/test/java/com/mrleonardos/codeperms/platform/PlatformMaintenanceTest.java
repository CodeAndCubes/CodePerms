package com.mrleonardos.codeperms.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;
import com.mrleonardos.codecore.api.config.ConfigFile;
import com.mrleonardos.codecore.api.config.ConfigService;
import com.mrleonardos.codecore.api.util.Scheduler;
import com.mrleonardos.codeperms.TestConfigs;
import com.mrleonardos.codeperms.api.PermsLimits;
import com.mrleonardos.codeperms.api.manage.ChangeEvent;
import com.mrleonardos.codeperms.api.manage.PermissionsListener;
import com.mrleonardos.codeperms.api.model.ChangeCause;
import com.mrleonardos.codeperms.api.model.GroupRecord;
import com.mrleonardos.codeperms.api.model.NodeEntry;
import com.mrleonardos.codeperms.api.model.Snapshot;
import com.mrleonardos.codeperms.api.store.ChangeBatch;
import com.mrleonardos.codeperms.api.store.OperationResult;
import com.mrleonardos.codeperms.api.store.PermissionStore;
import com.mrleonardos.codeperms.internal.Ceilings;
import com.mrleonardos.codeperms.internal.MainSettings;
import com.mrleonardos.codeperms.internal.PermsSettings;
import com.mrleonardos.codeperms.internal.admin.ChangeCoalescer;
import com.mrleonardos.codeperms.internal.admin.PermsAdminImpl;
import com.mrleonardos.codeperms.internal.engine.ResolverImpl;
import com.mrleonardos.codeperms.internal.store.CoreGroupsImporter;
import com.mrleonardos.codeperms.internal.store.GroupsStore;
import com.mrleonardos.codeperms.internal.store.JsonPermissionStore;
import com.mrleonardos.codeperms.internal.store.PermsGroupsFile;
import com.mrleonardos.codeperms.internal.store.PlayersStore;
import com.mrleonardos.codeperms.internal.store.SingleWriterImpl;

class PlatformMaintenanceTest {

    private static final Logger LOG = LogManager.getLogger(PlatformMaintenanceTest.class);
    private static final String[] CORE_FILE = { "[groups.player]", "nodes = [\"codechat.create\"]" };

    @TempDir
    Path root;

    private ConfigService configs;
    private ConfigFile<PermsSettings> settings;
    private ConfigFile<PermsGroupsFile> groups;
    private ConfigFile<JsonObject> players;
    private ChangeCoalescer coalescer;
    private RecordingListener listener;

    @BeforeEach
    void setUp() {
        configs = TestConfigs.of(root);
        settings = configs.open(PermsSettings.spec());
        groups = configs.open(GroupsStore.spec());
        players = configs.open(PlayersStore.spec());
        coalescer = new ChangeCoalescer(LOG);
        listener = new RecordingListener();
        coalescer.register(0, listener);
    }

    @Test
    void refusedImportLeavesNoMarkerAndNoEvents() throws Exception {
        Path coreFile = writeCoreFile();
        RefusingStore store = new RefusingStore();
        store.refuse = true;
        PlatformMaintenance maintenance = maintenance(writer(store), coreFile, new Ceilings(settings, LOG));

        OperationResult result = maintenance.importFromCore(false, false).result;

        assertFalse(result.successful());
        assertFalse(marked(coreFile), "маркер обязан появиться только после удачного коммита");
        coalescer.dispatch();
        assertTrue(listener.events.isEmpty(), "события правок, которых не было: " + listener.events);
    }

    @Test
    void landedImportMarksTheCoreFileAndTellsTheListeners() throws Exception {
        Path coreFile = writeCoreFile();
        PlatformMaintenance maintenance = maintenance(
            writer(new RefusingStore()),
            coreFile,
            new Ceilings(settings, LOG));

        OperationResult result = maintenance.importFromCore(false, false).result;

        assertTrue(result.successful());
        assertTrue(marked(coreFile));
        coalescer.dispatch();
        assertFalse(listener.events.isEmpty());
    }

    @Test
    void dryRunTouchesNothing() throws Exception {
        Path coreFile = writeCoreFile();
        PlatformMaintenance maintenance = maintenance(
            writer(new RefusingStore()),
            coreFile,
            new Ceilings(settings, LOG));

        OperationResult result = maintenance.importFromCore(true, false).result;

        assertTrue(result.successful());
        assertFalse(marked(coreFile));
        coalescer.dispatch();
        assertTrue(listener.events.isEmpty());
    }

    @Test
    void reloadKeepsWorkThatWasNotFlushedYet() throws Exception {
        PermissionStore builtin = new JsonPermissionStore(
            new MainSettings(configs),
            groups,
            players,
            () -> PermsLimits.defaults(),
            LOG);
        SingleWriterImpl writer = writer(builtin);
        writer.commit(withGroup(writer.snapshot(), "vip"), batch("vip"));
        assertTrue(writer.unsaved());
        PlatformMaintenance maintenance = maintenance(writer, writeCoreFile(), new Ceilings(settings, LOG));

        OperationResult result = maintenance.reload();

        assertTrue(result.successful());
        assertFalse(writer.unsaved());
        assertTrue(
            writer.snapshot()
                .group("vip")
                .isPresent(),
            "перечитывание не имеет права терять несохранённое");
        assertEquals(
            "vip",
            new GroupsStore(groups, () -> PermsLimits.defaults(), LOG).load()
                .groups()
                .get(2)
                .id());
    }

    @Test
    void reloadPicksUpNewCeilingsForTheStoresAndTheAdmin() throws Exception {
        Ceilings ceilings = new Ceilings(settings, LOG);
        PermissionStore builtin = new JsonPermissionStore(new MainSettings(configs), groups, players, ceilings, LOG);
        SingleWriterImpl writer = writer(builtin);
        assertEquals(
            2,
            writer.snapshot()
                .groups()
                .size());
        Path file = TestConfigs.permissions(root)
            .resolve("perms.toml");
        String text = TestConfigs.read(file);
        assertTrue(text.contains("groups = 512"), "ожидалось заводское значение потолка:\n" + text);
        TestConfigs.write(file, text.replace("groups = 512", "groups = 1"));
        PlatformMaintenance maintenance = maintenance(writer, writeCoreFile(), ceilings);
        PermsAdminImpl admin = new PermsAdminImpl(writer, coalescer, ceilings, new ResolverImpl());

        OperationResult result = maintenance.reload();

        assertTrue(result.successful());
        assertEquals(
            1,
            writer.snapshot()
                .groups()
                .size(),
            "потолок групп обязан ужаться после reload, а не ждать перезапуска");
        assertEquals(
            OperationResult.Failure.LIMIT_REACHED,
            admin.createGroup("mod", "", 0, ChangeCause.COMMAND, "console")
                .failure()
                .get(),
            "админ спрашивает потолки у того же источника, что и сторы");
    }

    @Test
    void unreadableSourceAnswersItsOwnFailure() throws Exception {
        Path coreFile = writeCoreFile();
        TestConfigs.write(coreFile, "[groups.player", "nodes = []");
        PlatformMaintenance maintenance = maintenance(
            writer(new RefusingStore()),
            coreFile,
            new Ceilings(settings, LOG));

        OperationResult result = maintenance.importFromCore(false, false).result;

        assertEquals(
            OperationResult.Failure.UNREADABLE_SOURCE,
            result.failure()
                .get());
        assertEquals(
            coreFile.toString(),
            result.message()
                .get());
    }

    private PlatformMaintenance maintenance(SingleWriterImpl writer, Path coreFile, Ceilings ceilings) {
        return new PlatformMaintenance(
            settings,
            groups,
            players,
            writer,
            new CoreGroupsImporter(configs, ceilings, LOG),
            new DefaultNodes(settings, ceilings, LOG),
            coalescer,
            ceilings);
    }

    private SingleWriterImpl writer(PermissionStore store) {
        SingleWriterImpl writer = new SingleWriterImpl(
            store,
            new InlineScheduler(),
            LOG,
            System::currentTimeMillis,
            20,
            200);
        writer.start();
        return writer;
    }

    private Path writeCoreFile() {
        Path coreFile = TestConfigs.permissions(root)
            .resolve(CoreGroupsImporter.CORE_FILE);
        TestConfigs.write(coreFile, CORE_FILE);
        return coreFile;
    }

    private static boolean marked(Path coreFile) {
        return TestConfigs.read(coreFile)
            .contains(CoreGroupsImporter.MARKER_FIELD);
    }

    private static Snapshot withGroup(Snapshot current, String groupId) {
        return Snapshot.builder()
            .revision(current.revision() + 1L)
            .defaultGroup(current.defaultGroup())
            .opGroup(current.opGroup())
            .from(current)
            .group(
                GroupRecord.of(
                    groupId,
                    groupId,
                    10,
                    new ArrayList<String>(),
                    new ArrayList<NodeEntry>(),
                    new java.util.LinkedHashMap<String, String>()))
            .build();
    }

    private static ChangeBatch batch(String groupId) {
        return ChangeBatch.builder(ChangeCause.COMMAND, "console")
            .upsert(
                GroupRecord.of(
                    groupId,
                    groupId,
                    10,
                    new ArrayList<String>(),
                    new ArrayList<NodeEntry>(),
                    new java.util.LinkedHashMap<String, String>()))
            .build();
    }

    private static final class InlineScheduler implements Scheduler {

        @Override
        public void onMainThread(Runnable task) {
            task.run();
        }

        @Override
        public void afterTicks(int ticks, Runnable task) {}
    }

    private static final class RefusingStore implements PermissionStore {

        private boolean refuse;

        @Override
        public String id() {
            return "json";
        }

        @Override
        public Snapshot load() {
            return Snapshot.builder()
                .revision(1L)
                .defaultGroup("player")
                .build();
        }

        @Override
        public OperationResult apply(ChangeBatch batch) {
            if (refuse) {
                return OperationResult.failure(OperationResult.Failure.PROVIDER_FAILED, "sql is down");
            }
            return OperationResult.success();
        }

        @Override
        public OperationResult save(Snapshot snapshot) {
            return OperationResult.success();
        }
    }

    private static final class RecordingListener implements PermissionsListener {

        private final List<ChangeEvent> events = new ArrayList<>();

        @Override
        public void onChange(ChangeEvent event) {
            events.add(event);
        }
    }
}
