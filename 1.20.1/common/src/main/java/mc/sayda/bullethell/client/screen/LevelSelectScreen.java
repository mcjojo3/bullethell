package mc.sayda.bullethell.client.screen;

import mc.sayda.bullethell.boss.BossLoader;
import mc.sayda.bullethell.boss.StageDefinition;
import mc.sayda.bullethell.boss.StageLoader;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

@Environment(EnvType.CLIENT)
public class LevelSelectScreen extends Screen {

    private static final int CARD_W = 130;
    private static final int CARD_H = 130;
    private static final int CARD_GAP = 22;
    private static final int BTN_H = 20;

    private static final int[] STAGE_COLORS = {
            0xFF00FFE0, 
            0xFFFFE600, 
            0xFFFF88AA, 
            0xFF88AAFF, 
            0xFFFF7700, 
            0xFF88FF88, 
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
        mc.sayda.bullethell.client.BHScaleManager.applyIdealScale();
        rebuildButtons();
    }

    private void rebuildButtons() {
        clearWidgets();
        int n = stages.size();
        int totalW = n * CARD_W + (n - 1) * CARD_GAP;
        cardStartX = (width - totalW) / 2;
        cardTopY = height / 2 - CARD_H / 2;

        for (int i = 0; i < n; i++) {
            final int idx = i;
            int bx = cardStartX + i * (CARD_W + CARD_GAP);
            int btnX = bx + (CARD_W - 80) / 2;
            int btnY = cardTopY + CARD_H - BTN_H - 6;

            addRenderableWidget(Button.builder(
                    Component.literal(i == selectedIndex ? "SELECT" : "PICK"),
                    btn -> {
                        selectedIndex = idx;
                        confirm();
                    })
                    .pos(btnX, btnY)
                    .size(80, BTN_H)
                    .build());
        }

        // Add a central "Invite Player" button at the bottom
        addRenderableWidget(Button.builder(
                Component.literal("INVITE PLAYER"),
                btn -> Minecraft.getInstance().setScreen(new InvitePlayerScreen(this)))
                .pos(width / 2 - 60, height - 35)
                .size(120, 20)
                .build());
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
            int col = stageColor(i);
            int bx = cardStartX + i * (CARD_W + CARD_GAP);
            boolean sel = (i == selectedIndex);

            gfx.fill(bx, cardTopY, bx + CARD_W, cardTopY + CARD_H,
                    sel ? 0xFF1A1A38 : 0xFF0A0A1E);
            int brd = sel ? col : 0xFF334466;
            gfx.hLine(bx, bx + CARD_W - 1, cardTopY, brd);
            gfx.hLine(bx, bx + CARD_W - 1, cardTopY + CARD_H - 1, brd);
            gfx.vLine(bx, cardTopY, cardTopY + CARD_H, brd);
            gfx.vLine(bx + CARD_W - 1, cardTopY, cardTopY + CARD_H, brd);

            int cx = bx + CARD_W / 2;
            int maxTitleW = CARD_W - 12;
            int textY = cardTopY + 8;
            for (String line : wrapStageTitleTwoLines(font, stage.title, maxTitleW)) {
                gfx.drawCenteredString(font, line, cx, textY, col);
                textY += font.lineHeight;
            }
            textY += 6;

            gfx.hLine(bx + 8, bx + CARD_W - 9, textY - 3, sel ? (col & 0x00FFFFFF | 0x66000000) : 0x22FFFFFF);

            String bossName = "???";
            try {
                bossName = BossLoader.load(stage.bossId).name;
            } catch (Exception ignored) {
            }
            gfx.drawCenteredString(font, "Boss: " + bossName, cx, textY, sel ? 0xFFFFDD88 : 0xFFAAAAAA);
            textY += font.lineHeight + 4;

            int waveCount = stage.waves != null ? stage.waves.size() : 0;
            gfx.drawCenteredString(font, "Waves: " + waveCount, cx, textY, 0xFF7799CC);

            int swatchY = cardTopY + CARD_H - BTN_H - 16;
            gfx.fill(bx + 10, swatchY, bx + CARD_W - 10, swatchY + 3,
                    sel ? col : (col & 0x00FFFFFF | 0x55000000));

            if (sel) {
                gfx.drawCenteredString(font, "\u25bc", cx,
                        cardTopY + CARD_H - BTN_H - 7, col);
            }
        }

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button))
            return true;
        int n = stages.size();
        for (int i = 0; i < n; i++) {
            int bx = cardStartX + i * (CARD_W + CARD_GAP);
            if (mx >= bx && mx < bx + CARD_W
                    && my >= cardTopY && my < cardTopY + CARD_H - BTN_H - 6) {
                if (selectedIndex != i) {
                    selectedIndex = i;
                    rebuildButtons();
                }
                return true;
            }
        }
        return false;
    }

    private void confirm() {
        if (stages.isEmpty())
            return;
        Minecraft.getInstance().setScreen(
                new DifficultySelectScreen(stages.get(selectedIndex).id));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 263 && selectedIndex > 0) {
            selectedIndex--;
            rebuildButtons();
            return true;
        }
        if (keyCode == 262 && selectedIndex < stages.size() - 1) {
            selectedIndex++;
            rebuildButtons();
            return true;
        }
        if (keyCode == 257 || keyCode == 335) {
            confirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static int stageColor(int index) {
        return (index >= 0 && index < STAGE_COLORS.length)
                ? STAGE_COLORS[index]
                : 0xFFCCCCCC;
    }

    /**
     * Fits long stage names into the narrow stage card: at most two centered
     * lines, word-wrapped; explicit newline in the title forces a line break.
     */
    private static String[] wrapStageTitleTwoLines(Font font, String title, int maxWidth) {
        if (title == null || title.isEmpty())
            return new String[] { "" };
        int nl = title.indexOf('\n');
        if (nl >= 0) {
            String a = title.substring(0, nl).trim();
            String b = title.substring(nl + 1).trim();
            if (font.width(b) > maxWidth)
                b = truncateToMaxWidth(font, b, maxWidth);
            return new String[] { a, b };
        }
        if (font.width(title) <= maxWidth)
            return new String[] { title };

        String[] words = title.trim().split("\\s+");
        if (words.length == 0)
            return new String[] { title };
        if (words.length == 1)
            return splitLongTokenTwoLines(font, words[0], maxWidth);

        StringBuilder line1 = new StringBuilder(words[0]);
        int i = 1;
        while (i < words.length) {
            String candidate = line1 + " " + words[i];
            if (font.width(candidate) <= maxWidth) {
                line1 = new StringBuilder(candidate);
                i++;
            } else
                break;
        }
        if (i >= words.length) {
            // Single token consumed all words (should not happen); fall back
            return splitLongTokenTwoLines(font, line1.toString(), maxWidth);
        }
        StringBuilder line2 = new StringBuilder(words[i++]);
        while (i < words.length)
            line2.append(' ').append(words[i++]);
        String second = line2.toString();
        if (font.width(second) > maxWidth)
            second = truncateToMaxWidth(font, second, maxWidth);
        return new String[] { line1.toString(), second };
    }

    private static String[] splitLongTokenTwoLines(Font font, String word, int maxWidth) {
        int bestCut = 1;
        for (int cut = 1; cut < word.length(); cut++) {
            if (font.width(word.substring(0, cut)) <= maxWidth
                    && font.width(word.substring(cut)) <= maxWidth)
                bestCut = cut;
        }
        String a = word.substring(0, bestCut);
        String b = word.substring(bestCut);
        if (font.width(b) > maxWidth)
            b = truncateToMaxWidth(font, b, maxWidth);
        return new String[] { a, b };
    }

    private static String truncateToMaxWidth(Font font, String s, int maxWidth) {
        if (font.width(s) <= maxWidth)
            return s;
        String ell = "...";
        int budget = maxWidth - font.width(ell);
        if (budget <= 0)
            return ell;
        String t = s;
        while (t.length() > 0 && font.width(t) > budget)
            t = t.substring(0, t.length() - 1);
        return t + ell;
    }
}
