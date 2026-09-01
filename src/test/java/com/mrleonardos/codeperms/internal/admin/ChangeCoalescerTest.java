package com.mrleonardos.codeperms.internal.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.Test;

import com.mrleonardos.codeperms.api.manage.ChangeEvent;
import com.mrleonardos.codeperms.api.manage.PermissionsListener;
import com.mrleonardos.codeperms.api.model.ChangeCause;

class ChangeCoalescerTest {

    private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000012");

    private final ChangeCoalescer coalescer = new ChangeCoalescer(LogManager.getLogger("codeperms-test"));

    @Test
    void recordsOfOneTickCollapseIntoOneEvent() {
        RecordingListener listener = new RecordingListener();
        coalescer.register(0, listener);

        coalescer.record(event(ChangeEvent.Kind.NODES, ChangeCause.COMMAND, ChangeEvent.Subject.player(FIRST)));
        coalescer.record(event(ChangeEvent.Kind.NODES, ChangeCause.COMMAND, ChangeEvent.Subject.player(SECOND)));
        coalescer.record(event(ChangeEvent.Kind.NODES, ChangeCause.API, ChangeEvent.Subject.group("vip")));
        coalescer.dispatch();

        assertEquals(2, listener.events.size());
        ChangeEvent merged = listener.events.get(0);
        assertEquals(ChangeEvent.Kind.NODES, merged.kind());
        assertEquals(ChangeCause.COMMAND, merged.cause());
        assertEquals(Arrays.asList(FIRST.toString(), SECOND.toString()), merged.subjectKeys());
        assertEquals(
            Collections.singletonList("vip"),
            listener.events.get(1)
                .subjectKeys());
    }

    @Test
    void differentKindsStaySeparate() {
        RecordingListener listener = new RecordingListener();
        coalescer.register(0, listener);

        coalescer.record(event(ChangeEvent.Kind.NODES, ChangeCause.COMMAND, ChangeEvent.Subject.player(FIRST)));
        coalescer.record(event(ChangeEvent.Kind.MEMBERSHIP, ChangeCause.COMMAND, ChangeEvent.Subject.player(FIRST)));
        coalescer.record(event(ChangeEvent.Kind.META, ChangeCause.COMMAND, ChangeEvent.Subject.player(FIRST)));
        coalescer.dispatch();

        assertEquals(3, listener.events.size());
        assertEquals(
            Arrays.asList(ChangeEvent.Kind.NODES, ChangeEvent.Kind.MEMBERSHIP, ChangeEvent.Kind.META),
            kinds(listener.events));
    }

    @Test
    void listenersRunByPriorityThenByRegistration() {
        List<String> order = new ArrayList<>();
        coalescer.register(10, named("late", order));
        coalescer.register(0, named("early", order));
        coalescer.register(0, named("second", order));

        coalescer.record(event(ChangeEvent.Kind.GROUPS, ChangeCause.COMMAND, ChangeEvent.Subject.group("vip")));
        coalescer.dispatch();

        assertEquals(Arrays.asList("early", "second", "late"), order);
        assertEquals(Arrays.asList("early", "second", "late"), names(coalescer.listeners()));
    }

    @Test
    void failingListenerIsSkippedAndOthersStillHear() {
        List<String> order = new ArrayList<>();
        coalescer.register(0, breaking());
        coalescer.register(1, named("healthy", order));

        coalescer.record(event(ChangeEvent.Kind.NODES, ChangeCause.COMMAND, ChangeEvent.Subject.player(FIRST)));
        coalescer.dispatch();

        assertEquals(Collections.singletonList("healthy"), order);
    }

    @Test
    void registerKeepsEarlierListenersReachable() {
        RecordingListener first = new RecordingListener();
        coalescer.register(0, first);

        coalescer.register(5, named("second", new ArrayList<String>()));

        assertEquals(
            2,
            coalescer.listeners()
                .size());
        assertSame(
            first,
            coalescer.listeners()
                .get(0));
    }

    @Test
    void unregisteredListenerStaysSilent() {
        RecordingListener listener = new RecordingListener();
        coalescer.register(0, listener);
        coalescer.unregister(listener);

        coalescer.record(event(ChangeEvent.Kind.NODES, ChangeCause.COMMAND, ChangeEvent.Subject.player(FIRST)));
        coalescer.dispatch();

        assertTrue(listener.events.isEmpty());
    }

    @Test
    void dispatchWithoutRecordsKeepsListenersUntouched() {
        RecordingListener listener = new RecordingListener();
        coalescer.register(0, listener);

        coalescer.dispatch();

        assertTrue(listener.events.isEmpty());
    }

    private static ChangeEvent event(ChangeEvent.Kind kind, ChangeCause cause, ChangeEvent.Subject subject) {
        return ChangeEvent.of(kind, Collections.singletonList(subject), cause);
    }

    private static List<ChangeEvent.Kind> kinds(List<ChangeEvent> events) {
        List<ChangeEvent.Kind> kinds = new ArrayList<>();
        for (ChangeEvent event : events) {
            kinds.add(event.kind());
        }
        return kinds;
    }

    private static List<String> names(List<com.mrleonardos.codeperms.api.manage.PermissionsListener> listeners) {
        List<String> names = new ArrayList<>();
        for (com.mrleonardos.codeperms.api.manage.PermissionsListener listener : listeners) {
            names.add(((NamedListener) listener).name);
        }
        return names;
    }

    private static PermissionsListener named(String name, List<String> order) {
        return new NamedListener(name, order);
    }

    private static PermissionsListener breaking() {
        return new PermissionsListener() {

            @Override
            public void onChange(ChangeEvent event) {
                throw new IllegalStateException("listener is broken");
            }
        };
    }

    private static final class NamedListener implements PermissionsListener {

        private final String name;
        private final List<String> order;

        private NamedListener(String name, List<String> order) {
            this.name = name;
            this.order = order;
        }

        @Override
        public void onChange(ChangeEvent event) {
            order.add(name);
        }
    }

    private static final class RecordingListener implements PermissionsListener {

        private final List<ChangeEvent> events = new ArrayList<>();

        @Override
        public void onChange(ChangeEvent event) {
            events.add(event);
        }
    }
}
