package com.mrleonardos.codeperms.platform;

import net.minecraft.entity.player.EntityPlayerMP;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mrleonardos.codecore.api.CodeApi;
import com.mrleonardos.codecore.api.config.ConfigFile;
import com.mrleonardos.codecore.api.config.ConfigService;
import com.mrleonardos.codecore.api.service.PermissionService;
import com.mrleonardos.codecore.api.util.Players;
import com.mrleonardos.codecore.api.util.Scheduler;
import com.mrleonardos.codeperms.Tags;
import com.mrleonardos.codeperms.api.PermsApi;
import com.mrleonardos.codeperms.api.PermsLimits;
import com.mrleonardos.codeperms.api.manage.ChangeEvent;
import com.mrleonardos.codeperms.api.model.Snapshot;
import com.mrleonardos.codeperms.api.store.ChangeBatch;
import com.mrleonardos.codeperms.internal.PermsSettings;
import com.mrleonardos.codeperms.internal.admin.ChangeCoalescer;
import com.mrleonardos.codeperms.internal.admin.PermsAdminImpl;
import com.mrleonardos.codeperms.internal.command.DebugView;
import com.mrleonardos.codeperms.internal.command.PermsCommands;
import com.mrleonardos.codeperms.internal.engine.ResolverImpl;
import com.mrleonardos.codeperms.internal.store.CoreJsonImporter;
import com.mrleonardos.codeperms.internal.store.JsonGroupsStore;
import com.mrleonardos.codeperms.internal.store.JsonPlayersStore;
import com.mrleonardos.codeperms.internal.store.SingleWriterImpl;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;

@Mod(
    modid = "codeperms",
    name = "CodePerms",
    version = Tags.VERSION,
    dependencies = "required-after:codecore",
    acceptableRemoteVersions = "*")
public final class CodePermsMod {

    public static final Logger LOG = LogManager.getLogger("CodePerms");

    private ConfigFile<PermsSettings> settings;
    private ServerThreads threads;
    private SingleWriterImpl writer;
    private OperatorWatch operators;
    private PlayerContexts contexts;
    private CoreJsonImporter importer;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOG.info("CodePerms {} is starting up", Tags.VERSION);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        ConfigService configs = CodeApi.configs();
        Scheduler scheduler = CodeApi.scheduler();

        settings = configs.open(PermsSettings.spec());
        PermsLimits limits = settings.get()
            .ceilings(LOG);
        DefaultNodes defaults = new DefaultNodes(settings, limits, LOG);
        defaults.refresh();

        threads = new ServerThreads(scheduler);
        writer = SingleWriterImpl.create(configs, threads, LOG);
        ChangeCoalescer coalescer = new ChangeCoalescer(LOG);
        ResolverImpl resolver = new ResolverImpl(System::currentTimeMillis, defaults);
        PermsAdminImpl admin = new PermsAdminImpl(writer, coalescer, limits, resolver);
        PermsApi.install(admin, coalescer);
        writer.sink((batch, events) -> {
            audit(batch);
            for (ChangeEvent change : events) {
                coalescer.record(change);
            }
            threads.afterTicks(1, coalescer::dispatch);
        });
        operators = new OperatorWatch(admin, settings, writer::snapshot, LOG);
        contexts = new PlayerContexts(PermsApi.contexts(), writer::snapshot, operators, LOG);
        NameResolver names = new NameResolver(writer::snapshot);
        SenderSubjects subjects = new SenderSubjects(names, contexts);

        importer = new CoreJsonImporter(
            CoreJsonImporter.coreFileOf(configs),
            configs.directory(PermsSettings.MODID)
                .resolve(PermsSettings.EXPORT_DIRECTORY),
            limits,
            LOG);
        PlatformMaintenance maintenance = new PlatformMaintenance(
            settings,
            configs.open(JsonGroupsStore.spec()),
            configs.open(JsonPlayersStore.spec()),
            writer,
            importer,
            defaults,
            coalescer);

        PermsCommands commands = new PermsCommands(
            writer,
            admin,
            limits,
            new PlatformArguments(names, writer::snapshot, limits),
            subjects,
            maintenance,
            new DebugView(resolver));

        ForgeLifecycle lifecycle = new ForgeLifecycle(contexts, operators);
        CodeApi.commands()
            .register(commands.root());
        ServiceBridge.register(
            new CorePermissionService(
                writer::snapshot,
                resolver,
                subjects,
                subjects::playerOf,
                () -> settings.get().audit.logChecks),
            settings.get()
                .priority(LOG));
        FMLCommonHandler.instance()
            .bus()
            .register(lifecycle);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        threads.attach(Thread.currentThread());
        PermsApi.freeze();
        writer.start();
        contexts.clear();
        for (EntityPlayerMP player : Players.allOnline()) {
            operators.onJoin(player.getUniqueID());
        }
        summary();
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        writer.stop();
        contexts.clear();
        operators.clear();
    }

    private void audit(ChangeBatch batch) {
        if (batch.isEmpty() || !settings.get().audit.logChanges) {
            return;
        }
        LOG.info("Permission change: {}", batch);
    }

    private void summary() {
        Snapshot snapshot = writer.snapshot();
        LOG.info(
            "PermissionService is held by {}, storage provider {} holds {} group(s), {} track(s) and {} player(s), core permissions file {}",
            owner(),
            writer.store()
                .id(),
            Integer.valueOf(
                snapshot.groups()
                    .size()),
            Integer.valueOf(
                snapshot.tracks()
                    .size()),
            Integer.valueOf(
                snapshot.users()
                    .size()),
            importState());
    }

    private String owner() {
        return CodeApi.services()
            .find(PermissionService.class)
            .map(
                service -> service.getClass()
                    .getName())
            .orElse("nobody");
    }

    private String importState() {
        if (importer.pendingImport()) {
            return "waits for /perms import";
        }
        return importer.available() ? "was imported before" : "is absent";
    }
}
