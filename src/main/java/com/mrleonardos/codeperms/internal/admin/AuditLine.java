package com.mrleonardos.codeperms.internal.admin;

import com.mrleonardos.codeperms.api.store.ChangeBatch;

public final class AuditLine {

    private AuditLine() {}

    public static String of(ChangeBatch batch) {
        return batch.cause() + " by "
            + batch.author()
            + ": "
            + batch.changes()
                .size()
            + " changes";
    }
}
