package mc.sayda.bullethell.client.screen;

import mc.sayda.bullethell.arena.DifficultyConfig;
import mc.sayda.bullethell.client.BHScaleManager;
import mc.sayda.bullethell.client.BHSfx;
import mc.sayda.bullethell.network.BHPackets;
import mc.sayda.bullethell.network.LobbyActionPacket;
import mc.sayda.bullethell.network.OpenChallengePacket;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Shown after accepting an NPC challenge: play it alone, or open a party for it. Laid
 * out like the difficulty and character screens - one card per choice.
 *
 * Singleplayer carries on to difficulty select. Multiplayer asks the server to open a
 * party for this stage; the roster it sends back opens the party screen, where
 * everything else about a multiplayer run is settled.
 */
@Environment(EnvType.CLIENT)
public class PlayModeScreen extends Screen {

    private static final int CARD_W   = 130;
    private static final int CARD_H   = 120;
    private static final int CARD_GAP = 22;
    private static final int BTN_H    = 20;

    private static final int SINGLEPLAYER = 0;
    private static final int MULTIPLAYER  = 1;

    private static final String[] TITLES = { "SINGLEPLAYER", "MULTIPLAYER" };
    private static final int[] COLORS = { 0xFF00FFE0, 0xFFFFAA00 };
    private static final String[] SUBTITLES = {
        "Take on the challenge alone",
        "Open a party and invite friends",
    };

    private final OpenChallengePacket pkt;
    private int selectedIndex = SINGLEPLAYER;

    private int cardStartX;
    private int cardTopY;

    public PlayModeScreen(OpenChallengePacket pkt) {
        super(Component.literal("Select Mode"));
        this.pkt = pkt;
    }

    @Override
    protected void init() {
        super.init();
        BHScaleManager.applyIdealScale();
        rebuildButtons();
    }

    private void rebuildButtons() {
        clearWidgets();
        int n = TITLES.length;
        int totalW = n * CARD_W + (n - 1) * CARD_GAP;
        cardStartX = (width - totalW) / 2;
        cardTopY = height / 2 - CARD_H / 2;

        for (int i = 0; i < n; i++) {
            final int idx = i;
            int bx = cardStartX + i * (CARD_W + CARD_GAP);
            addRenderableWidget(Button.builder(
                    Component.literal(i == selectedIndex ? "SELECT" : "PICK"),
                    btn -> {
                        selectedIndex = idx;
                        confirm();
                    })
                    .pos(bx + (CARD_W - 80) / 2, cardTopY + CARD_H - BTN_H - 6)
                    .size(80, BTN_H)
                    .build());
        }
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        gfx.fill(0, 0, width, height, 0xFF060610);

        gfx.drawCenteredString(font, "SELECT MODE", width / 2, 20, 0xFFFFE600);
        gfx.drawCenteredString(font, "Challenge: " + pkt.npcName, width / 2, 33, 0xFFFFDD44);
        gfx.drawCenteredString(font, "← / →  browse     Enter  confirm",
                width / 2, 44, 0xFF445566);

        for (int i = 0; i < TITLES.length; i++) {
            int col = COLORS[i];
            int bx = cardStartX + i * (CARD_W + CARD_GAP);
            boolean sel = (i == selectedIndex);

            gfx.fill(bx, cardTopY, bx + CARD_W, cardTopY + CARD_H, sel ? 0xFF1A1A38 : 0xFF0A0A1E);
            int brd = sel ? col : 0xFF334466;
            gfx.hLine(bx, bx + CARD_W - 1, cardTopY, brd);
            gfx.hLine(bx, bx + CARD_W - 1, cardTopY + CARD_H - 1, brd);
            gfx.vLine(bx, cardTopY, cardTopY + CARD_H, brd);
            gfx.vLine(bx + CARD_W - 1, cardTopY, cardTopY + CARD_H, brd);

            int cx = bx + CARD_W / 2;
            int textY = cardTopY + 18;
            gfx.drawCenteredString(font, TITLES[i], cx, textY, col);
            textY += font.lineHeight + 6;
            for (String line : TextWrap.twoLines(font, SUBTITLES[i], CARD_W - 12)) {
                gfx.drawCenteredString(font, line, cx, textY, 0xFF8888AA);
                textY += font.lineHeight;
            }
            gfx.drawCenteredString(font, detail(i), cx, textY + 4, 0xFF7799CC);

            int swatchY = cardTopY + CARD_H - BTN_H - 14;
            gfx.fill(bx + 10, swatchY, bx + CARD_W - 10, swatchY + 3,
                    sel ? col : (col & 0x00FFFFFF | 0x66000000));
            if (sel) {
                gfx.drawCenteredString(font, "▼", cx, cardTopY + CARD_H - BTN_H - 5, col);
            }
        }

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    /** The difficulty limit each mode plays under. */
    private String detail(int mode) {
        if (mode == MULTIPLAYER)
            return "Up to the party's lowest";
        return "Up to " + DifficultyConfig.fromId(pkt.maxAllowedDifficultyOrdinal).name();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        for (int i = 0; i < TITLES.length; i++) {
            int bx = cardStartX + i * (CARD_W + CARD_GAP);
            if (mx >= bx && mx < bx + CARD_W && my >= cardTopY && my < cardTopY + CARD_H - BTN_H - 6) {
                if (selectedIndex != i) {
                    selectedIndex = i;
                    BHSfx.playSelect();
                    rebuildButtons();
                }
                return true;
            }
        }
        return false;
    }

    private void confirm() {
        BHSfx.playSelect();
        if (selectedIndex == SINGLEPLAYER) {
            Minecraft.getInstance().setScreen(
                    new DifficultySelectScreen(pkt.stageId, pkt.maxAllowedDifficultyOrdinal));
            return;
        }
        BHPackets.sendLobbyAction(LobbyActionPacket.create(pkt.stageId));
        // The party screen opens when the server answers with the roster; if it refuses,
        // the reason arrives in chat.
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            BHSfx.playBack();
            Minecraft.getInstance().setScreen(new ChallengeScreen(pkt));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT && selectedIndex > 0) {
            selectedIndex--;
            BHSfx.playSelect();
            rebuildButtons();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT && selectedIndex < TITLES.length - 1) {
            selectedIndex++;
            BHSfx.playSelect();
            rebuildButtons();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            confirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
