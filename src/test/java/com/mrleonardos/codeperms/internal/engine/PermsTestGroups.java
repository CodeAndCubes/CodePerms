package com.mrleonardos.codeperms.internal.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.mrleonardos.codeperms.api.model.GroupRecord;
import com.mrleonardos.codeperms.api.model.NodeEntry;

final class PermsTestGroups {

    private PermsTestGroups() {}

    static GroupRecord group(String id, int weight, String... nodes) {
        return GroupRecord.of(id, "", weight, new ArrayList<String>(), nodes(nodes), emptyMeta());
    }

    static GroupRecord parented(String id, int weight, String parent, String... nodes) {
        return GroupRecord.of(id, "", weight, Arrays.asList(parent), nodes(nodes), emptyMeta());
    }

    static GroupRecord entries(String id, int weight, NodeEntry... entries) {
        return GroupRecord.of(id, "", weight, new ArrayList<String>(), Arrays.asList(entries), emptyMeta());
    }

    static GroupRecord meta(String id, int weight, String key, String value) {
        return GroupRecord.of(
            id,
            "",
            weight,
            new ArrayList<String>(),
            new ArrayList<NodeEntry>(),
            Collections.singletonMap(key, value));
    }

    static List<NodeEntry> nodes(String... nodes) {
        List<NodeEntry> parsed = new ArrayList<>();
        for (String node : nodes) {
            parsed.add(NodeEntry.parse(node));
        }
        return parsed;
    }

    private static Map<String, String> emptyMeta() {
        return Collections.emptyMap();
    }
}
