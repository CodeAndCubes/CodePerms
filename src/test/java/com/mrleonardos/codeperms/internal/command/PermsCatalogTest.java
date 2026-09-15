package com.mrleonardos.codeperms.internal.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codeperms.api.NodeCatalog;

class PermsCatalogTest {

    @Test
    void everyOwnNodeIsDescribedInTheCatalog() throws Exception {
        NodeCatalog catalog = new NodeCatalog();

        PermsCatalog.registerOwnNodes(catalog);

        Set<String> described = new HashSet<>();
        for (NodeCatalog.Entry entry : catalog.entries()) {
            described.add(entry.node());
            assertTrue(
                descriptionKeys().contains(entry.descriptionKey()),
                "ключ описания " + entry.descriptionKey() + " обязан жить в PermsMessages и в обоих .lang");
        }
        assertEquals(declaredNodes(), described, "ни одна собственная нода мода не должна пропадать из каталога");
    }

    @Test
    void catalogSuggestionsFindTheOwnNodes() {
        NodeCatalog catalog = new NodeCatalog();

        PermsCatalog.registerOwnNodes(catalog);

        assertTrue(
            catalog.suggest("codeperms.group", 50)
                .contains("codeperms.group.list"));
        assertTrue(
            catalog.suggest("codeperms.player", 50)
                .contains("codeperms.player.info"));
        assertTrue(
            catalog.suggest("codeperms.re", 50)
                .contains("codeperms.reload"));
    }

    private static Set<String> declaredNodes() throws Exception {
        Set<String> nodes = new HashSet<>();
        for (Field field : PermsPermissions.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                nodes.add((String) field.get(null));
            }
        }
        return nodes;
    }

    private static Set<String> descriptionKeys() throws Exception {
        Set<String> keys = new HashSet<>();
        for (Field field : PermsMessages.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                keys.add((String) field.get(null));
            }
        }
        return keys;
    }
}
