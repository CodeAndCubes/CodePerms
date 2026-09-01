package com.mrleonardos.codeperms.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NodeCatalogTest {

    @Test
    void entriesComeSorted() {
        NodeCatalog catalog = new NodeCatalog();
        catalog.register("codeperms.player.*", "codeperms.node.player");
        catalog.register("codeperms.debug", "codeperms.node.debug");
        catalog.register("codeperms.group.*", "codeperms.node.group");

        assertEquals(
            3,
            catalog.entries()
                .size());
        assertEquals(
            "codeperms.debug",
            catalog.entries()
                .get(0)
                .node());
        assertEquals(
            "codeperms.node.group",
            catalog.entries()
                .get(1)
                .descriptionKey());
    }

    @Test
    void suggestionsFollowPrefix() {
        NodeCatalog catalog = new NodeCatalog();
        catalog.register("codeperms.group.*", "codeperms.node.group");
        catalog.register("codeperms.group.edit", "codeperms.node.group.edit");
        catalog.register("codeperms.me", "codeperms.node.me");

        assertEquals(
            2,
            catalog.suggest("codeperms.group", 10)
                .size());
        assertTrue(
            catalog.suggest("CODEPERMS.ME", 10)
                .contains("codeperms.me"));
        assertTrue(
            catalog.suggest("nonsense", 10)
                .isEmpty());
        assertEquals(
            1,
            catalog.suggest("codeperms.group", 1)
                .size());
        assertTrue(
            catalog.suggest("anything", 0)
                .isEmpty());
    }

    @Test
    void repeatRegistrationReplacesDescription() {
        NodeCatalog catalog = new NodeCatalog();
        catalog.register("codeperms.me", "codeperms.node.me");
        catalog.register("codeperms.me", "codeperms.node.me.own");
        assertEquals(
            1,
            catalog.entries()
                .size());
        assertEquals(
            "codeperms.node.me.own",
            catalog.entries()
                .get(0)
                .descriptionKey());

        catalog.unregister("codeperms.me");
        assertTrue(
            catalog.entries()
                .isEmpty());
    }

    @Test
    void blankValuesAreRefused() {
        NodeCatalog catalog = new NodeCatalog();
        assertThrows(IllegalArgumentException.class, () -> catalog.register("  ", "codeperms.node.me"));
        assertThrows(IllegalArgumentException.class, () -> catalog.register("codeperms.me", ""));
        assertThrows(NullPointerException.class, () -> catalog.register(null, "codeperms.node.me"));
        assertEquals(
            0,
            catalog.entries()
                .size());
    }
}
