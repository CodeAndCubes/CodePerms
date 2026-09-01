package com.mrleonardos.codeperms.platform;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

public final class ForgeLifecycle {

    private final PlayerContexts contexts;
    private final OperatorWatch operators;

    ForgeLifecycle(PlayerContexts contexts, OperatorWatch operators) {
        this.contexts = contexts;
        this.operators = operators;
    }

    @SubscribeEvent
    public void onJoin(PlayerEvent.PlayerLoggedInEvent event) {
        UUID player = id(event.player);
        operators.onJoin(player);
        contexts.refresh(player);
    }

    @SubscribeEvent
    public void onQuit(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID player = id(event.player);
        operators.onQuit(player);
        contexts.refresh(player);
    }

    @SubscribeEvent
    public void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        contexts.refresh(id(event.player));
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        contexts.refresh(id(event.player));
    }

    private static UUID id(EntityPlayer player) {
        return player.getUniqueID();
    }
}
