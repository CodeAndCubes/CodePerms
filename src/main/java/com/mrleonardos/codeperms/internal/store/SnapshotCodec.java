package com.mrleonardos.codeperms.internal.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.logging.log4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mrleonardos.codeperms.api.PermsLimits;
import com.mrleonardos.codeperms.api.model.ContextSet;
import com.mrleonardos.codeperms.api.model.GroupRecord;
import com.mrleonardos.codeperms.api.model.NodeEntry;
import com.mrleonardos.codeperms.api.model.TrackRecord;
import com.mrleonardos.codeperms.api.model.UserRecord;

public final class SnapshotCodec {

    public static final String SCHEMA_VERSION = SchemaMigrations.VERSION_FIELD;
    public static final String GROUPS = "groups";
    public static final String PLAYERS = "players";

    public static final String ID = "id";
    public static final String UUID_FIELD = "uuid";
    public static final String NAME = "name";
    public static final String DISPLAY_NAME = "displayName";
    public static final String WEIGHT = "weight";
    public static final String INHERITS = "inherits";
    public static final String NODES = "nodes";
    public static final String META = "meta";
    public static final String PRIMARY = "primary";
    public static final String GRANT_GROUP = "group";
    public static final String EXPIRES_AT = "expiresAt";
    public static final String CONTEXTS = "contexts";
    public static final String NODE = "node";
    public static final String VALUE = "value";

    private static final char DENY_PREFIX = '-';

    private final PermsLimits limits;

    public SnapshotCodec(PermsLimits limits) {
        this.limits = limits;
    }

    public PermsLimits limits() {
        return limits;
    }

    public PermsGroupsFile emptyGroupsFile() {
        return new PermsGroupsFile();
    }

    public JsonObject emptyPlayersFile() {
        JsonObject file = new JsonObject();
        file.add(PLAYERS, new JsonArray());
        return file;
    }

    public void writeGroups(PermsGroupsFile file, List<GroupRecord> groups, List<TrackRecord> tracks) {
        writeGroups(file, groups, tracks, Quarantine.empty());
    }

    public void writeGroups(PermsGroupsFile file, List<GroupRecord> groups, List<TrackRecord> tracks,
        Quarantine quarantine) {
        file.groups = encodeGroups(groups, quarantine);
        file.tracks = encodeTracks(tracks, quarantine);
    }

    public void writePlayers(JsonObject file, List<UserRecord> players) {
        writePlayers(file, players, Quarantine.empty());
    }

    public void writePlayers(JsonObject file, List<UserRecord> players, Quarantine quarantine) {
        removeSchemaVersion(file);
        file.add(PLAYERS, encodePlayers(players, quarantine));
    }

    public DecodedGroups readGroups(PermsGroupsFile file, Logger log) {
        List<GroupRecord> groups = new ArrayList<>();
        List<TrackRecord> tracks = new ArrayList<>();
        int dropped = 0;
        Tally tally = new Tally();
        Quarantine quarantine = new Quarantine();

        Set<String> knownGroups = new HashSet<>();
        for (JsonElement element : array(file.groups)) {
            if (!element.isJsonObject()) {
                dropped++;
                quarantine.groups.add(element);
                continue;
            }
            Spares spares = new Spares();
            GroupRecord group = readGroup(element.getAsJsonObject(), log, tally, spares);
            if (group == null) {
                dropped++;
                quarantine.groups.add(element);
                continue;
            }
            if (!knownGroups.add(group.id())) {
                dropped++;
                quarantine.groups.add(element);
                warn(log, "Group {} is declared twice, keeping the first declaration", group.id());
                continue;
            }
            if (groups.size() >= limits.groups()) {
                dropped++;
                quarantine.groups.add(element);
                warn(
                    log,
                    "Group {} is beyond the ceiling of {} groups and stays in the file untouched",
                    group.id(),
                    Integer.valueOf(limits.groups()));
                continue;
            }
            groups.add(group);
            quarantine.keep(group.id(), spares);
        }

        Set<String> knownTracks = new HashSet<>();
        for (JsonElement element : array(file.tracks)) {
            if (!element.isJsonObject()) {
                dropped++;
                quarantine.tracks.add(element);
                continue;
            }
            TrackRecord track = readTrack(element.getAsJsonObject(), log);
            if (track == null) {
                dropped++;
                quarantine.tracks.add(element);
                continue;
            }
            if (!knownTracks.add(track.name())) {
                dropped++;
                quarantine.tracks.add(element);
                warn(log, "Track {} is declared twice, keeping the first declaration", track.name());
                continue;
            }
            tracks.add(track);
        }

        return new DecodedGroups(groups, tracks, dropped + tally.total(), quarantine);
    }

