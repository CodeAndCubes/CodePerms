package com.mrleonardos.codeperms.platform;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import net.minecraft.command.ICommandSender;

import com.mrleonardos.codecore.api.command.CommandSender;
import com.mrleonardos.codecore.api.service.PermissionService;
import com.mrleonardos.codecore.platform.Senders;
import com.mrleonardos.codeperms.api.model.Snapshot;
import com.mrleonardos.codeperms.api.resolve.Resolution;
import com.mrleonardos.codeperms.internal.command.PermsSubjects;
import com.mrleonardos.codeperms.internal.engine.ResolverImpl;

public final class CorePermissionService implements PermissionService {

    private final Supplier<Snapshot> snapshots;
    private final ResolverImpl resolver;
    private final PermsSubjects subjects;
    private final BooleanSupplier logChecks;

    public CorePermissionService(Supplier<Snapshot> snapshots, ResolverImpl resolver, PermsSubjects subjects) {
        this(snapshots, resolver, subjects, () -> false);
    }

    public CorePermissionService(Supplier<Snapshot> snapshots, ResolverImpl resolver, PermsSubjects subjects,
        BooleanSupplier logChecks) {
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.subjects = Objects.requireNonNull(subjects, "subjects");
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
        CommandSender author = Senders.of(sender);
        return author == null || has(author, node);
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
