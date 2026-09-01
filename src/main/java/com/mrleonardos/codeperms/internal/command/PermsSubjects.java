package com.mrleonardos.codeperms.internal.command;

import java.util.Optional;
import java.util.UUID;

import com.mrleonardos.codecore.api.command.CommandContext;
import com.mrleonardos.codeperms.api.model.ContextSet;

public interface PermsSubjects {

    Optional<UUID> subjectOf(CommandContext context);

    String senderName(CommandContext context);

    Optional<String> playerName(UUID player);

    ContextSet contexts(UUID player);
}
