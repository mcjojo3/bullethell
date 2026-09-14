package mc.sayda.bullethell.client.screen;

import mc.sayda.bullethell.arena.DifficultyConfig;
import mc.sayda.bullethell.boss.CharacterLoader;
import mc.sayda.bullethell.boss.StageLoader;
import mc.sayda.bullethell.client.BHSfx;
import mc.sayda.bullethell.client.ClientLobbyState;
import mc.sayda.bullethell.network.BHPackets;
import mc.sayda.bullethell.network.LobbyActionPacket;
import mc.sayda.bullethell.network.LobbyStatePacket;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

/**
 * The party room, opened by choosing Multiplayer on an NPC challenge. Everything about a
 * multiplayer run is settled here: the stage is the NPC's, the host picks the difficulty,
 * invites people and starts; each member picks their own character and readies up.
 *
 * Each member's unlock progress on the stage is shown, and the difficulty can never go
 * above what the least-progressed member has unlocked. The server enforces the same cap;
 * this only keeps the buttons honest.
 *
 * Purely a view over {@link ClientLobbyState} - it sends actions and re-reads the
 * server's answer rather than predicting anything, so two clients cannot disagree
 * about who is ready.
 */
@Environment(EnvType.CLIENT)
public class LobbyScreen extends Screen {

    private static final int PANEL_W = 300;
    private static final int ROW_H = 22;
    private static final int BTN_H = 20;
    private static final int DIFF_Y = 62;
    /** Where the difficulty name sits; the host's arrows flank it. */
    private static final int DIFF_VALUE_X = 80;

    private static final int COL_TITLE = 0xFFFFE600;
    private static final int COL_DIM = 0xFF8899AA;
    private static final int COL_TEXT = 0xFFCCCCCC;
    private static final int COL_READY = 0xFF55DD77;
    private static final int COL_WAITING = 0xFFCCAA44;
    private static final int COL_LOCKED = 0xFFDD5555;

    private int panelX;
    private int rosterY;
    /** Top of the join-request block, so render() and rebuild() lay out the same rows. */
    private int pendingY;

    public LobbyScreen() {
        super(Component.literal("Party"));
    }

    @Override
    protected void init() {
        super.init();
        mc.sayda.bullethell.client.BHScaleManager.applyIdealScale();
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        ClientLobbyState lobby = ClientLobbyState.INSTANCE;
        panelX = (width - PANEL_W) / 2;
        rosterY = 92;

        boolean host = lobby.isSelfHost();
        int cap = lobby.partyCapOrdinal();

        if (host) {
            Button down = Button.builder(Component.literal("<"), b -> stepDifficulty(-1))
                    .bounds(panelX + DIFF_VALUE_X - 20, DIFF_Y - 4, 16, 16).build();
            down.active = !lobby.starting && lobby.difficultyOrdinal > 0;
            addRenderableWidget(down);
            Button up = Button.builder(Component.literal(">"), b -> stepDifficulty(1))
                    .bounds(panelX + DIFF_VALUE_X + 56, DIFF_Y - 4, 16, 16).build();
            up.active = !lobby.starting && lobby.difficultyOrdinal < cap;
            addRenderableWidget(up);
        }

        int y = rosterY + Math.max(1, lobby.members.size()) * ROW_H + 14;

        // Join requests answered right here in the roster. Chat cannot be opened from a
        // screen, so the clickable chat prompt is out of reach for a host in the lobby.
        pendingY = y;
        if (host && !lobby.pending.isEmpty()) {
            y += 12; // header row, drawn in render()
            for (LobbyStatePacket.Pending p : lobby.pending) {
                final UUID who = p.uuid();
                Button accept = Button.builder(Component.literal("Accept"), b -> {
                    BHSfx.playSelect();
                    BHPackets.sendLobbyAction(LobbyActionPacket.acceptJoin(who));
                }).bounds(panelX + PANEL_W - 126, y - 5, 60, 16).build();
                accept.active = !lobby.starting;
                addRenderableWidget(accept);
                addRenderableWidget(Button.builder(Component.literal("Deny"), b -> {
                    BHSfx.playBack();
                    BHPackets.sendLobbyAction(LobbyActionPacket.denyJoin(who));
                }).bounds(panelX + PANEL_W - 62, y - 5, 60, 16).build());
                y += ROW_H;
            }
            y += 8;
        }

        LobbyStatePacket.Member self = lobby.self();
        boolean hasCharacter = self != null && !self.characterId().isBlank();

        // Character - everyone picks their own.
        addRenderableWidget(Button.builder(
                Component.literal(hasCharacter ? "Change Character" : "Pick Character"),
                b -> Minecraft.getInstance().setScreen(CharacterSelectScreen.forLobby()))
                .pos(panelX, y).size(140, BTN_H).build());

        // Ready is blocked until a character is chosen, since a run needs one.
        boolean ready = self != null && self.ready();
        Button readyBtn = Button.builder(
                Component.literal(ready ? "Unready" : "Ready"),
                b -> {
                    BHSfx.playSelect();
                    BHPackets.sendLobbyAction(LobbyActionPacket.setReady(!ready));
                })
                .pos(panelX + PANEL_W - 140, y).size(140, BTN_H).build();
        readyBtn.active = hasCharacter && !lobby.starting;
        addRenderableWidget(readyBtn);

        y += BTN_H + 6;

        if (host) {
            addRenderableWidget(Button.builder(
                    Component.literal("Invite Player"),
                    b -> Minecraft.getInstance().setScreen(new InvitePlayerScreen(this)))
                    .pos(panelX + PANEL_W - 140, y).size(140, BTN_H).build());

            y += BTN_H + 6;

            Button start = Button.builder(
                    Component.literal(lobby.starting ? "Starting..." : "Start Run"),
                    b -> {
                        BHSfx.playSelect();
                        BHPackets.sendLobbyAction(LobbyActionPacket.start());
                    })
                    .pos(panelX, y).size(PANEL_W, BTN_H).build();
            start.active = !lobby.starting && lobby.allReady() && !lobby.stageId.isBlank()
                    && cap >= 0 && lobby.difficultyOrdinal <= cap;
            addRenderableWidget(start);
            y += BTN_H + 6;
        }

        addRenderableWidget(Button.builder(
                Component.literal(host ? "Disband Party" : "Leave Party"),
                b -> {
                    BHSfx.playBack();
                    BHPackets.sendLobbyAction(LobbyActionPacket.leave());
                })
                .pos(panelX, y).size(PANEL_W, BTN_H).build());
    }

