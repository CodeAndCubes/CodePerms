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

import com.mrleonardos.codeperms.api.PermsLimits;
import com.mrleonardos.codeperms.api.model.NodeEntry;

class PermsSettingsTest {

    private static final Logger LOG = LogManager.getLogger(PermsSettingsTest.class);

    @Test
    void defaultsMatchFactoryValues() {
        PermsSettings settings = PermsSettings.defaults();

        assertTrue(settings.applyOps);
        assertEquals(200, settings.scanTicks());
        assertEquals(512, settings.cache.offlineCacheSize);
        assertEquals(Arrays.asList("codeperms.me"), settings.defaultNodes);
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

    @Test
    void scanTicksNeverFallToZero() {
        PermsSettings settings = new PermsSettings();
        settings.expiry.scanTicks = 0;

        assertEquals(1, settings.scanTicks());
    }

    private List<String> texts(List<NodeEntry> nodes) {
        List<String> values = new ArrayList<>();
        for (NodeEntry node : nodes) {
            values.add(node.toShortString());
        }
        return values;
    }
}
