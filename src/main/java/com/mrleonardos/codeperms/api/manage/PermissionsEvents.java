package com.mrleonardos.codeperms.api.manage;

import java.util.List;

/**
 * Реестр слушателей правок прав.
 *
 * <p>
 * Уведомление приходит один раз за тик на всю пачку правок, уже после того, как правки стали видны
 * проверкам. Порядок задаётся числом: меньшее значит более ранний вызов. При равном приоритете
 * слушатели идут в порядке регистрации, поэтому поведение не зависит от порядка загрузки модов.
 */
public interface PermissionsEvents {

    /**
     * Добавить слушателя.
     *
     * @param priority меньшее число означает более ранний вызов
     */
    void register(int priority, PermissionsListener listener);

    /** Убрать слушателя. */
    void unregister(PermissionsListener listener);

    /** Слушатели в порядке вызова. */
    List<PermissionsListener> listeners();
}
