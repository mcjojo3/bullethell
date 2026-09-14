package mc.sayda.bullethell.client.screen;

import mc.sayda.bullethell.client.BHSfx;
import mc.sayda.bullethell.client.ClientLobbyState;
import mc.sayda.bullethell.network.BHPackets;
import mc.sayda.bullethell.network.LobbyStatePacket;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Pick someone to invite to the party, from a scrolling list of everyone online.
 *
 * A list rather than a row of cards: a server can have far more players than fit across
 * the screen, and a name is all there is to tell them apart. People already in the party
 * stay listed but greyed out, so the roster reads the same here as it does in the lobby.
 */
@Environment(EnvType.CLIENT)
public class InvitePlayerScreen extends Screen {

    private static final int PANEL_W = 300;
    private static final int ROW_H = 20;
    private static final int BTN_H = 20;
    private static final int LIST_TOP = 72;

    private static final int COL_TITLE = 0xFFFFE600;
    private static final int COL_DIM = 0xFF8899AA;
    private static final int COL_TEXT = 0xFFCCCCCC;
    private static final int COL_SEL = 0xFFFFDD00;
    private static final int COL_IN_PARTY = 0xFF55DD77;
    private static final int COL_ROW_SEL = 0xFF1A1A38;

    /** One online player, with how they already relate to this party. */
    private record Candidate(UUID uuid, String name, boolean inParty, boolean requested) {}

    private final Screen parent;
    private final List<Candidate> candidates = new ArrayList<>();

    private int selected = 0;
    private int scroll = 0;
    private int panelX;
    private int listTop;
    private int visibleRows;
    private Button inviteBtn;

