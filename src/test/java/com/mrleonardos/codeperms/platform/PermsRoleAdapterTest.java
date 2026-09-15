package com.mrleonardos.codeperms.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mrleonardos.codecore.api.adapter.PermissionCapabilities;
import com.mrleonardos.codecore.api.adapter.RoleOwnerKind;
import com.mrleonardos.codecore.api.config.ConfigService;
import com.mrleonardos.codecore.api.service.PermissionService;
import com.mrleonardos.codecore.api.util.Scheduler;
import com.mrleonardos.codeperms.TestConfigs;
import com.mrleonardos.codeperms.internal.Ceilings;
import com.mrleonardos.codeperms.internal.MainSettings;
import com.mrleonardos.codeperms.internal.PermsSettings;
import com.mrleonardos.codeperms.internal.store.SingleWriterImpl;

class PermsRoleAdapterTest {

    private static final Logger LOG = LogManager.getLogger(PermsRoleAdapterTest.class);

    @TempDir
    Path root;

    @Test
    void adapterCallsItselfCodepermsAndNamesEverythingItCanDo() {
        PermsRoleAdapter adapter = new PermsRoleAdapter(StubService::new, () -> true);

        assertEquals("permissions", adapter.role());
        assertEquals("codeperms", adapter.name());
        assertEquals(RoleOwnerKind.MOD, adapter.kind());
        assertTrue(adapter.available());
        assertEquals(
            new LinkedHashSet<>(
                Arrays.asList(
                    PermissionCapabilities.HAS,
                    PermissionCapabilities.GROUP,
                    PermissionCapabilities.META,
                    PermissionCapabilities.CONTEXTS,
                    PermissionCapabilities.EXPIRY,
                    PermissionCapabilities.TRACKS)),
            adapter.capabilities(),
            "заявка называет весь перечень роли из codecore-api, своих строк умений у мода нет");
    }

    @Test
    void availabilityFollowsTheStorageSeam() {
        assertFalse(new PermsRoleAdapter(StubService::new, () -> false).available());
        assertTrue(new PermsRoleAdapter(StubService::new, () -> true).available());
    }

    @Test
    void filesAreOpenedOnlyWhenTheRoleIsOurs() {
        AtomicInteger assembled = new AtomicInteger();
        PermsRoleAdapter adapter = new PermsRoleAdapter(() -> {
            assembled.incrementAndGet();
            return assemble();
        }, () -> true);

        assertEquals(0, assembled.get(), "заявка ничего не собирает");
        assertFalse(Files.exists(TestConfigs.permissions(root)), "до решения роли мод не открывает файлов");

        PermissionService service = adapter.create()
            .find(PermissionService.class);

        assertEquals(1, assembled.get());
        assertNotNull(service, "победитель роли отдаёт ядру реализацию прав");
        assertTrue(
            Files.isRegularFile(
                TestConfigs.permissions(root)
                    .resolve("perms.toml")));
        assertTrue(
            Files.isRegularFile(
                TestConfigs.permissions(root)
                    .resolve("perms-groups.toml")));
    }

    @Test
    void whenTheRoleWentToAnotherOwnerTheExistingFilesStayUntouched() throws Exception {
        assemble();
        Map<Path, FileTime> before = stamps();
        assertFalse(before.isEmpty(), "файлы прошлого запуска должны быть на месте");
        Thread.sleep(20L);

        new PermsRoleAdapter(
            () -> { throw new AssertionError("create() зовут только у победителя роли"); },
            () -> true);

        assertEquals(before, stamps(), "время изменения файлов не поменялось");
    }

    private PermissionService assemble() {
        ConfigService configs = TestConfigs.of(root);
        SingleWriterImpl writer = SingleWriterImpl.create(
            configs,
            new MainSettings(configs),
            new Ceilings(configs.open(PermsSettings.spec()), LOG),
            new IdleScheduler(),
            LOG);
        writer.snapshot();
        return new StubService();
    }

    private Map<Path, FileTime> stamps() throws IOException {
        Map<Path, FileTime> stamps = new LinkedHashMap<>();
        try (java.util.stream.Stream<Path> files = Files.list(TestConfigs.permissions(root))) {
            for (Path file : (Iterable<Path>) files.sorted()::iterator) {
                stamps.put(file, Files.getLastModifiedTime(file));
            }
        }
        return stamps;
    }

    private static final class IdleScheduler implements Scheduler {

        @Override
        public void onMainThread(Runnable task) {
            task.run();
        }

        @Override
        public void afterTicks(int ticks, Runnable task) {}
    }

    private static final class StubService implements PermissionService {

        @Override
        public boolean has(UUID player, String node) {
            return false;
        }

        @Override
        public String group(UUID player) {
            return "player";
        }

        @Override
        public String meta(UUID player, String key, String fallback) {
            return fallback;
        }
    }
}
