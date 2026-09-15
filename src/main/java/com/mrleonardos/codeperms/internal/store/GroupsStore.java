package com.mrleonardos.codeperms.internal.store;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codecore.api.config.ConfigFile;
import com.mrleonardos.codecore.api.config.ConfigRoles;
import com.mrleonardos.codecore.api.config.ConfigScope;
import com.mrleonardos.codecore.api.config.ConfigSpec;
import com.mrleonardos.codecore.api.config.Migration;
import com.mrleonardos.codeperms.api.PermsLimits;
import com.mrleonardos.codeperms.api.model.GroupRecord;
import com.mrleonardos.codeperms.api.model.NodeEntry;
import com.mrleonardos.codeperms.api.model.TrackRecord;
import com.mrleonardos.codeperms.internal.PermsSettings;

public final class GroupsStore {

    private final ConfigFile<PermsGroupsFile> file;
    private final Supplier<PermsLimits> limits;
    private final Logger log;

    private volatile SnapshotCodec.Quarantine quarantine = SnapshotCodec.Quarantine.empty();

    public static ConfigSpec<PermsGroupsFile> spec() {
        ConfigSpec.Builder<PermsGroupsFile> builder = ConfigSpec
            .of(PermsSettings.MODID, PermsSettings.GROUPS_FILE, PermsGroupsFile.class)
            .role(ConfigRoles.PERMISSIONS)
            .scope(ConfigScope.SETTINGS)
            .schemaVersion(SchemaMigrations.GROUPS_VERSION);
        for (Migration migration : SchemaMigrations.groupsChain()) {
            builder.migration(migration);
        }
        return builder.defaults(GroupsStore::defaults)
            .build();
    }

    public GroupsStore(ConfigFile<PermsGroupsFile> file, Supplier<PermsLimits> limits, Logger log) {
        this.file = file;
        this.limits = limits;
        this.log = log;
    }

    public SnapshotCodec.DecodedGroups load() {
        SnapshotCodec.DecodedGroups decoded = new SnapshotCodec(limits.get()).readGroups(file.get(), log);
        quarantine = decoded.quarantine();
        return decoded;
    }

    public void save(List<GroupRecord> groups, List<TrackRecord> tracks) {
        new SnapshotCodec(limits.get()).writeGroups(file.get(), groups, tracks, quarantine);
        file.save();
    }

    public static PermsGroupsFile defaults() {
        SnapshotCodec codec = new SnapshotCodec(PermsLimits.defaults());
        PermsGroupsFile file = codec.emptyGroupsFile();
        codec.writeGroups(file, defaultGroups(), defaultTracks());
        return file;
    }

    public static List<GroupRecord> defaultGroups() {
        List<GroupRecord> groups = new ArrayList<>();
        groups.add(GroupRecord.of("player", "", 0, noParents(), noNodes(), noMeta()));
        groups.add(
            GroupRecord.of("admin", "", 100, Arrays.asList("player"), Arrays.asList(NodeEntry.allow("*")), noMeta()));
        return groups;
    }

    public static List<TrackRecord> defaultTracks() {
        List<TrackRecord> tracks = new ArrayList<>();
        tracks.add(TrackRecord.of("main", Arrays.asList("player", "admin")));
        return tracks;
    }

    private static List<String> noParents() {
        return new ArrayList<>();
    }

    private static List<NodeEntry> noNodes() {
        return new ArrayList<>();
    }

    private static Map<String, String> noMeta() {
        return new LinkedHashMap<>();
    }
}
