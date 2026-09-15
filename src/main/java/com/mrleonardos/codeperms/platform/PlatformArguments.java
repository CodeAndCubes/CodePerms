package com.mrleonardos.codeperms.platform;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

import com.mrleonardos.codecore.api.command.ArgumentType;
import com.mrleonardos.codecore.api.command.CommandInputException;
import com.mrleonardos.codecore.api.command.CommandSender;
import com.mrleonardos.codeperms.api.PermsApi;
import com.mrleonardos.codeperms.api.PermsLimits;
import com.mrleonardos.codeperms.api.model.Snapshot;
import com.mrleonardos.codeperms.internal.command.PermsArguments;
import com.mrleonardos.codeperms.internal.command.PermsMessages;

final class PlatformArguments implements PermsArguments {

    private static final int SUGGESTION_LIMIT = 50;

    private final NameResolver names;
    private final Supplier<Snapshot> snapshots;
    private final Supplier<PermsLimits> limits;

    PlatformArguments(NameResolver names, Supplier<Snapshot> snapshots, Supplier<PermsLimits> limits) {
        this.names = names;
        this.snapshots = snapshots;
        this.limits = limits;
    }

    @Override
    public ArgumentType<String> groupId() {
        return new ArgumentType<String>() {

            @Override
            public String parse(String raw) {
                if (!limits.get()
                    .acceptsGroupId(raw)) {
                    throw new CommandInputException(PermsMessages.FAILURE_LIMIT_REACHED, raw);
                }
                return normalize(raw);
            }

            @Override
            public List<String> suggestions(CommandSender sender, String partial) {
                return startingWith(
                    snapshots.get()
                        .groups()
                        .keySet(),
                    partial);
            }
        };
    }

    @Override
    public ArgumentType<String> trackName() {
        return new ArgumentType<String>() {

            @Override
            public String parse(String raw) {
                if (!limits.get()
                    .acceptsTrackName(raw)) {
                    throw new CommandInputException(PermsMessages.FAILURE_LIMIT_REACHED, raw);
                }
                return normalize(raw);
            }

            @Override
            public List<String> suggestions(CommandSender sender, String partial) {
                return startingWith(
                    snapshots.get()
                        .tracks()
                        .keySet(),
                    partial);
            }
        };
    }

    @Override
    public ArgumentType<UUID> player() {
        return new ArgumentType<UUID>() {

            @Override
            public UUID parse(String raw) {
                UUID player = names.id(raw)
                    .orElse(null);
                if (player == null) {
                    throw new CommandInputException(PermsMessages.FAILURE_NOT_FOUND, raw);
                }
                return player;
            }

            @Override
            public List<String> suggestions(CommandSender sender, String partial) {
                return names.suggest(partial, SUGGESTION_LIMIT);
            }
        };
    }

    @Override
    public ArgumentType<String> node() {
        return new ArgumentType<String>() {

            @Override
            public String parse(String raw) {
                if (!limits.get()
                    .acceptsNode(raw)) {
                    throw new CommandInputException(PermsMessages.FAILURE_LIMIT_REACHED, raw);
                }
                return raw;
            }

            @Override
            public List<String> suggestions(CommandSender sender, String partial) {
                return PermsApi.catalog()
                    .suggest(partial, SUGGESTION_LIMIT);
            }
        };
    }

    @Override
    public ArgumentType<Long> expiry() {
        return PermsArguments.expiryType();
    }

    private static String normalize(String raw) {
        return raw.trim()
            .toLowerCase(Locale.ROOT);
    }

    private static List<String> startingWith(Iterable<String> candidates, String partial) {
        List<String> found = new ArrayList<>();
        String prefix = partial.toLowerCase(Locale.ROOT);
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT)
                .startsWith(prefix)) {
                found.add(candidate);
                if (found.size() == SUGGESTION_LIMIT) {
                    break;
                }
            }
        }
        return found;
    }
}
