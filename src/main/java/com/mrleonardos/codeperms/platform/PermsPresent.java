package com.mrleonardos.codeperms.platform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.LongSupplier;

import net.minecraft.command.ICommandSender;
import net.minecraft.util.EnumChatFormatting;

import com.mrleonardos.codecore.api.command.CommandContext;
import com.mrleonardos.codecore.api.util.Durations;
import com.mrleonardos.codecore.platform.Senders;
import com.mrleonardos.codecore.platform.ServerTexts;
import com.mrleonardos.codecore.platform.present.Pages;
import com.mrleonardos.codecore.platform.present.PresentReplies;
import com.mrleonardos.codecore.platform.present.PresentTheme;
import com.mrleonardos.codecore.platform.present.RichCard;
import com.mrleonardos.codecore.platform.present.RichLine;
import com.mrleonardos.codeperms.api.model.GroupRecord;
import com.mrleonardos.codeperms.api.model.NodeEntry;
import com.mrleonardos.codeperms.api.model.Snapshot;
import com.mrleonardos.codeperms.api.model.TrackRecord;
import com.mrleonardos.codeperms.api.model.UserRecord;
import com.mrleonardos.codeperms.internal.command.PermsCommands;
import com.mrleonardos.codeperms.internal.command.PermsMessages;
import com.mrleonardos.codeperms.internal.command.PermsPermissions;
import com.mrleonardos.codeperms.internal.command.PermsPresents;

/**
 * Карточки и списки команд из кирпичей ядра.
 *
 * <p>
 * Акцент мода синий: зелёный занят регионами, золотой остаётся акцентом линейки по умолчанию, бирюза
 * красит шапки списков у всех модов сразу, а жёлтый стоит на словах кнопок и с ним заголовок читался бы
 * как кнопка. Синий не спорит ни с одним из них и держит права отдельно от соседей по чату.
 *
 * <p>
 * Клик ведёт командой фактического корня: имя приходит с деревом команд при каждом вызове, и смена
 * корня меняет клики вместе с командами. Что кликается, решают права: строка без ноды на цель остаётся
 * строкой, а не приманкой. Консоль получает те же ответы плоским текстом.
 */
public final class PermsPresent implements PermsPresents {

    private static final PresentTheme THEME = PresentTheme.LINEUP.accent(EnumChatFormatting.BLUE);
    private static final int PAGE_SIZE = 8;
    private static final String CHAIN_ARROW = " → ";
    private static final String MISSING_WEIGHT = "-";

    private final BiPredicate<UUID, String> mayHold;
    private final LongSupplier clock;

    public PermsPresent(BiPredicate<UUID, String> mayHold, LongSupplier clock) {
        this.mayHold = mayHold;
        this.clock = clock;
    }

    @Override
    public void groupList(CommandContext context, Snapshot snapshot, UUID viewer, int page, String root) {
        page(
            context,
            Pages.of(groupRows(snapshot, viewer, root), PAGE_SIZE, THEME),
            page,
            ServerTexts.format(PermsMessages.CARD_GROUPS),
            command(root, "group list %d"),
            PermsMessages.GROUP_EMPTY);
    }

    @Override
    public void groupInfo(CommandContext context, Snapshot snapshot, GroupRecord group, UUID viewer, int page,
        String root) {
        if (page > 1) {
            page(
                context,
                Pages.of(nodeRows(group.nodes()), PAGE_SIZE, THEME),
                page,
                ServerTexts.format(PermsMessages.CARD_NODES_GROUP_TITLE, group.displayName()),
                command(root, "group info " + group.id() + " %d"),
                PermsMessages.CARD_NODES_EMPTY);
            return;
        }
        send(context, groupCard(snapshot, group, viewer, root));
    }

    @Override
    public void playerInfo(CommandContext context, Snapshot snapshot, UUID player, String name, UUID viewer, int page,
        String root) {
        if (page > 1) {
            UserRecord user = snapshot.user(player)
                .orElse(null);
            page(
                context,
                Pages.of(nodeRows(user == null ? Collections.<NodeEntry>emptyList() : user.nodes()), PAGE_SIZE, THEME),
                page,
                ServerTexts.format(PermsMessages.CARD_NODES_PLAYER_TITLE, name),
                command(root, "player info " + name + " %d"),
                PermsMessages.CARD_NODES_EMPTY);
            return;
        }
        send(context, playerCard(snapshot, player, name, viewer, root));
    }

    @Override
    public void trackList(CommandContext context, Snapshot snapshot, UUID viewer, int page, String root) {
        page(
            context,
            Pages.of(trackRows(snapshot, viewer, root), PAGE_SIZE, THEME),
            page,
            ServerTexts.format(PermsMessages.CARD_TRACKS),
            command(root, "track list %d"),
            PermsMessages.TRACK_EMPTY);
    }

