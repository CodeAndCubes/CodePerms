package com.mrleonardos.codeperms.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PermsLimitsTest {

    @Test
    void factoryValuesMatchTheSpec() {
        PermsLimits limits = PermsLimits.defaults();
        assertEquals(128, limits.nodeLength());
        assertEquals(16, limits.nodeSegments());
        assertEquals(1024, limits.nodesPerSubject());
        assertEquals(32, limits.groupIdLength());
        assertEquals(512, limits.groups());
        assertEquals(256, limits.metaValueLength());
        assertEquals(32, limits.metaKeysPerSubject());
        assertEquals(32, limits.trackNameLength());
    }

    @Test
    void nodeChecksCountSegmentsByDots() {
        PermsLimits limits = PermsLimits.defaults();
        assertTrue(limits.acceptsNode("codechat.channel.global.read"));
        assertFalse(limits.acceptsNode(null));
        assertFalse(limits.acceptsNode(""));
        assertFalse(limits.acceptsNode("a.b.c.d.e.f.g.h.i.j.k.l.m.n.o.p.q"));
    }

    @Test
    void groupMetaAndTrackChecks() {
        PermsLimits limits = PermsLimits.defaults();
        assertTrue(limits.acceptsGroupId("vip"));
        assertFalse(limits.acceptsGroupId(times('g', 33)));
        assertTrue(limits.acceptsMetaValue(times('v', 256)));
        assertFalse(limits.acceptsMetaValue(times('v', 257)));
        assertFalse(limits.acceptsMetaValue(null));
        assertTrue(limits.acceptsTrackName("ladder"));
        assertFalse(limits.acceptsTrackName(times('t', 33)));
        assertFalse(limits.acceptsTrackName(""));
    }

    @Test
    void loweringTakesTheMinimum() {
        PermsLimits lowered = PermsLimits.defaults()
            .loweredTo(
                PermsLimits.builder()
                    .nodeLength(64)
                    .nodeSegments(8)
                    .nodesPerSubject(100)
                    .groups(10)
                    .metaValueLength(32)
                    .metaKeysPerSubject(4)
                    .trackNameLength(8)
                    .groupIdLength(16)
                    .build());
        assertEquals(64, lowered.nodeLength());
        assertEquals(8, lowered.nodeSegments());
        assertEquals(100, lowered.nodesPerSubject());
        assertEquals(16, lowered.groupIdLength());
        assertEquals(10, lowered.groups());
        assertEquals(32, lowered.metaValueLength());
        assertEquals(4, lowered.metaKeysPerSubject());
        assertEquals(8, lowered.trackNameLength());
    }

    @Test
    void raisingAboveFactoryValueChangesNothing() {
        PermsLimits limits = PermsLimits.defaults();
        PermsLimits greedy = limits.loweredTo(
            PermsLimits.builder()
                .nodeLength(9999)
                .nodesPerSubject(9999)
                .build());
        assertEquals(limits, greedy);
        assertEquals(limits.hashCode(), greedy.hashCode());
    }

    @Test
    void zeroAndNegativeAreTreatedAsUnsetAndReported() {
        PermsLimits.Builder builder = PermsLimits.builder()
            .nodesPerSubject(0)
            .groups(-1)
            .nodeLength(64);
        PermsLimits limits = builder.build();

        assertEquals(PermsLimits.DEFAULT_NODES_PER_SUBJECT, limits.nodesPerSubject());
        assertEquals(PermsLimits.DEFAULT_GROUPS, limits.groups());
        assertEquals(64, limits.nodeLength());
        assertEquals(
            2,
            builder.remarks()
                .size());
        assertTrue(
            builder.remarks()
                .get(0)
                .startsWith("limits.nodesPerSubject = 0"));
        assertTrue(
            builder.remarks()
                .get(1)
                .startsWith("limits.groups = -1"));
    }

    @Test
    void goodValuesLeaveNoRemarks() {
        PermsLimits.Builder builder = PermsLimits.builder()
            .groups(10)
            .nodesPerSubject(100);
        builder.build();

        assertTrue(
            builder.remarks()
                .isEmpty());
    }

    private static String times(char symbol, int count) {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < count; index++) {
            text.append(symbol);
        }
        return text.toString();
    }
}
