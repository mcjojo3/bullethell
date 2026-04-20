package mc.sayda.bullethell.client.screen;

import mc.sayda.bullethell.arena.DifficultyConfig;
import mc.sayda.bullethell.boss.CharacterDefinition;
import mc.sayda.bullethell.boss.CharacterLoader;
import mc.sayda.bullethell.client.BHSfx;
import mc.sayda.bullethell.network.BHPackets;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.UUID;

/**
 * Chooses shot type (A/B, …) after character selection. Skipped when the character
 * only has one shot option.
 */
@Environment(EnvType.CLIENT)
public class ShotTypeSelectScreen extends Screen {

    private static final int CARD_W = 208;
    /** Title + wrapped description fit inside the frame. */
    private static final int CARD_H = 152;
    private static final int CARD_GAP = 20;
    private static final int CARD_PAD = 10;
    private static final int TITLE_COLOR_SEL = 0xFFFFDD00;
    private static final int TITLE_COLOR_DIM = 0xFFAAAAAA;
    private static final int DESC_COLOR = 0xFFBBBBBB;

    public static ShotTypeSelectScreen forSolo(DifficultyConfig difficulty, String stageId,
            int maxAllowedDifficultyOrdinal, String characterId) {
        return new ShotTypeSelectScreen(difficulty, stageId, maxAllowedDifficultyOrdinal, characterId, null, null);
    }

    public static ShotTypeSelectScreen forJoin(UUID hostUuid, String hostName, String characterId) {
        return new ShotTypeSelectScreen(null, "", 0, characterId, hostUuid, hostName);
    }

    private final DifficultyConfig difficulty;
    private final String stageId;
    private final int maxAllowedDifficultyOrdinal;
    private final String characterId;
    private final UUID joinHostUuid;
    private final String joinHostName;
    private final CharacterDefinition charDef;
    private final int optionCount;

    private int selectedShot = 0;

    private int layoutCardStartX;
    private int layoutCardY;

    private ShotTypeSelectScreen(DifficultyConfig difficulty, String stageId, int maxAllowedDifficultyOrdinal,
            String characterId, UUID joinHostUuid, String joinHostName) {
        super(Component.literal("Select Shot Type"));
        this.difficulty = difficulty;
        this.stageId = stageId;
        this.maxAllowedDifficultyOrdinal = maxAllowedDifficultyOrdinal;
        this.characterId = characterId;
        this.joinHostUuid = joinHostUuid;
        this.joinHostName = joinHostName;
        this.charDef = CharacterLoader.load(characterId);
        if (charDef.usesDataDrivenShots())
            this.optionCount = charDef.shotOptions.size();
        else if (charDef.shotTypes != null && !charDef.shotTypes.isEmpty())
            this.optionCount = charDef.shotTypes.size();
        else
            this.optionCount = 1;
    }

