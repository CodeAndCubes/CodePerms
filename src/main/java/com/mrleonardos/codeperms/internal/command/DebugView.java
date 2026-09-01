package com.mrleonardos.codeperms.internal.command;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.mrleonardos.codecore.api.command.CommandContext;
import com.mrleonardos.codeperms.api.model.ContextSet;
import com.mrleonardos.codeperms.api.model.Snapshot;
import com.mrleonardos.codeperms.api.resolve.Candidate;
import com.mrleonardos.codeperms.api.resolve.Resolution;
import com.mrleonardos.codeperms.internal.engine.ResolverImpl;

public final class DebugView {

    private final ResolverImpl resolver;

    public DebugView(ResolverImpl resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    public void show(CommandContext context, Snapshot snapshot, UUID player, String node, ContextSet contexts,
        String playerName) {
        context.reply(PermsMessages.DEBUG_HEADER, node, playerName);
        Resolution resolution = resolver.resolve(snapshot, player, node, contexts);
        List<Candidate> candidates = resolution.candidates();
        if (candidates.isEmpty()) {
            context.reply(PermsMessages.DEBUG_EMPTY);
            return;
        }
        for (Candidate candidate : candidates) {
            if (candidate.deny()) {
                context.reply(
                    PermsMessages.DEBUG_ROW_DENY,
                    candidate.source(),
                    candidate.entry()
                        .toShortString(),
                    candidate.contextMatches(),
                    candidate.specificity());
            } else {
                context.reply(
                    PermsMessages.DEBUG_ROW_ALLOW,
                    candidate.source(),
                    candidate.entry()
                        .toShortString(),
                    candidate.contextMatches(),
                    candidate.specificity());
            }
        }
        Candidate winner = resolution.winner()
            .get();
        if (winner.deny()) {
            context.replyError(
                PermsMessages.DEBUG_WINNER_DENY,
                winner.source(),
                winner.entry()
                    .toShortString());
        } else {
            context.reply(
                PermsMessages.DEBUG_WINNER_ALLOW,
                winner.source(),
                winner.entry()
                    .toShortString());
        }
    }
}
