package com.mrleonardos.codeperms.platform;

import java.util.Optional;
import java.util.UUID;

import com.mrleonardos.codecore.api.actor.PlayerRef;
import com.mrleonardos.codecore.api.command.CommandContext;
import com.mrleonardos.codeperms.api.model.ContextSet;
import com.mrleonardos.codeperms.internal.command.PermsSubjects;

final class SenderSubjects implements PermsSubjects {

    private final NameResolver names;
    private final PlayerContexts contexts;

    SenderSubjects(NameResolver names, PlayerContexts contexts) {
        this.names = names;
        this.contexts = contexts;
    }

    @Override
    public Optional<UUID> subjectOf(CommandContext context) {
        return context.caller()
            .player()
            .map(PlayerRef::id);
    }

    @Override
    public String senderName(CommandContext context) {
        return context.caller()
            .name();
    }

    @Override
    public Optional<String> playerName(UUID player) {
        return names.name(player);
    }

    @Override
    public ContextSet contexts(UUID player) {
        return contexts.of(player);
    }
}
