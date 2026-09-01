package com.mrleonardos.codeperms.internal.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codeperms.api.manage.ChangeEvent;
import com.mrleonardos.codeperms.api.model.ContextSet;
import com.mrleonardos.codeperms.api.model.GroupRecord;
import com.mrleonardos.codeperms.api.model.NodeEntry;
import com.mrleonardos.codeperms.api.model.Snapshot;
import com.mrleonardos.codeperms.api.model.UserRecord;

class ExpiryHeapTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000005");

    @Test
    void emptyHeapCostsNothing() {
        ExpiryHeap heap = new ExpiryHeap();

        heap.rebuild(Snapshot.empty());

        assertTrue(heap.isEmpty());
        assertEquals(Long.MAX_VALUE, heap.nextExpiry());
    }

    @Test
    void nextExpiryNamesTheEarliestMark() {
        ExpiryHeap heap = new ExpiryHeap();
        heap.rebuild(
            Snapshot.builder()
                .revision(1L)
                .user(userWithTimedEntries(7000L, 5000L))
                .build());

        assertFalse(heap.isEmpty());
        assertEquals(5000L, heap.nextExpiry());

        heap.rebuild(Snapshot.empty());

        assertTrue(heap.isEmpty());
        assertEquals(Long.MAX_VALUE, heap.nextExpiry());
    }

    @Test
    void permanentEntriesLeaveTheHeapEmpty() {
        ExpiryHeap heap = new ExpiryHeap();
        heap.rebuild(
            Snapshot.builder()
                .revision(1L)
                .group(
                    GroupRecord.of(
                        "player",
                        "player",
                        0,
                        Collections.<String>emptyList(),
                        Arrays.asList(NodeEntry.parse("codechat.create")),
                        Collections.emptyMap()))
                .build());

        assertTrue(heap.isEmpty());
        assertEquals(Long.MAX_VALUE, heap.nextExpiry());
    }

    @Test
    void pruneKeepsSnapshotWhenNothingExpired() {
        ExpiryHeap heap = new ExpiryHeap();
        Snapshot snapshot = snapshotWithTimedNode(5000L);

        ExpiryHeap.Prune pruned = heap.prune(snapshot, 4000L);

        assertFalse(pruned.changed());
        assertSame(snapshot, pruned.snapshot());
        assertEquals(
            1,
            pruned.snapshot()
                .user(PLAYER)
                .get()
                .nodes()
                .size());
    }

    @Test
    void pruneDropsExpiredNodesAndGrants() {
        Snapshot snapshot = Snapshot.builder()
            .revision(4L)
            .user(userWithTimedEntries(100L, 200L))
            .build();
        ExpiryHeap heap = new ExpiryHeap();
        heap.rebuild(snapshot);

        ExpiryHeap.Prune pruned = heap.prune(snapshot, 500L);

        assertTrue(pruned.changed());
        assertEquals(Collections.singletonList(ChangeEvent.Subject.player(PLAYER)), pruned.subjects());
        assertTrue(
            pruned.snapshot()
                .user(PLAYER)
                .get()
                .nodes()
                .isEmpty());
        assertEquals(
            1,
            pruned.snapshot()
                .user(PLAYER)
                .get()
                .groups()
                .size());
        assertEquals(
            "player",
            pruned.snapshot()
                .user(PLAYER)
                .get()
                .groups()
                .get(0)
                .groupId());
    }

    private Snapshot snapshotWithTimedNode(long expiresAt) {
        return Snapshot.builder()
            .revision(1L)
            .user(userWithTimedEntries(expiresAt, expiresAt))
            .build();
    }

    private UserRecord userWithTimedEntries(long nodeExpiry, long grantExpiry) {
        return UserRecord.of(
            PLAYER,
            "Steve",
            null,
            Arrays.asList(UserRecord.Grant.permanent("player"), UserRecord.Grant.of("vip", grantExpiry)),
            Arrays.asList(NodeEntry.of("codechat.muted", false, ContextSet.empty(), nodeExpiry)),
            Collections.emptyMap());
    }
}
