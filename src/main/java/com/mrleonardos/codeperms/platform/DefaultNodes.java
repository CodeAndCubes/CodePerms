package com.mrleonardos.codeperms.platform;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codecore.api.config.ConfigFile;
import com.mrleonardos.codeperms.api.PermsLimits;
import com.mrleonardos.codeperms.api.model.NodeEntry;
import com.mrleonardos.codeperms.internal.PermsSettings;

final class DefaultNodes implements Supplier<List<NodeEntry>> {

    private final ConfigFile<PermsSettings> settings;
    private final PermsLimits limits;
    private final Logger log;
    private volatile List<NodeEntry> parsed = Collections.emptyList();

    DefaultNodes(ConfigFile<PermsSettings> settings, PermsLimits limits, Logger log) {
        this.settings = settings;
        this.limits = limits;
        this.log = log;
    }

    @Override
    public List<NodeEntry> get() {
        return parsed;
    }

    void refresh() {
        parsed = Collections.unmodifiableList(
            settings.get()
                .parsedDefaultNodes(limits, log));
    }
}
