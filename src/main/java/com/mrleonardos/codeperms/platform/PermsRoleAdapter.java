package com.mrleonardos.codeperms.platform;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

import com.mrleonardos.codecore.api.adapter.RoleAdapter;
import com.mrleonardos.codecore.api.adapter.RoleCapability;
import com.mrleonardos.codecore.api.adapter.RoleOwnerKind;
import com.mrleonardos.codecore.api.adapter.RoleServices;
import com.mrleonardos.codecore.api.config.ConfigRoles;
import com.mrleonardos.codecore.api.service.PermissionService;
import com.mrleonardos.codeperms.internal.PermsSettings;

final class PermsRoleAdapter implements RoleAdapter {

    static final RoleCapability HAS = RoleCapability.of("has");
    static final RoleCapability GROUP = RoleCapability.of("group");
    static final RoleCapability META = RoleCapability.of("meta");
    static final RoleCapability CONTEXTS = RoleCapability.of("contexts");
    static final RoleCapability EXPIRY = RoleCapability.of("expiry");
    static final RoleCapability TRACKS = RoleCapability.of("tracks");

    private final Supplier<PermissionService> assembly;

    PermsRoleAdapter(Supplier<PermissionService> assembly) {
        this.assembly = assembly;
    }

    @Override
    public String role() {
        return ConfigRoles.PERMISSIONS;
    }

    @Override
    public String name() {
        return PermsSettings.MODID;
    }

    @Override
    public RoleOwnerKind kind() {
        return RoleOwnerKind.MOD;
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public Set<RoleCapability> capabilities() {
        return new LinkedHashSet<>(Arrays.asList(HAS, GROUP, META, CONTEXTS, EXPIRY, TRACKS));
    }

    @Override
    public RoleServices create() {
        return RoleServices.builder()
            .add(PermissionService.class, assembly.get())
            .build();
    }
}
