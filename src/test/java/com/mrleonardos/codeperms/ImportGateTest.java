package com.mrleonardos.codeperms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codeperms.api.PermsApi;
import com.mrleonardos.codesides.gate.PackageGate;

class ImportGateTest {

    private static final String[] GATED = { "com/mrleonardos/codeperms/api", "com/mrleonardos/codeperms/internal" };

    private static final String[] FORBIDDEN = { "net/minecraft", "net/minecraftforge", "cpw/mods", "io/netty",
        "org/lwjgl", "com/mojang", "com/mrleonardos/codecore/platform" };

    @Test
    void apiAndInternalHoldNoPlatformTypes() throws IOException {
        List<String> violations = gate().violations(GATED);

        assertTrue(
            violations.isEmpty(),
            () -> "типы игры, netty, lwjgl и слой платформы ядра живут только в platform, чужие ссылки:\n"
                + String.join("\n", violations));
    }

    @Test
    void eventListenersArePublic() throws IOException {
        List<String> hidden = gate().hiddenListeners();

        assertTrue(hidden.isEmpty(), () -> "классы с @SubscribeEvent обязаны быть public: " + hidden);
    }

    @Test
    void gateNoticesAForbiddenReference() throws IOException {
        List<String> found = gate().scan(PackageGate.foreignSample());

        assertFalse(found.isEmpty(), "гейт обязан ловить ссылку на тип Minecraft");
    }

    private static PackageGate gate() throws IOException {
        return PackageGate.of(PermsApi.class, FORBIDDEN);
    }
}
