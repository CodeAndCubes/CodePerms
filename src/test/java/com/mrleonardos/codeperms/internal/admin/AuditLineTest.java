package com.mrleonardos.codeperms.internal.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codeperms.api.model.ChangeCause;
import com.mrleonardos.codeperms.api.model.GroupRecord;
import com.mrleonardos.codeperms.api.model.NodeEntry;
import com.mrleonardos.codeperms.api.store.ChangeBatch;

class AuditLineTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void theLineNamesTheCauseTheAuthorAndHowManyChangesLanded() {
        ChangeBatch batch = ChangeBatch.builder(ChangeCause.COMMAND, "Steve")
            .upsert(group("vip"))
            .upsert(group("moderator"))
            .build();

        assertEquals("COMMAND by Steve: 2 changes", AuditLine.of(batch));
    }

    @Test
    void theLineHoldsItsShapeForEveryCauseAndForOnePlayerChange() {
        ChangeBatch batch = ChangeBatch.builder(ChangeCause.API, "myclan")
            .removePlayer(PLAYER)
            .build();

        assertEquals("API by myclan: 1 changes", AuditLine.of(batch));
    }

    @Test
    void everyCauseIsSpelledInTheLineTheWayTheAdminGrepsIt() {
        assertEquals("COMMAND by console: 1 changes", line(ChangeCause.COMMAND));
        assertEquals("API by console: 1 changes", line(ChangeCause.API));
        assertEquals("IMPORT by console: 1 changes", line(ChangeCause.IMPORT));
        assertEquals("EXPIRY by console: 1 changes", line(ChangeCause.EXPIRY));
        assertEquals("MIGRATION by console: 1 changes", line(ChangeCause.MIGRATION));
        assertEquals(
            5,
            ChangeCause.values().length,
            "причин стало больше: допишите новую сюда, её имя попадает в аудит на чужих серверах");
    }

    private static String line(ChangeCause cause) {
        return AuditLine.of(
            ChangeBatch.builder(cause, "console")
                .removePlayer(PLAYER)
                .build());
    }

    private static GroupRecord group(String id) {
        return GroupRecord.of(
            id,
            id,
            0,
            Collections.<String>emptyList(),
            Collections.<NodeEntry>emptyList(),
            Collections.<String, String>emptyMap());
    }
}
