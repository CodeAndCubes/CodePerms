package com.mrleonardos.codeperms.internal.store;

import com.mrleonardos.codeperms.api.model.Snapshot;
import com.mrleonardos.codeperms.api.store.ChangeBatch;
import com.mrleonardos.codeperms.api.store.OperationResult;

public interface SingleWriter {

    Snapshot snapshot();

    OperationResult commit(Snapshot next, ChangeBatch batch);

    void flush();
}
