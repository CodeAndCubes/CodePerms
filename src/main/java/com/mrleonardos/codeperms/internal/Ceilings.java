package com.mrleonardos.codeperms.internal;

import java.util.function.Supplier;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codecore.api.config.ConfigFile;
import com.mrleonardos.codeperms.api.PermsLimits;

/**
 * Потолки из perms.toml с перечитыванием по требованию. Сторы, админ и команды берут значения отсюда,
 * поэтому {@code /perms reload} обновляет потолки вместе с содержимым файлов, а не до перезапуска.
 */
public final class Ceilings implements Supplier<PermsLimits> {

    private final ConfigFile<PermsSettings> settings;
    private final Logger log;
    private volatile PermsLimits current;

    public Ceilings(ConfigFile<PermsSettings> settings, Logger log) {
        this.settings = settings;
        this.log = log;
        refresh();
    }

    /** Перечитать потолки из файла настроек. Зовёт {@code /perms reload} сразу после перечитывания файла. */
    public void refresh() {
        current = settings.get()
            .ceilings(log);
    }

    @Override
    public PermsLimits get() {
        return current;
    }
}
