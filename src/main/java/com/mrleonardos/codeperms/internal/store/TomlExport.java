package com.mrleonardos.codeperms.internal.store;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

final class TomlExport {

    private static final String HEADER = "# Выгрузка CodePerms в формате встроенной реализации ядра.\n"
        + "# Чтобы ядро её прочитало, файл кладут в config/code/permissions/core-groups.toml.\n"
        + "# Веса групп, треки, контексты и сроки в этот формат не помещаются и здесь не сохранены.\n";

    private static final String GROUPS = "groups";
    private static final String PLAYERS = "players";
    private static final String META = "meta";

    private TomlExport() {}

    static void write(Writer writer, CoreGroupsView view) throws IOException {
        writer.write(HEADER);
        section(writer, GROUPS, view.groups);
        section(writer, PLAYERS, view.players);
    }

    private static void section(Writer writer, String name, JsonObject entries) throws IOException {
        if (entries == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : entries.entrySet()) {
            if (!entry.getValue()
                .isJsonObject()) {
                continue;
            }
            JsonObject body = entry.getValue()
                .getAsJsonObject();
            String path = name + "." + key(entry.getKey());
            writer.write("\n[" + path + "]\n");
            for (Map.Entry<String, JsonElement> field : body.entrySet()) {
                if (!META.equals(field.getKey())) {
                    pair(writer, field.getKey(), field.getValue());
                }
            }
            table(writer, path + "." + META, body.get(META));
        }
    }

    private static void table(Writer writer, String path, JsonElement values) throws IOException {
        if (values == null || !values.isJsonObject()
            || values.getAsJsonObject()
                .entrySet()
                .isEmpty()) {
            return;
        }
        writer.write("\n[" + path + "]\n");
        for (Map.Entry<String, JsonElement> entry : values.getAsJsonObject()
            .entrySet()) {
            pair(writer, entry.getKey(), entry.getValue());
        }
    }

    private static void pair(Writer writer, String name, JsonElement value) throws IOException {
        writer.write(key(name) + " = " + value(value) + "\n");
    }

    private static String value(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return "\"\"";
        }
        if (value.isJsonArray()) {
            StringBuilder text = new StringBuilder("[");
            boolean first = true;
            for (JsonElement element : value.getAsJsonArray()) {
                if (!first) {
                    text.append(", ");
                }
                first = false;
                text.append(value(element));
            }
            return text.append(']')
                .toString();
        }
        if (value.isJsonObject()) {
            return "{}";
        }
        if (value.getAsJsonPrimitive()
            .isBoolean()) {
            return String.valueOf(value.getAsBoolean());
        }
        if (value.getAsJsonPrimitive()
            .isNumber()) {
            return value.getAsString();
        }
        return quoted(value.getAsString());
    }

    private static String key(String name) {
        for (int index = 0; index < name.length(); index++) {
            char symbol = name.charAt(index);
            boolean bare = (symbol >= 'a' && symbol <= 'z') || (symbol >= 'A' && symbol <= 'Z')
                || (symbol >= '0' && symbol <= '9')
                || symbol == '_'
                || symbol == '-';
            if (!bare) {
                return quoted(name);
            }
        }
        return name.isEmpty() ? quoted(name) : name;
    }

    private static String quoted(String text) {
        StringBuilder quoted = new StringBuilder("\"");
        for (int index = 0; index < text.length(); index++) {
            char symbol = text.charAt(index);
            switch (symbol) {
                case '"':
                    quoted.append("\\\"");
                    break;
                case '\\':
                    quoted.append("\\\\");
                    break;
                case '\n':
                    quoted.append("\\n");
                    break;
                case '\r':
                    quoted.append("\\r");
                    break;
                case '\t':
                    quoted.append("\\t");
                    break;
                default:
                    if (symbol < 0x20 || symbol == 0x7F) {
                        quoted.append(String.format("\\u%04X", Integer.valueOf(symbol)));
                    } else {
                        quoted.append(symbol);
                    }
                    break;
            }
        }
        return quoted.append('"')
            .toString();
    }
}
