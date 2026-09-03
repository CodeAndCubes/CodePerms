package com.mrleonardos.codeperms.platform;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

import com.mrleonardos.codecore.api.adapter.PermissionCapabilities;
import com.mrleonardos.codecore.api.adapter.RoleAdapter;
import com.mrleonardos.codecore.api.adapter.RoleCapability;
import com.mrleonardos.codecore.api.adapter.RoleOwnerKind;
import com.mrleonardos.codecore.api.adapter.RoleServices;
import com.mrleonardos.codecore.api.config.ConfigRoles;
import com.mrleonardos.codecore.api.service.PermissionService;
import com.mrleonardos.codeperms.internal.PermsSettings;

final class PermsRoleAdapter implements RoleAdapter {

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
        return new LinkedHashSet<>(
            Arrays.asList(
                PermissionCapabilities.HAS,
                PermissionCapabilities.GROUP,
                PermissionCapabilities.META,
                PermissionCapabilities.CONTEXTS,
                PermissionCapabilities.EXPIRY,
                PermissionCapabilities.TRACKS));
    }

    @Override
    public RoleServices create() {
        return RoleServices.builder()
            .add(PermissionService.class, assembly.get())
            .build();
    }
}
