package com.mrleonardos.codeperms.platform;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mrleonardos.codecore.api.CodeApi;
import com.mrleonardos.codecore.api.actor.PlayerRef;
import com.mrleonardos.codecore.api.config.ConfigFile;
import com.mrleonardos.codecore.api.config.ConfigRoles;
import com.mrleonardos.codecore.api.config.ConfigService;
import com.mrleonardos.codecore.api.service.PermissionService;
import com.mrleonardos.codecore.api.util.Scheduler;
import com.mrleonardos.codecore.platform.PlayerRefs;
import com.mrleonardos.codeperms.Tags;
import com.mrleonardos.codeperms.api.PermsApi;
import com.mrleonardos.codeperms.api.PermsLimits;
import com.mrleonardos.codeperms.api.manage.ChangeEvent;
import com.mrleonardos.codeperms.api.model.Snapshot;
import com.mrleonardos.codeperms.api.store.ChangeBatch;
import com.mrleonardos.codeperms.internal.MainSettings;
import com.mrleonardos.codeperms.internal.PermsSettings;
import com.mrleonardos.codeperms.internal.admin.AuditLine;
import com.mrleonardos.codeperms.internal.admin.ChangeCoalescer;
import com.mrleonardos.codeperms.internal.admin.PermsAdminImpl;
import com.mrleonardos.codeperms.internal.command.DebugView;
import com.mrleonardos.codeperms.internal.command.PermsCommands;
import com.mrleonardos.codeperms.internal.engine.ResolverImpl;
import com.mrleonardos.codeperms.internal.store.CoreGroupsImporter;
import com.mrleonardos.codeperms.internal.store.GroupsStore;
import com.mrleonardos.codeperms.internal.store.PlayersStore;
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

    private MainSettings main;
    private ConfigFile<PermsSettings> settings;
    private ServerThreads threads;
    private SingleWriterImpl writer;
    private OperatorWatch operators;
    private PlayerContexts contexts;
    private CoreGroupsImporter importer;
    private ForgeLifecycle lifecycle;
    private boolean listening;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOG.info("CodePerms {} is starting up", Tags.VERSION);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        CodeApi.adapters()
            .offer(new PermsRoleAdapter(this::assemble));
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        if (writer == null) {
            LOG.info("CodePerms stands aside, the permissions role is held by {}", owner());
            return;
        }
        threads.attach(Thread.currentThread());
        PermsApi.freeze();
        writer.start();
        contexts.clear();
        for (PlayerRef player : PlayerRefs.allOnline()) {
            operators.onJoin(player.id());
        }
        if (!listening) {
            listening = true;
            FMLCommonHandler.instance()
                .bus()
                .register(lifecycle);
        }
        summary();
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        if (writer == null) {
            return;
        }
        writer.stop();
        contexts.clear();
        operators.clear();
    }

    /**
     * Собрать мод целиком. Зовётся реестром адаптеров у победителя роли, поэтому до этого места мод не
     * открывает ни одного файла и не заводит ни одной команды.
     */
    private PermissionService assemble() {
        ConfigService configs = CodeApi.configs();
        Scheduler scheduler = CodeApi.scheduler();

        main = new MainSettings(configs);
        settings = configs.open(PermsSettings.spec());
        PermsLimits limits = settings.get()
            .ceilings(LOG);
        DefaultNodes defaults = new DefaultNodes(settings, limits, LOG);
        defaults.refresh();

        threads = new ServerThreads(scheduler);
        writer = SingleWriterImpl.create(configs, main, threads, LOG);
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
        operators = new OperatorWatch(admin, settings, main, writer::snapshot, LOG);
        contexts = new PlayerContexts(PermsApi.contexts(), writer::snapshot, operators, LOG);
        NameResolver names = new NameResolver(writer::snapshot);
        SenderSubjects subjects = new SenderSubjects(names, contexts);

        importer = new CoreGroupsImporter(configs, limits, LOG);
        PlatformMaintenance maintenance = new PlatformMaintenance(
            settings,
            configs.open(GroupsStore.spec()),
            configs.open(PlayersStore.spec()),
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

        lifecycle = new ForgeLifecycle(contexts, operators);
        CodeApi.commands()
            .register(commands.root());
        return new CorePermissionService(writer::snapshot, resolver, subjects, main::logChecks);
    }

    private void audit(ChangeBatch batch) {
        if (batch.isEmpty() || !main.logChanges()) {
            return;
        }
        LOG.info("Permission change: {}", AuditLine.of(batch));
    }

    private void summary() {
        Snapshot snapshot = writer.snapshot();
        LOG.info(
            "Permissions role is held by {}, storage provider {} holds {} group(s), {} track(s) and {} player(s), core permissions file {}",
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

    private static String owner() {
        String named = CodeApi.adapters()
            .owner(ConfigRoles.PERMISSIONS);
        return named == null ? "nobody" : named;
    }

    private String importState() {
        if (importer.pendingImport()) {
            return "waits for /perms import";
        }
        return importer.available() ? "was imported before" : "is absent";
    }
}
