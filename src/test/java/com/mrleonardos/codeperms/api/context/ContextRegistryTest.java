package com.mrleonardos.codeperms.api.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codecore.api.actor.PlayerRef;
import com.mrleonardos.codeperms.api.model.ContextSet;

class ContextRegistryTest {

    private static final PlayerRef KNOWN = PlayerRef
        .of(UUID.fromString("00000000-0000-0000-0000-000000000001"), "Steve");
    private static final PlayerRef STRANGER = PlayerRef
        .of(UUID.fromString("00000000-0000-0000-0000-000000000002"), "Alex");

    @Test
    void emptyRegistryCollectsNothing() {
        ContextRegistry registry = new ContextRegistry();
        assertTrue(
            registry.providers()
                .isEmpty());
        assertSame(ContextSet.empty(), collect(registry, KNOWN));
    }

    @Test
    void providersMergeTheirPairs() {
        ContextRegistry registry = new ContextRegistry();
        registry.register(fixed("clan", "clan.officer", "yes"));
        registry.register(fixed("rank", "rank.level", "5"));

        assertEquals(
            2,
            registry.providers()
                .size());
        ContextSet collected = collect(registry, KNOWN);
        assertEquals(2, collected.specificity());
        assertTrue(
            collected.matches(
                ContextSet.builder()
                    .put("clan.officer", "yes")
                    .put("rank.level", "5")
                    .build()));
    }

    @Test
    void aPlayerTheProviderDoesNotKnowYieldsNothing() {
        ContextRegistry registry = new ContextRegistry();
        registry.register(fixed("clan", "clan.officer", "yes"));
        assertTrue(collect(registry, STRANGER).isEmpty());
    }

    @Test
    void fallenProviderIsSkippedAndReportedWithoutLosingTheOthers() {
        ContextRegistry registry = new ContextRegistry();
        registry.register(new ContextProvider() {

            @Override
            public String id() {
                return "broken";
            }

            @Override
            public Map<String, String> collect(PlayerRef subject) {
                throw new IllegalStateException("the clan database is down");
            }
        });
        registry.register(new ContextProvider() {

            @Override
            public String id() {
                return "silent";
            }

            @Override
            public Map<String, String> collect(PlayerRef subject) {
                return null;
            }
        });
        registry.register(fixed("rank", "rank.level", "5"));

        List<String> failed = new ArrayList<>();
        ContextSet collected = registry.collect(KNOWN, (id, failure) -> failed.add(id));

        assertEquals(Collections.singletonList("broken"), failed);
        assertTrue(
            collected.matches(
                ContextSet.builder()
                    .put("rank.level", "5")
                    .build()));
    }

    @Test
    void frozenRegistryRefusesLateProviders() {
        ContextRegistry registry = new ContextRegistry();
        registry.register(fixed("clan", "clan.officer", "yes"));

        registry.freeze();

        assertTrue(registry.frozen());
        assertThrows(IllegalStateException.class, () -> registry.register(fixed("rank", "rank.level", "5")));
        assertThrows(IllegalStateException.class, () -> registry.unregister("clan"));
        assertEquals(
            1,
            registry.providers()
                .size());
    }

    @Test
    void sameIdIsRefused() {
        ContextRegistry registry = new ContextRegistry();
        registry.register(fixed("clan", "clan.officer", "yes"));
        assertThrows(IllegalArgumentException.class, () -> registry.register(fixed("clan", "other.key", "1")));
    }

    @Test
    void unregisterRemovesTheProvider() {
        ContextRegistry registry = new ContextRegistry();
        ContextProvider provider = fixed("clan", "clan.officer", "yes");
        registry.register(provider);
        assertTrue(registry.unregister("clan"));
        assertFalse(registry.unregister("clan"));
        assertTrue(
            registry.providers()
                .isEmpty());
    }

    @Test
    void valuesFoldToLowerCase() {
        ContextRegistry registry = new ContextRegistry();
        Map<String, String> pairs = new HashMap<>();
        pairs.put("Clan.Officer", "YES");
        registry.register(new ContextProvider() {

            @Override
            public String id() {
                return "clan";
            }

            @Override
            public Map<String, String> collect(PlayerRef subject) {
                return pairs;
            }
        });
        assertTrue(
            collect(registry, KNOWN).matches(
                ContextSet.builder()
                    .put("clan.officer", "yes")
                    .build()));
    }

    private static ContextSet collect(ContextRegistry registry, PlayerRef subject) {
        return registry.collect(
            subject,
            (id, failure) -> { throw new AssertionError("Provider " + id + " was not expected to fail", failure); });
    }

    private ContextProvider fixed(String id, String key, String value) {
        return new ContextProvider() {

            @Override
            public String id() {
                return id;
            }

            @Override
            public Map<String, String> collect(PlayerRef subject) {
                if (!KNOWN.id()
                    .equals(subject.id())) {
                    return new HashMap<>();
                }
                Map<String, String> pairs = new HashMap<>();
                pairs.put(key, value);
                return pairs;
            }
        };
    }
}
