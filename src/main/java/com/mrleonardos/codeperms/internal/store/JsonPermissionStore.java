package com.mrleonardos.codeperms.internal.store;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.logging.log4j.Logger;

import com.google.gson.JsonObject;
import com.mrleonardos.codecore.api.config.ConfigFile;
import com.mrleonardos.codeperms.api.PermsLimits;
import com.mrleonardos.codeperms.api.model.GroupRecord;
import com.mrleonardos.codeperms.api.model.Snapshot;
import com.mrleonardos.codeperms.api.model.TrackRecord;
import com.mrleonardos.codeperms.api.model.UserRecord;
import com.mrleonardos.codeperms.api.store.Change;
import com.mrleonardos.codeperms.api.store.ChangeBatch;
import com.mrleonardos.codeperms.api.store.OperationResult;
import com.mrleonardos.codeperms.api.store.PermissionStore;
import com.mrleonardos.codeperms.internal.MainSettings;
import com.mrleonardos.codeperms.internal.PermsSettings;

public final class JsonPermissionStore implements PermissionStore {

    public static final String ID = "json";

    private final ConfigFile<PermsSettings> settings;
    private final MainSettings main;
    private final GroupsStore groups;
    private final PlayersStore players;
    private final Logger log;

    private volatile Snapshot state;

    public JsonPermissionStore(ConfigFile<PermsSettings> settings, MainSettings main,
        ConfigFile<PermsGroupsFile> groupsFile, ConfigFile<JsonObject> playersFile, PermsLimits limits, Logger log) {
        this.settings = settings;
        this.main = main;
        this.groups = new GroupsStore(groupsFile, limits, log);
        this.players = new PlayersStore(playersFile, limits, log);
        this.log = log;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Snapshot load() {
        SnapshotCodec codec = new SnapshotCodec(ceilings());
        SnapshotCodec.DecodedGroups decodedGroups = groups.load();
        SnapshotCodec.DecodedPlayers decodedPlayers = players.load();
        if (decodedGroups.dropped() > 0) {
            log.warn("{} record(s) in perms-groups.toml are unusable and were skipped", decodedGroups.dropped());
        }
        if (decodedPlayers.dropped() > 0) {
            log.warn("{} record(s) in perms-players.json are unusable and were skipped", decodedPlayers.dropped());
        }
        Snapshot snapshot = assemble(decodedGroups.groups(), decodedGroups.tracks(), decodedPlayers.players(), 0L);
        state = snapshot;
        return snapshot;
    }

    @Override
    public OperationResult apply(ChangeBatch batch) {
        state = patch(state(), batch);
        return OperationResult.success();
    }

    @Override
    public OperationResult save(Snapshot snapshot) {
        state = snapshot;
        List<GroupRecord> groupRecords = values(snapshot.groups());
        List<TrackRecord> tracks = values(snapshot.tracks());
        List<UserRecord> users = values(snapshot.users());
        groups.save(groupRecords, tracks);
        players.save(users);
        return OperationResult.success();
    }

    public Snapshot state() {
        Snapshot known = state;
        return known == null ? load() : known;
    }

    private Snapshot assemble(List<GroupRecord> groupRecords, List<TrackRecord> tracks, List<UserRecord> users,
        long revision) {
        Snapshot.Builder builder = Snapshot.builder()
            .revision(revision)
            .defaultGroup(main.defaultGroup())
            .opGroup(main.opGroup());
        for (GroupRecord group : groupRecords) {
            builder.group(group);
        }
        for (TrackRecord track : tracks) {
            builder.track(track);
        }
        for (UserRecord user : users) {
            builder.user(user);
        }
        return builder.build();
    }

    private PermsLimits ceilings() {
        return settings.get()
            .ceilings(log);
    }

    static Snapshot patch(Snapshot current, ChangeBatch batch) {
        Snapshot.Builder builder = Snapshot.builder()
            .revision(current.revision() + 1L)
            .defaultGroup(current.defaultGroup())
            .opGroup(current.opGroup())
            .from(current);
        for (Change change : batch.changes()) {
            boolean removal = change.operation() == Change.Operation.REMOVE;
            switch (change.kind()) {
                case GROUP:
                    if (removal) {
                        builder.removeGroup(change.subject());
                    } else {
                        builder.group(
                            change.group()
                                .get());
                    }
                    break;
                case TRACK:
                    if (removal) {
                        builder.removeTrack(change.subject());
                    } else {
                        builder.track(
                            change.track()
                                .get());
                    }
                    break;
                default:
                    if (removal) {
                        builder.removeUser(UUID.fromString(change.subject()));
                    } else {
                        builder.user(
                            change.player()
                                .get());
                    }
                    break;
            }
        }
        return builder.build();
    }

    private static <K, V> List<V> values(Map<K, V> values) {
        return new ArrayList<>(values.values());
    }
}
