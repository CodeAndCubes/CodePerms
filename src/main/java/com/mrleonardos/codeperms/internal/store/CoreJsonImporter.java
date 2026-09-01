package com.mrleonardos.codeperms.internal.store;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mrleonardos.codecore.api.config.ConfigService;
import com.mrleonardos.codeperms.api.PermsLimits;
import com.mrleonardos.codeperms.api.model.ChangeCause;
import com.mrleonardos.codeperms.api.model.GroupRecord;
import com.mrleonardos.codeperms.api.model.NodeEntry;
import com.mrleonardos.codeperms.api.model.Snapshot;
import com.mrleonardos.codeperms.api.model.UserRecord;
import com.mrleonardos.codeperms.api.store.ChangeBatch;
import com.mrleonardos.codeperms.internal.PermsSettings;

public final class CoreJsonImporter {

    public static final String MARKER_FIELD = "codepermsImport";
    public static final String MARKER_HASH = "sha256";
    public static final String MARKER_TIME = "importedAt";
    public static final String CORE_FILE = "permissions.json";

    private static final String DEFAULT_GROUP_FIELD = "defaultGroup";
    private static final String OP_GROUP_FIELD = "opGroup";
    private static final String GROUPS_FIELD = "groups";
    private static final String PLAYERS_FIELD = "players";
    private static final String INHERITS_FIELD = "inherits";
    private static final String NODES_FIELD = "nodes";
    private static final String META_FIELD = "meta";
    private static final String GROUP_FIELD = "group";

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping()
        .setPrettyPrinting()
        .create();

    private final Path coreFile;
    private final Path exportDirectory;
    private final PermsLimits limits;
    private final Logger log;

    public CoreJsonImporter(Path coreFile, Path exportDirectory, PermsLimits limits, Logger log) {
        this.coreFile = coreFile;
        this.exportDirectory = exportDirectory;
        this.limits = limits;
        this.log = log;
    }

    public static Path coreFileOf(ConfigService configs) {
        return configs.directory("codecore")
            .resolve(CORE_FILE);
    }

    public Path coreFile() {
        return coreFile;
    }

    public boolean available() {
        return Files.isRegularFile(coreFile);
    }

    public boolean pendingImport() {
        if (!available()) {
            return false;
        }
        byte[] raw = read();
        JsonObject data = raw == null ? null : parse(raw);
        if (data == null) {
            return true;
        }
        return markerHash(data) == null;
    }

    public Result run(Snapshot current, boolean force, boolean dryRun) {
        if (!available()) {
            return Result.of(Status.SOURCE_MISSING, new Counts(), dryRun, null);
        }
        byte[] raw = read();
        if (raw == null) {
            return Result.of(Status.SOURCE_MISSING, new Counts(), dryRun, null);
        }
        JsonObject data = parse(raw);
        if (data == null) {
            return Result.of(Status.SOURCE_UNREADABLE, new Counts(), dryRun, null);
        }
        String imported = markerHash(data);
        data.remove(MARKER_FIELD);
        String hash = hashOf(canonical(data));
        if (imported != null && !imported.equals(hash) && !force) {
            return Result.of(Status.SOURCE_CHANGED, new Counts(), dryRun, hash);
        }

        Counts counts = new Counts();
        List<GroupRecord> groups = readGroups(data, counts);
        List<UserRecord> players = readPlayers(data, counts);
        if (groups.isEmpty() && players.isEmpty()) {
            return new Result(Status.NOTHING_TO_DO, counts, dryRun, hash, null, null, data);
        }

        Snapshot next = merge(
            current,
            groups,
            players,
            text(data.get(DEFAULT_GROUP_FIELD)),
            text(data.get(OP_GROUP_FIELD)));
        ChangeBatch batch = batchOf(groups, players);
        return new Result(Status.IMPORTED, counts, dryRun, hash, next, batch, data);
    }

