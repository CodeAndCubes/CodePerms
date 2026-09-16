package com.mrleonardos.codeperms.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.command.ICommandSender;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mrleonardos.codecore.api.command.CommandContext;
import com.mrleonardos.codecore.api.command.CommandSender;
import com.mrleonardos.codecore.platform.Senders;
import com.mrleonardos.codecore.platform.present.Pages;
import com.mrleonardos.codeperms.api.model.ContextSet;
import com.mrleonardos.codeperms.api.model.GroupRecord;
import com.mrleonardos.codeperms.api.model.NodeEntry;
import com.mrleonardos.codeperms.api.model.Snapshot;
import com.mrleonardos.codeperms.api.model.TrackRecord;
import com.mrleonardos.codeperms.api.model.UserRecord;
import com.mrleonardos.codeperms.internal.command.PermsPermissions;

/**
 * Карточки и страницы собираются из полей снимка, без запуска игры.
 *
 * <p>
 * Проверяется сборка: акцент мода, клики и подсказки стоят на своих местах и ведут командой фактического
 * корня, права отрезают то, на что смотрящий не имеет ноды, сроки временных выдач считаются от
 * подставных часов. Консоль получает те же ответы плоским текстом. Ожидания на английском: это подложка
 * линейки, лежащая под любым языком.
 */
class PermsPresentTest {

    private static final UUID VIEWER = UUID.fromString("00000000-0000-0000-0000-000000000009");
    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final long NOW = 1_000_000_000_000L;

    private final PermsPresent present = new PermsPresent((player, node) -> true, () -> NOW);

    @Test
    @DisplayName("строка группы ведёт на карточку и называет в подсказке участников и ноды")
    void groupRowsLeadAndCount() {
        Snapshot snapshot = Snapshot.builder()
            .group(group("vip", 10, NodeEntry.parse("codeperms.me")))
            .group(group("player", 0))
            .user(holder(STEVE, "Steve", "vip"))
            .user(holder(ALEX, "Alex", "vip"))
            .build();

        List<IChatComponent> rows = Pages.of(present.groupRows(snapshot, VIEWER, "perms"), 8)
            .card(1, "Groups", "/perms group list %d")
            .build();

        assertEquals("Groups · total 2", plain(rows.get(0)));
        assertEquals("vip · 10", plain(rows.get(1)), "старшая группа стоит первой");
        assertTrue(hasClick(rows.get(1), "/perms group info vip"), "строка ведёт на карточку группы");
        assertEquals("members 2 · nodes 1", hover(rows.get(1)), "подсказка называет участников и ноды");
    }

    @Test
    @DisplayName("длинный список групп режется страницами")
    void groupRowsSliceIntoPages() {
        Snapshot.Builder builder = Snapshot.builder();
        for (int index = 0; index < 9; index++) {
            builder.group(group("g" + index, index));
        }

        Pages pages = Pages.of(present.groupRows(builder.build(), VIEWER, "perms"), 8);

        assertEquals(2, pages.pageCount());
        List<IChatComponent> second = pages.card(2, "Groups", "/perms group list %d")
            .build();
        assertEquals("g0 · 0", plain(second.get(1)), "на второй странице осталась младшая группа");
        assertTrue(hasClick(second.get(second.size() - 1), "/perms group list 1"), "листание ведёт назад");
    }

    @Test
    @DisplayName("клики следуют корню, переданному с деревом, а не строке в коде")
    void clicksFollowTheActualRoot() {
        Snapshot snapshot = Snapshot.builder()
            .group(group("vip", 10))
            .build();

        List<IChatComponent> rows = Pages.of(present.groupRows(snapshot, VIEWER, "pr"), 8)
            .card(1, "Groups", "/pr group list %d")
            .build();

        assertTrue(hasClick(rows.get(1), "/pr group info vip"));
    }