    @Override
    public void trackInfo(CommandContext context, TrackRecord track, UUID viewer, String root) {
        send(context, trackCard(track, viewer, root));
    }

    /** Карточка группы: заголовок, поля, треки и кнопки правки по праву смотрящего. */
    RichCard groupCard(Snapshot snapshot, GroupRecord group, UUID viewer, String root) {
        RichCard card = RichCard.of(group.displayName(), THEME);
        if (!group.displayName()
            .equals(group.id())) {
            card.field(PermsMessages.CARD_IDENTIFIER, group.id());
        }
        card.field(PermsMessages.CARD_GROUP_WEIGHT, String.valueOf(group.weight()));
        if (!group.inherits()
            .isEmpty()) {
            card.line(parentsLine(group, root));
        }
        card.field(
            PermsMessages.CARD_GROUP_MEMBERS,
            String.valueOf(memberCounts(snapshot).getOrDefault(group.id(), 0)));
        if (!group.nodes()
            .isEmpty()) {
            card.field(
                PermsMessages.CARD_NODES,
                String.valueOf(
                    group.nodes()
                        .size()));
            card.button(PermsMessages.CARD_NODES_LIST, command(root, "group info " + group.id() + " 2"));
        }
        if (!group.meta()
            .isEmpty()) {
            card.field(PermsMessages.CARD_META, joinedMeta(group.meta()));
        }
        List<TrackRecord> held = tracksHolding(snapshot, group.id());
        if (!held.isEmpty()) {
            card.line(tracksLine(held, viewer, root));
        }
        if (allowed(viewer, PermsPermissions.GROUP_EDIT)) {
            card.suggestion(
                PermsMessages.CARD_GROUP_NODE_BUTTON,
                command(root, "group node set " + group.id() + " "),
                PermsMessages.CARD_GROUP_NODE_HOVER);
            card.suggestion(
                PermsMessages.CARD_GROUP_META_BUTTON,
                command(root, "group meta set " + group.id() + " "),
                PermsMessages.CARD_GROUP_META_HOVER);
            card.suggestion(
                PermsMessages.CARD_GROUP_WEIGHT_BUTTON,
                command(root, "group setweight " + group.id() + " "),
                PermsMessages.CARD_GROUP_WEIGHT_HOVER);
        }
        return card;
    }

    /** Карточка игрока: группы с весами и сроками, треки с прогрессом, мета и личные ноды. */
    RichCard playerCard(Snapshot snapshot, UUID player, String name, UUID viewer, String root) {
        RichCard card = RichCard.of(name, THEME);
        card.field(PermsMessages.CARD_IDENTIFIER, player.toString());
        UserRecord user = snapshot.user(player)
            .orElse(null);
        if (user == null) {
            card.line(
                RichLine.of()
                    .label(PermsMessages.GROUP_EMPTY));
            card.line(
                RichLine.of()
                    .label(PermsMessages.CARD_NO_TRACKS));
            return card;
        }
        card.line(primaryLine(user, viewer, root));
        groupsSection(card, snapshot, user, viewer, root);
        tracksSection(card, snapshot, player, viewer, root);
        if (!user.meta()
            .isEmpty()) {
            card.field(PermsMessages.CARD_META, joinedMeta(user.meta()));
        }
        if (!user.nodes()
            .isEmpty()) {
            card.field(
                PermsMessages.CARD_NODES,
                String.valueOf(
                    user.nodes()
                        .size()));
            card.button(PermsMessages.CARD_NODES_LIST, command(root, "player info " + name + " 2"));
        }
        return card;
    }

    /** Карточка трека: цепочка групп по порядку возрастания, каждая ведёт на свою карточку. */
    RichCard trackCard(TrackRecord track, UUID viewer, String root) {
        RichCard card = RichCard.of(track.name(), THEME);
        RichLine chain = RichLine.of()
            .label(PermsMessages.CARD_TRACK_CHAIN)
            .muted(": ");
        boolean groupsOpen = allowed(viewer, PermsPermissions.GROUP_INFO);
        boolean first = true;
        for (String groupId : track.groups()) {
            if (!first) {
                chain.muted(CHAIN_ARROW);
            }
            first = false;
            chain.accent(groupId);
            if (groupsOpen) {
                chain.run(command(root, "group info " + groupId));
            }
        }
        card.line(chain);
        return card;
    }

