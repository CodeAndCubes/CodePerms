package com.mrleonardos.codeperms.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mrleonardos.codeperms.api.context.ContextRegistry;
import com.mrleonardos.codeperms.api.manage.PermissionsEvents;
import com.mrleonardos.codeperms.api.manage.PermissionsListener;
import com.mrleonardos.codeperms.api.manage.PermsAdmin;
import com.mrleonardos.codeperms.api.model.ChangeCause;
import com.mrleonardos.codeperms.api.model.ContextSet;
import com.mrleonardos.codeperms.api.model.NodeEntry;
import com.mrleonardos.codeperms.api.model.Snapshot;
import com.mrleonardos.codeperms.api.resolve.Resolution;
import com.mrleonardos.codeperms.api.store.ChangeBatch;
import com.mrleonardos.codeperms.api.store.OperationResult;
import com.mrleonardos.codeperms.api.store.PermissionStore;

class PermsApiTest {

    @BeforeEach
    void setUp() {
        PermsApi.reopen();
    }

    @AfterEach
    void tearDown() {
        PermsApi.reopen();
    }

    @Test
    void registriesAreShared() {
        assertSame(PermsApi.contexts(), PermsApi.contexts());
        assertSame(PermsApi.catalog(), PermsApi.catalog());
        assertTrue(PermsApi.contexts() instanceof ContextRegistry);
    }

    @Test
    void storesAreRegisteredById() {
        StubStore sql = new StubStore("sql");
        StubStore other = new StubStore("mongo");

        PermsApi.registerStore(sql);
        PermsApi.registerStore(other);
        PermsApi.registerStore(sql);

        assertSame(
            sql,
            PermsApi.store("sql")
                .get());
        assertSame(
            other,
            PermsApi.store("mongo")
                .get());
        assertEquals(
            2,
            PermsApi.stores()
                .size());
        assertEquals(
            "mongo",
            PermsApi.stores()
                .get(0)
                .id());
        assertEquals(
            "sql",
            PermsApi.stores()
                .get(1)
                .id());
    }

    @Test
    void takenStoreNameIsRefused() {
        PermsApi.registerStore(new StubStore("sql"));

        assertThrows(IllegalArgumentException.class, () -> PermsApi.registerStore(new StubStore("sql")));
    }

    @Test
    void storeRegisteredAfterTheServerStartIsRefused() {
        PermsApi.freeze();

        assertTrue(PermsApi.frozen());
        assertTrue(
            PermsApi.contexts()
                .frozen());
        assertThrows(IllegalStateException.class, () -> PermsApi.registerStore(new StubStore("sql")));
        assertTrue(
            PermsApi.stores()
                .isEmpty());
    }

    @Test
    void unknownStoreIsAbsent() {
        assertEquals(
            "absent",
            PermsApi.store("absent")
                .orElse(new StubStore("absent"))
                .id());
    }

    @Test
    void adminAppearsOnlyAfterInstall() {
        PermsAdmin installedAdmin = new StubAdmin();
        StubEvents installedEvents = new StubEvents();
        PermsApi.install(installedAdmin, installedEvents);
        assertSame(installedAdmin, PermsApi.admin());
        assertSame(installedEvents, PermsApi.events());
    }

    @Test
    void nullInstallIsRefused() {
        assertThrows(NullPointerException.class, () -> PermsApi.install(null, new StubEvents()));
        assertThrows(NullPointerException.class, () -> PermsApi.install(new StubAdmin(), null));
    }

    /** Хранилище-заглушка для проверки реестра по имени. */
    static final class StubStore implements PermissionStore {

        private final String id;

        StubStore(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Snapshot load() {
            return Snapshot.empty();
        }

        @Override
        public OperationResult apply(ChangeBatch batch) {
            return OperationResult.success();
        }
    }

    /** Администратор-заглушка: проверяется сама установка, а не поведение правок. */
    static final class StubAdmin implements PermsAdmin {

        @Override
        public OperationResult createGroup(String id, String displayName, int weight, ChangeCause cause,
            String author) {
            return OperationResult.success();
        }

        @Override
        public OperationResult deleteGroup(String id, ChangeCause cause, String author) {
            return OperationResult.success();
        }

        @Override
        public OperationResult renameGroup(String id, String newId, ChangeCause cause, String author) {
            return OperationResult.success();
        }

        @Override
        public OperationResult copyGroup(String source, String target, ChangeCause cause, String author) {
            return OperationResult.success();
        }

        @Override
        public OperationResult setWeight(String groupId, int weight, ChangeCause cause, String author) {
            return OperationResult.success();
        }

        @Override
        public OperationResult addParent(String groupId, String parentId, ChangeCause cause, String author) {
            return OperationResult.success();
        }

        @Override
        public OperationResult removeParent(String groupId, String parentId, ChangeCause cause, String author) {
            return OperationResult.success();
        }

        @Override
        public OperationResult setGroupNode(String groupId, NodeEntry entry, ChangeCause cause, String author) {
            return OperationResult.success();
        }

        @Override
        public OperationResult removeGroupNode(String groupId, String node, ChangeCause cause, String author) {
            return OperationResult.success();
        }

        @Override
        public OperationResult setGroupMeta(String groupId, String key, String value, ChangeCause cause,
            String author) {
            return OperationResult.success();
        }

        @Override
        public OperationResult removeGroupMeta(String groupId, String key, ChangeCause cause, String author) {
            return OperationResult.success();
        }

        @Override
        public OperationResult setPlayerNode(UUID player, NodeEntry entry, ChangeCause cause, String author) {
            return OperationResult.success();
        }

        @Override
        public OperationResult removePlayerNode(UUID player, String node, ChangeCause cause, String author) {
            return OperationResult.success();
        }

        @Override
        public OperationResult setPlayerMeta(UUID player, String key, String value, ChangeCause cause, String author) {
            return OperationResult.success();
        }

        @Override
        public OperationResult removePlayerMeta(UUID player, String key, ChangeCause cause, String author) {
            return OperationResult.success();
        }

        @Override
        public OperationResult setPrimaryGroup(UUID player, String groupId, ChangeCause cause, String author) {
            return OperationResult.success();
        }

        @Override
        public OperationResult addPlayerGroup(UUID player, String groupId, long expiresAt, ChangeCause cause,
            String author) {
            return OperationResult.success();
        }

        @Override
        public OperationResult removePlayerGroup(UUID player, String groupId, ChangeCause cause, String author) {
            return OperationResult.success();
        }

        @Override
        public OperationResult movePlayerGroup(UUID player, String fromGroupId, String toGroupId, ChangeCause cause,
            String author) {
            return OperationResult.success();
        }

        @Override
        public OperationResult cleanupPlayer(UUID player, ChangeCause cause, String author) {
            return OperationResult.success();
        }

        @Override
        public List<String> metaStack(UUID player, String key) {
            return new ArrayList<>();
        }

        @Override
        public Resolution explain(UUID player, String node, ContextSet contexts) {
            return Resolution.none();
        }
    }

    /** Реестр слушателей-заглушка. */
    static final class StubEvents implements PermissionsEvents {

        @Override
        public void register(int priority, PermissionsListener listener) {}

        @Override
        public void unregister(PermissionsListener listener) {}

        @Override
        public List<PermissionsListener> listeners() {
            return new ArrayList<>();
        }
    }
}