    private void stepDifficulty(int delta) {
        ClientLobbyState lobby = ClientLobbyState.INSTANCE;
        int next = lobby.difficultyOrdinal + delta;
        int max = Math.min(lobby.partyCapOrdinal(), DifficultyConfig.values().length - 1);
        if (next < 0 || next > max) return;
        BHSfx.playSelect();
        BHPackets.sendLobbyAction(LobbyActionPacket.setDifficulty(next));
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        ClientLobbyState lobby = ClientLobbyState.INSTANCE;
        if (!lobby.active) {
            // Server closed the party: the run started, or it disbanded.
            Minecraft.getInstance().setScreen(null);
            return;
        }

        gfx.fill(0, 0, width, height, 0xFF0A0A14);
        gfx.drawCenteredString(font, "BULLET HELL - PARTY", width / 2, 24, COL_TITLE);

        String stageLabel = lobby.stageId.isBlank()
                ? "(no stage)"
                : StageLoader.load(lobby.stageId).title;
        DifficultyConfig diff = DifficultyConfig.fromId(lobby.difficultyOrdinal);

        gfx.drawString(font, "Stage:", panelX, 50, COL_DIM, false);
        gfx.drawString(font, stageLabel, panelX + DIFF_VALUE_X, 50,
                lobby.stageId.isBlank() ? COL_WAITING : COL_TEXT, false);
        gfx.drawString(font, "Difficulty:", panelX, DIFF_Y, COL_DIM, false);
        gfx.drawString(font, diff.name(), panelX + DIFF_VALUE_X, DIFF_Y, difficultyColor(diff), false);

        gfx.drawString(font, "PLAYERS (" + lobby.readyCount() + "/" + lobby.members.size() + " ready)",
                panelX, 78, COL_DIM, false);
        gfx.hLine(panelX, panelX + PANEL_W, 88, 0xFF223355);

        int y = rosterY;
        for (LobbyStatePacket.Member m : lobby.members) {
            boolean isHost = m.uuid().equals(lobby.hostUuid);
            gfx.drawString(font, m.name(), panelX + 4, y, isHost ? COL_TITLE : COL_TEXT, false);

            String charLabel = m.characterId().isBlank()
                    ? "picking..."
                    : CharacterLoader.load(m.characterId()).name;
            gfx.drawString(font, charLabel, panelX + 92, y,
                    m.characterId().isBlank() ? COL_WAITING : COL_TEXT, false);

            // How far this member has unlocked the stage - what caps the party's difficulty.
            int memberCap = m.maxDifficultyOrdinal();
            String unlock = memberCap < 0 ? "LOCKED" : "max " + DifficultyConfig.fromId(memberCap).name();
            gfx.drawString(font, unlock, panelX + 172, y,
                    memberCap < lobby.difficultyOrdinal ? COL_LOCKED : COL_DIM, false);

            String status = isHost ? "HOST" : "";
            if (m.ready()) status = status.isEmpty() ? "READY" : "HOST READY";
            gfx.drawString(font, status, panelX + PANEL_W - font.width(status) - 4, y,
                    m.ready() ? COL_READY : COL_DIM, false);
            y += ROW_H;
        }

        // Join requests, mirroring the rows laid out in rebuild().
        if (lobby.isSelfHost() && !lobby.pending.isEmpty()) {
            int py = pendingY;
            gfx.drawString(font, "JOIN REQUESTS", panelX, py, COL_WAITING, false);
            py += 12;
            for (LobbyStatePacket.Pending p : lobby.pending) {
                gfx.drawString(font, p.name(), panelX + 4, py, COL_TEXT, false);
                gfx.drawString(font, "wants to join", panelX + 92, py, COL_DIM, false);
                py += ROW_H;
            }
        }

        int cap = lobby.partyCapOrdinal();
        String hint;
        if (cap < 0) {
            hint = "Someone in the party has not unlocked this stage.";
        } else if (lobby.isSelfHost()) {
            hint = "Difficulty goes up to " + DifficultyConfig.fromId(cap).name()
                    + " - the lowest unlock in the party.";
        } else {
            hint = "The host picks the difficulty and starts the run.";
        }
        gfx.drawCenteredString(font, hint, width / 2, height - 28, cap < 0 ? COL_LOCKED : COL_DIM);

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    private static int difficultyColor(DifficultyConfig d) {
        return switch (d) {
            case EASY -> 0xFF88FF88;
            case NORMAL -> 0xFF00FFE0;
            case HARD -> 0xFFFFAA00;
            case LUNATIC -> 0xFFFF3344;
        };
    }

    /** Re-lay the widgets whenever the server pushes a new roster. */
    public void onLobbyUpdated() {
        rebuild();
    }

    @Override
    public void renderBackground(GuiGraphics gfx) {
        // Solid fill happens in render().
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            BHSfx.playBack();
            BHPackets.sendLobbyAction(LobbyActionPacket.leave());
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