    /** Строки списка групп: имя ведёт на карточку, рядом вес, в подсказке участники и ноды. */
    List<RichLine> groupRows(Snapshot snapshot, UUID viewer, String root) {
        boolean clicks = allowed(viewer, PermsPermissions.GROUP_INFO);
        Map<String, Integer> members = memberCounts(snapshot);
        List<RichLine> rows = new ArrayList<>();
        for (GroupRecord group : sortedGroups(snapshot)) {
            RichLine row = RichLine.of()
                .accent(group.id());
            if (clicks) {
                row.run(command(root, "group info " + group.id()))
                    .hover(
                        PermsMessages.CARD_GROUP_HOVER,
                        String.valueOf(members.getOrDefault(group.id(), 0)),
                        String.valueOf(
                            group.nodes()
                                .size()));
            }
            rows.add(
                row.muted(" · ")
                    .value(String.valueOf(group.weight())));
        }
        return rows;
    }

    /** Строки списка треков: имя ведёт на карточку, рядом состав по порядку. */
    List<RichLine> trackRows(Snapshot snapshot, UUID viewer, String root) {
        boolean clicks = allowed(viewer, PermsPermissions.TRACK_INFO);
        List<RichLine> rows = new ArrayList<>();
        for (TrackRecord track : sortedTracks(snapshot)) {
            RichLine row = RichLine.of()
                .accent(track.name());
            if (clicks) {
                row.run(command(root, "track info " + track.name()))
                    .hover(PermsMessages.CARD_TRACK_HOVER, track.name());
            }
            rows.add(
                row.muted(" · ")
                    .muted(chain(track.groups())));
        }
        return rows;
    }

    /** Строки нод: разрешение акцентом, запрет красным, рядом срок временной выдачи. */
    List<RichLine> nodeRows(List<NodeEntry> nodes) {
        List<RichLine> rows = new ArrayList<>();
        for (NodeEntry entry : nodes) {
            RichLine row = RichLine.of();
            if (entry.value()) {
                row.accent(entry.toShortString());
            } else {
                row.text(entry.toShortString(), EnumChatFormatting.RED);
            }
            if (!entry.permanent() && !entry.expiredAt(clock.getAsLong())) {
                row.muted(" · ")
                    .muted(
                        ServerTexts.format(PermsMessages.CARD_EXPIRES, Durations.format(remaining(entry.expiresAt()))));
            }
            rows.add(row);
        }
        return rows;
    }

    private RichLine parentsLine(GroupRecord group, String root) {
        RichLine line = RichLine.of()
            .label(PermsMessages.CARD_GROUP_PARENTS)
            .muted(": ");
        boolean first = true;
        for (String parent : group.inherits()) {
            if (!first) {
                line.muted(", ");
            }
            first = false;
            line.accent(parent)
                .run(command(root, "group info " + parent));
        }
        return line;
    }

    private RichLine tracksLine(List<TrackRecord> held, UUID viewer, String root) {
        RichLine line = RichLine.of()
            .label(PermsMessages.CARD_TRACKS)
            .muted(": ");
        boolean clicks = allowed(viewer, PermsPermissions.TRACK_INFO);
        boolean first = true;
        for (TrackRecord track : held) {
            if (!first) {
                line.muted(", ");
            }
            first = false;
            line.accent(track.name());
            if (clicks) {
                line.run(command(root, "track info " + track.name()));
            }
        }
        return line;
    }

    private RichLine primaryLine(UserRecord user, UUID viewer, String root) {
        RichLine line = RichLine.of()
            .label(PermsMessages.CARD_PLAYER_PRIMARY)
            .muted(": ");
        if (user.primary() == null) {
            return line.value(ServerTexts.format(PermsMessages.CARD_NONE));
        }
        line.accent(user.primary());
        if (allowed(viewer, PermsPermissions.GROUP_INFO)) {
            line.run(command(root, "group info " + user.primary()));
        }
        return line;
    }

    private void groupsSection(RichCard card, Snapshot snapshot, UserRecord user, UUID viewer, String root) {
        List<UserRecord.Grant> grants = activeGrants(user);
        if (grants.isEmpty()) {
            card.line(
                RichLine.of()
                    .label(PermsMessages.GROUP_EMPTY));
            return;
        }
        boolean clicks = allowed(viewer, PermsPermissions.GROUP_INFO);
        card.line(
            RichLine.of()
                .label(PermsMessages.CARD_GROUPS)
                .muted(":"));
        for (UserRecord.Grant grant : grants) {
            RichLine row = RichLine.of()
                .accent(grant.groupId());
            if (clicks) {
                row.run(command(root, "group info " + grant.groupId()));
            }
            GroupRecord group = snapshot.group(grant.groupId())
                .orElse(null);
            row.muted(" · ")
                .value(group == null ? MISSING_WEIGHT : String.valueOf(group.weight()));
            if (!grant.permanent()) {
                row.muted(" · ")
                    .muted(
                        ServerTexts.format(PermsMessages.CARD_EXPIRES, Durations.format(remaining(grant.expiresAt()))));
            }
            card.line(row);
        }
    }

