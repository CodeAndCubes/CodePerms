package com.mrleonardos.codeperms.internal.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mrleonardos.codecore.api.config.ConfigService;
import com.mrleonardos.codeperms.TestConfigs;
import com.mrleonardos.codeperms.api.PermsLimits;
import com.mrleonardos.codeperms.api.model.ChangeCause;
import com.mrleonardos.codeperms.api.model.ContextSet;
import com.mrleonardos.codeperms.api.model.GroupRecord;
import com.mrleonardos.codeperms.api.model.NodeEntry;
import com.mrleonardos.codeperms.api.model.Snapshot;
import com.mrleonardos.codeperms.api.model.TrackRecord;
import com.mrleonardos.codeperms.api.model.UserRecord;
import com.mrleonardos.codeperms.api.store.ChangeBatch;
import com.mrleonardos.codeperms.internal.MainSettings;
import com.mrleonardos.codeperms.internal.PermsSettings;

class JsonPermissionStoreTest {

    private static final Logger LOG = LogManager.getLogger(JsonPermissionStoreTest.class);
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @TempDir
    Path root;

    @Test
    void firstRunCreatesWorkingExample() {
        Snapshot snapshot = store().load();

        assertEquals(
            Arrays.asList("player", "admin"),
            new ArrayList<>(
                snapshot.groups()
                    .keySet()));
        assertNotNull(snapshot.track("main"));
        assertTrue(
            snapshot.users()
                .isEmpty());
        assertEquals("player", snapshot.defaultGroup());
        assertEquals("admin", snapshot.opGroup());
        assertTrue(Files.isRegularFile(permissions().resolve("perms.toml")));
        assertTrue(Files.isRegularFile(permissions().resolve("perms-groups.toml")));
        assertTrue(Files.isRegularFile(permissions().resolve("perms-players.json")));
    }

    @Test
    void defaultAndOperatorGroupsComeFromTheMainFile() {
        TestConfigs.writeMain(root, "[permissions]", "defaultGroup = \"guest\"", "opGroup = \"owner\"");

        Snapshot snapshot = store().load();

        assertEquals("guest", snapshot.defaultGroup());
        assertEquals("owner", snapshot.opGroup());
        assertTrue(
            !TestConfigs.read(permissions().resolve("perms.toml"))
                .contains("defaultGroup"),
            "имя группы по умолчанию живёт ровно в одном файле");
    }

    @Test
    void roundTripKeepsNodesContextsAndExpiry() {
        Snapshot written = snapshot();
        store().save(written);

        Snapshot read = freshStore().load();

        assertEquals(written.groups(), read.groups());
        assertEquals(written.users(), read.users());
        assertEquals(written.tracks(), read.tracks());
    }

    @Test
    void factoryAdminGroupKeepsItsStarNode() {
        Snapshot snapshot = store().load();

        assertEquals(
            Arrays.asList("*"),
            strings(
                snapshot.group("admin")
                    .get()
                    .nodes()));
    }

    @Test
    void roundTripKeepsTheStarNode() {
        Snapshot written = Snapshot.builder()
            .revision(3L)
            .group(
                GroupRecord.of(
                    "admin",
                    "",
                    100,
                    Arrays.asList("player"),
                    Arrays.asList(NodeEntry.parse("*")),
                    new LinkedHashMap<String, String>()))
            .build();
        store().save(written);

        Snapshot read = freshStore().load();

        assertEquals(
            Arrays.asList("*"),
            strings(
                read.group("admin")
                    .get()
                    .nodes()));
    }

    @Test
    void groupReferencesAreNormalizedWhileReading() {
        TestConfigs.write(
            groupsFile(),
            "[groups.player]",
            "nodes = [\"codechat.create\"]",
            "",
            "[groups.admin]",
            "nodes = [\"*\"]",
            "inherits = [\" Player \"]",
            "",
            "[tracks.main]",
            "groups = [\"PLAYER\", \"admin\"]");

        Snapshot snapshot = store().load();

        assertEquals(
            Arrays.asList("player"),
            snapshot.group("admin")
                .get()
                .inherits());
        assertEquals(
            Arrays.asList("player", "admin"),
            snapshot.track("main")
                .get()
                .groups());
    }

    @Test
    void groupDeclaredTwiceTakesTheWholeFileAside() {
        TestConfigs.write(
            groupsFile(),
            "[groups.player]",
            "nodes = [\"codechat.create\"]",
            "",
            "[groups.player]",
            "nodes = [\"codechat.channel.*\"]");

        Snapshot snapshot = store().load();

        assertTrue(
            Files.isRegularFile(permissions().resolve("perms-groups.toml.broken")),
            "toml не разрешает объявить секцию дважды, поэтому файл целиком уходит в .broken");
        assertEquals(
            Arrays.asList("player", "admin"),
            new ArrayList<>(
                snapshot.groups()
                    .keySet()));
    }

    @Test
    void brokenGroupsFileIsSetAsideAndReplacedWithDefaults() {
        TestConfigs.write(groupsFile(), "[groups.player", "nodes = []");

        Snapshot snapshot = store().load();

        assertTrue(Files.isRegularFile(permissions().resolve("perms-groups.toml.broken")));
        assertEquals(
            Arrays.asList("player", "admin"),
            new ArrayList<>(
                snapshot.groups()
                    .keySet()));
    }

