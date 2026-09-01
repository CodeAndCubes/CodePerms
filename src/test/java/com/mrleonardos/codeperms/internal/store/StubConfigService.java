package com.mrleonardos.codeperms.internal.store;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mrleonardos.codecore.api.config.ConfigFile;
import com.mrleonardos.codecore.api.config.ConfigScope;
import com.mrleonardos.codecore.api.config.ConfigService;
import com.mrleonardos.codecore.api.config.ConfigSpec;

public final class StubConfigService implements ConfigService {

    private final Path root;
    private final Map<String, StubFile<?>> files = new LinkedHashMap<>();

    public StubConfigService(Path root) {
        this.root = root;
    }

    @Override
    public <T> ConfigFile<T> open(ConfigSpec<T> spec) {
        String key = spec.scope() + ":" + spec.modid() + "/" + spec.name();
        StubFile<?> existing = files.get(key);
        if (existing != null) {
            return cast(existing);
        }
        StubFile<T> file = new StubFile<>(spec, path(spec));
        files.put(key, file);
        file.load();
        return cast(file);
    }

    @Override
    public Path directory(String modid) {
        return root.resolve(modid);
    }

    @Override
    public void reloadAll() {
        for (StubFile<?> file : files.values()) {
            file.reload();
        }
    }

    public Path pathOf(String modid, String name) {
        return root.resolve(modid)
            .resolve(name + ".json");
    }

    @SuppressWarnings("unchecked")
    private static <T> ConfigFile<T> cast(StubFile<?> file) {
        return (ConfigFile<T>) file;
    }

    private Path path(ConfigSpec<?> spec) {
        if (spec.scope() != ConfigScope.SETTINGS) {
            throw new IllegalArgumentException("Stub supports settings scope only");
        }
        return pathOf(spec.modid(), spec.name());
    }

    static final class StubFile<T> implements ConfigFile<T> {

        private final ConfigSpec<T> spec;
        private final Path path;
        private T value;

        StubFile(ConfigSpec<T> spec, Path path) {
            this.spec = spec;
            this.path = path;
        }

        @Override
        public T get() {
            if (value == null) {
                throw new IllegalStateException("Config " + spec.name() + " is not loaded");
            }
            return value;
        }

        @Override
        public boolean loaded() {
            return value != null;
        }

        @Override
        public void save() {
            JsonObject data = Json.GSON.toJsonTree(value)
                .getAsJsonObject();
            data.addProperty("schemaVersion", spec.schemaVersion());
            Json.write(path, data);
        }

        @Override
        public void reload() {
            load();
        }

        @Override
        public Path path() {
            return path;
        }

        void load() {
            if (!Files.isRegularFile(path)) {
                value = spec.defaults()
                    .get();
                save();
                return;
            }
            JsonObject data = Json.read(path);
            if (data == null) {
                quarantine();
                value = spec.defaults()
                    .get();
                save();
                return;
            }
            value = Json.GSON.fromJson(data, spec.type());
        }

        private void quarantine() {
            Path broken = path.resolveSibling(
                path.getFileName()
                    .toString() + ".broken");
            try {
                Files.move(path, broken, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException failure) {
                throw new IllegalStateException(failure);
            }
        }
    }

    static final class Json {

        static final com.google.gson.Gson GSON = new com.google.gson.GsonBuilder().disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

        static JsonObject read(Path path) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                JsonElement parsed = new JsonParser().parse(reader);
                if (parsed == null || !parsed.isJsonObject()) {
                    return null;
                }
                return parsed.getAsJsonObject();
            } catch (IOException | JsonParseException failure) {
                return null;
            }
        }

        static void write(Path path, JsonObject data) {
            Path temporary = path.resolveSibling(
                path.getFileName()
                    .toString() + ".tmp");
            try {
                Files.createDirectories(path.getParent());
                try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                    GSON.toJson(data, writer);
                }
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException failure) {
                throw new IllegalStateException(failure);
            }
        }
    }
}
