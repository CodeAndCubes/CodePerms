package com.mrleonardos.codeperms.internal.engine;

import java.util.ArrayList;
import java.util.List;

import com.mrleonardos.codeperms.api.model.GroupRecord;
import com.mrleonardos.codeperms.api.model.UserRecord;

final class MetaStack {

    private MetaStack() {}

    static List<String> values(UserRecord user, List<GroupRecord> orderedGroups, String key) {
        List<String> values = new ArrayList<>();
        if (user != null) {
            String personal = user.meta()
                .get(key);
            if (personal != null) {
                values.add(personal);
            }
        }
        for (GroupRecord group : orderedGroups) {
            String value = group.meta()
                .get(key);
            if (value != null) {
                values.add(value);
            }
        }
        return values;
    }
}
