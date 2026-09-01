package com.mrleonardos.codeperms.internal.store;

import com.google.gson.JsonArray;
import com.mrleonardos.codecore.api.config.Comment;

@Comment({ "Группы и треки CodePerms. Игроки лежат рядом в perms-players.json,",
    "настройки в perms.toml, имена групп по умолчанию в config/code/config.toml.",
    "Мод переписывает записи групп и треков целиком при каждом сохранении: строка, дописанная",
    "внутрь записи, не сохранится, а ключ верхнего уровня, которого мод не читает, останется." })
public final class PermsGroupsFile {

    @Comment({ "Группы: id, вес, наследование, правила и мета.",
        "Спор двух групп решает больший вес, а не порядок в файле.",
        "Правило со знаком минус в начале запрещает и перебивает разрешение той же точности.",
        "Правило с контекстом или сроком пишется таблицей: { node = \"fly.use\", expiresAt = 0, contexts = { world = \"nether\" } }." })
    public JsonArray groups = new JsonArray();

    @Comment({ "Треки: порядок групп для повышения и понижения.",
        "Игрока двигают команды /perms track promote и demote." })
    public JsonArray tracks = new JsonArray();
}