    public InvitePlayerScreen(Screen parent) {
        super(Component.literal("Invite Player"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        mc.sayda.bullethell.client.BHScaleManager.applyIdealScale();
        refresh();
        rebuild();
    }

    /** Rebuilt on open so the list reflects the roster as it stands right now. */
    private void refresh() {
        candidates.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;

        UUID self = mc.player.getUUID();
        ClientLobbyState lobby = ClientLobbyState.INSTANCE;

        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            UUID id = info.getProfile().getId();
            if (id.equals(self)) continue;

            boolean inParty = false;
            for (LobbyStatePacket.Member m : lobby.members) {
                if (m.uuid().equals(id)) { inParty = true; break; }
            }
            boolean requested = false;
            for (LobbyStatePacket.Pending p : lobby.pending) {
                if (p.uuid().equals(id)) { requested = true; break; }
            }
            candidates.add(new Candidate(id, info.getProfile().getName(), inParty, requested));
        }
        candidates.sort(Comparator.comparing(Candidate::name, String.CASE_INSENSITIVE_ORDER));

        if (selected >= candidates.size()) selected = Math.max(0, candidates.size() - 1);
    }

    private void rebuild() {
        clearWidgets();
        panelX = (width - PANEL_W) / 2;
        listTop = LIST_TOP;
        // Leave room for the buttons and the hint line below the list.
        visibleRows = Math.max(3, (height - listTop - 70) / ROW_H);

        int btnY = listTop + visibleRows * ROW_H + 12;

        inviteBtn = Button.builder(Component.literal("Invite"), b -> confirm())
                .pos(panelX, btnY).size(140, BTN_H).build();
        inviteBtn.active = canInviteSelected();
        addRenderableWidget(inviteBtn);

        addRenderableWidget(Button.builder(Component.literal("Back"), b -> {
            BHSfx.playBack();
            onClose();
        }).pos(panelX + PANEL_W - 140, btnY).size(140, BTN_H).build());

        ensureVisible();
    }

    // ---------------------------------------------------------------- selection

    private boolean canInviteSelected() {
        if (selected < 0 || selected >= candidates.size()) return false;
        return !candidates.get(selected).inParty();
    }

    private void ensureVisible() {
        if (selected < scroll) scroll = selected;
        else if (selected >= scroll + visibleRows) scroll = selected - visibleRows + 1;
        clampScroll();
    }

    private void clampScroll() {
        int max = Math.max(0, candidates.size() - visibleRows);
        if (scroll > max) scroll = max;
        if (scroll < 0) scroll = 0;
    }

    private void move(int delta) {
        if (candidates.isEmpty()) return;
        int next = selected + delta;
        if (next < 0 || next >= candidates.size()) return;
        selected = next;
        BHSfx.playSelect();
        ensureVisible();
        if (inviteBtn != null) inviteBtn.active = canInviteSelected();
    }

    private void confirm() {
        if (!canInviteSelected()) return;
        Candidate target = candidates.get(selected);
        BHSfx.playSelect();
        BHPackets.sendInvitePlayer(target.uuid());
        onClose();
    }

    // ---------------------------------------------------------------- render

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        gfx.fill(0, 0, width, height, 0xFF0A0A14);
        gfx.drawCenteredString(font, "INVITE PLAYER", width / 2, 24, COL_TITLE);

        if (candidates.isEmpty()) {
            gfx.drawCenteredString(font, "No other players online.", width / 2, height / 2 - 4, COL_DIM);
            super.render(gfx, mouseX, mouseY, partialTick);
            return;
        }

        gfx.drawString(font, "ONLINE (" + candidates.size() + ")", panelX, listTop - 14, COL_DIM, false);
        gfx.hLine(panelX, panelX + PANEL_W, listTop - 4, 0xFF223355);

        int shown = Math.min(visibleRows, candidates.size() - scroll);
        for (int i = 0; i < shown; i++) {
            int idx = scroll + i;
            Candidate c = candidates.get(idx);
            int rowY = listTop + i * ROW_H;
            boolean sel = idx == selected;
            boolean hover = mouseX >= panelX && mouseX < panelX + PANEL_W
                    && mouseY >= rowY && mouseY < rowY + ROW_H;

            if (sel || hover) {
                gfx.fill(panelX, rowY, panelX + PANEL_W, rowY + ROW_H - 2, COL_ROW_SEL);
            }
            int nameCol = c.inParty() ? COL_DIM : (sel ? COL_SEL : COL_TEXT);
            gfx.drawString(font, c.name(), panelX + 6, rowY + 5, nameCol, false);

            String status = c.inParty() ? "in party" : c.requested() ? "asked to join" : "";
            if (!status.isEmpty()) {
                gfx.drawString(font, status, panelX + PANEL_W - font.width(status) - 6, rowY + 5,
                        c.inParty() ? COL_IN_PARTY : COL_DIM, false);
            }
        }

        // Scroll position, only when the list actually overflows.
        if (candidates.size() > visibleRows) {
            int trackX = panelX + PANEL_W + 4;
            int trackTop = listTop;
            int trackH = visibleRows * ROW_H;
            gfx.fill(trackX, trackTop, trackX + 2, trackTop + trackH, 0xFF223355);
            int thumbH = Math.max(8, trackH * visibleRows / candidates.size());
            int maxScroll = candidates.size() - visibleRows;
            int thumbY = trackTop + (trackH - thumbH) * scroll / Math.max(1, maxScroll);
            gfx.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, COL_TITLE);
        }

        String hint = canInviteSelected()
                ? "↑ / ↓  browse     Enter  invite     ESC  back"
                : "That player is already in your party.";
        gfx.drawCenteredString(font, hint, width / 2, height - 26,
                canInviteSelected() ? COL_DIM : COL_IN_PARTY);

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics gfx) {
        // Solid fill happens in render().
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;

        int shown = Math.min(visibleRows, Math.max(0, candidates.size() - scroll));
        for (int i = 0; i < shown; i++) {
            int rowY = listTop + i * ROW_H;
            if (mx >= panelX && mx < panelX + PANEL_W && my >= rowY && my < rowY + ROW_H) {
                int idx = scroll + i;
                if (idx == selected) {
                    confirm(); // second click on the same name invites
                } else {
                    selected = idx;
                    BHSfx.playSelect();
                    if (inviteBtn != null) inviteBtn.active = canInviteSelected();
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (candidates.size() > visibleRows) {
            scroll -= (int) Math.signum(delta);
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            BHSfx.playBack();
            onClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            move(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            move(1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            confirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
