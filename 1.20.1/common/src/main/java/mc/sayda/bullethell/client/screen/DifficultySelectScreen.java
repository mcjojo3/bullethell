package mc.sayda.bullethell.client.screen;

import mc.sayda.bullethell.arena.DifficultyConfig;
import mc.sayda.bullethell.client.BHSfx;
import mc.sayda.bullethell.network.BHPackets;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class DifficultySelectScreen extends Screen {

    private static final int CARD_W   = 110;
    private static final int CARD_H   = 120;
    private static final int CARD_GAP = 18;
    private static final int BTN_H    = 20;

    private static final DifficultyConfig[] DIFFS = DifficultyConfig.values();
    private static final int[] COLORS = {
        0xFF88FF88,  
        0xFF00FFE0,  
        0xFFFFAA00,  
        0xFFFF3344,  
    };
    private static final String[] SUBTITLES = {
        "Slower bullets, more lives",
        "Classic experience",
        "Faster patterns, less mercy",
        "Full speed. Good luck.",
    };

    private final String stageId;
    private final int maxAllowedDifficultyOrdinal;

    private int selectedIndex = 1;

    private int cardStartX;
    private int cardTopY;

    public DifficultySelectScreen(String stageId) {
        this(stageId, DifficultyConfig.LUNATIC.ordinal());
    }

    public DifficultySelectScreen(String stageId, int maxAllowedDifficultyOrdinal) {
        super(Component.literal("Select Difficulty"));
        this.stageId = stageId;
        this.maxAllowedDifficultyOrdinal = maxAllowedDifficultyOrdinal;
        this.selectedIndex = Math.min(1, Math.max(0, maxAllowedDifficultyOrdinal));
    }

    @Override
    protected void init() {
        super.init();
        mc.sayda.bullethell.client.BHScaleManager.applyIdealScale();
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
            boolean allowed = isAllowed(i);

            Button b = Button.builder(
                    Component.literal(i == selectedIndex ? "SELECT" : "PICK"),
                    btn -> {
                        if (!isAllowed(idx))
                            return;
                        selectedIndex = idx;
                        confirm();
                    })
                    .pos(btnX, btnY)
                    .size(80, BTN_H)
                    .build();
            b.active = allowed;
            addRenderableWidget(b);
        }

        addRenderableWidget(Button.builder(
                Component.literal("SHARE LAST RUN"),
                btn -> BHPackets.sendShareLastRun())
                .pos(width / 2 - 60, height - 32)
                .size(120, 20)
                .build());
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
            boolean allowed = isAllowed(i);

            gfx.fill(bx, cardTopY, bx + CARD_W, cardTopY + CARD_H,
                    sel ? 0xFF1A1A38 : 0xFF0A0A1E);
            int brd = sel ? col : 0xFF334466;
            gfx.hLine(bx, bx + CARD_W - 1, cardTopY,              brd);
            gfx.hLine(bx, bx + CARD_W - 1, cardTopY + CARD_H - 1, brd);
            gfx.vLine(bx,              cardTopY, cardTopY + CARD_H, brd);
            gfx.vLine(bx + CARD_W - 1, cardTopY, cardTopY + CARD_H, brd);

            int cx   = bx + CARD_W / 2;
            int nameY = cardTopY + 18;
            gfx.drawCenteredString(font, diff.name(), cx, nameY, allowed ? col : 0xFF666666);

            String sub  = SUBTITLES[i];
            int    subY = nameY + font.lineHeight + 6;
            if (font.width(sub) <= CARD_W - 8) {
                gfx.drawCenteredString(font, sub, cx, subY, allowed ? 0xFF8888AA : 0xFF555566);
            } else {
                int mid = sub.length() / 2;
                int sp  = sub.indexOf(' ', mid);
                if (sp < 0) sp = mid;
                int subCol = allowed ? 0xFF8888AA : 0xFF555566;
                gfx.drawCenteredString(font, sub.substring(0, sp).trim(),  cx, subY,                   subCol);
                gfx.drawCenteredString(font, sub.substring(sp).trim(),     cx, subY + font.lineHeight,  subCol);
            }

            int swatchY = cardTopY + CARD_H - BTN_H - 14;
            int sw = allowed ? col : 0xFF444444;
            gfx.fill(bx + 10, swatchY, bx + CARD_W - 10, swatchY + 3,
                    sel ? sw : (sw & 0x00FFFFFF | 0x66000000));

            if (sel && allowed) {
                gfx.drawCenteredString(font, "\u25bc", cx,
                        cardTopY + CARD_H - BTN_H - 5, col);
            }
            if (!allowed) {
                gfx.drawCenteredString(font, "LOCKED", cx, cardTopY + CARD_H - BTN_H - 26, 0xFFAA4444);
            }
        }

        super.render(gfx, mouseX, mouseY, partialTick); 
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int n = DIFFS.length;
        for (int i = 0; i < n; i++) {
            int bx = cardStartX + i * (CARD_W + CARD_GAP);
                if (mx >= bx && mx < bx + CARD_W
                    && my >= cardTopY && my < cardTopY + CARD_H - BTN_H - 6) {
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
        if (!isAllowed(selectedIndex))
            return;
        BHSfx.playSelect();
        Minecraft.getInstance().setScreen(new CharacterSelectScreen(DIFFS[selectedIndex], stageId, maxAllowedDifficultyOrdinal));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            BHSfx.playBack();
            Minecraft.getInstance().setScreen(new LevelSelectScreen());
            return true;
        }
        if (keyCode == 263 && selectedIndex > 0) {
            selectedIndex--;
            BHSfx.playSelect();
            rebuildButtons();
            return true;
        }
        if (keyCode == 262 && selectedIndex < DIFFS.length - 1) {
            selectedIndex++;
            BHSfx.playSelect();
            rebuildButtons();
            return true;
        }
        if (keyCode == 257 || keyCode == 335) {
            confirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean isAllowed(int difficultyOrdinal) {
        return difficultyOrdinal <= maxAllowedDifficultyOrdinal;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