    public DecodedPlayers readPlayers(JsonObject file, Logger log) {
        List<UserRecord> players = new ArrayList<>();
        int dropped = 0;
        Set<UUID> known = new HashSet<>();
        Tally tally = new Tally();
        Quarantine quarantine = new Quarantine();

        for (JsonElement element : array(file.get(PLAYERS))) {
            if (!element.isJsonObject()) {
                dropped++;
                quarantine.players.add(element);
                continue;
            }
            Spares spares = new Spares();
            UserRecord player = readPlayer(element.getAsJsonObject(), log, tally, spares);
            if (player == null) {
                dropped++;
                quarantine.players.add(element);
                continue;
            }
            if (!known.add(player.uuid())) {
                dropped++;
                quarantine.players.add(element);
                warn(log, "Player {} is declared twice, keeping the first declaration", player.uuid());
                continue;
            }
            players.add(player);
            quarantine.keep(
                player.uuid()
                    .toString(),
                spares);
        }

        return new DecodedPlayers(players, dropped + tally.total(), quarantine);
    }

    private GroupRecord readGroup(JsonObject data, Logger log, Tally tally, Spares spares) {
        String id = text(data.get(ID));
        if (!limits.acceptsGroupId(id)) {
            warn(log, "Group id {} does not fit the ceiling of {} characters", id, limits.groupIdLength());
            return null;
        }
        String normalized = id.toLowerCase(Locale.ROOT);
        int weight = integer(data.get(WEIGHT), 0);
        List<String> inherits = groupIds(data.get(INHERITS), log);
        Map<String, String> meta = readMeta(data.get(META), log, tally, spares, normalized);
        List<NodeEntry> nodes = readNodes(data.get(NODES), log, tally, spares, normalized);
        try {
            return GroupRecord.of(normalized, text(data.get(DISPLAY_NAME)), weight, inherits, nodes, meta);
        } catch (RuntimeException failure) {
            warn(log, "Group {} is invalid and was skipped: {}", id, failure.getMessage());
            return null;
        }
    }

    private TrackRecord readTrack(JsonObject data, Logger log) {
        String name = text(data.get(NAME));
        List<String> groups = groupIds(data.get(GROUPS), log);
        try {
            if (!limits.acceptsTrackName(name)) {
                warn(log, "Track name {} does not fit the ceiling of {} characters", name, limits.trackNameLength());
                return null;
            }
            return TrackRecord.of(
                name.trim()
                    .toLowerCase(Locale.ROOT),
                groups);
        } catch (RuntimeException failure) {
            warn(log, "Track {} is invalid and was skipped: {}", name, failure.getMessage());
            return null;
        }
    }

    private UserRecord readPlayer(JsonObject data, Logger log, Tally tally, Spares spares) {
        UUID uuid = uuidOf(text(data.get(UUID_FIELD)));
        if (uuid == null) {
            warn(log, "Player record without a readable uuid was skipped");
            return null;
        }
        String subject = uuid.toString();
        String primary = text(data.get(PRIMARY));
        if (primary != null) {
            primary = primary.trim()
                .toLowerCase(Locale.ROOT);
        }
        List<UserRecord.Grant> grants = readGrants(data.get(GROUPS), log, tally);
        List<NodeEntry> nodes = readNodes(data.get(NODES), log, tally, spares, subject);
        Map<String, String> meta = readMeta(data.get(META), log, tally, spares, subject);
        try {
            return UserRecord.of(uuid, text(data.get(NAME)), primary, grants, nodes, meta);
        } catch (RuntimeException failure) {
            warn(log, "Player {} is invalid and was skipped: {}", uuid, failure.getMessage());
            return null;
        }
    }

    private List<UserRecord.Grant> readGrants(JsonElement element, Logger log, Tally tally) {
        List<UserRecord.Grant> grants = new ArrayList<>();
        for (JsonElement raw : array(element)) {
            if (raw.isJsonPrimitive()) {
                addGrant(grants, raw.getAsString(), 0L, log, tally);
                continue;
            }
            if (!raw.isJsonObject()) {
                continue;
            }
            JsonObject data = raw.getAsJsonObject();
            addGrant(grants, text(data.get(GRANT_GROUP)), longOf(data.get(EXPIRES_AT)), log, tally);
        }
        return grants;
    }

