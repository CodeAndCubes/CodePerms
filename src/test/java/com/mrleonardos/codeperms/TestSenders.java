package com.mrleonardos.codeperms;

import java.util.Optional;
import java.util.UUID;

import com.mrleonardos.codecore.api.actor.PlayerRef;
import com.mrleonardos.codecore.api.command.CommandSender;
import com.mrleonardos.codecore.api.command.SenderKind;
import com.mrleonardos.codecore.api.command.SenderPosition;

/**
 * Подставные отправители команды: вид и ссылка на игрока, ни одного типа игры.
 *
 * <p>
 * Лежит рядом с {@link TestConfigs}, а не в тестах одного пакета, потому что отправитель нужен и слою
 * платформы, и подставному контексту команд. Две копии одной заглушки разъезжаются молча.
 */
public final class TestSenders {

    private TestSenders() {}

    /** Консоль сервера: ссылки на игрока у неё нет. */
    public static CommandSender console() {
        return of(SenderKind.CONSOLE);
    }

    /** Отправитель заданного вида без ссылки на игрока. */
    public static CommandSender of(SenderKind kind) {
        return new StubSender(kind, null);
    }

    /** Игрок под ником Steve. */
    public static CommandSender player(UUID id) {
        return new StubSender(SenderKind.PLAYER, PlayerRef.of(id, "Steve"));
    }

    private static final class StubSender implements CommandSender {

        private final SenderKind kind;
        private final PlayerRef player;

        private StubSender(SenderKind kind, PlayerRef player) {
            this.kind = kind;
            this.player = player;
        }

        @Override
        public SenderKind kind() {
            return kind;
        }

        @Override
        public Optional<PlayerRef> player() {
            return Optional.ofNullable(player);
        }

        @Override
        public String name() {
            return player == null ? "Server" : player.name();
        }

        @Override
        public Optional<SenderPosition> position() {
            return Optional.empty();
        }

        @Override
        public void reply(String translationKey, Object... arguments) {}

        @Override
        public void replyError(String translationKey, Object... arguments) {}
    }
}
