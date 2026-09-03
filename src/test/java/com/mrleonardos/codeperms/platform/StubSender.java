package com.mrleonardos.codeperms.platform;

import java.util.Optional;
import java.util.UUID;

import com.mrleonardos.codecore.api.actor.PlayerRef;
import com.mrleonardos.codecore.api.command.CommandSender;
import com.mrleonardos.codecore.api.command.SenderKind;
import com.mrleonardos.codecore.api.command.SenderPosition;

/** Подставной отправитель команды: вид и ссылка на игрока, ни одного типа игры. */
final class StubSender implements CommandSender {

    private final SenderKind kind;
    private final PlayerRef player;

    private StubSender(SenderKind kind, PlayerRef player) {
        this.kind = kind;
        this.player = player;
    }

    static CommandSender of(SenderKind kind) {
        return new StubSender(kind, null);
    }

    static CommandSender console() {
        return of(SenderKind.CONSOLE);
    }

    static CommandSender player(UUID id) {
        return new StubSender(SenderKind.PLAYER, PlayerRef.of(id, "Steve"));
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
