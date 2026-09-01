package com.mrleonardos.codeperms.platform;

import com.mrleonardos.codecore.api.CodeApi;
import com.mrleonardos.codecore.api.service.PermissionService;
import com.mrleonardos.codecore.api.service.ServicePriority;

final class ServiceBridge {

    private ServiceBridge() {}

    static void register(PermissionService implementation, ServicePriority priority) {
        CodeApi.services()
            .register(PermissionService.class, implementation, priority);
    }
}
