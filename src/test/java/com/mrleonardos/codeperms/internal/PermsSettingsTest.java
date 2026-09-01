package com.mrleonardos.codeperms.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

import com.mrleonardos.codecore.api.service.ServicePriority;
import com.mrleonardos.codeperms.api.PermsLimits;
import com.mrleonardos.codeperms.api.model.NodeEntry;

class PermsSettingsTest {

    private static final Logger LOG = LogManager.getLogger(PermsSettingsTest.class);
    private static final String LONG_NODE = "codechat.channel.global.read";

    @Test
    void defaultsMatchFactoryValues() {
        PermsSettings settings = PermsSettings.defaults();

        assertEquals("json", settings.provider());
        assertEquals("player", settings.defaultGroup);
        assertEquals("admin", settings.opGroup);
        assertTrue(settings.applyOps);
        assertEquals(30, settings.autosaveSeconds);
        assertEquals(30 * 20, settings.autosaveTicks());
        assertEquals(200, settings.scanTicks());
        assertEquals(512, settings.cache.offlineCacheSize);
        assertTrue(settings.audit.logChanges);
        assertTrue(!settings.audit.logChecks);
        assertEquals(Arrays.asList("codeperms.me"), settings.defaultNodes);
        assertEquals(ServicePriority.ADDON, settings.priority(LOG));
    }

    @Test
    void serviceWeightComesFromTheConfigAndFallsBackToAddon() {
        PermsSettings settings = new PermsSettings();

        settings.servicePriority = "override";
        assertEquals(ServicePriority.OVERRIDE, settings.priority(LOG));

        settings.servicePriority = "BUILTIN";
        assertEquals(ServicePriority.BUILTIN, settings.priority(LOG));

        settings.servicePriority = "highest";
        assertEquals(ServicePriority.ADDON, settings.priority(LOG));

        settings.servicePriority = null;
        assertEquals(ServicePriority.ADDON, settings.priority(LOG));
    }

    @Test
    void unusableCeilingIsReplacedByTheFactoryValueInsteadOfFailing() {
        PermsSettings settings = new PermsSettings();
        settings.limits.groups = -1;
        settings.limits.nodesPerSubject = 0;

        PermsLimits ceilings = settings.ceilings(LOG);

        assertEquals(PermsLimits.DEFAULT_GROUPS, ceilings.groups());
        assertEquals(PermsLimits.DEFAULT_NODES_PER_SUBJECT, ceilings.nodesPerSubject());
    }

    @Test
    void ceilingsComeFromConfigAndNeverExceedFactory() {
        PermsSettings settings = new PermsSettings();
        settings.limits.nodeLength = 500;
        settings.limits.nodesPerSubject = 100;

        PermsLimits ceilings = settings.ceilings();

        assertEquals(PermsLimits.DEFAULT_NODE_LENGTH, ceilings.nodeLength());
        assertEquals(100, ceilings.nodesPerSubject());
    }

    @Test
    void blankProviderFallsBackToJson() {
        PermsSettings settings = new PermsSettings();
        settings.storage.provider = "  ";

        assertEquals("json", settings.provider());
    }

    @Test
    void defaultNodesAreParsedAndBrokenOnesAreSkipped() {
        PermsSettings settings = new PermsSettings();
        settings.defaultNodes = Arrays.asList("codechat.create", "-codechat.delete", "not a node", "");

        assertEquals(
            Arrays.asList("codechat.create", "-codechat.delete"),
            texts(settings.parsedDefaultNodes(settings.ceilings(), LOG)));
    }

    @Test
    void emptyDefaultNodesStayEmpty() {
        PermsSettings settings = new PermsSettings();
        settings.defaultNodes = Collections.emptyList();

        assertTrue(
            settings.parsedDefaultNodes(settings.ceilings(), LOG)
                .isEmpty());
    }

    private List<String> texts(List<NodeEntry> nodes) {
        List<String> values = new ArrayList<>();
        for (NodeEntry node : nodes) {
            values.add(node.toShortString());
        }
        return values;
    }
}
