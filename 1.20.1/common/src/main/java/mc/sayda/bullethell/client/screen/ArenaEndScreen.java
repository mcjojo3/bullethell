package mc.sayda.bullethell.client.screen;

import mc.sayda.bullethell.client.BHScaleManager;
import mc.sayda.bullethell.client.BHSfx;
import mc.sayda.bullethell.client.ClientArenaState;
import mc.sayda.bullethell.network.ArenaEndPacket;
import mc.sayda.bullethell.network.BHPackets;
import mc.sayda.bullethell.network.RetryArenaPacket;
import mc.sayda.bullethell.render.BulletHellRenderer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Full-screen overlay shown after an arena ends, rendered over the frozen arena
 * (isPauseScreen=true keeps the game world visible behind it, matching ArenaQuitScreen).
 *
 * Phase 1 – DIALOG: the boss's victory/defeat quote rendered using the same
 * dialog-box style as the in-game pre-boss intro (via BulletHellRenderer.drawEndDialogBox).
 * Skipped automatically when there is no quote.
 *
 * Phase 2 – STATS: score panel (score, lives, bombs, graze, spells) with three
 * actions: OK (close), SHARE (broadcast run), RETRY (restart stage).
 */
@Environment(EnvType.CLIENT)
public class ArenaEndScreen extends Screen {

    private enum Phase { DIALOG, STATS }

    // Button indices
    private static final int BTN_OK    = 0;
    private static final int BTN_SHARE = 1;
    private static final int BTN_RETRY = 2;
    private static final int BTN_COUNT = 3;

    private final ArenaEndPacket data;
    private Phase phase;

    /** Ticks since the current phase started — drives slide-in and blink. */
    private int phaseTick = 0;
    private int selectedBtn = BTN_OK;

    // ---- button layout (computed in init) ----
    private int btnY;
    private final int[] btnX = new int[BTN_COUNT];
    private final int[] btnW = new int[BTN_COUNT];
    private static final int BTN_H = 20;

    public ArenaEndScreen(ArenaEndPacket data) {
        super(Component.empty());
        this.data  = data;
        this.phase = data.bossDialog.isBlank() ? Phase.STATS : Phase.DIALOG;
    }

    // Arena rendering stays active while this screen is open
    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    protected void init() {
        super.init();
        String[] labels = {"  OK  ", " SHARE ", " RETRY "};
        int totalW = 0;
        for (int i = 0; i < BTN_COUNT; i++) {
            btnW[i] = font.width(labels[i]) + 16;
            totalW += btnW[i];
        }
        int gap = 16;
        totalW += gap * (BTN_COUNT - 1);
        int startX = (width - totalW) / 2;
        for (int i = 0; i < BTN_COUNT; i++) {
            btnX[i] = startX;
            startX += btnW[i] + gap;
        }
        btnY = height - 50;
    }

    @Override
    public void tick() {
        phaseTick++;
    }

    // ---------------------------------------------------------------- render

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        // Semi-transparent overlay over the frozen arena (same as ArenaQuitScreen)
        gfx.fill(0, 0, width, height, 0x88000000);