    /**
     * Пометить файл ядра как перенесённый. Зовётся только после того, как снимок дошёл до хранилища:
     * маркер обещает, что импорт состоялся.
     *
     * @return удалось ли записать маркер
     */
    public boolean mark(Result result) {
        if (result.data == null || result.hash() == null) {
            return false;
        }
        return writeMarker(result.data, result.hash());
    }

    public Result export(Snapshot snapshot) {
        Counts counts = new Counts();
        JsonObject data = new JsonObject();
        data.addProperty(DEFAULT_GROUP_FIELD, snapshot.defaultGroup());
        data.addProperty(OP_GROUP_FIELD, snapshot.opGroup());
        data.add(GROUPS_FIELD, groupsOf(snapshot, counts));
        data.add(PLAYERS_FIELD, playersOf(snapshot, counts));
        Path target = exportDirectory.resolve(CORE_FILE);
        try {
            Files.createDirectories(exportDirectory);
            write(target, data);
        } catch (IOException failure) {
            log.error("Export to {} failed: {}", target, failure.toString());
            return Result.of(Status.EXPORT_FAILED, counts, false, null);
        }
        return new Result(Status.EXPORTED, counts, false, null, null, null, null);
    }

    private List<GroupRecord> readGroups(JsonObject data, Counts counts) {
        List<GroupRecord> groups = new ArrayList<>();
        JsonObject source = object(data.get(GROUPS_FIELD));
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            if (groups.size() >= limits.groups()) {
                counts.groupsSkipped++;
                continue;
            }
            String id = entry.getKey();
            if (!limits.acceptsGroupId(id)) {
                counts.groupsSkipped++;
                continue;
            }
            JsonObject body = object(entry.getValue());
            List<NodeEntry> nodes = nodes(body, counts);
            groups.add(
                GroupRecord.of(
                    id.toLowerCase(Locale.ROOT),
                    "",
                    groups.size(),
                    groupIds(body.get(INHERITS_FIELD)),

                    nodes,
                    meta(body, counts)));
            counts.groups++;
        }
        return groups;
    }

    private List<UserRecord> readPlayers(JsonObject data, Counts counts) {
        List<UserRecord> players = new ArrayList<>();
        JsonObject source = object(data.get(PLAYERS_FIELD));
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            UUID uuid = uuidOf(entry.getKey());
            if (uuid == null) {
                counts.playersSkipped++;
                continue;
            }
            JsonObject body = object(entry.getValue());
            String groupId = text(body.get(GROUP_FIELD));
            List<UserRecord.Grant> grants = new ArrayList<>();
            if (groupId != null) {
                grants.add(UserRecord.Grant.permanent(groupId.toLowerCase(Locale.ROOT)));
            }
            players.add(
                UserRecord.of(
                    uuid,
                    null,
                    groupId != null ? groupId.toLowerCase(Locale.ROOT) : null,
                    grants,
                    nodes(body, counts),
                    meta(body, counts)));
            counts.players++;
        }
        return players;
    }

    private List<NodeEntry> nodes(JsonObject body, Counts counts) {
        List<NodeEntry> nodes = new ArrayList<>();
        for (JsonElement element : array(body.get(NODES_FIELD))) {
            if (nodes.size() >= limits.nodesPerSubject() || !element.isJsonPrimitive()) {
                counts.nodesSkipped++;
                continue;
            }
            try {
                nodes.add(NodeEntry.parse(element.getAsString(), limits));
                counts.nodes++;
            } catch (RuntimeException failure) {
                counts.nodesSkipped++;
            }
        }
        return nodes;
    }

    private Map<String, String> meta(JsonObject body, Counts counts) {
        Map<String, String> meta = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : object(body.get(META_FIELD)).entrySet()) {
            JsonElement value = entry.getValue();
            if (!value.isJsonPrimitive()) {
                counts.metaSkipped++;
                continue;
            }
            String text = value.getAsString();
            if (text.length() > limits.metaValueLength() || meta.size() >= limits.metaKeysPerSubject()) {
                counts.metaSkipped++;
                continue;
            }
            meta.put(entry.getKey(), text);
            counts.meta++;
        }
        return meta;
    }

    private JsonObject groupsOf(Snapshot snapshot, Counts counts) {
        JsonObject groups = new JsonObject();
        for (GroupRecord group : snapshot.groups()
            .values()) {
            JsonObject body = new JsonObject();
            body.add(INHERITS_FIELD, strings(group.inherits()));
            body.add(NODES_FIELD, plainNodes(group.nodes(), counts));
            body.add(META_FIELD, mapOf(group.meta()));
            groups.add(group.id(), body);
        }
        return groups;
    }

    private JsonObject playersOf(Snapshot snapshot, Counts counts) {
        JsonObject players = new JsonObject();
        for (UserRecord player : snapshot.users()
            .values()) {
            JsonObject body = new JsonObject();
            if (player.primary() != null) {
                body.addProperty(GROUP_FIELD, player.primary());
            }
            body.add(NODES_FIELD, plainNodes(player.nodes(), counts));
            body.add(META_FIELD, mapOf(player.meta()));
            players.add(
                player.uuid()
                    .toString(),
                body);
        }
        return players;
    }

    private JsonArray plainNodes(List<NodeEntry> nodes, Counts counts) {
        JsonArray array = new JsonArray();
        for (NodeEntry node : nodes) {
            boolean expressible = node.contexts()
                .isEmpty() && node.permanent();
            if (!expressible) {
                counts.nodesSkipped++;
                continue;
            }
            array.add(new JsonPrimitive(node.toShortString()));
            counts.nodes++;
        }
        return array;
    }

    private JsonArray strings(List<String> values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(new JsonPrimitive(value));
        }
        return array;
    }

    private JsonObject mapOf(Map<String, String> values) {
        JsonObject data = new JsonObject();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            data.addProperty(entry.getKey(), entry.getValue());
        }
        return data;
    }

    private Snapshot merge(Snapshot current, List<GroupRecord> groups, List<UserRecord> players, String defaultGroup,
        String opGroup) {
        Snapshot.Builder builder = Snapshot.builder()
            .revision(current.revision() + 1L)
            .defaultGroup(pick(current.defaultGroup(), defaultGroup))
            .opGroup(pick(current.opGroup(), opGroup))
            .from(current);
        for (GroupRecord group : groups) {
            builder.group(group);
        }
        for (UserRecord player : players) {
            builder.user(player);
        }
        return builder.build();
    }

    private static String pick(String own, String imported) {
        if (own != null && !own.isEmpty()) {
            return own;
        }
        return imported != null ? imported.toLowerCase(Locale.ROOT) : null;
    }

    private ChangeBatch batchOf(List<GroupRecord> groups, List<UserRecord> players) {
        ChangeBatch.Builder builder = ChangeBatch.builder(ChangeCause.IMPORT, PermsSettings.MODID);
        for (GroupRecord group : groups) {
            builder.upsert(group);
        }
        for (UserRecord player : players) {
            builder.upsert(player);
        }
        return builder.build();
    }

    private boolean writeMarker(JsonObject data, String hash) {
        JsonObject marker = new JsonObject();
        marker.addProperty(MARKER_HASH, hash);
        marker.addProperty(MARKER_TIME, Long.valueOf(System.currentTimeMillis()));
        data.add(MARKER_FIELD, marker);
        try {
            write(coreFile, data);
            return true;
        } catch (IOException failure) {
            log.error("Marker for {} was not written: {}", coreFile, failure.toString());
            return false;
        }
    }

    private String markerHash(JsonObject data) {
        JsonObject marker = object(data.get(MARKER_FIELD));
        return text(marker.get(MARKER_HASH));
    }

    private byte[] read() {
        try {
            return Files.readAllBytes(coreFile);
        } catch (IOException failure) {
            log.error("Core permission file {} was not read: {}", coreFile, failure.toString());
            return null;
        }
    }

    private JsonObject parse(byte[] raw) {
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(raw), StandardCharsets.UTF_8)) {
            JsonElement parsed = new JsonParser().parse(reader);
            if (parsed == null || !parsed.isJsonObject()) {
                return null;
            }
            return parsed.getAsJsonObject();
        } catch (IOException | JsonParseException | IllegalStateException failure) {
            log.error("Core permission file {} is not readable json: {}", coreFile, failure.toString());
            return null;
        }
    }

    private void write(Path target, JsonObject data) throws IOException {
        Path temporary = target.resolveSibling(
            target.getFileName()
                .toString() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            GSON.toJson(data, writer);
        }
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
    }

    static byte[] canonical(JsonObject data) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try (Writer writer = new java.io.OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            GSON.toJson(data, writer);
        } catch (IOException failure) {
            throw new IllegalStateException("Canonical json was not written", failure);
        }
        return out.toByteArray();
    }

    private static String hashOf(byte[] raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder hex = new StringBuilder();
            for (byte item : digest.digest(raw)) {
                hex.append(Character.forDigit((item >> 4) & 0xF, 16));
                hex.append(Character.forDigit(item & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is not available", failure);
        }
    }

    private static JsonObject object(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return new JsonObject();
        }
        return element.getAsJsonObject();
    }

    private static JsonArray array(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return new JsonArray();
        }
        return element.getAsJsonArray();
    }

    private static List<String> strings(JsonElement element) {
        List<String> values = new ArrayList<>();
        for (JsonElement item : array(element)) {
            if (item.isJsonPrimitive()) {
                values.add(item.getAsString());
            }
        }
        return values;
    }

    private static List<String> groupIds(JsonElement element) {
        List<String> values = new ArrayList<>();
        for (String raw : strings(element)) {
            String normalized = raw.trim()
                .toLowerCase(Locale.ROOT);
            if (!normalized.isEmpty()) {
                values.add(normalized);
            }
        }
        return values;
    }

    private static String text(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        return element.getAsString();
    }

    private static UUID uuidOf(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException failure) {
            return null;
        }
    }

    public enum Status {

        IMPORTED,
        NOTHING_TO_DO,
        SOURCE_MISSING,
        SOURCE_UNREADABLE,
        SOURCE_CHANGED,
        EXPORTED,
        EXPORT_FAILED
    }

    public static final class Counts {

        int groups;
        int players;
        int nodes;
        int meta;
        int groupsSkipped;
        int playersSkipped;
        int nodesSkipped;
        int metaSkipped;

        public int groups() {
            return groups;
        }

        public int players() {
            return players;
        }

        public int nodes() {
            return nodes;
        }

        public int meta() {
            return meta;
        }

        public int groupsSkipped() {
            return groupsSkipped;
        }

        public int playersSkipped() {
            return playersSkipped;
        }

        public int nodesSkipped() {
            return nodesSkipped;
        }

        public int metaSkipped() {
            return metaSkipped;
        }
    }

    public static final class Result {

        private final Status status;
        private final Counts counts;
        private final boolean dryRun;
        private final String hash;
        private final Snapshot snapshot;
        private final ChangeBatch batch;
        final JsonObject data;

        static Result of(Status status, Counts counts, boolean dryRun, String hash) {
            return new Result(status, counts, dryRun, hash, null, null, null);
        }

        Result(Status status, Counts counts, boolean dryRun, String hash, Snapshot snapshot, ChangeBatch batch,
            JsonObject data) {
            this.status = status;
            this.counts = counts;
            this.dryRun = dryRun;
            this.hash = hash;
            this.snapshot = snapshot;
            this.batch = batch;
            this.data = data;
        }

        public Status status() {
            return status;
        }

        public Counts counts() {
            return counts;
        }

        public boolean dryRun() {
            return dryRun;
        }

        public String hash() {
            return hash;
        }

        public Snapshot snapshot() {
            return snapshot;
        }

        public ChangeBatch batch() {
            return batch;
        }

        public boolean imported() {
            return status == Status.IMPORTED && !dryRun;
        }
    }
}
