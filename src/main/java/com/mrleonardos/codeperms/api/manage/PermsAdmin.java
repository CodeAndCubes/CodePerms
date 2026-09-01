package com.mrleonardos.codeperms.api.manage;

import java.util.List;
import java.util.UUID;

import com.mrleonardos.codeperms.api.model.ChangeCause;
import com.mrleonardos.codeperms.api.model.ContextSet;
import com.mrleonardos.codeperms.api.model.NodeEntry;
import com.mrleonardos.codeperms.api.resolve.Resolution;
import com.mrleonardos.codeperms.api.store.OperationResult;

/**
 * Правки прав: группы, игроки, треки.
 *
 * <p>
 * Каждая правка уходит в главный поток, применяется к снимку целиком и попадает в аудит с причиной и
 * автором. Следующая проверка права уже видит новое состояние, перезаход игрока не нужен.
 *
 * <p>
 * Отказ приходит значением {@link OperationResult}, а не исключением: у отказа есть понятная причина,
 * и её можно показать игроку.
 */
public interface PermsAdmin {

    /**
     * Создать группу.
     *
     * @param displayName имя для вывода человеку, пустое заменяется идентификатором
     */
    OperationResult createGroup(String id, String displayName, int weight, ChangeCause cause, String author);

    /**
     * Удалить группу вместе со ссылками на неё: наследование чужих групп, выдачи и primary игроков,
     * место в треках. Трек, в котором она была единственной, удаляется тоже.
     *
     * <p>
     * Группа, названная в {@code defaultGroup} или {@code opGroup} файла {@code config.json}, не
     * удаляется: снимок разъехался бы с настройками. Отказ приходит кодом {@code IN_USE}.
     */
    OperationResult deleteGroup(String id, ChangeCause cause, String author);

    /**
     * Переименовать группу: за новым идентификатором едут наследование чужих групп, выдачи и primary
     * игроков и место в треках.
     *
     * <p>
     * Группа из {@code defaultGroup} или {@code opGroup} не переименовывается по той же причине, что и
     * не удаляется: отказ кодом {@code IN_USE}.
     */
    OperationResult renameGroup(String id, String newId, ChangeCause cause, String author);

    /** Скопировать группу с нодами, метой и наследованием. */
    OperationResult copyGroup(String source, String target, ChangeCause cause, String author);

    /** Назначить вес группы, большее число значит старше. */
    OperationResult setWeight(String groupId, int weight, ChangeCause cause, String author);

    /** Добавить группе родителя. */
    OperationResult addParent(String groupId, String parentId, ChangeCause cause, String author);

    /** Убрать у группы родителя. */
    OperationResult removeParent(String groupId, String parentId, ChangeCause cause, String author);

    /**
     * Записать ноду группы.
     *
     * <p>
     * Ключ правила это пара нода и контексты, значение в ключ не входит: разрешение поверх запрета с тем
     * же набором контекстов заменяет его, а правила с разными контекстами живут рядом.
     */
    OperationResult setGroupNode(String groupId, NodeEntry entry, ChangeCause cause, String author);

    /** Снять ноду группы независимо от контекстов и срока. */
    OperationResult removeGroupNode(String groupId, String node, ChangeCause cause, String author);

    /** Записать мета группы. */
    OperationResult setGroupMeta(String groupId, String key, String value, ChangeCause cause, String author);

    /** Убрать мета группы. */
    OperationResult removeGroupMeta(String groupId, String key, ChangeCause cause, String author);

    /** Записать личную ноду игрока. Ключ правила тот же, что у {@link #setGroupNode}. */
    OperationResult setPlayerNode(UUID player, NodeEntry entry, ChangeCause cause, String author);

    /** Снять личную ноду игрока независимо от контекстов и срока. */
    OperationResult removePlayerNode(UUID player, String node, ChangeCause cause, String author);

    /** Записать личную мета игрока. */
    OperationResult setPlayerMeta(UUID player, String key, String value, ChangeCause cause, String author);

    /** Убрать личную мета игрока. */
    OperationResult removePlayerMeta(UUID player, String key, ChangeCause cause, String author);

    /**
     * Назначить основную группу.
     *
     * @param groupId идентификатор группы или null, чтобы снять назначение и оставить решение весам
     */
    OperationResult setPrimaryGroup(UUID player, String groupId, ChangeCause cause, String author);

    /**
     * Выдать игроку группу.
     *
     * @param expiresAt метка истечения в миллисекундах, ноль значит бессрочно
     */
    OperationResult addPlayerGroup(UUID player, String groupId, long expiresAt, ChangeCause cause, String author);

    /** Снять у игрока группу. */
    OperationResult removePlayerGroup(UUID player, String groupId, ChangeCause cause, String author);

    /**
     * Переставить игрока с одной группы на другую одной правкой: так работают повышение и понижение по
     * треку. Срок прежней выдачи переезжает вместе с группой, primary едет следом, если указывал на
     * прежнюю.
     *
     * <p>
     * Двух коммитов здесь нет намеренно: отказ на полпути оставил бы игрока вообще без группы трека.
     */
    OperationResult movePlayerGroup(UUID player, String fromGroupId, String toGroupId, ChangeCause cause,
        String author);

    /** Снять у игрока истёкшие выдачи групп и истёкшие ноды. */
    OperationResult cleanupPlayer(UUID player, ChangeCause cause, String author);

    /**
     * Спросить мета-стек: значения ключа от личного значения игрока дальше по его группам в порядке
     * источников. Список пуст, если ключа нет нигде.
     */
    List<String> metaStack(UUID player, String key);

    /**
     * Объяснить проверку: все применимые правила в порядке силы и тот, кто решил исход. Список кандидатов
     * пуст, когда ни одно правило не подошло, тогда право закрыто.
     */
    Resolution explain(UUID player, String node, ContextSet contexts);
}
