package com.mrleonardos.codeperms.internal.store;

import com.google.gson.JsonObject;
import com.mrleonardos.codecore.api.config.Comment;

@Comment({ "Группы и треки CodePerms. Игроки лежат рядом в perms-players.json,",
    "настройки в perms.toml, имена групп по умолчанию в config/code/config.toml." })
public final class PermsGroupsFile {

    @Comment({ "Группа: вес, наследование, правила и мета. Имя группы стоит в заголовке секции.",
        "Спор двух групп решает больший вес, а не порядок в файле.",
        "Правило со знаком минус в начале запрещает и перебивает разрешение той же точности.",
        "Правило с контекстом или сроком пишется таблицей:",
        "{ node = \"fly.use\", expiresAt = 0, contexts = { world = \"nether\" } }." })
    public JsonObject groups = new JsonObject();

    @Comment({ "Трек: порядок групп для повышения и понижения.",
        "Игрока двигают команды /perms track promote и demote." })
    public JsonObject tracks = new JsonObject();
}
