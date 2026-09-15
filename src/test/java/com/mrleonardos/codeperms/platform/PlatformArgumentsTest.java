package com.mrleonardos.codeperms.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.mrleonardos.codecore.api.command.CommandInputException;
import com.mrleonardos.codecore.api.command.CommandSender;
import com.mrleonardos.codeperms.TestSenders;
import com.mrleonardos.codeperms.api.PermsApi;
import com.mrleonardos.codeperms.api.PermsLimits;
import com.mrleonardos.codeperms.api.model.GroupRecord;
import com.mrleonardos.codeperms.api.model.NodeEntry;
import com.mrleonardos.codeperms.api.model.Snapshot;
import com.mrleonardos.codeperms.api.model.TrackRecord;
import com.mrleonardos.codeperms.api.model.UserRecord;
import com.mrleonardos.codeperms.internal.command.PermsArguments;
import com.mrleonardos.codeperms.internal.command.PermsMessages;

class PlatformArgumentsTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String CATALOG_NODE = "codeperms.test.suggest";
    private static final CommandSender CONSOLE = TestSenders.console();

    @AfterEach
    void tearDown() {
        PermsApi.catalog()
            .unregister(CATALOG_NODE);
    }

    @Test
    void everyArgumentTypeStillAnswersWithSuggestions() {
        PermsApi.catalog()
            .register(CATALOG_NODE, "codeperms.test.description");
        PermsArguments arguments = arguments();

        assertFalse(
            arguments.groupId()
                .suggestions(CONSOLE, "mo")
                .isEmpty(),
            "подсказки группы");
        assertFalse(
            arguments.trackName()
                .suggestions(CONSOLE, "st")
                .isEmpty(),
            "подсказки трека");
        assertFalse(
            arguments.player()
                .suggestions(CONSOLE, "st")
                .isEmpty(),
            "подсказки игрока");
        assertFalse(
            arguments.node()
                .suggestions(CONSOLE, "codeperms.test")
                .isEmpty(),
            "подсказки ноды");
        assertFalse(
            arguments.expiry()
                .suggestions(CONSOLE, "pe")
                .isEmpty(),
            "подсказки срока");
    }

    @Test
    void suggestionsNarrowDownByThePrefix() {
        PermsArguments arguments = arguments();

        assertEquals(
            Collections.singletonList("moderator"),
            arguments.groupId()
                .suggestions(CONSOLE, "mod"));
        assertTrue(
            arguments.groupId()
                .suggestions(CONSOLE, "zzz")
                .isEmpty());
    }

    @Test
    void whatDoesNotFitTheCeilingComesBackAsAnInputError() {
        PermsArguments arguments = arguments();
        String tooLong = repeat('g', PermsLimits.DEFAULT_GROUP_ID_LENGTH + 1);

        CommandInputException failure = assertThrows(
            CommandInputException.class,
            () -> arguments.groupId()
                .parse(tooLong));

        assertEquals(PermsMessages.FAILURE_LIMIT_REACHED, failure.translationKey());
        assertEquals(tooLong, failure.arguments()[0]);
    }

    @Test
    void unknownPlayerComesBackAsAnInputError() {
        PermsArguments arguments = arguments();

        CommandInputException failure = assertThrows(
            CommandInputException.class,
            () -> arguments.player()
                .parse("nobody"));

        assertEquals(PermsMessages.FAILURE_NOT_FOUND, failure.translationKey());
        assertEquals("nobody", failure.arguments()[0]);
    }

    private static PermsArguments arguments() {
        Snapshot snapshot = snapshot();
        return new PlatformArguments(new NameResolver(() -> snapshot), () -> snapshot, () -> PermsLimits.defaults());
    }

    private static Snapshot snapshot() {
        return Snapshot.builder()
            .group(group("moderator"))
            .group(group("player"))
            .track(TrackRecord.of("staff", Arrays.asList("player", "moderator")))
            .user(
                UserRecord.of(
                    PLAYER,
                    "Steve",
                    "player",
                    Collections.<UserRecord.Grant>emptyList(),
                    Collections.<NodeEntry>emptyList(),
                    new LinkedHashMap<String, String>()))
            .build();
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

    private static String repeat(char letter, int times) {
        char[] filled = new char[times];
        Arrays.fill(filled, letter);
        return new String(filled);
    }
}
