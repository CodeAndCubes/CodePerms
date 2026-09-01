package com.mrleonardos.codeperms.internal.store;

import com.google.gson.JsonArray;
import com.mrleonardos.codecore.api.config.Comment;

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
