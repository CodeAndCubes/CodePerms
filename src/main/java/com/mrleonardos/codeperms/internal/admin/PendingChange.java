package com.mrleonardos.codeperms.internal.admin;

import java.util.Objects;

import com.mrleonardos.codeperms.api.model.ChangeCause;
import com.mrleonardos.codeperms.api.model.GroupRecord;
import com.mrleonardos.codeperms.api.model.Snapshot;
import com.mrleonardos.codeperms.api.model.TrackRecord;
import com.mrleonardos.codeperms.api.model.UserRecord;
import com.mrleonardos.codeperms.api.store.ChangeBatch;
import com.mrleonardos.codeperms.api.store.OperationResult;

final class PendingChange {

    private final Snapshot current;
    private final Snapshot.Builder next;
    private final ChangeBatch.Builder batch;
    private OperationResult failure;

    PendingChange(Snapshot current, ChangeCause cause, String author) {
        this.current = Objects.requireNonNull(current, "current");
        this.next = Snapshot.builder()
            .revision(current.revision() + 1)
            .defaultGroup(current.defaultGroup())
            .opGroup(current.opGroup())
            .from(current);
        this.batch = ChangeBatch.builder(cause, author);
    }

    Snapshot current() {
        return current;
    }

    void putGroup(GroupRecord group) {
        batch.upsert(group);
        next.group(group);
    }

    void removeGroup(String groupId) {
        batch.removeGroup(groupId);
        next.removeGroup(groupId);
    }

    void putPlayer(UserRecord player) {
        batch.upsert(player);
        next.user(player);
    }

    void putTrack(TrackRecord track) {
        batch.upsert(track);
        next.track(track);
    }

    void removeTrack(String name) {
        batch.removeTrack(name);
        next.removeTrack(name);
    }

    OperationResult reject(OperationResult.Failure reason, String message) {
        if (failure == null) {
            failure = OperationResult.failure(reason, message);
        }
        return failure;
    }

    boolean rejected() {
        return failure != null;
    }

    OperationResult failure() {
        return failure;
    }

    Snapshot snapshot() {
        return next.build();
    }

    ChangeBatch batch() {
        return batch.build();
    }
}
