package com.mrleonardos.codeperms.platform;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codecore.platform.PlayerRefs;
import com.mrleonardos.codecore.platform.Players;
import com.mrleonardos.codeperms.api.context.ContextKeys;
import com.mrleonardos.codeperms.api.context.ContextRegistry;
import com.mrleonardos.codeperms.api.model.ContextSet;
import com.mrleonardos.codeperms.api.model.Snapshot;

final class PlayerContexts {

    private final ContextRegistry foreign;
    private final Supplier<Snapshot> snapshots;
    private final OperatorWatch operators;
    private final Logger log;
    private final Map<UUID, ContextSet> active = new ConcurrentHashMap<>();

    PlayerContexts(ContextRegistry foreign, Supplier<Snapshot> snapshots, OperatorWatch operators, Logger log) {
        this.foreign = foreign;
        this.snapshots = snapshots;
        this.operators = operators;
        this.log = log;
    }

    ContextSet of(UUID player) {
        Snapshot snapshot = snapshots.get();
        if (!snapshot.hasContextNodes()) {
            if (!active.isEmpty()) {
                active.clear();
            }
            return ContextSet.empty();
        }
        ContextSet cached = active.get(player);
        if (cached != null) {
            return cached;
        }
        ContextSet built = build(player);
        active.put(player, built);
        return built;
    }

    void refresh(UUID player) {
        active.remove(player);
    }

    void clear() {
        active.clear();
    }

    private ContextSet build(UUID player) {
        EntityPlayerMP online = Players.online(player);
        if (online == null) {
            return ContextSet.empty();
        }
        ContextSet.Builder builder = ContextSet.builder();
        String world = worldOf(online);
        if (!world.isEmpty()) {
            builder.put(ContextKeys.WORLD, world);
        }
        builder.put(ContextKeys.DIM, String.valueOf(online.dimension));
        builder.put(ContextKeys.OP, String.valueOf(operators.isOperator(player)));
        builder.putAll(
            foreign
                .collect(
                    PlayerRefs.of(online),
                    (id, failure) -> log.warn("Context provider {} failed: {}", id, failure.toString()))
                .asMap());
        return builder.build();
    }

    private static String worldOf(EntityPlayerMP player) {
        World world = player.worldObj;
        if (world == null || world.getWorldInfo() == null) {
            return "";
        }
        String name = world.getWorldInfo()
            .getWorldName();
        return name == null ? "" : name.trim();
    }
}
