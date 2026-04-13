package mc.sayda.bullethell.forge.client;

import mc.sayda.bullethell.arena.DifficultyConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * First screen shown after /bullethell start.
 * Player picks a difficulty, then the character select screen opens.
 *
 * Layout: 4 vertical difficulty cards (Easy / Normal / Hard / Lunatic)
 *         with a short description and SELECT button per card.
 */
public class DifficultySelectScreen extends Screen {

    private static final int CARD_W   = 110;
    private static final int CARD_H   = 120;
    private static final int CARD_GAP = 18;
    private static final int BTN_H    = 20;

    // Difficulty metadata
    private static final DifficultyConfig[] DIFFS = DifficultyConfig.values();
    private static final int[] COLORS = {
        0xFF88FF88,  // EASY
        0xFF00FFE0,  // NORMAL
        0xFFFFAA00,  // HARD
        0xFFFF3344,  // LUNATIC
    };
    private static final String[] SUBTITLES = {
        "Slower bullets, more lives",
        "Classic experience",
        "Faster patterns, less mercy",
        "Full speed. Good luck.",
    };

    private final String stageId;

    private int selectedIndex = 1; // default NORMAL

    private int cardStartX;
    private int cardTopY;

    public DifficultySelectScreen(String stageId) {
        super(Component.literal("Select Difficulty"));
        this.stageId = stageId;
    }

    @Override
    protected void init() {
        super.init();
        rebuildButtons();
    }

    private void rebuildButtons() {
        clearWidgets();
        int n      = DIFFS.length;
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

        gfx.drawCenteredString(font, "SELECT DIFFICULTY", width / 2, 20, 0xFFFFE600);
        gfx.drawCenteredString(font, "\u2190 / \u2192  browse     Enter  confirm",
                width / 2, 33, 0xFF445566);

        int n = DIFFS.length;
        for (int i = 0; i < n; i++) {
            DifficultyConfig diff = DIFFS[i];
            int col  = COLORS[i];
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

            // Difficulty name (large, coloured)
            int cx   = bx + CARD_W / 2;
            int nameY = cardTopY + 18;
            gfx.drawCenteredString(font, diff.name(), cx, nameY, col);

            // Subtitle
            // Word-wrap to card width manually (simple: split into two lines if too wide)
            String sub  = SUBTITLES[i];
            int    subY = nameY + font.lineHeight + 6;
            if (font.width(sub) <= CARD_W - 8) {
                gfx.drawCenteredString(font, sub, cx, subY, 0xFF8888AA);
            } else {
                // Split at first space past midpoint
                int mid = sub.length() / 2;
                int sp  = sub.indexOf(' ', mid);
                if (sp < 0) sp = mid;
                gfx.drawCenteredString(font, sub.substring(0, sp).trim(),  cx, subY,                   0xFF8888AA);
                gfx.drawCenteredString(font, sub.substring(sp).trim(),     cx, subY + font.lineHeight,  0xFF8888AA);
            }

            // Colour swatch strip below subtitle
            int swatchY = cardTopY + CARD_H - BTN_H - 14;
            gfx.fill(bx + 10, swatchY, bx + CARD_W - 10, swatchY + 3,
                    sel ? col : (col & 0x00FFFFFF | 0x66000000));

            // Selection indicator
            if (sel) {
                gfx.drawCenteredString(font, "\u25bc", cx,
                        cardTopY + CARD_H - BTN_H - 5, col);
            }
        }

        super.render(gfx, mouseX, mouseY, partialTick); // buttons
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int n = DIFFS.length;
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
        Minecraft.getInstance().setScreen(
                new CharacterSelectScreen(DIFFS[selectedIndex], stageId));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 263 && selectedIndex > 0)                   { selectedIndex--; rebuildButtons(); return true; }
        if (keyCode == 262 && selectedIndex < DIFFS.length - 1)    { selectedIndex++; rebuildButtons(); return true; }
        if (keyCode == 257 || keyCode == 335)                      { confirm(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