    @Test
    void keyTheModDoesNotReadSurvivesTheNextWrite() {
        store().load();
        Path file = groupsFile();
        TestConfigs.write(file, "# заметка админа\nremark = \"перевести vip на треки\"\n" + TestConfigs.read(file));

        JsonPermissionStore store = freshStore();
        store.save(store.load());

        String written = TestConfigs.read(file);
        assertTrue(written.contains("remark = \"перевести vip на треки\""), "чужой ключ остаётся в файле");
        assertTrue(written.contains("# заметка админа"), "строка человека над своим ключом остаётся");
    }

    @Test
    void orderOfGroupsAndOfMetaKeysSurvivesTheWrite() {
        TestConfigs.write(
            groupsFile(),
            "[groups.zeta]",
            "nodes = []",
            "",
            "[groups.zeta.meta]",
            "prefix = \"&7\"",
            "suffix = \"&8\"",
            "rank = \"1\"",
            "",
            "# альфу завёл админ, не трогать",
            "[groups.alpha]",
            "nodes = []",
            "",
            "[groups.middle]",
            "nodes = []");

        JsonPermissionStore store = store();
        Snapshot loaded = store.load();
        store.save(loaded);

        assertEquals(
            Arrays.asList("zeta", "alpha", "middle"),
            new ArrayList<>(
                loaded.groups()
                    .keySet()));
        String written = TestConfigs.read(groupsFile());
        assertTrue(
            written.indexOf("[groups.zeta]") < written.indexOf("[groups.alpha]")
                && written.indexOf("[groups.alpha]") < written.indexOf("[groups.middle]"),
            "группы не тасуются в файле после записи:\n" + written);
        assertTrue(
            written.contains("# альфу завёл админ, не трогать"),
            "строка человека над группой переживает запись:\n" + written);
        assertTrue(
            written.indexOf("prefix") < written.indexOf("suffix")
                && written.indexOf("suffix") < written.indexOf("rank"),
            "ключи меты не тасуются в файле после записи:\n" + written);
    }

    @Test
    void applyPatchesStateAndKeepsRevisionMoving() {
        JsonPermissionStore store = store();
        store.load();

        store.apply(
            ChangeBatch.builder(ChangeCause.COMMAND, "console")
                .upsert(
                    GroupRecord.of(
                        "vip",
                        "",
                        10,
                        new ArrayList<String>(),
                        new ArrayList<NodeEntry>(),
                        new LinkedHashMap<String, String>()))
                .build());

        assertEquals(
            Arrays.asList("player", "admin", "vip"),
            new ArrayList<>(
                store.state()
                    .groups()
                    .keySet()));
        assertEquals(
            1L,
            store.state()
                .revision());
    }

    @Test
    void saveWritesEncodedNodesToTheFile() {
        store().save(snapshot());

        String text = TestConfigs.read(groupsFile());
        assertTrue(text.contains("codechat.channel.*"));
        assertTrue(text.contains("expiresAt"));
        assertTrue(text.contains("contexts"));
    }

    private Path permissions() {
        return TestConfigs.permissions(root);
    }

    private Path groupsFile() {
        return permissions().resolve("perms-groups.toml");
    }

    private Snapshot snapshot() {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("prefix", "&7");
        GroupRecord moderator = GroupRecord.of(
            "moderator",
            "",
            50,
            Arrays.asList("player"),
            Arrays.asList(
                NodeEntry.of("codechat.channel.*", true, ContextSet.empty(), 0L),
                NodeEntry.of(
                    "codechat.format",
                    false,
                    ContextSet.builder()
                        .put("world", "nether")
                        .build(),
                    1500L)),
            meta);
        UserRecord player = UserRecord.of(
            PLAYER,
            "Steve",
            "moderator",
            Arrays.asList(UserRecord.Grant.of("vip", 9000L)),
            Arrays.asList(NodeEntry.parse("-codechat.create")),
            new LinkedHashMap<String, String>());
        return Snapshot.builder()
            .revision(7L)
            .defaultGroup("player")
            .opGroup("admin")
            .group(moderator)
            .track(TrackRecord.of("main", Arrays.asList("player", "moderator")))
            .user(player)
            .build();
    }

    private List<String> strings(List<NodeEntry> nodes) {
        List<String> values = new ArrayList<>();
        for (NodeEntry node : nodes) {
            values.add(node.toShortString());
        }
        return values;
    }

    private JsonPermissionStore store() {
        return storeOf(TestConfigs.of(root));
    }

    private JsonPermissionStore freshStore() {
        return storeOf(TestConfigs.of(root));
    }

    private JsonPermissionStore storeOf(ConfigService configs) {
        configs.open(PermsSettings.spec());
        return new JsonPermissionStore(
            new MainSettings(configs),
            configs.open(GroupsStore.spec()),
            configs.open(PlayersStore.spec()),
            () -> PermsLimits.defaults(),
            LOG);
    }
}
