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

/**
 * The party room: everyone sees the roster, who has picked whom, and what is about to
 * be played. The host owns stage and difficulty; each member owns their own character
 * and ready flag.
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

    private static final int COL_TITLE = 0xFFFFE600;
    private static final int COL_DIM = 0xFF8899AA;
    private static final int COL_TEXT = 0xFFCCCCCC;
    private static final int COL_READY = 0xFF55DD77;
    private static final int COL_WAITING = 0xFFCCAA44;

    private int panelX;
    private int rosterY;

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

        int y = rosterY + Math.max(1, lobby.members.size()) * ROW_H + 14;
        boolean host = lobby.isSelfHost();
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
            // No stage button: the party runs whichever stage the host accepted from an
            // NPC, so there is nothing to pick here.
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
            start.active = !lobby.starting && lobby.allReady() && !lobby.stageId.isBlank();
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
                ? "(host has not picked a stage)"
                : StageLoader.load(lobby.stageId).title;
        DifficultyConfig diff = DifficultyConfig.fromId(lobby.difficultyOrdinal);

        gfx.drawString(font, "Stage:", panelX, 50, COL_DIM, false);
        gfx.drawString(font, stageLabel, panelX + 60, 50,
                lobby.stageId.isBlank() ? COL_WAITING : COL_TEXT, false);
        gfx.drawString(font, "Difficulty:", panelX, 62, COL_DIM, false);
        gfx.drawString(font, diff.name(), panelX + 60, 62, difficultyColor(diff), false);

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
            gfx.drawString(font, charLabel, panelX + 110, y,
                    m.characterId().isBlank() ? COL_WAITING : COL_TEXT, false);

            String status = isHost ? "HOST" : "";
            if (m.ready()) status = status.isEmpty() ? "READY" : "HOST  READY";
            gfx.drawString(font, status, panelX + PANEL_W - font.width(status) - 4, y,
                    m.ready() ? COL_READY : COL_DIM, false);
            y += ROW_H;
        }

        if (!lobby.isSelfHost()) {
            gfx.drawCenteredString(font, "The host chooses the stage and starts the run.",
                    width / 2, height - 28, COL_DIM);
        }

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
