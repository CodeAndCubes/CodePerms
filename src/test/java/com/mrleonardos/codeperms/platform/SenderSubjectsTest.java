package com.mrleonardos.codeperms.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codecore.api.command.CommandContext;
import com.mrleonardos.codecore.api.command.CommandSender;
import com.mrleonardos.codecore.api.command.SenderKind;
import com.mrleonardos.codeperms.TestSenders;
import com.mrleonardos.codeperms.api.model.Snapshot;

class SenderSubjectsTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void aPlayerIsHisOwnSubject() {
        SenderSubjects subjects = subjects();

        assertEquals(Optional.of(PLAYER), subjects.subjectOf(by(TestSenders.player(PLAYER))));
    }

    @Test
    void whoeverIsNotAPlayerHasNoSubjectOfHisOwn() {
        SenderSubjects subjects = subjects();

        for (SenderKind kind : new SenderKind[] { SenderKind.CONSOLE, SenderKind.RCON, SenderKind.COMMAND_BLOCK }) {
            assertFalse(
                subjects.subjectOf(by(TestSenders.of(kind)))
                    .isPresent(),
                kind + " себя субъектом не назовёт");
        }
    }

    @Test
    void theNameInTheJournalIsTheOneTheSenderCallsHimself() {
        SenderSubjects subjects = subjects();

        assertEquals("Steve", subjects.senderName(by(TestSenders.player(PLAYER))));
        assertEquals("Server", subjects.senderName(by(TestSenders.console())));
    }

    /**
     * Подписки на имена и контексты этим двум методам не нужны, поэтому директория игроков тут пустая, а
     * контекстов нет вовсе: класс спрашивает только отправителя.
     */
    private static SenderSubjects subjects() {
        return new SenderSubjects(new NameResolver(Snapshot::empty), null);
    }

    private static CommandContext by(CommandSender sender) {
        return new CallerOnlyContext(sender);
    }

    /** Подставной контекст: из всего договора здесь нужен один отправитель. */
    private static final class CallerOnlyContext implements CommandContext {

        private final CommandSender caller;

        private CallerOnlyContext(CommandSender caller) {
            this.caller = caller;
        }

        @Override
        public CommandSender caller() {
            return caller;
        }

        @Override
        public ICommandSender sender() {
            throw new UnsupportedOperationException("отправителя спрашивают через caller()");
        }

        @Override
        public EntityPlayerMP player() {
            throw new UnsupportedOperationException("ссылку на игрока спрашивают через caller().player()");
        }

        @Override
        public <T> T get(String name) {
            throw new IllegalArgumentException(name);
        }

        @Override
        public <T> T getOrDefault(String name, T fallback) {
            return fallback;
        }

        @Override
        public boolean has(String name) {
            return false;
        }

        @Override
        public void reply(String translationKey, Object... arguments) {}

        @Override
        public void replyError(String translationKey, Object... arguments) {}
    }
}