    private void addGrant(List<UserRecord.Grant> grants, String groupId, long expiresAt, Logger log, Tally tally) {
        if (groupId == null) {
            return;
        }
        try {
            grants.add(UserRecord.Grant.of(groupId, expiresAt));
        } catch (RuntimeException failure) {
            warn(log, "Grant {} is invalid and was skipped: {}", groupId, failure.getMessage());
        }
    }

    public List<NodeEntry> readNodes(JsonElement element, Logger log) {
        return readNodes(element, log, new Tally(), new Spares(), "");
    }

    private List<NodeEntry> readNodes(JsonElement element, Logger log, Tally tally, Spares spares, String subject) {
        List<NodeEntry> nodes = new ArrayList<>();
        boolean told = false;
        for (JsonElement raw : array(element)) {
            if (nodes.size() >= limits.nodesPerSubject()) {
                tally.nodes++;
                spares.node(raw);
                if (!told) {
                    told = true;
                    warn(
                        log,
                        "Subject {} holds more than {} nodes, the tail stays in the file untouched",
                        subject,
                        Integer.valueOf(limits.nodesPerSubject()));
                }
                continue;
            }
            NodeEntry node = readNode(raw, log);
            if (node == null) {
                tally.nodes++;
                spares.node(raw);
                continue;
            }
            nodes.add(node);
        }
        return nodes;
    }

