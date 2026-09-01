package com.mrleonardos.codeperms.api.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codeperms.api.model.ChangeCause;
import com.mrleonardos.codeperms.api.model.GroupRecord;
import com.mrleonardos.codeperms.api.model.NodeEntry;
import com.mrleonardos.codeperms.api.model.Snapshot;
import com.mrleonardos.codeperms.api.model.UserRecord;

class StoreTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void batchCarriesCauseAuthorAndChanges() {
        ChangeBatch batch = ChangeBatch.builder(ChangeCause.COMMAND, "console")
            .upsert(group())
            .upsert(player())
            .removeGroup("old")
            .build();
        assertEquals(ChangeCause.COMMAND, batch.cause());
        assertEquals("console", batch.author());
        assertEquals(
            3,
            batch.changes()
                .size());
        assertFalse(batch.isEmpty());
        assertEquals(
            batch,
            ChangeBatch.builder(ChangeCause.COMMAND, "console")
                .upsert(group())
                .upsert(player())
                .removeGroup("old")
                .build());
    }

    @Test
    void changesInsideBatchHoldTogether() {
        ChangeBatch batch = ChangeBatch.builder(ChangeCause.EXPIRY, "codeperms")
            .upsert(group())
            .build();
        assertThrows(
            UnsupportedOperationException.class,
            () -> batch.changes()
                .remove(0));
    }

    @Test
    void emptyBatchIsRefused() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ChangeBatch.builder(ChangeCause.API, "mymod")
                .build());
        assertThrows(NullPointerException.class, () -> ChangeBatch.builder(null, "console"));
        assertThrows(NullPointerException.class, () -> ChangeBatch.builder(ChangeCause.API, null));
    }

    @Test
    void changePointsAtTheSubject() {
        Change group = Change.upsertGroup(group());
        assertEquals(Change.Kind.GROUP, group.kind());
        assertEquals(Change.Operation.UPSERT, group.operation());
        assertEquals("vip", group.subject());
        assertEquals(
            "vip",
            group.group()
                .get()
                .id());
        assertFalse(
            group.player()
                .isPresent());

        Change player = Change.upsertPlayer(player());
        assertEquals(Change.Kind.PLAYER, player.kind());
        assertEquals(PLAYER.toString(), player.subject());
        assertEquals(
            PLAYER,
            player.player()
                .get()
                .uuid());

        Change removed = Change.removeGroup("vip");
        assertEquals(Change.Operation.REMOVE, removed.operation());
        assertFalse(
            removed.group()
                .isPresent());
        assertEquals("REMOVE GROUP vip", removed.toString());
        assertEquals(removed, Change.removeGroup("vip"));
    }

    @Test
    void resultSpeaksWithFiniteReasons() {
        assertTrue(
            OperationResult.success()
                .successful());
        assertFalse(
            OperationResult.success()
                .failure()
                .isPresent());
        assertEquals(9, OperationResult.Failure.values().length);

        OperationResult refusal = OperationResult.failure(OperationResult.Failure.NOT_FOUND, "нет группы vip");
        assertFalse(refusal.successful());
        assertEquals(
            OperationResult.Failure.NOT_FOUND,
            refusal.failure()
                .get());
        assertEquals(
            "нет группы vip",
            refusal.message()
                .get());
        assertEquals("NOT_FOUND: нет группы vip", refusal.toString());
        assertEquals(refusal, OperationResult.failure(OperationResult.Failure.NOT_FOUND, "нет группы vip"));
        assertThrows(NullPointerException.class, () -> OperationResult.failure(null, "причина"));
    }

    @Test
    void defaultSaveSpeaksOfUnsupported() {
        StubStore store = new StubStore();
        assertEquals("stub", store.id());
        assertEquals(Snapshot.empty(), store.load());
        assertTrue(
            store.apply(
                ChangeBatch.builder(ChangeCause.API, "mymod")
                    .upsert(group())
                    .build())
                .successful());

        OperationResult save = store.save(Snapshot.empty());
        assertFalse(save.successful());
        assertEquals(
            OperationResult.Failure.UNSUPPORTED,
            save.failure()
                .get());
        assertTrue(
            save.message()
                .get()
                .contains("stub"));
    }

    private static GroupRecord group() {
        return GroupRecord.of(
            "vip",
            "VIP",
            10,
            new ArrayList<String>(),
            new ArrayList<NodeEntry>(),
            new java.util.LinkedHashMap<String, String>());
    }

    private static UserRecord player() {
        return UserRecord.of(
            PLAYER,
            "Steve",
            null,
            new ArrayList<UserRecord.Grant>(),
            new ArrayList<NodeEntry>(),
            new java.util.LinkedHashMap<String, String>());
    }

    /** Пустое хранилище, чтобы проверить поведение по умолчанию. */
    static final class StubStore implements PermissionStore {

        @Override
        public String id() {
            return "stub";
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
}
