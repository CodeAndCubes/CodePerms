package com.mrleonardos.codeperms.internal.command;

import com.mrleonardos.codeperms.api.NodeCatalog;

/**
 * Собственные ноды мода в каталоге. Без них автодополнение нод пусто, пока администратор не выдал хоть
 * одну ноду руками. Вызывается один раз при сборке, когда роль прав у CodePerms.
 */
public final class PermsCatalog {

    private PermsCatalog() {}

    public static void registerOwnNodes(NodeCatalog catalog) {
        catalog.register(PermsPermissions.ME, PermsMessages.NODE_ME);
        catalog.register(PermsPermissions.GROUP_LIST, PermsMessages.NODE_GROUP_LIST);
        catalog.register(PermsPermissions.GROUP_INFO, PermsMessages.NODE_GROUP_INFO);
        catalog.register(PermsPermissions.GROUP_CREATE, PermsMessages.NODE_GROUP_CREATE);
        catalog.register(PermsPermissions.GROUP_DELETE, PermsMessages.NODE_GROUP_DELETE);
        catalog.register(PermsPermissions.GROUP_RENAME, PermsMessages.NODE_GROUP_RENAME);
        catalog.register(PermsPermissions.GROUP_COPY, PermsMessages.NODE_GROUP_COPY);
        catalog.register(PermsPermissions.GROUP_WEIGHT, PermsMessages.NODE_GROUP_WEIGHT);
        catalog.register(PermsPermissions.GROUP_EDIT, PermsMessages.NODE_GROUP_EDIT);
        catalog.register(PermsPermissions.PLAYER_INFO, PermsMessages.NODE_PLAYER_INFO);
        catalog.register(PermsPermissions.PLAYER_SET_GROUP, PermsMessages.NODE_PLAYER_SET_GROUP);
        catalog.register(PermsPermissions.PLAYER_ADD_GROUP, PermsMessages.NODE_PLAYER_ADD_GROUP);
        catalog.register(PermsPermissions.PLAYER_REMOVE_GROUP, PermsMessages.NODE_PLAYER_REMOVE_GROUP);
        catalog.register(PermsPermissions.PLAYER_NODE, PermsMessages.NODE_PLAYER_NODE);
        catalog.register(PermsPermissions.PLAYER_META, PermsMessages.NODE_PLAYER_META);
        catalog.register(PermsPermissions.PLAYER_CLEANUP, PermsMessages.NODE_PLAYER_CLEANUP);
        catalog.register(PermsPermissions.TRACK_LIST, PermsMessages.NODE_TRACK_LIST);
        catalog.register(PermsPermissions.TRACK_INFO, PermsMessages.NODE_TRACK_INFO);
        catalog.register(PermsPermissions.TRACK_PROMOTE, PermsMessages.NODE_TRACK_PROMOTE);
        catalog.register(PermsPermissions.TRACK_DEMOTE, PermsMessages.NODE_TRACK_DEMOTE);
        catalog.register(PermsPermissions.IMPORT, PermsMessages.NODE_IMPORT);
        catalog.register(PermsPermissions.EXPORT, PermsMessages.NODE_EXPORT);
        catalog.register(PermsPermissions.RELOAD, PermsMessages.NODE_RELOAD);
        catalog.register(PermsPermissions.DEBUG, PermsMessages.NODE_DEBUG);
    }
}
