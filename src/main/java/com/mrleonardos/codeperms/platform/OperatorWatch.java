package com.mrleonardos.codeperms.platform;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codecore.api.config.ConfigFile;
import com.mrleonardos.codeperms.api.manage.PermsAdmin;
import com.mrleonardos.codeperms.api.model.ChangeCause;
import com.mrleonardos.codeperms.api.model.Snapshot;
import com.mrleonardos.codeperms.api.model.UserRecord;
import com.mrleonardos.codeperms.api.store.OperationResult;
import com.mrleonardos.codeperms.internal.MainSettings;
import com.mrleonardos.codeperms.internal.PermsSettings;
import com.mrleonardos.codeperms.internal.store.SingleWriterImpl;

final class OperatorWatch {

    private final PermsAdmin admin;
    private final ConfigFile<PermsSettings> settings;
    private final MainSettings main;
    private final Supplier<Snapshot> snapshots;
    private final Logger log;
    private final Map<UUID, Boolean> answers;

    OperatorWatch(PermsAdmin admin, ConfigFile<PermsSettings> settings, MainSettings main, Supplier<Snapshot> snapshots,
        Logger log) {
        this.admin = admin;
        this.settings = settings;
        this.main = main;
        this.snapshots = snapshots;
        this.log = log;
        this.answers = Collections.synchronizedMap(new LinkedHashMap<UUID, Boolean>(16, 0.75f, true) {

            @Override
            protected boolean removeEldestEntry(Map.Entry<UUID, Boolean> eldest) {
                return size() > ceiling();
            }
        });
    }

    boolean isOperator(UUID player) {
        Boolean known;
        synchronized (answers) {
            known = answers.get(player);
            if (known == null) {
                known = Boolean.valueOf(onServerList(player));
                answers.put(player, known);
            }
        }
        return known.booleanValue();
    }

    /**
     * Сверить закешированный статус оператора с фактическим. События смены op в 1.7.10 нет, поэтому
     * {@code /op} посреди сессии узнаётся здесь: расхождение обновляет группу и просит контексты
     * пересобраться. Сверка стоит поиска игрока в списке онлайн и проверки его профиля в таблице
     * операторов, поэтому контексты зовут её только когда в снимке есть контекстные правила: серверы
     * без них получают прежний бесплатный путь. Оффлайн-игрока сверка не трогает: его фактический
     * статус неизвестен, пока он не зашёл, и кеш отвечает за него сам.
     *
     * @return правда ли статус изменился и кеш контекстов устарел
     */
    boolean syncIfOutdated(UUID player) {
        Boolean known;
        synchronized (answers) {
            known = answers.get(player);
        }
        if (known == null) {
            return false;
        }
        EntityPlayerMP online = onlineOf(player);
        if (online == null) {
            return false;
        }
        boolean actual = MinecraftServer.getServer()
            .getConfigurationManager()
            .func_152596_g(online.getGameProfile());
        if (actual == known.booleanValue()) {
            return false;
        }
        synchronized (answers) {
            answers.put(player, Boolean.valueOf(actual));
        }
        sync(player, known);
        return true;
    }

    void onJoin(UUID player) {
        Boolean previous;
        synchronized (answers) {
            previous = answers.remove(player);
        }
        sync(player, previous);
    }

    void onQuit(UUID player) {
        isOperator(player);
    }

    void clear() {
        answers.clear();
    }

    private void sync(UUID player, Boolean previous) {
        if (!settings.get().applyOps) {
            return;
        }
        String groupId = main.opGroup();
        Snapshot snapshot = snapshots.get();
        if (!snapshot.group(groupId)
            .isPresent()) {
            return;
        }
        boolean operator = isOperator(player);
        boolean member = memberOf(snapshot, player, groupId);
        if (operator == member) {
            return;
        }
        if (!operator && !Boolean.TRUE.equals(previous)) {
            return;
        }
        OperationResult result = operator
            ? admin.addPlayerGroup(player, groupId, 0L, ChangeCause.API, SingleWriterImpl.AUTHOR)
            : admin.removePlayerGroup(player, groupId, ChangeCause.API, SingleWriterImpl.AUTHOR);
        if (!result.successful()) {
            log.warn(
                "Operator group {} was not {} {}: {}",
                groupId,
                operator ? "granted to" : "taken from",
                player,
                result);
        }
    }

    private static boolean memberOf(Snapshot snapshot, UUID player, String groupId) {
        UserRecord user = snapshot.user(player)
            .orElse(null);
        return user != null && user.memberOf(groupId, System.currentTimeMillis());
    }

    private int ceiling() {
        return Math.max(1, settings.get().cache.offlineCacheSize);
    }

    private static boolean onServerList(UUID playerId) {
        EntityPlayerMP online = onlineOf(playerId);
        return online != null && MinecraftServer.getServer()
            .getConfigurationManager()
            .func_152596_g(online.getGameProfile());
    }

    private static EntityPlayerMP onlineOf(UUID playerId) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) {
            return null;
        }
        List<?> online = server.getConfigurationManager().playerEntityList;
        for (Object candidate : online) {
            EntityPlayerMP player = (EntityPlayerMP) candidate;
            if (player.getUniqueID()
                .equals(playerId)) {
                return player;
            }
        }
        return null;
    }
}