    @Test
    @DisplayName("без ноды на карточку строки остаются строками")
    void rowClicksDisappearWithoutTheInfoNode() {
        PermsPresent blind = new PermsPresent((player, node) -> false, () -> NOW);
        Snapshot snapshot = Snapshot.builder()
            .group(group("vip", 10))
            .build();

        List<IChatComponent> rows = Pages.of(blind.groupRows(snapshot, VIEWER, "perms"), 8)
            .card(1, "Groups", "/perms group list %d")
            .build();

        assertFalse(hasClick(rows.get(1), "/perms group info vip"));
        assertEquals("vip · 10", plain(rows.get(1)), "строка читается и без клика");
    }

    @Test
    @DisplayName("карточка группы держит поля, треки и кнопку нод, заголовок несёт акцент мода")
    void groupCardHoldsFieldsTracksAndTheNodesButton() {
        GroupRecord moderator = GroupRecord.of(
            "moderator",
            "Moderators",
            50,
            Arrays.asList("player"),
            Arrays.asList(NodeEntry.parse("-codechat.create")),
            meta("prefix", "&7"));
        Snapshot snapshot = Snapshot.builder()
            .group(moderator)
            .group(group("player", 0))
            .user(holder(STEVE, "Steve", "moderator"))
            .track(TrackRecord.of("staff", Arrays.asList("player", "moderator")))
            .build();

        List<IChatComponent> lines = present.groupCard(snapshot, moderator, VIEWER, "perms")
            .build();

        assertEquals("Moderators", plain(lines.get(0)));
        assertEquals(
            EnumChatFormatting.BLUE,
            color(lines.get(0), 0),
            "заголовок несёт акцент мода, не золотой линейки");
        assertEquals("Identifier: moderator", plain(lines.get(1)), "имя выдаётся отдельным полем");
        assertEquals("Weight: 50", plain(lines.get(2)));
        assertEquals("Parents: player", plain(lines.get(3)));
        assertTrue(hasClick(lines.get(3), "/perms group info player"), "родитель ведёт на свою карточку");
        assertEquals("Members: 1", plain(lines.get(4)));
        assertEquals("Nodes: 1", plain(lines.get(5)));
        assertEquals("Meta: prefix=&7", plain(lines.get(6)));
        assertEquals("Tracks: staff", plain(lines.get(7)));
        assertTrue(hasClick(lines.get(7), "/perms track info staff"), "трек ведёт на свой состав");
        assertEquals("[all nodes]   [node]   [meta]   [weight]", plain(lines.get(8)));
        assertTrue(hasClick(lines.get(8), "/perms group info moderator 2"), "кнопка открывает ноды второй страницей");
        assertTrue(hasClick(lines.get(8), "/perms group node set moderator "));
        assertTrue(hasClick(lines.get(8), "/perms group meta set moderator "));
        assertTrue(hasClick(lines.get(8), "/perms group setweight moderator "));
    }

    @Test
    @DisplayName("кнопки правки отрезаются без ноды правки группы, пустые поля не занимают строк")
    void editButtonsFollowTheEditNode() {
        PermsPresent viewer = new PermsPresent((player, node) -> node.equals(PermsPermissions.GROUP_INFO), () -> NOW);
        GroupRecord vip = group("vip", 10);
        Snapshot snapshot = Snapshot.builder()
            .group(vip)
            .build();

        List<IChatComponent> lines = viewer.groupCard(snapshot, vip, VIEWER, "perms")
            .build();

        assertEquals(3, lines.size(), "вес и участники без нод, мета и треков занимают три строки");
        assertEquals("vip", plain(lines.get(0)));
        assertEquals("Weight: 10", plain(lines.get(1)));
        assertEquals("Members: 0", plain(lines.get(2)));
    }

    @Test
    @DisplayName("ноды группы режутся страницами, запрет красный, срок считается от часов")
    void groupNodesSliceAndColour() {
        List<NodeEntry> nodes = Arrays.asList(
            NodeEntry.parse("-codechat.create"),
            NodeEntry.of("codeperms.me", true, ContextSet.empty(), NOW + 3_600_000L));

        List<IChatComponent> lines = Pages.of(present.nodeRows(nodes), 8)
            .card(1, "Nodes of Moderators", "/perms group info moderator %d")
            .build();

        assertEquals(3, lines.size());
        assertEquals("-codechat.create", plain(lines.get(1)));
        assertEquals(
            EnumChatFormatting.RED,
            color(lines.get(1), 0),
            "запрет стоит красным, чтобы отличаться от разрешения");
        assertEquals("codeperms.me · expires in 1h", plain(lines.get(2)));
    }

