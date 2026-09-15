package com.mrleonardos.codeperms.internal.store;

import java.io.IOException;
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
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;

import org.apache.logging.log4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mrleonardos.codecore.api.config.ConfigFile;
import com.mrleonardos.codecore.api.config.ConfigRoles;
import com.mrleonardos.codecore.api.config.ConfigScope;
import com.mrleonardos.codecore.api.config.ConfigService;
import com.mrleonardos.codecore.api.config.ConfigSpec;
import com.mrleonardos.codeperms.api.PermsLimits;
import com.mrleonardos.codeperms.api.model.ChangeCause;
import com.mrleonardos.codeperms.api.model.GroupRecord;
import com.mrleonardos.codeperms.api.model.NodeEntry;
import com.mrleonardos.codeperms.api.model.Snapshot;
import com.mrleonardos.codeperms.api.model.UserRecord;
import com.mrleonardos.codeperms.api.store.ChangeBatch;
import com.mrleonardos.codeperms.internal.PermsSettings;

public final class CoreGroupsImporter {

    public static final String MARKER_FIELD = "codepermsImport";
    public static final String MARKER_HASH = "sha256";
    public static final String MARKER_TIME = "importedAt";

    public static final String CORE_MODID = "codecore";
    public static final String CORE_NAME = "groups";
    public static final String CORE_FILE = "core-groups.toml";

    private static final String GROUPS_FIELD = SnapshotCodec.GROUPS;
    private static final String INHERITS_FIELD = SnapshotCodec.INHERITS;
    private static final String NODES_FIELD = SnapshotCodec.NODES;
    private static final String META_FIELD = SnapshotCodec.META;
    private static final String GROUP_FIELD = SnapshotCodec.GRANT_GROUP;

    private final ConfigService configs;
    private final Path coreFile;
    private final Path exportDirectory;
    private final Supplier<PermsLimits> limits;
    private final Logger log;

    private ConfigFile<CoreGroupsView> file;

    public CoreGroupsImporter(ConfigService configs, Supplier<PermsLimits> limits, Logger log) {
        this.configs = configs;
        this.coreFile = configs.directory(ConfigRoles.PERMISSIONS)
            .resolve(CORE_FILE);
        this.exportDirectory = configs.directory(ConfigRoles.PERMISSIONS)
            .resolve(PermsSettings.EXPORT_DIRECTORY);
        this.limits = limits;
        this.log = log;
    }

    /**
     * Файл ядра описан чужим: мы его читаем, а не ведём. Ядро тогда не создаёт его за нас, не
     * дописывает в него свои поля и не отодвигает нечитаемый в {@code .broken}, поэтому импорт не
     * трогает источник до тех пор, пока его не пометят перенесённым.
     */
    public static ConfigSpec<CoreGroupsView> spec() {
        return ConfigSpec.of(CORE_MODID, CORE_NAME, CoreGroupsView.class)
            .role(ConfigRoles.PERMISSIONS)
            .scope(ConfigScope.SETTINGS)
            .foreign()
            .build();
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
        CoreGroupsView view = read();
        return view == null || view.codepermsImport == null;
    }

    public Result run(Snapshot current, boolean force, boolean dryRun) {
        if (!available()) {
            return Result.of(Status.SOURCE_MISSING, new Counts(), dryRun, null);
        }
        CoreGroupsView view = read();
        if (view == null) {
            return Result.of(Status.SOURCE_UNREADABLE, new Counts(), dryRun, null);
        }
        String imported = markerHash(view);
        String hash = hashOf(canonical(view));
        if (imported != null && !imported.equals(hash) && !force) {
            return Result.of(Status.SOURCE_CHANGED, new Counts(), dryRun, hash);
        }

        Counts counts = new Counts();
        List<GroupRecord> groups = readGroups(view, counts);
        List<UserRecord> players = readPlayers(view, counts);
        if (groups.isEmpty() && players.isEmpty()) {
            return new Result(Status.NOTHING_TO_DO, counts, dryRun, hash, null, null);
        }

        Snapshot next = merge(current, groups, players);
        ChangeBatch batch = batchOf(groups, players);
        return new Result(Status.IMPORTED, counts, dryRun, hash, next, batch);
    }

    /**
     * Пометить файл ядра как перенесённый. Зовётся только после того, как снимок дошёл до хранилища:
     * маркер обещает, что импорт состоялся.
     *
     * @return удалось ли записать маркер
     */
    public boolean mark(Result result) {
        if (result.hash() == null || file == null || !file.loaded()) {
            return false;
        }
        CoreGroupsView view = file.get();
        JsonObject marker = new JsonObject();
        marker.addProperty(MARKER_HASH, result.hash());
        marker.addProperty(MARKER_TIME, Long.valueOf(System.currentTimeMillis()));
        view.codepermsImport = marker;
        file.save();
        return true;
    }

