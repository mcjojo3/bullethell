package mc.sayda.bullethell.forge.client;

import mc.sayda.bullethell.boss.BossLoader;
import mc.sayda.bullethell.boss.StageDefinition;
import mc.sayda.bullethell.boss.StageLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * First screen shown after /bullethell start.
 * Displays one card per registered stage; on confirm opens DifficultySelectScreen.
 *
 * Card layout:
 *   ┌──────────────────────┐
 *   │  Stage Title         │  (large, coloured)
 *   │  Boss: Boss Name     │
 *   │  Waves: N            │
 *   │  ───────────────     │
 *   │  [ SELECT ]          │
 *   └──────────────────────┘
 */
public class LevelSelectScreen extends Screen {

    private static final int CARD_W   = 130;
    private static final int CARD_H   = 130;
    private static final int CARD_GAP = 22;
    private static final int BTN_H    = 20;

    // One accent colour per registered stage slot — extend when adding stages
    private static final int[] STAGE_COLORS = {
        0xFF00FFE0,  // Stage 1 — cyan
        0xFFFFE600,  // Marisa  — gold
        0xFFFF88AA,  // slot 3
        0xFF88AAFF,  // slot 4
        0xFFFF7700,  // slot 5
        0xFF88FF88,  // slot 6
    };

    private final List<StageDefinition> stages;
    private int selectedIndex = 0;
    private int cardStartX;
    private int cardTopY;

    public LevelSelectScreen() {
        super(Component.literal("Select Stage"));
        this.stages = StageLoader.loadAll();
    }

    @Override
    protected void init() {
        super.init();
        rebuildButtons();
    }

    private void rebuildButtons() {
        clearWidgets();
        int n      = stages.size();
        int totalW = n * CARD_W + (n - 1) * CARD_GAP;
        cardStartX = (width  - totalW) / 2;
        cardTopY   = height / 2 - CARD_H / 2;

        for (int i = 0; i < n; i++) {
            final int idx = i;
            int bx   = cardStartX + i * (CARD_W + CARD_GAP);
            int btnX = bx + (CARD_W - 80) / 2;
            int btnY = cardTopY + CARD_H - BTN_H - 6;

            addRenderableWidget(Button.builder(
                    Component.literal(i == selectedIndex ? "SELECT" : "PICK"),
                    btn -> { selectedIndex = idx; confirm(); })
                    .pos(btnX, btnY)
                    .size(80, BTN_H)
                    .build());
        }
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        gfx.fill(0, 0, width, height, 0xFF060610);
        gfx.drawCenteredString(font, "SELECT STAGE", width / 2, 20, 0xFFFFE600);
        gfx.drawCenteredString(font, "\u2190 / \u2192  browse     Enter  confirm",
                width / 2, 33, 0xFF445566);

        int n = stages.size();
        for (int i = 0; i < n; i++) {
            StageDefinition stage = stages.get(i);
            int col  = stageColor(i);
            int bx   = cardStartX + i * (CARD_W + CARD_GAP);
            boolean sel = (i == selectedIndex);

            // Background
            gfx.fill(bx, cardTopY, bx + CARD_W, cardTopY + CARD_H,
                    sel ? 0xFF1A1A38 : 0xFF0A0A1E);
            // Border
            int brd = sel ? col : 0xFF334466;
            gfx.hLine(bx, bx + CARD_W - 1, cardTopY,              brd);
            gfx.hLine(bx, bx + CARD_W - 1, cardTopY + CARD_H - 1, brd);
            gfx.vLine(bx,              cardTopY, cardTopY + CARD_H, brd);
            gfx.vLine(bx + CARD_W - 1, cardTopY, cardTopY + CARD_H, brd);

            int cx    = bx + CARD_W / 2;
            int textY = cardTopY + 16;

            // Stage title
            gfx.drawCenteredString(font, stage.title, cx, textY, col);
            textY += font.lineHeight + 8;

            // Divider
            gfx.hLine(bx + 8, bx + CARD_W - 9, textY - 3, sel ? (col & 0x00FFFFFF | 0x66000000) : 0x22FFFFFF);

            // Boss name (load from BossDefinition)
            String bossName = "???";
            try {
                bossName = BossLoader.load(stage.bossId).name;
            } catch (Exception ignored) {}
            gfx.drawCenteredString(font, "Boss: " + bossName, cx, textY, sel ? 0xFFFFDD88 : 0xFFAAAAAA);
            textY += font.lineHeight + 4;

            // Wave count
            int waveCount = stage.waves != null ? stage.waves.size() : 0;
            gfx.drawCenteredString(font, "Waves: " + waveCount, cx, textY, 0xFF7799CC);

            // Colour swatch
            int swatchY = cardTopY + CARD_H - BTN_H - 16;
            gfx.fill(bx + 10, swatchY, bx + CARD_W - 10, swatchY + 3,
                    sel ? col : (col & 0x00FFFFFF | 0x55000000));

            // Selection indicator arrow above button
            if (sel) {
                gfx.drawCenteredString(font, "\u25bc", cx,
                        cardTopY + CARD_H - BTN_H - 7, col);
            }
        }

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int n = stages.size();
        for (int i = 0; i < n; i++) {
            int bx = cardStartX + i * (CARD_W + CARD_GAP);
            if (mx >= bx && mx < bx + CARD_W
                    && my >= cardTopY && my < cardTopY + CARD_H - BTN_H - 6) {
                if (selectedIndex != i) { selectedIndex = i; rebuildButtons(); }
                return true;
            }
        }
        return false;
    }

    private void confirm() {
        if (stages.isEmpty()) return;
        Minecraft.getInstance().setScreen(
                new DifficultySelectScreen(stages.get(selectedIndex).id));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 263 && selectedIndex > 0)                   { selectedIndex--; rebuildButtons(); return true; }
        if (keyCode == 262 && selectedIndex < stages.size() - 1)   { selectedIndex++; rebuildButtons(); return true; }
        if (keyCode == 257 || keyCode == 335)                      { confirm(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static int stageColor(int index) {
        return (index >= 0 && index < STAGE_COLORS.length)
                ? STAGE_COLORS[index] : 0xFFCCCCCC;
    }
}
