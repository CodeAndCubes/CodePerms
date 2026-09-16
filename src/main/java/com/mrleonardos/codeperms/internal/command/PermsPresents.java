package com.mrleonardos.codeperms.internal.command;

import java.util.UUID;

import com.mrleonardos.codecore.api.command.CommandContext;
import com.mrleonardos.codeperms.api.model.GroupRecord;
import com.mrleonardos.codeperms.api.model.Snapshot;
import com.mrleonardos.codeperms.api.model.TrackRecord;

/**
 * Богатые ответы команд: карточки и постраничные списки.
 *
 * <p>
 * Сюда команды обращаются за тем единственным, чего им не сделать самим: карточки собираются кирпичами
 * ядра, которым нужен тип игры, а команды проверяются тестом без запуска Minecraft. Мост между слоями,
 * как Presents регионов, а не шов SPI.
 *
 * <p>
 * Имя корня приходит с каждым вызовом из построенного дерева, а не из строки в коде: корень
 * переименовывается, и клик обязан звать ту же команду, что и разбор. Снимок уезжает целиком: число
 * участников и треки группы считаются на месте, из тех же данных, что и остальной вывод.
 */
public interface PermsPresents {

    /** Список групп страницей: строки ведут на карточку группы. */
    void groupList(CommandContext context, Snapshot snapshot, UUID viewer, int page, String root);

    /** Карточка группы, со второй страницы её ноды. */
    void groupInfo(CommandContext context, Snapshot snapshot, GroupRecord group, UUID viewer, int page, String root);

    /** Карточка игрока, со второй страницы его личные ноды. */
    void playerInfo(CommandContext context, Snapshot snapshot, UUID player, String name, UUID viewer, int page,
        String root);

    /** Список треков страницей: строки ведут на карточку трека. */
    void trackList(CommandContext context, Snapshot snapshot, UUID viewer, int page, String root);

    /** Карточка трека: цепочка групп по порядку возрастания. */
    void trackInfo(CommandContext context, TrackRecord track, UUID viewer, String root);
}
