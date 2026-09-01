package com.mrleonardos.codeperms.internal.store;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mrleonardos.codeperms.api.model.ContextSet;
import com.mrleonardos.codeperms.api.model.GroupRecord;
import com.mrleonardos.codeperms.api.model.NodeEntry;
import com.mrleonardos.codeperms.api.model.UserRecord;

final class PermsFixtures {

    private PermsFixtures() {}

    static GroupRecord group(String id, int weight, String... nodes) {
        return GroupRecord.of(id, "", weight, new ArrayList<String>(), nodes(nodes), meta());
    }

    static GroupRecord parented(String id, int weight, String parent, String... nodes) {
        return GroupRecord.of(id, "", weight, Arrays.asList(parent), nodes(nodes), meta());
    }

    static GroupRecord timedGroup(String id, int weight, long expiresAt) {
        return GroupRecord.of(
            id,
            "",
            weight,
            new ArrayList<String>(),
            Arrays.asList(NodeEntry.of("codechat.muted", false, ContextSet.empty(), expiresAt)),
            meta());
    }

    static UserRecord user(UUID uuid, String name, String primary) {
        return UserRecord
            .of(uuid, name, primary, new ArrayList<UserRecord.Grant>(), new ArrayList<NodeEntry>(), meta());
    }

    static List<NodeEntry> nodes(String... nodes) {
        List<NodeEntry> parsed = new ArrayList<>();
        for (String node : nodes) {
            parsed.add(NodeEntry.parse(node));
        }
        return parsed;
    }

    private static Map<String, String> meta() {
        return new LinkedHashMap<>();
    }
}
