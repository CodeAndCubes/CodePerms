package com.mrleonardos.codeperms.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ContextSetTest {

    @Test
    void emptySetMatchesEverything() {
        ContextSet empty = ContextSet.empty();
        assertTrue(empty.isEmpty());
        assertEquals(0, empty.specificity());
        assertTrue(empty.matches(ContextSet.empty()));
        assertTrue(empty.matches(nether()));
        assertTrue(
            empty.matches(
                ContextSet.builder()
                    .put("world", "nether")
                    .put("dim", "-1")
                    .build()));
    }

    @Test
    void everyPairMustBePresentInQuery() {
        ContextSet rule = ContextSet.builder()
            .put("world", "nether")
            .put("op", "true")
            .build();
        assertTrue(
            rule.matches(
                ContextSet.builder()
                    .put("world", "nether")
                    .put("op", "true")
                    .put("dim", "-1")
                    .build()));
        assertFalse(rule.matches(nether()));
        assertFalse(
            rule.matches(
                ContextSet.builder()
                    .put("world", "overworld")
                    .put("op", "true")
                    .build()));
        assertFalse(rule.matches(ContextSet.empty()));
    }

    @Test
    void keysAndValuesFoldToLowerCase() {
        ContextSet rule = ContextSet.builder()
            .put("World", "Nether")
            .build();
        assertEquals(nether(), rule);
        assertTrue(rule.matches(nether()));
        assertEquals(1, rule.specificity());
    }

    @Test
    void specificityCountsPairs() {
        ContextSet rule = ContextSet.builder()
            .put("world", "nether")
            .put("dim", "-1")
            .build();
        assertEquals(2, rule.specificity());
        assertFalse(rule.equals(nether()));
        assertNotEquals(nether().hashCode(), rule.hashCode());
    }

    @Test
    void storedMapCannotChange() {
        Map<String, String> source = new HashMap<>();
        source.put("world", "nether");
        ContextSet rule = ContextSet.of(source);
        source.put("dim", "-1");
        assertEquals(
            1,
            rule.asMap()
                .size());
        assertThrows(
            UnsupportedOperationException.class,
            () -> rule.asMap()
                .put("dim", "-1"));
    }

    @Test
    void emptySetIsShared() {
        assertSame(ContextSet.empty(), ContextSet.of(new HashMap<String, String>()));
        assertSame(
            ContextSet.empty(),
            ContextSet.builder()
                .build());
    }

    @Test
    void badPairsAreRefused() {
        assertThrows(
            NullPointerException.class,
            () -> ContextSet.builder()
                .put("world", null));
        assertThrows(
            NullPointerException.class,
            () -> ContextSet.builder()
                .put(null, "nether"));
        assertThrows(
            IllegalArgumentException.class,
            () -> ContextSet.builder()
                .put("  ", "nether"));
        assertThrows(NullPointerException.class, () -> ContextSet.of(null));
    }

    @Test
    void nullQueryIsRefused() {
        assertThrows(NullPointerException.class, () -> nether().matches(null));
    }

    private static ContextSet nether() {
        return ContextSet.builder()
            .put("world", "nether")
            .build();
    }
}