    @Override
    protected void init() {
        super.init();
        mc.sayda.bullethell.client.BHScaleManager.applyIdealScale();
        if (charDef.shotTypeOptionCount() < 2) {
            if (joinHostUuid != null) {
                BHPackets.sendJoinMatch(joinHostUuid, characterId, 0);
            } else {
                BHPackets.sendCharSelect(characterId, difficulty, stageId, 0);
            }
            onClose();
            return;
        }

        int n = optionCount;
        int totalW = n * CARD_W + (n - 1) * CARD_GAP;
        layoutCardStartX = (width - totalW) / 2;
        // Lower on screen than previous (height/2 - 100); keep margin above bottom hint
        layoutCardY = Math.min(height / 2 - 8, height - CARD_H - 56);
        clearWidgets();
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        gfx.fill(0, 0, width, height, 0xFF0A0A14);
        gfx.drawCenteredString(font, "SELECT SHOT TYPE", width / 2, 24, 0xFFFFE600);
        gfx.drawCenteredString(font, charDef.name, width / 2, 42, 0xFFCCCCCC);

        if (joinHostUuid != null) {
            gfx.drawCenteredString(font, "Joining " + joinHostName + "'s arena", width / 2, 55, 0xFF88CCFF);
        } else {
            String diffLabel = difficulty.name();
            int diffCol = switch (difficulty) {
                case EASY -> 0xFF88FF88;
                case NORMAL -> 0xFF00FFE0;
                case HARD -> 0xFFFFAA00;
                case LUNATIC -> 0xFFFF3344;
            };
            gfx.drawCenteredString(font, "Difficulty: " + diffLabel, width / 2, 55, diffCol);
        }

        gfx.drawCenteredString(font,
                "\u2190 / \u2192  highlight   Click card to choose   Enter  confirm   Esc  back",
                width / 2, 72, 0xFF445566);

        int innerW = CARD_W - 2 * CARD_PAD;
        int n = optionCount;
        for (int i = 0; i < n; i++) {
            int bx = layoutCardStartX + i * (CARD_W + CARD_GAP);
            boolean sel = (i == selectedShot);
            gfx.fill(bx, layoutCardY, bx + CARD_W, layoutCardY + CARD_H, sel ? 0xFF1C1C36 : 0xFF0D0D20);
            int brd = sel ? 0xFFFFE600 : 0xFF334466;
            gfx.hLine(bx, bx + CARD_W - 1, layoutCardY, brd);
            gfx.hLine(bx, bx + CARD_W - 1, layoutCardY + CARD_H - 1, brd);
            gfx.vLine(bx, layoutCardY, layoutCardY + CARD_H, brd);
            gfx.vLine(bx + CARD_W - 1, layoutCardY, layoutCardY + CARD_H, brd);

            int ly = layoutCardY + CARD_PAD;
            String label = charDef.shotTypeLabel(i);
            for (FormattedCharSequence line : font.split(Component.literal(label), innerW)) {
                int lw = font.width(line);
                gfx.drawString(font, line, bx + CARD_PAD + (innerW - lw) / 2, ly,
                        sel ? TITLE_COLOR_SEL : TITLE_COLOR_DIM, false);
                ly += font.lineHeight;
            }

            ly += 4;
            String desc = charDef.shotTypeDescription(i);
            if (desc.isBlank())
                desc = "No description.";
            List<FormattedCharSequence> descLines = font.split(Component.literal(desc), innerW);
            int maxDescPx = layoutCardY + CARD_H - CARD_PAD - ly;
            int maxLines = Math.max(1, maxDescPx / font.lineHeight);
            int lineIdx = 0;
            for (FormattedCharSequence line : descLines) {
                if (lineIdx >= maxLines)
                    break;
                gfx.drawString(font, line, bx + CARD_PAD, ly, DESC_COLOR, false);
                ly += font.lineHeight;
                lineIdx++;
            }
        }

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button))
            return true;
        int n = optionCount;
        for (int i = 0; i < n; i++) {
            int bx = layoutCardStartX + i * (CARD_W + CARD_GAP);
            if (mx >= bx && mx < bx + CARD_W && my >= layoutCardY && my < layoutCardY + CARD_H) {
                selectedShot = i;
                confirm();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            BHSfx.playBack();
            if (joinHostUuid != null) {
                Minecraft.getInstance().setScreen(new JoinCharacterSelectScreen(joinHostUuid, joinHostName));
            } else {
                Minecraft.getInstance().setScreen(
                        new CharacterSelectScreen(difficulty, stageId, maxAllowedDifficultyOrdinal));
            }
            return true;
        }
        int nOpt = optionCount;
        if (keyCode == 263 && nOpt > 0) {
            selectedShot = (selectedShot - 1 + nOpt) % nOpt;
            BHSfx.playSelect();
            return true;
        }
        if (keyCode == 262 && nOpt > 0) {
            selectedShot = (selectedShot + 1) % nOpt;
            BHSfx.playSelect();
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

    private void confirm() {
        BHSfx.playSelect();
        int ord = Math.max(0, Math.min(selectedShot, Math.max(0, optionCount - 1)));
        if (joinHostUuid != null) {
            BHPackets.sendJoinMatch(joinHostUuid, characterId, ord);
        } else {
            BHPackets.sendCharSelect(characterId, difficulty, stageId, ord);
        }
        onClose();
    }
}
