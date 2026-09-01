package com.mrleonardos.codeperms.api.manage;

/**
 * Наблюдатель правок прав.
 *
 * <p>
 * Слушатель ни на что не влияет: он для логов, кэшей чужих модов и мостов. Упавший слушатель пишется
 * в лог и пропускается, остальные получают событие.
 */
public interface PermissionsListener {

    /** Правки применены и уже видны проверкам права. */
    void onChange(ChangeEvent event);
}