    public Result export(Snapshot snapshot) {
        Counts counts = new Counts();
        CoreGroupsView view = new CoreGroupsView();
        view.groups = groupsOf(snapshot, counts);
        view.players = playersOf(snapshot, counts);
        Path target = exportDirectory.resolve(CORE_FILE);
        try {
            Files.createDirectories(exportDirectory);
            write(target, view);
        } catch (IOException failure) {
            log.error("Export to {} failed: {}", target, failure.toString());
            return Result.of(Status.EXPORT_FAILED, counts, false, null);
        }
        return new Result(Status.EXPORTED, counts, false, null, null, null);
    }

    /** Прочитать файл ядра. Нечитаемый остаётся на месте: за него отвечает признак чужого файла. */
    private CoreGroupsView read() {
        ConfigFile<CoreGroupsView> opened = opened();
        return opened.loaded() ? opened.get() : null;
    }

    private ConfigFile<CoreGroupsView> opened() {
        if (file == null) {
            file = configs.open(spec());
            return file;
        }
        file.reload();
        return file;
    }

    private List<GroupRecord> readGroups(CoreGroupsView view, Counts counts) {
        List<GroupRecord> groups = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : object(view.groups).entrySet()) {
            if (groups.size() >= limits().groups()) {
                counts.groupsSkipped++;
                continue;
            }
            String id = entry.getKey();
            if (!limits().acceptsGroupId(id)) {
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

    private List<UserRecord> readPlayers(CoreGroupsView view, Counts counts) {
        List<UserRecord> players = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : object(view.players).entrySet()) {
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
            if (nodes.size() >= limits().nodesPerSubject() || !element.isJsonPrimitive()) {
                counts.nodesSkipped++;
                continue;
            }
            try {
                nodes.add(NodeEntry.parse(element.getAsString(), limits()));
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
            if (text.length() > limits().metaValueLength() || meta.size() >= limits().metaKeysPerSubject()) {
                counts.metaSkipped++;
                continue;
            }
            meta.put(entry.getKey(), text);
            counts.meta++;
        }
        return meta;
    }

    private PermsLimits limits() {
        return limits.get();
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

    private Snapshot merge(Snapshot current, List<GroupRecord> groups, List<UserRecord> players) {
        Snapshot.Builder builder = Snapshot.builder()
            .revision(current.revision() + 1L)
            .defaultGroup(current.defaultGroup())
            .opGroup(current.opGroup())
            .from(current);
        for (GroupRecord group : groups) {
            builder.group(group);
        }
        for (UserRecord player : players) {
            builder.user(player);
        }
        return builder.build();
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

    private static String markerHash(CoreGroupsView view) {
        return view.codepermsImport == null ? null : text(view.codepermsImport.get(MARKER_HASH));
    }

    private static void write(Path target, CoreGroupsView view) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            TomlExport.write(writer, view);
        }
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Значения обеих секций в одном порядке при любом чтении.
     *
     * <p>
     * Хеш считается по нему, поэтому дописанный человеком комментарий и переставленные строки правкой
     * источника не считаются: в дерево значений они не попадают.
     */
    static byte[] canonical(CoreGroupsView view) {
        StringBuilder text = new StringBuilder();
        appendSorted(text, object(view.groups));
        text.append('|');
        appendSorted(text, object(view.players));
        return text.toString()
            .getBytes(StandardCharsets.UTF_8);
    }

    private static void appendSorted(StringBuilder text, JsonObject data) {
        text.append('{');
        for (Map.Entry<String, JsonElement> entry : sorted(data).entrySet()) {
            text.append(entry.getKey())
                .append('=');
            appendValue(text, entry.getValue());
            text.append(';');
        }
        text.append('}');
    }

    private static void appendValue(StringBuilder text, JsonElement value) {
        if (value == null || value.isJsonNull()) {
            text.append("null");
            return;
        }
        if (value.isJsonObject()) {
            appendSorted(text, value.getAsJsonObject());
            return;
        }
        if (value.isJsonArray()) {
            text.append('[');
            for (JsonElement element : value.getAsJsonArray()) {
                appendValue(text, element);
                text.append(',');
            }
            text.append(']');
            return;
        }
        text.append(value.getAsString());
    }

    private static Map<String, JsonElement> sorted(JsonObject data) {
        Map<String, JsonElement> byKey = new TreeMap<>();
        for (Map.Entry<String, JsonElement> entry : data.entrySet()) {
            byKey.put(entry.getKey(), entry.getValue());
        }
        return byKey;
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

        public int groups;
        public int players;
        public int nodes;
        public int meta;
        public int groupsSkipped;
        public int playersSkipped;
        public int nodesSkipped;
        public int metaSkipped;

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

        static Result of(Status status, Counts counts, boolean dryRun, String hash) {
            return new Result(status, counts, dryRun, hash, null, null);
        }

        Result(Status status, Counts counts, boolean dryRun, String hash, Snapshot snapshot, ChangeBatch batch) {
            this.status = status;
            this.counts = counts;
            this.dryRun = dryRun;
            this.hash = hash;
            this.snapshot = snapshot;
            this.batch = batch;
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
