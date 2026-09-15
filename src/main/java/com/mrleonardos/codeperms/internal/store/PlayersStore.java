package com.mrleonardos.codeperms.internal.store;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import org.apache.logging.log4j.Logger;

import com.google.gson.JsonObject;
import com.mrleonardos.codecore.api.config.ConfigFile;
import com.mrleonardos.codecore.api.config.ConfigFormat;
import com.mrleonardos.codecore.api.config.ConfigRoles;
import com.mrleonardos.codecore.api.config.ConfigScope;
import com.mrleonardos.codecore.api.config.ConfigSpec;
import com.mrleonardos.codecore.api.config.Migration;
import com.mrleonardos.codeperms.api.PermsLimits;
import com.mrleonardos.codeperms.api.model.UserRecord;
import com.mrleonardos.codeperms.internal.PermsSettings;

public final class PlayersStore {

    private final ConfigFile<JsonObject> file;
    private final Supplier<PermsLimits> limits;
    private final Logger log;

    private volatile SnapshotCodec.Quarantine quarantine = SnapshotCodec.Quarantine.empty();

    public static ConfigSpec<JsonObject> spec() {
        ConfigSpec.Builder<JsonObject> builder = ConfigSpec
            .of(PermsSettings.MODID, PermsSettings.PLAYERS_FILE, JsonObject.class)
            .role(ConfigRoles.PERMISSIONS)
            .scope(ConfigScope.SETTINGS)
            .format(ConfigFormat.JSON)
            .schemaVersion(SchemaMigrations.PLAYERS_VERSION);
        for (Migration migration : SchemaMigrations.playersChain()) {
            builder.migration(migration);
        }
        return builder.defaults(PlayersStore::defaults)
            .build();
    }

    public PlayersStore(ConfigFile<JsonObject> file, Supplier<PermsLimits> limits, Logger log) {
        this.file = file;
        this.limits = limits;
        this.log = log;
    }

    public SnapshotCodec.DecodedPlayers load() {
        SnapshotCodec.DecodedPlayers decoded = new SnapshotCodec(limits.get()).readPlayers(file.get(), log);
        quarantine = decoded.quarantine();
        return decoded;
    }

    public void save(List<UserRecord> players) {
        new SnapshotCodec(limits.get()).writePlayers(file.get(), players, quarantine);
        file.save();
    }

    public static JsonObject defaults() {
        SnapshotCodec codec = new SnapshotCodec(PermsLimits.defaults());
        JsonObject file = codec.emptyPlayersFile();
        codec.writePlayers(file, Collections.<UserRecord>emptyList());
        return file;
    }
}
