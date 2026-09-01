package com.mrleonardos.codeperms.platform;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.mrleonardos.codecore.api.config.ConfigFile;
import com.mrleonardos.codeperms.api.manage.ChangeEvent;
import com.mrleonardos.codeperms.api.store.Change;
import com.mrleonardos.codeperms.api.store.ChangeBatch;
import com.mrleonardos.codeperms.api.store.OperationResult;
import com.mrleonardos.codeperms.internal.PermsSettings;
import com.mrleonardos.codeperms.internal.admin.ChangeCoalescer;
import com.mrleonardos.codeperms.internal.command.PermsMaintenance;
import com.mrleonardos.codeperms.internal.store.CoreGroupsImporter;
import com.mrleonardos.codeperms.internal.store.PermsGroupsFile;
import com.mrleonardos.codeperms.internal.store.SingleWriterImpl;

final class PlatformMaintenance implements PermsMaintenance {

    private final ConfigFile<PermsSettings> settings;
    private final ConfigFile<PermsGroupsFile> groups;
    private final ConfigFile<JsonObject> players;
    private final SingleWriterImpl writer;
    private final CoreGroupsImporter importer;
    private final DefaultNodes defaults;
    private final ChangeCoalescer coalescer;

    PlatformMaintenance(ConfigFile<PermsSettings> settings, ConfigFile<PermsGroupsFile> groups,
        ConfigFile<JsonObject> players, SingleWriterImpl writer, CoreGroupsImporter importer, DefaultNodes defaults,
        ChangeCoalescer coalescer) {
        this.settings = settings;
        this.groups = groups;
        this.players = players;
        this.writer = writer;
        this.importer = importer;
        this.defaults = defaults;
        this.coalescer = coalescer;
    }

    @Override
    public OperationResult reload() {
        OperationResult reloaded = writer.reload(() -> {
            settings.reload();
            groups.reload();
            players.reload();
        });
        if (!reloaded.successful()) {
            return reloaded;
        }
        defaults.refresh();
        return OperationResult.success(
            writer.snapshot()
                .toString());
    }

    @Override
    public OperationResult importFromCore(boolean dryRun, boolean force) {
        CoreGroupsImporter.Result result = importer.run(writer.snapshot(), force, dryRun);
        switch (result.status()) {
            case IMPORTED:
                if (!result.imported()) {
                    return OperationResult.success(report(result.counts()));
                }
                OperationResult applied = writer.commit(result.snapshot(), result.batch());
                if (!applied.successful()) {
                    return applied;
                }
                if (!importer.mark(result)) {
                    return markerFailure();
                }
                record(result.batch());
                return OperationResult.success(report(result.counts()));
            case NOTHING_TO_DO:
                if (!result.dryRun() && !importer.mark(result)) {
                    return markerFailure();
                }
                return OperationResult.success("nothing to import");
            case SOURCE_MISSING:
                return OperationResult.failure(
                    OperationResult.Failure.NOT_FOUND,
                    importer.coreFile()
                        .toString());
            case SOURCE_CHANGED:
                return OperationResult.failure(
                    OperationResult.Failure.INVALID_VALUE,
                    importer.coreFile()
                        .toString() + " changed since the last import");
            case SOURCE_UNREADABLE:
                return OperationResult.failure(
                    OperationResult.Failure.INVALID_VALUE,
                    importer.coreFile()
                        .toString() + " is not readable");
            default:
                return OperationResult.failure(
                    OperationResult.Failure.UNSUPPORTED,
                    result.status()
                        .name());
        }
    }

    private static OperationResult markerFailure() {
        return OperationResult.failure(
            OperationResult.Failure.PROVIDER_FAILED,
            "the change was applied, but the import marker was not written");
    }

    @Override
    public OperationResult exportToCoreFormat() {
        CoreGroupsImporter.Result result = importer.export(writer.snapshot());
        if (result.status() == CoreGroupsImporter.Status.EXPORTED) {
            return OperationResult.success(report(result.counts()));
        }
        return OperationResult.failure(
            OperationResult.Failure.PROVIDER_FAILED,
            result.status()
                .name());
    }

    private void record(ChangeBatch batch) {
        List<ChangeEvent.Subject> groups = new ArrayList<>();
        List<ChangeEvent.Subject> players = new ArrayList<>();
        for (Change change : batch.changes()) {
            if (change.kind() == Change.Kind.PLAYER) {
                players.add(ChangeEvent.Subject.player(UUID.fromString(change.subject())));
            } else if (change.kind() == Change.Kind.GROUP) {
                groups.add(ChangeEvent.Subject.group(change.subject()));
            }
        }
        if (!groups.isEmpty()) {
            coalescer.record(ChangeEvent.of(ChangeEvent.Kind.GROUPS, groups, batch.cause()));
        }
        if (!players.isEmpty()) {
            coalescer.record(ChangeEvent.of(ChangeEvent.Kind.NODES, players, batch.cause()));
            coalescer.record(ChangeEvent.of(ChangeEvent.Kind.MEMBERSHIP, players, batch.cause()));
        }
    }

    private static String report(CoreGroupsImporter.Counts counts) {
        StringBuilder text = new StringBuilder();
        append(text, counts.groups(), "group");
        append(text, counts.players(), "player");
        append(text, counts.nodes(), "node");
        append(text, counts.meta(), "meta value");
        return text.length() == 0 ? "nothing" : text.toString();
    }

    private static void append(StringBuilder text, int count, String what) {
        if (count == 0) {
            return;
        }
        if (text.length() > 0) {
            text.append(", ");
        }
        text.append(count)
            .append(' ')
            .append(what);
        if (count > 1) {
            text.append('s');
        }
    }
}
