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
import com.mrleonardos.codeperms.internal.MainSettings;
import com.mrleonardos.codeperms.internal.PermsSettings;
import com.mrleonardos.codeperms.internal.admin.ChangeCoalescer;
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
        PlatformMaintenance maintenance = maintenance(writer(store), coreFile);

        OperationResult result = maintenance.importFromCore(false, false);

        assertFalse(result.successful());
        assertFalse(marked(coreFile), "маркер обязан появиться только после удачного коммита");
        coalescer.dispatch();
        assertTrue(listener.events.isEmpty(), "события правок, которых не было: " + listener.events);
    }

    @Test
    void landedImportMarksTheCoreFileAndTellsTheListeners() throws Exception {
        Path coreFile = writeCoreFile();
        PlatformMaintenance maintenance = maintenance(writer(new RefusingStore()), coreFile);

        OperationResult result = maintenance.importFromCore(false, false);

        assertTrue(result.successful());
        assertTrue(marked(coreFile));
        coalescer.dispatch();
        assertFalse(listener.events.isEmpty());
    }

    @Test
    void dryRunTouchesNothing() throws Exception {
        Path coreFile = writeCoreFile();
        PlatformMaintenance maintenance = maintenance(writer(new RefusingStore()), coreFile);

        OperationResult result = maintenance.importFromCore(true, false);

        assertTrue(result.successful());
        assertFalse(marked(coreFile));
        coalescer.dispatch();
        assertTrue(listener.events.isEmpty());
    }

    @Test
    void reloadKeepsWorkThatWasNotFlushedYet() throws Exception {
        PermissionStore builtin = new JsonPermissionStore(
            settings,
            new MainSettings(configs),
            groups,
            players,
            PermsLimits.defaults(),
            LOG);
        SingleWriterImpl writer = writer(builtin);
        writer.commit(withGroup(writer.snapshot(), "vip"), batch("vip"));
        assertTrue(writer.unsaved());
        PlatformMaintenance maintenance = maintenance(writer, writeCoreFile());

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
            new GroupsStore(groups, PermsLimits.defaults(), LOG).load()
                .groups()
                .get(2)
                .id());
    }

    private PlatformMaintenance maintenance(SingleWriterImpl writer, Path coreFile) {
        return new PlatformMaintenance(
            settings,
            groups,
            players,
            writer,
            new CoreGroupsImporter(configs, PermsLimits.defaults(), LOG),
            new DefaultNodes(settings, PermsLimits.defaults(), LOG),
            coalescer);
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
