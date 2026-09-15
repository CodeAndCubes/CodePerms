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
import com.mrleonardos.codeperms.internal.Ceilings;
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
    private final Ceilings ceilings;

    PlatformMaintenance(ConfigFile<PermsSettings> settings, ConfigFile<PermsGroupsFile> groups,
        ConfigFile<JsonObject> players, SingleWriterImpl writer, CoreGroupsImporter importer, DefaultNodes defaults,
        ChangeCoalescer coalescer, Ceilings ceilings) {
        this.settings = settings;
        this.groups = groups;
        this.players = players;
        this.writer = writer;
        this.importer = importer;
        this.defaults = defaults;
        this.coalescer = coalescer;
        this.ceilings = ceilings;
    }

    @Override
    public OperationResult reload() {
        OperationResult reloaded = writer.reload(() -> {
            settings.reload();
            ceilings.refresh();
            groups.reload();
            players.reload();
        });
        if (!reloaded.successful()) {
            return reloaded;
        }
        defaults.refresh();
        return OperationResult.success();
    }

    @Override
    public PermsMaintenance.Outcome importFromCore(boolean dryRun, boolean force) {
        CoreGroupsImporter.Result result = importer.run(writer.snapshot(), force, dryRun);
        switch (result.status()) {
            case IMPORTED:
                if (!result.imported()) {
                    return new PermsMaintenance.Outcome(OperationResult.success(), result.counts());
                }
                OperationResult applied = writer.commit(result.snapshot(), result.batch());
                if (!applied.successful()) {
                    return new PermsMaintenance.Outcome(applied, result.counts());
                }
                if (!importer.mark(result)) {
                    return new PermsMaintenance.Outcome(markerFailure(), result.counts());
                }
                record(result.batch());
                return new PermsMaintenance.Outcome(OperationResult.success(), result.counts());
            case NOTHING_TO_DO:
                if (!result.dryRun() && !importer.mark(result)) {
                    return new PermsMaintenance.Outcome(markerFailure(), result.counts());
                }
                return new PermsMaintenance.Outcome(OperationResult.success(), result.counts());
            case SOURCE_MISSING:
                return new PermsMaintenance.Outcome(sourceMissing(), result.counts());
            case SOURCE_CHANGED:
                return new PermsMaintenance.Outcome(sourceChanged(), result.counts());
            case SOURCE_UNREADABLE:
                return new PermsMaintenance.Outcome(sourceUnreadable(), result.counts());
            default:
                return new PermsMaintenance.Outcome(
                    OperationResult.failure(
                        OperationResult.Failure.UNSUPPORTED,
                        result.status()
                            .name()),
                    result.counts());
        }
    }

    private static OperationResult markerFailure() {
        return OperationResult.failure(
            OperationResult.Failure.PROVIDER_FAILED,
            "the change was applied, but the import marker was not written");
    }

    private OperationResult sourceMissing() {
        return OperationResult.failure(
            OperationResult.Failure.NOT_FOUND,
            importer.coreFile()
                .toString());
    }

    private OperationResult sourceChanged() {
        return OperationResult.failure(
            OperationResult.Failure.INVALID_VALUE,
            importer.coreFile() + " changed since the last import, repeat the import from scratch");
    }

    private OperationResult sourceUnreadable() {
        return OperationResult.failure(
            OperationResult.Failure.UNREADABLE_SOURCE,
            importer.coreFile()
                .toString());
    }

    @Override
    public PermsMaintenance.Outcome exportToCoreFormat() {
        CoreGroupsImporter.Result result = importer.export(writer.snapshot());
        if (result.status() == CoreGroupsImporter.Status.EXPORTED) {
            return new PermsMaintenance.Outcome(OperationResult.success(), result.counts());
        }
        return new PermsMaintenance.Outcome(
            OperationResult.failure(
                OperationResult.Failure.PROVIDER_FAILED,
                result.status()
                    .name()),
            result.counts());
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
}
