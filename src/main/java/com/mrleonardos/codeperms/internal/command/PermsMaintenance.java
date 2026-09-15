package com.mrleonardos.codeperms.internal.command;

import com.mrleonardos.codeperms.api.store.OperationResult;
import com.mrleonardos.codeperms.internal.store.CoreGroupsImporter;

public interface PermsMaintenance {

    OperationResult reload();

    Outcome importFromCore(boolean dryRun, boolean force);

    Outcome exportToCoreFormat();

    /**
     * Итог переноса: исход операции и счётчики. Числа едут аргументами формата в ключи перевода,
     * готовый текст команда собирает сама, поэтому локаль выбирает показывающий код.
     */
    final class Outcome {

        public final OperationResult result;
        public final CoreGroupsImporter.Counts counts;

        public Outcome(OperationResult result, CoreGroupsImporter.Counts counts) {
            this.result = result;
            this.counts = counts;
        }
    }
}