    @Test
    @DisplayName("пустая страница нод это шапка и готовая строка")
    void emptyNodesPageCarriesAReadyLine() {
        Console console = new Console();
        GroupRecord bare = group("bare", 0);
        Snapshot snapshot = Snapshot.builder()
            .group(bare)
            .build();

        present.groupInfo(context(console), snapshot, bare, null, 2, "perms");

        assertEquals(2, console.heard.size());
        assertEquals("Nodes of bare · total 0", plain(console.heard.get(0)));
        assertEquals("No nodes", plain(console.heard.get(1)));
    }

    @Test
    @DisplayName("карточка игрока держит группы с весами и сроками, треки с прогрессом")
    void playerCardHoldsGroupsAndTrackProgress() {
        Snapshot snapshot = Snapshot.builder()
            .group(group("vip", 10))
            .group(group("mod", 5))
            .group(group("player", 0))
            .track(TrackRecord.of("ladder", Arrays.asList("player", "mod", "vip")))
            .user(
                UserRecord.of(
                    STEVE,
                    "Steve",
                    "vip",
                    Arrays.asList(UserRecord.Grant.permanent("vip"), UserRecord.Grant.of("mod", NOW + 7_200_000L)),
                    Arrays.asList(NodeEntry.parse("codeperms.me"), NodeEntry.parse("-codechat.create")),
                    meta("prefix", "&c")))
            .build();

        List<IChatComponent> lines = present.playerCard(snapshot, STEVE, "Steve", VIEWER, "perms")
            .build();

        assertEquals("Steve", plain(lines.get(0)));
        assertEquals("Identifier: " + STEVE, plain(lines.get(1)));
        assertEquals("Primary group: vip", plain(lines.get(2)));
        assertTrue(hasClick(lines.get(2), "/perms group info vip"), "основная группа ведёт на карточку");
        assertEquals("Groups:", plain(lines.get(3)));
        assertEquals("vip · 10", plain(lines.get(4)));
        assertTrue(hasClick(lines.get(4), "/perms group info vip"));
        assertEquals("mod · 5 · expires in 2h", plain(lines.get(5)), "временная выдача называет остаток срока");
        assertEquals("Tracks:", plain(lines.get(6)));
        assertEquals("ladder · 3/3", plain(lines.get(7)), "прогресс называет ступень основной группы");
        assertTrue(hasClick(lines.get(7), "/perms track info ladder"));
        assertEquals("Meta: prefix=&c", plain(lines.get(8)));
        assertEquals("Nodes: 2", plain(lines.get(9)));
        assertEquals("[all nodes]", plain(lines.get(10)));
        assertTrue(hasClick(lines.get(10), "/perms player info Steve 2"), "кнопка открывает личные ноды");
    }

    @Test
    @DisplayName("игрок без записи получает честные строки вместо прочерков")
    void unknownPlayerGetsHonestLines() {
        Snapshot snapshot = Snapshot.empty();

        List<IChatComponent> lines = present.playerCard(snapshot, ALEX, "Alex", VIEWER, "perms")
            .build();

        assertEquals(4, lines.size());
        assertEquals("Alex", plain(lines.get(0)));
        assertEquals("Identifier: " + ALEX, plain(lines.get(1)));
        assertEquals("No groups", plain(lines.get(2)));
        assertEquals("Not on any track", plain(lines.get(3)));
    }