    private void tracksSection(RichCard card, Snapshot snapshot, UUID player, UUID viewer, String root) {
        List<TrackRecord> held = tracksOf(snapshot, player);
        if (held.isEmpty()) {
            card.line(
                RichLine.of()
                    .label(PermsMessages.CARD_NO_TRACKS));
            return;
        }
        boolean clicks = allowed(viewer, PermsPermissions.TRACK_INFO);
        card.line(
            RichLine.of()
                .label(PermsMessages.CARD_TRACKS)
                .muted(":"));
        for (TrackRecord track : held) {
            RichLine row = RichLine.of()
                .accent(track.name());
            if (clicks) {
                row.run(command(root, "track info " + track.name()));
            }
            String current = PermsCommands.currentTrackGroup(snapshot, player, track, clock.getAsLong());
            int position = current == null ? 0 : track.position(current) + 1;
            row.muted(" · ")
                .muted(
                    position + "/"
                        + track.groups()
                            .size());
            card.line(row);
        }
    }

    /** Пустой список это шапка и готовая строка, листать нечего. */
    private void page(CommandContext context, Pages pages, int number, String title, String commandTemplate,
        String emptyKey) {
        if (pages.isEmpty()) {
            RichCard card = pages.card(number, title, commandTemplate);
            send(
                context,
                card.line(
                    RichLine.of()
                        .label(emptyKey)));
            return;
        }
        PresentReplies.send(sender(context), pages, number, title, commandTemplate);
    }

    private static List<GroupRecord> sortedGroups(Snapshot snapshot) {
        List<GroupRecord> groups = new ArrayList<>(
            snapshot.groups()
                .values());
        groups.sort(
            Comparator.comparingInt(GroupRecord::weight)
                .reversed()
                .thenComparing(GroupRecord::id));
        return groups;
    }

    private static List<TrackRecord> sortedTracks(Snapshot snapshot) {
        return new ArrayList<>(new TreeMap<>(snapshot.tracks()).values());
    }

    private List<TrackRecord> tracksHolding(Snapshot snapshot, String groupId) {
        List<TrackRecord> held = new ArrayList<>();
        for (TrackRecord track : sortedTracks(snapshot)) {
            if (track.groups()
                .contains(groupId)) {
                held.add(track);
            }
        }
        return held;
    }

    private List<TrackRecord> tracksOf(Snapshot snapshot, UUID player) {
        List<TrackRecord> held = new ArrayList<>();
        for (TrackRecord track : sortedTracks(snapshot)) {
            if (PermsCommands.currentTrackGroup(snapshot, player, track, clock.getAsLong()) != null) {
                held.add(track);
            }
        }
        return held;
    }

    private List<UserRecord.Grant> activeGrants(UserRecord user) {
        List<UserRecord.Grant> grants = new ArrayList<>();
        for (UserRecord.Grant grant : user.groups()) {
            if (!grant.expiredAt(clock.getAsLong())) {
                grants.add(grant);
            }
        }
        return grants;
    }

    /**
     * Участники по группам за один проход по игрокам.
     *
     * <p>
     * Спрашивать каждую группу отдельно значило бы ходить по всем игрокам столько раз, сколько групп в
     * списке, и на потолках это миллион вызовов на один показ. Действующие выдачи считаются один раз на
     * игрока.
     */
    private Map<String, Integer> memberCounts(Snapshot snapshot) {
        Map<String, Integer> counts = new HashMap<>();
        for (UserRecord user : snapshot.users()
            .values()) {
            for (String groupId : user.activeGroupIds(clock.getAsLong())) {
                counts.merge(groupId, 1, Integer::sum);
            }
        }
        return counts;
    }

    private int remaining(long expiresAt) {
        long seconds = (expiresAt - clock.getAsLong()) / 1000L;
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, seconds));
    }

    private static String chain(List<String> groups) {
        StringBuilder text = new StringBuilder();
        for (String groupId : groups) {
            if (text.length() > 0) {
                text.append(CHAIN_ARROW);
            }
            text.append(groupId);
        }
        return text.toString();
    }

    private static String joinedMeta(Map<String, String> meta) {
        List<String> texts = new ArrayList<>();
        for (Map.Entry<String, String> entry : meta.entrySet()) {
            texts.add(entry.getKey() + "=" + entry.getValue());
        }
        return String.join(", ", texts);
    }

    private boolean allowed(UUID viewer, String node) {
        return viewer != null && mayHold.test(viewer, node);
    }

    private static String command(String root, String tail) {
        return "/" + root + " " + tail;
    }

    private static ICommandSender sender(CommandContext context) {
        return Senders.platform(context.caller());
    }

    private static void send(CommandContext context, RichCard card) {
        PresentReplies.send(sender(context), card);
    }
}
