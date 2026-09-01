package com.mrleonardos.codeperms.platform;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;

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
        EntityPlayerMP player = context.player();
        return player == null ? Optional.<UUID>empty() : Optional.of(player.getUniqueID());
    }

    @Override
    public String senderName(CommandContext context) {
        return context.sender()
            .getCommandSenderName();
    }

    @Override
    public Optional<String> playerName(UUID player) {
        return names.name(player);
    }

    @Override
    public ContextSet contexts(UUID player) {
        return contexts.of(player);
    }

    UUID playerOf(ICommandSender sender) {
        return sender instanceof EntityPlayerMP ? ((EntityPlayerMP) sender).getUniqueID() : null;
    }
}