    private Map<String, String> readMeta(JsonElement element, Logger log, Tally tally, Spares spares, String subject) {
        Map<String, String> values = new LinkedHashMap<>();
        if (element == null || !element.isJsonObject()) {
            return values;
        }
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject()
            .entrySet()) {
            JsonElement value = entry.getValue();
            if (!value.isJsonPrimitive()) {
                tally.meta++;
                spares.meta(entry.getKey(), value);
                warn(log, "Meta {} holds a non-text value and was skipped", entry.getKey());
                continue;
            }
            String text = value.getAsString();
            if (!limits.acceptsMetaValue(text) || values.size() >= limits.metaKeysPerSubject()) {
                tally.meta++;
                spares.meta(entry.getKey(), value);
                warn(
                    log,
                    "Meta {} of {} does not fit the ceilings and stays in the file untouched",
                    entry.getKey(),
                    subject);
                continue;
            }
            values.put(entry.getKey(), text);
        }
        return values;
    }

    public NodeEntry readNode(JsonElement element, Logger log) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            return parseNode(element.getAsString(), ContextSet.empty(), 0L, log);
        }
        if (!element.isJsonObject()) {
            return null;
        }
        JsonObject data = element.getAsJsonObject();
        String node = text(data.get(NODE));
        if (node == null) {
            warn(log, "Node record without a node was skipped");
            return null;
        }
        boolean value = booleanOf(data.get(VALUE), true);
        ContextSet contexts = readContexts(data.get(CONTEXTS));
        long expiresAt = longOf(data.get(EXPIRES_AT));
        return parseNode(value ? node : DENY_PREFIX + node, contexts, expiresAt, log);
    }

    private NodeEntry parseNode(String raw, ContextSet contexts, long expiresAt, Logger log) {
        try {
            NodeEntry parsed = NodeEntry.parse(raw, limits);
            return NodeEntry.of(parsed.node(), parsed.value(), contexts, expiresAt);
        } catch (RuntimeException failure) {
            warn(log, "Node {} does not fit the ceilings and was skipped: {}", raw, failure.getMessage());
            return null;
        }
    }

    public ContextSet readContexts(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return ContextSet.empty();
        }
        ContextSet.Builder builder = ContextSet.builder();
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject()
            .entrySet()) {
            JsonElement value = entry.getValue();
            if (value.isJsonPrimitive()) {
                builder.put(entry.getKey(), value.getAsString());
            }
        }
        return builder.build();
    }

    public JsonArray encodeNodes(List<NodeEntry> nodes) {
        JsonArray array = new JsonArray();
        for (NodeEntry node : nodes) {
            array.add(encodeNode(node));
        }
        return array;
    }

    public JsonElement encodeNode(NodeEntry node) {
        boolean plain = node.contexts()
            .isEmpty() && node.permanent();
        if (plain) {
            return new JsonPrimitive(node.toShortString());
        }
        JsonObject data = new JsonObject();
        data.addProperty(NODE, node.node());
        if (!node.value()) {
            data.addProperty(VALUE, false);
        }
        if (!node.contexts()
            .isEmpty()) {
            JsonObject contexts = new JsonObject();
            for (Map.Entry<String, String> pair : node.contexts()
                .asMap()
                .entrySet()) {
                contexts.addProperty(pair.getKey(), pair.getValue());
            }
            data.add(CONTEXTS, contexts);
        }
        if (!node.permanent()) {
            data.addProperty(EXPIRES_AT, Long.valueOf(node.expiresAt()));
        }
        return data;
    }

    private JsonArray encodeGroups(List<GroupRecord> groups, Quarantine quarantine) {
        JsonArray array = new JsonArray();
        for (GroupRecord group : groups) {
            JsonObject data = new JsonObject();
            data.addProperty(ID, group.id());
            if (!group.displayName()
                .equals(group.id())) {
                data.addProperty(DISPLAY_NAME, group.displayName());
            }
            if (group.weight() != 0) {
                data.addProperty(WEIGHT, Integer.valueOf(group.weight()));
            }
            if (!group.inherits()
                .isEmpty()) {
                data.add(INHERITS, strings(group.inherits()));
            }
            putNodes(data, group.nodes(), quarantine, group.id());
            putMeta(data, group.meta(), quarantine, group.id());
            array.add(data);
        }
        for (JsonElement held : quarantine.groups) {
            array.add(held);
        }
        return array;
    }

    private JsonArray encodeTracks(List<TrackRecord> tracks, Quarantine quarantine) {
        JsonArray array = new JsonArray();
        for (TrackRecord track : tracks) {
            JsonObject data = new JsonObject();
            data.addProperty(NAME, track.name());
            data.add(GROUPS, strings(track.groups()));
            array.add(data);
        }
        for (JsonElement held : quarantine.tracks) {
            array.add(held);
        }
        return array;
    }

    private JsonArray encodePlayers(List<UserRecord> players, Quarantine quarantine) {
        JsonArray array = new JsonArray();
        for (UserRecord player : players) {
            String subject = player.uuid()
                .toString();
            if (isEmptyPlayer(player) && quarantine.isCleanFor(subject)) {
                continue;
            }
            JsonObject data = new JsonObject();
            data.addProperty(UUID_FIELD, subject);
            if (player.name() != null) {
                data.addProperty(NAME, player.name());
            }
            if (player.primary() != null) {
                data.addProperty(PRIMARY, player.primary());
            }
            if (!player.groups()
                .isEmpty()) {
                JsonArray grants = new JsonArray();
                for (UserRecord.Grant grant : player.groups()) {
                    JsonObject grantData = new JsonObject();
                    grantData.addProperty(GRANT_GROUP, grant.groupId());
                    if (!grant.permanent()) {
                        grantData.addProperty(EXPIRES_AT, Long.valueOf(grant.expiresAt()));
                    }
                    grants.add(grantData);
                }
                data.add(GROUPS, grants);
            }
            putNodes(data, player.nodes(), quarantine, subject);
            putMeta(data, player.meta(), quarantine, subject);
            array.add(data);
        }
        for (JsonElement held : quarantine.players) {
            array.add(held);
        }
        return array;
    }

    private void putNodes(JsonObject data, List<NodeEntry> nodes, Quarantine quarantine, String subject) {
        JsonArray encoded = encodeNodes(nodes);
        JsonArray spare = quarantine.nodes.get(subject);
        if (spare != null) {
            for (JsonElement held : spare) {
                encoded.add(held);
            }
        }
        if (encoded.size() > 0) {
            data.add(NODES, encoded);
        }
    }

    private void putMeta(JsonObject data, Map<String, String> meta, Quarantine quarantine, String subject) {
        JsonObject encoded = map(meta);
        JsonObject spare = quarantine.meta.get(subject);
        if (spare != null) {
            for (Map.Entry<String, JsonElement> entry : spare.entrySet()) {
                encoded.add(entry.getKey(), entry.getValue());
            }
        }
        if (encoded.entrySet()
            .size() > 0) {
            data.add(META, encoded);
        }
    }

    private boolean isEmptyPlayer(UserRecord player) {
        return player.name() == null && player.primary() == null
            && player.groups()
                .isEmpty()
            && player.nodes()
                .isEmpty()
            && player.meta()
                .isEmpty();
    }

    private static JsonArray strings(List<String> values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(new JsonPrimitive(value));
        }
        return array;
    }

    private JsonObject map(Map<String, String> values) {
        JsonObject data = new JsonObject();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            data.addProperty(entry.getKey(), entry.getValue());
        }
        return data;
    }

    private static List<String> groupIds(JsonElement element, Logger log) {
        List<String> values = new ArrayList<>();
        for (JsonElement raw : array(element)) {
            if (!raw.isJsonPrimitive()) {
                warn(log, "Expected a text value but found {}", raw);
                continue;
            }
            String normalized = normalizeId(raw.getAsString());
            if (normalized == null) {
                warn(log, "Empty group reference was skipped");
                continue;
            }
            values.add(normalized);
        }
        return values;
    }

    private static String normalizeId(String id) {
        if (id == null) {
            return null;
        }
        String normalized = id.trim()
            .toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private static Iterable<JsonElement> array(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return Collections.emptyList();
        }
        if (element.isJsonArray()) {
            return element.getAsJsonArray();
        }
        return Collections.emptyList();
    }

    private static void removeSchemaVersion(JsonObject file) {
        file.remove(SCHEMA_VERSION);
    }

    private static String text(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        return element.getAsString();
    }

    private static int integer(JsonElement element, int fallback) {
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsInt();
        } catch (RuntimeException malformed) {
            return fallback;
        }
    }

    private static long longOf(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return 0L;
        }
        try {
            return element.getAsLong();
        } catch (RuntimeException malformed) {
            return 0L;
        }
    }

    private static boolean booleanOf(JsonElement element, boolean fallback) {
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsBoolean();
        } catch (RuntimeException malformed) {
            return fallback;
        }
    }

    private static UUID uuidOf(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    private static void warn(Logger log, String message, Object... arguments) {
        if (log != null) {
            log.warn(message, arguments);
        }
    }

    private static final class Tally {

        int nodes;
        int meta;

        int total() {
            return nodes + meta;
        }
    }

    private static final class Spares {

        private JsonArray nodes;
        private JsonObject meta;

        void node(JsonElement raw) {
            if (nodes == null) {
                nodes = new JsonArray();
            }
            nodes.add(raw);
        }

        void meta(String key, JsonElement raw) {
            if (meta == null) {
                meta = new JsonObject();
            }
            meta.add(key, raw);
        }
    }

    /**
     * Записи, которые модель не приняла: они не попадают в снимок, но и не пропадают из файла. Первая же
     * запись на диск иначе стёрла бы всё, что чтение отбросило, и правки человека вернуть было бы
     * неоткуда.
     */
    public static final class Quarantine {

        private static final Quarantine EMPTY = new Quarantine();

        private final List<JsonElement> groups = new ArrayList<>();
        private final List<JsonElement> tracks = new ArrayList<>();
        private final List<JsonElement> players = new ArrayList<>();
        private final Map<String, JsonArray> nodes = new LinkedHashMap<>();
        private final Map<String, JsonObject> meta = new LinkedHashMap<>();

        /** Пустой карантин: чтение ничего не отбросило. */
        public static Quarantine empty() {
            return EMPTY;
        }

        void keep(String subject, Spares spares) {
            if (spares.nodes != null) {
                nodes.put(subject, spares.nodes);
            }
            if (spares.meta != null) {
                meta.put(subject, spares.meta);
            }
        }

        boolean isCleanFor(String subject) {
            return !nodes.containsKey(subject) && !meta.containsKey(subject);
        }

        /** Сколько целых записей отложено. */
        public int records() {
            return groups.size() + tracks.size() + players.size();
        }

        /** Есть ли отложенное вообще. */
        public boolean isEmpty() {
            return records() == 0 && nodes.isEmpty() && meta.isEmpty();
        }
    }

    public static final class DecodedGroups {

        private final List<GroupRecord> groups;
        private final List<TrackRecord> tracks;
        private final int dropped;
        private final Quarantine quarantine;

        DecodedGroups(List<GroupRecord> groups, List<TrackRecord> tracks, int dropped, Quarantine quarantine) {
            this.groups = groups;
            this.tracks = tracks;
            this.dropped = dropped;
            this.quarantine = quarantine;
        }

        public List<GroupRecord> groups() {
            return groups;
        }

        public List<TrackRecord> tracks() {
            return tracks;
        }

        public int dropped() {
            return dropped;
        }

        public Quarantine quarantine() {
            return quarantine;
        }
    }

    public static final class DecodedPlayers {

        private final List<UserRecord> players;
        private final int dropped;
        private final Quarantine quarantine;

        DecodedPlayers(List<UserRecord> players, int dropped, Quarantine quarantine) {
            this.players = players;
            this.dropped = dropped;
            this.quarantine = quarantine;
        }

        public List<UserRecord> players() {
            return players;
        }

        public int dropped() {
            return dropped;
        }

        public Quarantine quarantine() {
            return quarantine;
        }
    }
}
