package com.example.charteams.client;

import com.example.charteams.Net;
import com.example.charteams.api.Mate;
import com.example.charteams.model.TeamRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

// The team menu. Everything is laid out in GUI units; the rounded shapes are drawn in real screen pixels so the corners stay smooth
// at any GUI scale. No straight-line box buttons: every button is a soft pill.
public class TeamScreen extends Screen {
    private static final int W = 236, H = 196, ROW = 20, ROWS = 5;
    private static final int PANEL = 0x2A1832, PANEL_EDGE = 0xFF88C8, ROW_BG = 0x3E2649, WELL = 0x1B0F22;
    private static final int PINK = 0xD85A9E, PLUM = 0x70507F, RED = 0xB04A6A, TEXT = 0xFFF0F6, DIM = 0xB9A3C4;

    private record Pill(int x0, int y0, int x1, int y1, Runnable on) {
        boolean in(double x, double y) {
            return x >= x0 && x < x1 && y >= y0 && y < y1;
        }
    }

    private final List<Pill> pills = new ArrayList<>();
    private EditBox nameBox, inviteBox;
    private int scroll;
    private int pickColour;
    private String status = "";
    private long statusUntil;
    private double s = 2;
    private int ox, oy, mouseX, mouseY;

    public TeamScreen() {
        super(Component.literal("Team"));
    }

    @Override
    protected void init() {
        ox = (width - W) / 2;
        oy = (height - H) / 2;
        nameBox = new EditBox(font, 0, 0, 120, 12, Component.literal("Team name"));
        nameBox.setBordered(false);
        nameBox.setMaxLength(TeamRules.MAX_NAME);
        nameBox.setHint(Component.literal("Team name").withStyle(net.minecraft.ChatFormatting.GRAY));
        inviteBox = new EditBox(font, 0, 0, 120, 12, Component.literal("Player"));
        inviteBox.setBordered(false);
        inviteBox.setMaxLength(16);
        inviteBox.setHint(Component.literal("Invite a player").withStyle(net.minecraft.ChatFormatting.GRAY));
        addRenderableWidget(nameBox);
        addRenderableWidget(inviteBox);
    }

    void heard(String text) {
        status = text;
        statusUntil = System.currentTimeMillis() + 6000;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int p(double v) {
        return (int) Math.round(v * s);
    }

    // ---- drawing helpers: shapes in real pixels, text in GUI units ----

    private void fill(GuiGraphics g, int x0, int y0, int x1, int y1, int r, int colour) {
        g.pose().pushPose();
        g.pose().scale((float) (1 / s), (float) (1 / s), 1f);
        Soft.fill(g, p(x0), p(y0), p(x1), p(y1), p(r), colour);
        g.pose().popPose();
    }

    private void plate(GuiGraphics g, int x0, int y0, int x1, int y1, int colour) {
        g.pose().pushPose();
        g.pose().scale((float) (1 / s), (float) (1 / s), 1f);
        Soft.plate(g, p(x0), p(y0), p(x1), p(y1), p((y1 - y0) / 2.0), colour, Math.max(1, (int) Math.round(s)));
        g.pose().popPose();
    }

    private void well(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.pose().pushPose();
        g.pose().scale((float) (1 / s), (float) (1 / s), 1f);
        Soft.well(g, p(x0), p(y0), p(x1), p(y1), p((y1 - y0) / 2.0), 0xFF000000 | WELL, Math.max(1, (int) Math.round(s)));
        g.pose().popPose();
    }

    private void ring(GuiGraphics g, int x0, int y0, int x1, int y1, int r, int colour) {
        g.pose().pushPose();
        g.pose().scale((float) (1 / s), (float) (1 / s), 1f);
        Soft.outline(g, p(x0), p(y0), p(x1), p(y1), p(r), colour);
        g.pose().popPose();
    }

    private void text(GuiGraphics g, String t, int x, int y, int colour) {
        g.drawString(font, t, x, y, 0xFF000000 | colour, false);
    }

    // A pill button; returns its width. Draws brighter under the mouse and remembers where it is so a click can find it.
    private int pill(GuiGraphics g, int x, int y, int h, String label, int colour, Runnable on) {
        int w = font.width(label) + 12;
        boolean over = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        plate(g, x, y, x + w, y + h, over ? brighter(colour) : colour);
        text(g, label, x + 6, y + (h - 8) / 2 + 1, TEXT);
        pills.add(new Pill(x, y, x + w, y + h, on));
        return w;
    }

    private static int brighter(int c) {
        int r = Math.min(255, ((c >> 16) & 255) + 28), gr = Math.min(255, ((c >> 8) & 255) + 28), b = Math.min(255, (c & 255) + 28);
        return (r << 16) | (gr << 8) | b;
    }

    private void dot(GuiGraphics g, int cx, int cy, int radius, int colour) {
        fill(g, cx - radius, cy - radius, cx + radius, cy + radius, radius, 0xFF000000 | colour);
    }

    private void head(GuiGraphics g, UUID id, int x, int y) {
        PlayerInfo info = minecraft.getConnection() == null ? null : minecraft.getConnection().getPlayerInfo(id);
        ResourceLocation skin = info != null ? info.getSkinLocation() : DefaultPlayerSkin.getDefaultSkin(id);
        fill(g, x - 1, y - 1, x + 15, y + 15, 3, 0xFF000000 | PLUM);
        PlayerFaceRenderer.draw(g, skin, x, y, 14);
    }

    private boolean online(UUID id) {
        return minecraft.getConnection() != null && minecraft.getConnection().getPlayerInfo(id) != null;
    }

    private Mate mate(UUID id) {
        for (Mate m : ClientState.mates()) {
            if (m.id().equals(id)) return m;
        }
        return null;
    }

    private void send(int kind, String text, int number, UUID id) {
        Net.toServer(new Net.Action(kind, text, number, id));
    }

    // ---- the screen ----

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        mouseX = mx;
        mouseY = my;
        s = minecraft.getWindow().getGuiScale();
        pills.clear();
        renderBackground(g);
        Net.State st = ClientState.state();
        boolean team = st.team() != null;
        boolean owner = team && minecraft.player != null && minecraft.player.getUUID().equals(st.owner());

        fill(g, ox - 1, oy - 1, ox + W + 1, oy + H + 1, 10, 0xFF000000 | PANEL_EDGE);
        fill(g, ox, oy, ox + W, oy + H, 9, 0xFF000000 | PANEL);

        int rgb = team && TeamRules.validColour(st.colour()) ? TeamRules.COLOUR_RGB[st.colour()] : PANEL_EDGE;
        dot(g, ox + 14, oy + 14, 4, rgb);
        text(g, team ? st.team() : "No team yet", ox + 24, oy + 10, rgb);
        if (team) {
            String count = st.members().size() + "/" + st.max();
            text(g, count, ox + W - 10 - font.width(count), oy + 10, DIM);
        }

        nameBox.visible = nameBox.active = !team;
        inviteBox.visible = inviteBox.active = team;
        if (team) drawTeam(g, st, owner);
        else drawNoTeam(g, st);

        // status line
        if (System.currentTimeMillis() < statusUntil) {
            text(g, font.plainSubstrByWidth(status, W - 20), ox + 10, oy + H - 14, DIM);
        }
        super.render(g, mx, my, pt);
    }

