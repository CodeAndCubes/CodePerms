package com.mrleonardos.codeperms.platform;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.entity.player.EntityPlayerMP;

import com.mrleonardos.codecore.platform.PlayerNames;
import com.mrleonardos.codecore.platform.Players;
import com.mrleonardos.codeperms.api.model.Snapshot;
import com.mrleonardos.codeperms.api.model.UserRecord;

final class NameResolver {

    private final Supplier<Snapshot> snapshots;

    NameResolver(Supplier<Snapshot> snapshots) {
        this.snapshots = snapshots;
    }

    Optional<String> name(UUID player) {
        String known = PlayerNames.byId(player);
        if (known != null && !known.isEmpty()) {
            return Optional.of(known);
        }
        UserRecord user = snapshots.get()
            .user(player)
            .orElse(null);
        return user != null && user.name() != null ? Optional.of(user.name()) : Optional.<String>empty();
    }

    Optional<UUID> id(String name) {
        EntityPlayerMP online = Players.online(name);
        if (online != null) {
            return Optional.of(online.getUniqueID());
        }
        for (UserRecord user : snapshots.get()
            .users()
            .values()) {
            if (user.name() != null && user.name()
                .equalsIgnoreCase(name)) {
                return Optional.of(user.uuid());
            }
        }
        return Optional.ofNullable(PlayerNames.idByName(name));
    }

    List<String> suggest(String partial, int limit) {
        if (limit <= 0) {
            return new ArrayList<>();
        }
        String prefix = partial.toLowerCase(Locale.ROOT);
        Set<String> candidates = new LinkedHashSet<>(Players.onlineNames());
        for (UserRecord user : snapshots.get()
            .users()
            .values()) {
            if (user.name() != null) {
                candidates.add(user.name());
            }
        }
        List<String> found = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT)
                .startsWith(prefix)) {
                found.add(candidate);
                if (found.size() == limit) {
                    break;
                }
            }
        }
        return found;
    }
}
