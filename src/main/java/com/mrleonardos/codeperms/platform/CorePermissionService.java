package com.mrleonardos.codeperms.platform;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

import net.minecraft.command.ICommandSender;

import com.mrleonardos.codecore.api.service.PermissionService;
import com.mrleonardos.codeperms.api.model.Snapshot;
import com.mrleonardos.codeperms.api.resolve.Resolution;
import com.mrleonardos.codeperms.internal.command.PermsSubjects;
import com.mrleonardos.codeperms.internal.engine.ResolverImpl;

public final class CorePermissionService implements PermissionService {

    private final Supplier<Snapshot> snapshots;
    private final ResolverImpl resolver;
    private final PermsSubjects subjects;
    private final Function<ICommandSender, UUID> players;
    private final BooleanSupplier logChecks;

    public CorePermissionService(Supplier<Snapshot> snapshots, ResolverImpl resolver, PermsSubjects subjects,
        Function<ICommandSender, UUID> players) {
        this(snapshots, resolver, subjects, players, () -> false);
    }

    public CorePermissionService(Supplier<Snapshot> snapshots, ResolverImpl resolver, PermsSubjects subjects,
        Function<ICommandSender, UUID> players, BooleanSupplier logChecks) {
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.subjects = Objects.requireNonNull(subjects, "subjects");
        this.players = Objects.requireNonNull(players, "players");
        this.logChecks = Objects.requireNonNull(logChecks, "logChecks");
    }

    @Override
    public boolean has(UUID player, String node) {
        Resolution resolution = resolver.resolve(snapshots.get(), player, node, subjects.contexts(player));
        if (logChecks.getAsBoolean()) {
            CodePermsMod.LOG.debug("Permission check of node {} for {}: {}", node, player, resolution);
        }
        return resolution.allowed();
    }

    @Override
    public boolean has(ICommandSender sender, String node) {
        UUID player = players.apply(sender);
        if (player == null) {
            return true;
        }
        return has(player, node);
    }

    @Override
    public String group(UUID player) {
        return resolver.group(snapshots.get(), player);
    }

    @Override
    public String meta(UUID player, String key, String fallback) {
        return resolver.meta(snapshots.get(), player, key)
            .orElse(fallback);
    }
}