    private void drawNoTeam(GuiGraphics g, Net.State st) {
        int y = oy + 28;
        if (st.invites().isEmpty()) {
            text(g, "Make a team below, or wait for an invite.", ox + 10, y + 2, DIM);
        }
        int shown = 0;
        for (Net.Inv inv : st.invites()) {
            if (shown++ == 3) break;
            fill(g, ox + 8, y, ox + W - 8, y + ROW - 2, 8, 0xFF000000 | ROW_BG);
            text(g, font.plainSubstrByWidth(inv.teamName(), 62), ox + 14, y + 4, TEXT);
            text(g, font.plainSubstrByWidth("from " + inv.inviter(), 74), ox + 14 + 66, y + 4, DIM);
            int px = ox + W - 14;
            int wNo = font.width("No") + 12, wYes = font.width("Accept") + 12;
            pill(g, px - wNo, y + 2, ROW - 6, "No", PLUM, () -> send(Net.DENY, "", 0, inv.team()));
            pill(g, px - wNo - 4 - wYes, y + 2, ROW - 6, "Accept", PINK, () -> send(Net.ACCEPT, "", 0, inv.team()));
            y += ROW;
        }
        int cy = oy + 112;
        text(g, "Make a team", ox + 12, cy - 12, DIM);
        well(g, ox + 8, cy, ox + 8 + 150, cy + 16);
        nameBox.setX(ox + 8 + 10);
        nameBox.setY(cy + 4);
        nameBox.setWidth(130);
        pill(g, ox + 8 + 150 + 6, cy, 16, "Create", PINK, this::create);
        colourDots(g, ox + 12, cy + 30, pickColour, i -> pickColour = i);
    }

    private void create() {
        send(Net.CREATE, nameBox.getValue(), pickColour, null);
        nameBox.setValue("");
    }

    private void colourDots(GuiGraphics g, int x, int y, int selected, java.util.function.IntConsumer pick) {
        for (int i = 0; i < TeamRules.COLOUR_RGB.length; i++) {
            int cx = x + i * 14 + 6;
            dot(g, cx, y + 6, 5, TeamRules.COLOUR_RGB[i]);
            if (i == selected) ring(g, cx - 7, y - 2, cx + 7, y + 14, 7, 0xFFFFFFFF);
            final int index = i;
            pills.add(new Pill(cx - 7, y - 1, cx + 7, y + 13, () -> pick.accept(index)));
        }
    }

