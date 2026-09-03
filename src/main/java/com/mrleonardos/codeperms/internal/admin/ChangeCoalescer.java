package com.mrleonardos.codeperms.internal.admin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codeperms.api.manage.ChangeEvent;
import com.mrleonardos.codeperms.api.manage.PermissionsEvents;
import com.mrleonardos.codeperms.api.manage.PermissionsListener;
import com.mrleonardos.codeperms.api.model.ChangeCause;

public final class ChangeCoalescer implements PermissionsEvents {

    private final Logger log;
    private final Map<Key, Set<ChangeEvent.Subject>> pending = new LinkedHashMap<>();
    private volatile List<Entry> listeners = Collections.emptyList();
    private int sequence;

    public ChangeCoalescer(Logger log) {
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public void register(int priority, PermissionsListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener must not be null");
        }
        synchronized (this) {
            List<Entry> ordered = new ArrayList<>(listeners);
            ordered.add(new Entry(priority, sequence++, listener));
            ordered.sort(
                Comparator.<Entry>comparingInt(entry -> entry.priority)
                    .thenComparingInt(entry -> entry.order));
            listeners = ordered;
        }
    }

    @Override
    public void unregister(PermissionsListener listener) {
        synchronized (this) {
            List<Entry> kept = new ArrayList<>();
            for (Entry entry : listeners) {
                if (entry.listener != listener) {
                    kept.add(entry);
                }
            }
            listeners = kept;
        }
    }

    @Override
    public List<PermissionsListener> listeners() {
        List<PermissionsListener> ordered = new ArrayList<>(listeners.size());
        for (Entry entry : listeners) {
            ordered.add(entry.listener);
        }
        return ordered;
    }

    public void record(ChangeEvent event) {
        Objects.requireNonNull(event, "event");
        synchronized (pending) {
            Key key = new Key(event.kind(), event.cause());
            Set<ChangeEvent.Subject> subjects = pending.get(key);
            if (subjects == null) {
                subjects = new LinkedHashSet<>();
                pending.put(key, subjects);
            }
            subjects.addAll(event.subjects());
        }
    }

    public void dispatch() {
        List<Map.Entry<Key, Set<ChangeEvent.Subject>>> drained;
        synchronized (pending) {
            if (pending.isEmpty()) {
                return;
            }
            drained = new ArrayList<>(pending.entrySet());
            pending.clear();
        }
        for (Map.Entry<Key, Set<ChangeEvent.Subject>> entry : drained) {
            ChangeEvent event = ChangeEvent.of(entry.getKey().kind, entry.getValue(), entry.getKey().cause);
            for (PermissionsListener listener : listeners()) {
                try {
                    listener.onChange(event);
                } catch (RuntimeException | Error failure) {
                    log.warn("CodePerms listener failed on {}", event, failure);
                }
            }
        }
    }

    private static final class Entry {

        private final int priority;
        private final int order;
        private final PermissionsListener listener;

        private Entry(int priority, int order, PermissionsListener listener) {
            this.priority = priority;
            this.order = order;
            this.listener = listener;
        }
    }

    private static final class Key {

        private final ChangeEvent.Kind kind;
        private final ChangeCause cause;

        private Key(ChangeEvent.Kind kind, ChangeCause cause) {
            this.kind = kind;
            this.cause = cause;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key)) {
                return false;
            }
            Key key = (Key) other;
            return kind == key.kind && cause == key.cause;
        }

        @Override
        public int hashCode() {
            return kind.hashCode() * 31 + cause.hashCode();
        }
    }
}