    @Test
    @DisplayName("строки треков ведут на карточку, карточка держит цепочку по порядку")
    void trackRowsLeadToTheChain() {
        TrackRecord ladder = TrackRecord.of("main", Arrays.asList("player", "moderator", "admin"));
        Snapshot snapshot = Snapshot.builder()
            .track(ladder)
            .build();

        List<IChatComponent> rows = Pages.of(present.trackRows(snapshot, VIEWER, "perms"), 8)
            .card(1, "Tracks", "/perms track list %d")
            .build();
        assertEquals("main · player → moderator → admin", plain(rows.get(1)));
        assertTrue(hasClick(rows.get(1), "/perms track info main"));

        List<IChatComponent> lines = present.trackCard(ladder, VIEWER, "perms")
            .build();
        assertEquals("main", plain(lines.get(0)));
        assertEquals("Chain: player → moderator → admin", plain(lines.get(1)));
        assertTrue(hasClick(lines.get(1), "/perms group info admin"));
    }

    @Test
    @DisplayName("консоль получает список плоским текстом без стилей")
    void theConsoleReceivesFlatPages() {
        Console console = new Console();
        Snapshot snapshot = Snapshot.builder()
            .group(group("vip", 10))
            .group(group("player", 0))
            .build();

        present.groupList(context(console), snapshot, null, 1, "perms");

        assertEquals(3, console.heard.size());
        assertEquals("Groups · total 2", plain(console.heard.get(0)));
        assertEquals("vip · 10", plain(console.heard.get(1)));
        for (IChatComponent message : console.heard) {
            assertNull(
                message.getChatStyle()
                    .getColor(),
                "у консоли не должно быть цвета");
            assertNull(
                message.getChatStyle()
                    .getChatClickEvent(),
                "у консоли не должно быть кликов");
        }
    }

    private static GroupRecord group(String id, int weight, NodeEntry... nodes) {
        return GroupRecord.of(
            id,
            id,
            weight,
            Collections.<String>emptyList(),
            Arrays.asList(nodes),
            Collections.<String, String>emptyMap());
    }

    private static UserRecord holder(UUID player, String name, String groupId) {
        return UserRecord.of(
            player,
            name,
            null,
            Collections.singletonList(UserRecord.Grant.permanent(groupId)),
            Collections.<NodeEntry>emptyList(),
            Collections.<String, String>emptyMap());
    }

    private static Map<String, String> meta(String key, String value) {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put(key, value);
        return meta;
    }

    private static CommandContext context(Console console) {
        return new CommandContext() {

            private final CommandSender caller = Senders.of(console);

            @Override
            public CommandSender caller() {
                return caller;
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> T get(String name) {
                return (T) null;
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> T getOrDefault(String name, T fallback) {
                return fallback;
            }

            @Override
            public boolean has(String name) {
                return false;
            }

            @Override
            public void reply(String translationKey, Object... arguments) {}

            @Override
            public void replyError(String translationKey, Object... arguments) {}
        };
    }

    private static String plain(IChatComponent line) {
        return line.getUnformattedText();
    }

    private static EnumChatFormatting color(IChatComponent line, int piece) {
        return line.getSiblings()
            .get(piece)
            .getChatStyle()
            .getColor();
    }

    private static String hover(IChatComponent line) {
        for (IChatComponent piece : line.getSiblings()) {
            if (piece.getChatStyle()
                .getChatHoverEvent() != null) {
                return piece.getChatStyle()
                    .getChatHoverEvent()
                    .getValue()
                    .getUnformattedText();
            }
        }
        return "";
    }

    private static boolean hasClick(IChatComponent line, String command) {
        for (IChatComponent piece : line.getSiblings()) {
            ClickEvent click = piece.getChatStyle()
                .getChatClickEvent();
            if (click != null && command.equals(click.getValue())) {
                return true;
            }
        }
        return false;
    }

    /** Консоль, которая помнит всё, что ей сказали. */
    private static final class Console implements ICommandSender {

        private final List<IChatComponent> heard = new ArrayList<>();

        @Override
        public String getCommandSenderName() {
            return "Server";
        }

        @Override
        public IChatComponent func_145748_c_() {
            return null;
        }

        @Override
        public void addChatMessage(IChatComponent message) {
            heard.add(message);
        }

        @Override
        public boolean canCommandSenderUseCommand(int permissionLevel, String command) {
            return true;
        }

        @Override
        public ChunkCoordinates getPlayerCoordinates() {
            return null;
        }

        @Override
        public World getEntityWorld() {
            return null;
        }
    }
}