    private void drawTeam(GuiGraphics g, Net.State st, boolean owner) {
        UUID me = minecraft.player == null ? null : minecraft.player.getUUID();
        List<Net.Member> members = st.members();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, members.size() - ROWS)));
        int y = oy + 28;
        for (int i = scroll; i < Math.min(members.size(), scroll + ROWS); i++) {
            Net.Member m = members.get(i);
            fill(g, ox + 8, y, ox + W - 8, y + ROW - 2, 8, 0xFF000000 | ROW_BG);
            head(g, m.id(), ox + 13, y + 2);
            boolean isOwner = m.id().equals(st.owner());
            boolean on = online(m.id());
            text(g, font.plainSubstrByWidth(m.name(), 66), ox + 32, y + 5, on ? TEXT : DIM);
            if (isOwner) text(g, "★", ox + 32 + Math.min(66, font.width(m.name())) + 3, y + 5, 0xFFD36B);
            dot(g, ox + 112, y + 9, 3, on ? 0x62DE6A : 0x6A5A72);
            Mate mate = m.id().equals(me) ? null : mate(m.id());
            float hp = 0, max = 20;
            if (m.id().equals(me) && minecraft.player != null) {
                hp = minecraft.player.getHealth();
                max = minecraft.player.getMaxHealth();
            } else if (mate != null) {
                hp = mate.health();
                max = mate.maxHealth();
            }
            if (on) {
                well(g, ox + 120, y + 6, ox + 156, y + 12);
                int bar = Math.round(34 * Math.max(0f, Math.min(1f, hp / Math.max(1f, max))));
                if (bar > 0) fill(g, ox + 121, y + 7, ox + 121 + bar, y + 11, 2, 0xFF000000 | (hp / max > 0.35f ? 0xFF88C8 : 0xFF5A5A));
            }
            if (owner && !m.id().equals(me)) {
                int px = ox + W - 14;
                int wKick = font.width("Remove") + 12;
                pill(g, px - wKick, y + 2, ROW - 6, "Remove", RED, () -> send(Net.KICK, "", 0, m.id()));
                int wOwn = font.width("Owner") + 12;
                pill(g, px - wKick - 3 - wOwn, y + 2, ROW - 6, "Owner", PLUM, () -> send(Net.OWNER, "", 0, m.id()));
            }
            y += ROW;
        }
        if (members.size() > ROWS) text(g, (scroll > 0 ? "▲ " : "") + (scroll + ROWS < members.size() ? "▼" : ""), ox + W - 30, oy + 28 + ROWS * ROW - 2, DIM);

        int iy = oy + 28 + ROWS * ROW + 4;
        well(g, ox + 8, iy, ox + 8 + 150, iy + 16);
        inviteBox.setX(ox + 18);
        inviteBox.setY(iy + 4);
        inviteBox.setWidth(130);
        pill(g, ox + 8 + 150 + 6, iy, 16, "Invite", PINK, this::invite);

        int fy = iy + 22;
        int x = ox + 10;
        x += pill(g, x, fy, 16, "Leave", PLUM, () -> send(Net.LEAVE, "", 0, null)) + 4;
        if (owner) {
            pill(g, x, fy, 16, "Disband", RED, () -> send(Net.DISBAND, "", 0, null));
            colourDots(g, ox + W - 8 - 8 * 14, fy + 1, st.colour(), i -> send(Net.COLOUR, "", i, null));
        }

        drawSuggestions(g, iy);
    }

    private void invite() {
        if (!inviteBox.getValue().isBlank()) send(Net.INVITE, inviteBox.getValue().trim(), 0, null);
        inviteBox.setValue("");
    }

    private void drawSuggestions(GuiGraphics g, int iy) {
        if (!inviteBox.isFocused() || minecraft.getConnection() == null) return;
        String typed = inviteBox.getValue().toLowerCase(Locale.ROOT);
        Net.State st = ClientState.state();
        List<String> names = new ArrayList<>();
        for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
            String n = info.getProfile().getName();
            boolean onTeam = st.members().stream().anyMatch(m -> m.id().equals(info.getProfile().getId()));
            if (!onTeam && n.toLowerCase(Locale.ROOT).startsWith(typed) && !n.equalsIgnoreCase(typed)) names.add(n);
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        int y = iy - 2;
        for (int i = 0; i < Math.min(4, names.size()); i++) {
            String n = names.get(i);
            y -= 16;
            pill(g, ox + 8, y, 14, n, PLUM, () -> {
                inviteBox.setValue(n);
                inviteBox.moveCursorToEnd();
            });
        }
        g.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (int i = pills.size() - 1; i >= 0; i--) {
                Pill pill = pills.get(i);
                if (pill.in(mx, my)) {
                    pill.on().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll -= (int) Math.signum(delta);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 257 || key == 335) {
            if (nameBox.isFocused() && nameBox.visible) {
                create();
                return true;
            }
            if (inviteBox.isFocused() && inviteBox.visible) {
                invite();
                return true;
            }
        }
        // the menu key closes the menu again, unless you are typing
        if (!nameBox.isFocused() && !inviteBox.isFocused() && ClientSetup.OPEN.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
