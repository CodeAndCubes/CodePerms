package com.mrleonardos.codeperms.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codeperms.api.PermsLimits;

class NodeEntryTest {

    @Test
    void stringFormReadsAsPermission() {
        NodeEntry entry = NodeEntry.parse("codechat.channel.*");
        assertEquals("codechat.channel.*", entry.node());
        assertTrue(entry.value());
        assertTrue(
            entry.contexts()
                .isEmpty());
        assertEquals(0L, entry.expiresAt());
        assertTrue(entry.permanent());
    }

    @Test
    void minusReadsAsDenial() {
        NodeEntry entry = NodeEntry.parse("-codechat.create");
        assertEquals("codechat.create", entry.node());
        assertFalse(entry.value());
        assertEquals("-codechat.create", entry.toShortString());
    }

    @Test
    void stringFormRoundTrips() {
        for (String raw : new String[] { "codechat.channel.*", "-codechat.create", "codechat" }) {
            assertEquals(
                raw,
                NodeEntry.parse(raw)
                    .toShortString());
            assertEquals(
                NodeEntry.parse(raw),
                NodeEntry.parse(
                    NodeEntry.parse(raw)
                        .toShortString()));
        }
    }

    @Test
    void leadingAndTrailingSpacesAreTrimmed() {
        assertEquals(
            "codechat.create",
            NodeEntry.parse("  codechat.create  ")
                .node());
    }

    @Test
    void garbageIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> NodeEntry.parse(""));
        assertThrows(IllegalArgumentException.class, () -> NodeEntry.parse("   "));
        assertThrows(IllegalArgumentException.class, () -> NodeEntry.parse("-"));
        assertThrows(IllegalArgumentException.class, () -> NodeEntry.parse("codechat create"));
        assertThrows(IllegalArgumentException.class, () -> NodeEntry.parse("codechat..create"));
        assertThrows(IllegalArgumentException.class, () -> NodeEntry.parse("codechat."));
        assertThrows(IllegalArgumentException.class, () -> NodeEntry.parse(".codechat"));
        assertThrows(NullPointerException.class, () -> NodeEntry.parse(null));
    }

    @Test
    void nodeLengthCeilingHolds() {
        PermsLimits limits = PermsLimits.defaults();
        String longest = times('a', limits.nodeLength());
        assertTrue(limits.acceptsNode(longest));
        assertEquals(
            NodeEntry.parse(longest, limits)
                .node(),
            longest);
        assertFalse(limits.acceptsNode(times('a', limits.nodeLength() + 1)));
        IllegalArgumentException refusal = assertThrows(
            IllegalArgumentException.class,
            () -> NodeEntry.parse(times('a', limits.nodeLength() + 1), limits));
        assertTrue(
            refusal.getMessage()
                .contains("longer than"));
    }

    @Test
    void nodeSegmentCeilingHolds() {
        PermsLimits limits = PermsLimits.defaults();
        String allowed = segments(limits.nodeSegments());
        String refused = segments(limits.nodeSegments() + 1);
        assertEquals(
            NodeEntry.parse(allowed, limits)
                .node(),
            allowed);
        assertFalse(limits.acceptsNode(refused));
        assertThrows(IllegalArgumentException.class, () -> NodeEntry.parse(refused, limits));
    }

    @Test
    void loweredCeilingsAreHonoured() {
        PermsLimits lowered = PermsLimits.builder()
            .nodeLength(20)
            .nodeSegments(2)
            .build();
        assertTrue(lowered.acceptsNode("codechat.create"));
        assertFalse(lowered.acceptsNode("codechat.channel.create"));
        assertEquals(
            "codechat.create",
            NodeEntry.parse("codechat.create", lowered)
                .node());
        assertThrows(IllegalArgumentException.class, () -> NodeEntry.parse("codechat.channel.create", lowered));
    }

    @Test
    void ceilingNeverGrowsAboveFactoryValue() {
        PermsLimits greedy = PermsLimits.builder()
            .nodeLength(10_000)
            .nodeSegments(1_000)
            .nodesPerSubject(100_000)
            .groups(10_000)
            .build();
        assertEquals(
            PermsLimits.defaults()
                .nodeLength(),
            greedy.nodeLength());
        assertEquals(
            PermsLimits.defaults()
                .nodeSegments(),
            greedy.nodeSegments());
        assertEquals(
            PermsLimits.defaults()
                .nodesPerSubject(),
            greedy.nodesPerSubject());
        assertEquals(
            PermsLimits.defaults()
                .groups(),
            greedy.groups());
    }

    @Test
    void negativeCeilingKeepsTheFactoryLength() {
        PermsLimits limits = PermsLimits.builder()
            .nodeLength(-1)
            .build();

        assertEquals(PermsLimits.DEFAULT_NODE_LENGTH, limits.nodeLength());
        assertEquals(
            "codechat.create",
            NodeEntry.parse("codechat.create", limits)
                .node());
    }

    @Test
    void objectFormHoldsContextsAndExpiry() {
        ContextSet contexts = ContextSet.builder()
            .put("world", "nether")
            .build();
        NodeEntry entry = NodeEntry.of("codechat.create", false, contexts, 1500L);
        assertSame(contexts, entry.contexts());
        assertFalse(entry.value());
        assertFalse(entry.permanent());
        assertFalse(entry.expiredAt(1499L));
        assertTrue(entry.expiredAt(1500L));
        assertEquals("-codechat.create", entry.toShortString());
    }

    @Test
    void permanentRuleNeverExpires() {
        NodeEntry entry = NodeEntry.allow("codechat.create");
        assertTrue(entry.permanent());
        assertFalse(entry.expiredAt(Long.MAX_VALUE));
    }

    @Test
    void factoriesMatchParsing() {
        assertEquals(NodeEntry.allow("codechat.create"), NodeEntry.parse("codechat.create"));
        assertEquals(NodeEntry.deny("codechat.create"), NodeEntry.parse("-codechat.create"));
    }

    @Test
    void badObjectFormIsRefused() {
        assertThrows(
            IllegalArgumentException.class,
            () -> NodeEntry.of("codechat.create", true, ContextSet.empty(), -1L));
        assertThrows(
            IllegalArgumentException.class,
            () -> NodeEntry.of("codechat create", true, ContextSet.empty(), 0L));
        assertThrows(NullPointerException.class, () -> NodeEntry.of(null, true, ContextSet.empty(), 0L));
        assertThrows(NullPointerException.class, () -> NodeEntry.of("codechat.create", true, null, 0L));
    }

    private static String times(char symbol, int count) {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < count; index++) {
            text.append(symbol);
        }
        return text.toString();
    }

    private static String segments(int count) {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < count; index++) {
            if (index > 0) {
                text.append('.');
            }
            text.append('a');
        }
        return text.toString();
    }
}
