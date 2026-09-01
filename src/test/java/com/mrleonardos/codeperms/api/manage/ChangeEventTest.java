package com.mrleonardos.codeperms.api.manage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codeperms.api.model.ChangeCause;

class ChangeEventTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000004");

    @Test
    void subjectTellsGroupFromPlayer() {
        ChangeEvent.Subject group = ChangeEvent.Subject.group("vip");
        assertTrue(group.isGroup());
        assertFalse(group.isPlayer());
        assertEquals("vip", group.groupId());
        assertNull(group.playerId());
        assertEquals("vip", group.key());

        ChangeEvent.Subject player = ChangeEvent.Subject.player(PLAYER);
        assertTrue(player.isPlayer());
        assertFalse(player.isGroup());
        assertEquals(PLAYER, player.playerId());
        assertNull(player.groupId());
        assertEquals(PLAYER.toString(), player.key());
    }

    @Test
    void groupAndPlayerStayDifferentSubjects() {
        ChangeEvent.Subject group = ChangeEvent.Subject.group(PLAYER.toString());
        ChangeEvent.Subject player = ChangeEvent.Subject.player(PLAYER);
        assertFalse(group.equals(player));
        assertFalse(player.equals(group));
        assertEquals(group, ChangeEvent.Subject.group(PLAYER.toString()));
        assertEquals(
            group.hashCode(),
            ChangeEvent.Subject.group(PLAYER.toString())
                .hashCode());
        assertThrows(NullPointerException.class, () -> ChangeEvent.Subject.group(null));
        assertThrows(NullPointerException.class, () -> ChangeEvent.Subject.player(null));
    }

    @Test
    void eventKeepsKindCauseAndSubjects() {
        ChangeEvent event = ChangeEvent.of(
            ChangeEvent.Kind.MEMBERSHIP,
            Arrays.asList(ChangeEvent.Subject.player(PLAYER), ChangeEvent.Subject.player(PLAYER)),
            ChangeCause.COMMAND);
        assertEquals(ChangeEvent.Kind.MEMBERSHIP, event.kind());
        assertEquals(ChangeCause.COMMAND, event.cause());
        assertEquals(
            1,
            event.subjects()
                .size());
        assertEquals(
            1,
            event.subjectKeys()
                .size());
        assertEquals("MEMBERSHIP [" + PLAYER + "] by COMMAND", event.toString());
        assertThrows(
            UnsupportedOperationException.class,
            () -> event.subjects()
                .add(ChangeEvent.Subject.group("vip")));
    }

    @Test
    void eventWithoutSubjectsIsRefused() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ChangeEvent.of(ChangeEvent.Kind.NODES, new ArrayList<ChangeEvent.Subject>(), ChangeCause.API));
        assertThrows(
            NullPointerException.class,
            () -> ChangeEvent.of(null, Arrays.asList(ChangeEvent.Subject.group("vip")), ChangeCause.API));
        assertThrows(
            NullPointerException.class,
            () -> ChangeEvent.of(ChangeEvent.Kind.NODES, Arrays.asList(ChangeEvent.Subject.group("vip")), null));
    }

    @Test
    void kindsCoverTheWholeModel() {
        assertEquals(4, ChangeEvent.Kind.values().length);
        List<String> keys = new ArrayList<>();
        for (ChangeEvent.Kind kind : ChangeEvent.Kind.values()) {
            keys.add(kind.name());
        }
        assertTrue(keys.contains("NODES"));
        assertTrue(keys.contains("GROUPS"));
        assertTrue(keys.contains("META"));
        assertTrue(keys.contains("MEMBERSHIP"));
    }
}