        if (phase == Phase.DIALOG) {
            BulletHellRenderer.drawEndDialogBox(gfx, width, height,
                    data.bossName.isBlank() ? "???" : data.bossName,
                    data.bossId,
                    data.bossDialog,
                    phaseTick,
                    true);
        } else {
            renderStatsPhase(gfx, mouseX, mouseY);
        }
    }

    @Override
    public void renderBackground(GuiGraphics gfx) {
        // No Minecraft dirt background — arena renders behind us
    }

    // ---------------------------------------------------------------- stats phase

    private void renderStatsPhase(GuiGraphics gfx, int mouseX, int mouseY) {
        boolean won = data.won;

        // ---- Header ----
        int headerY = 22;
        if (won) {
            gfx.drawCenteredString(font, "\u2605 STAGE CLEAR \u2605", width / 2, headerY, 0xFFFFE000);
        } else {
            gfx.drawCenteredString(font, "\u2620 GAME OVER \u2620", width / 2, headerY, 0xFFFF3344);
        }

        // Decorative separator
        int sepY = headerY + font.lineHeight + 4;
        int sepW = 160;
        int sepX = (width - sepW) / 2;
        gfx.hLine(sepX, sepX + sepW - 1, sepY, won ? 0x66FFE000 : 0x66FF3344);

        // ---- Panel background ----
        int panelW = 220;
        int panelH = 90;
        int panelX = (width - panelW) / 2;
        int panelY = sepY + 6;
        gfx.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xAA000020);
        gfx.hLine(panelX, panelX + panelW - 1, panelY, 0x44FFFFFF);
        gfx.hLine(panelX, panelX + panelW - 1, panelY + panelH - 1, 0x44FFFFFF);
        gfx.vLine(panelX, panelY, panelY + panelH, 0x44FFFFFF);
        gfx.vLine(panelX + panelW - 1, panelY, panelY + panelH, 0x44FFFFFF);

        // ---- Stats ----
        int lh   = font.lineHeight;
        int rowY = panelY + 6;
        int lx   = panelX + 10;
        int rx   = panelX + panelW / 2 + 4;

        // Row 1: Character | Score
        gfx.drawString(font, "\u266A " + data.characterName, lx, rowY, 0xFFCCCCFF, false);
        gfx.drawString(font, "Score: " + String.format("%,d", data.score), rx, rowY, 0xFFFFEE88, false);

        // Row 2: Lives | Bombs | Graze
        rowY += lh + 3;
        gfx.drawString(font, "\u2665 " + Math.max(0, data.lives), lx, rowY, 0xFFFF7777, false);
        gfx.drawString(font, "\u2736 " + Math.max(0, data.bombs), lx + 42, rowY, 0xFF88AAFF, false);
        gfx.drawString(font, "Graze: " + data.graze, rx, rowY, 0xFF88FF88, false);

        // Row 3: Spells
        rowY += lh + 3;
        boolean perfect = data.spellsAttempted > 0 && data.spellsCaptured == data.spellsAttempted;
        String spellStr = "Spells: " + data.spellsCaptured + " / " + data.spellsAttempted;
        gfx.drawString(font, spellStr, lx, rowY, 0xFFCCCCCC, false);
        if (perfect)
            gfx.drawString(font, "\u2605 PERFECT!", lx + font.width(spellStr) + 4, rowY, 0xFFFFDD00, false);

        // Row 4: Progress (defeat only) or character portrait (win)
        if (!won) {
            rowY += lh + 3;
            gfx.drawString(font, String.format("Progress: %.1f%%", data.completionPercent),
                    lx, rowY, 0xFFAAAAAA, false);
        } else if (!data.characterId.isBlank()) {
            int portHalf = 22;
            int portCx = panelX + panelW - portHalf - 8;
            int portCy = panelY + panelH / 2;
            BulletHellRenderer.drawCharacterPortrait(gfx, data.characterId, portCx, portCy, portHalf);
        }

        // ---- Buttons ----
        String[] labels = {"  OK  ", " SHARE ", " RETRY "};
        for (int i = 0; i < BTN_COUNT; i++) {
            boolean sel   = (i == selectedBtn);
            boolean hover = mouseX >= btnX[i] && mouseX < btnX[i] + btnW[i]
                         && mouseY >= btnY    && mouseY < btnY + BTN_H;
            int bgCol  = (sel || hover) ? 0xCC223366 : 0xAA0A0A20;
            int txtCol = (sel || hover) ? 0xFFFFE600 : 0xFF8899BB;
            int brdCol = (sel || hover) ? 0xFFFFE600 : 0xFF334466;

            gfx.fill(btnX[i], btnY, btnX[i] + btnW[i], btnY + BTN_H, bgCol);
            gfx.hLine(btnX[i], btnX[i] + btnW[i] - 1, btnY, brdCol);
            gfx.hLine(btnX[i], btnX[i] + btnW[i] - 1, btnY + BTN_H - 1, brdCol);
            gfx.vLine(btnX[i], btnY, btnY + BTN_H, brdCol);
            gfx.vLine(btnX[i] + btnW[i] - 1, btnY, btnY + BTN_H, brdCol);
            gfx.drawCenteredString(font, labels[i], btnX[i] + btnW[i] / 2,
                    btnY + (BTN_H - font.lineHeight) / 2, txtCol);
        }

        // ---- Key hint ----
        gfx.drawCenteredString(font, "\u2190 / \u2192  navigate     Enter / Z  confirm",
                width / 2, btnY + BTN_H + 8, 0x55FFFFFF);
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (phase == Phase.DIALOG) {
            boolean ctrl = (mods & GLFW.GLFW_MOD_CONTROL) != 0;
            if (key == GLFW.GLFW_KEY_Z || key == GLFW.GLFW_KEY_ENTER
                    || key == GLFW.GLFW_KEY_KP_ENTER || ctrl) {
                advanceDialog();
                return true;
            }
            return true; // Consume all keys while dialog is showing
        }

        // Stats phase
        if (key == GLFW.GLFW_KEY_LEFT) {
            selectedBtn = (selectedBtn + BTN_COUNT - 1) % BTN_COUNT;
            BHSfx.playSelect();
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT) {
            selectedBtn = (selectedBtn + 1) % BTN_COUNT;
            BHSfx.playSelect();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_Z) {
            confirm(selectedBtn);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            confirm(BTN_OK);
            return true;
        }
        return true; // Consume all keys to prevent inventory / chat opening
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (phase == Phase.DIALOG) {
            advanceDialog();
            return true;
        }
        for (int i = 0; i < BTN_COUNT; i++) {
            if (mx >= btnX[i] && mx < btnX[i] + btnW[i]
                    && my >= btnY && my < btnY + BTN_H) {
                confirm(i);
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- actions

    private void advanceDialog() {
        BHSfx.playSelect();
        phase     = Phase.STATS;
        phaseTick = 0;
    }

    private void confirm(int btn) {
        BHSfx.playSelect();
        switch (btn) {
            case BTN_OK -> Minecraft.getInstance().setScreen(null);
            case BTN_SHARE -> BHPackets.sendShareLastRun(); // Stay on screen
            case BTN_RETRY -> {
                BHPackets.sendRetryArena(new RetryArenaPacket(data.stageId, data.difficulty, data.characterId));
                Minecraft.getInstance().setScreen(null);
            }
        }
    }

    // ---------------------------------------------------------------- cleanup

    @Override
    public void removed() {
        ClientArenaState state = ClientArenaState.INSTANCE;
        state.pendingEndOverlay = false;
        BHScaleManager.restoreOriginalScale();
        state.reset();
    }
}
