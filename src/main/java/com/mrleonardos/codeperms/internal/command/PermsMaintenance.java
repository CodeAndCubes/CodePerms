package com.mrleonardos.codeperms.internal.command;

import com.mrleonardos.codeperms.api.store.OperationResult;

public interface PermsMaintenance {

    OperationResult reload();

    OperationResult importFromCore(boolean dryRun, boolean force);

    OperationResult exportToCoreFormat();
}
