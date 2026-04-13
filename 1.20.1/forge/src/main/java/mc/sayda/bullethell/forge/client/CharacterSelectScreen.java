package mc.sayda.bullethell.forge.client;

import com.mojang.blaze3d.systems.RenderSystem;
import mc.sayda.bullethell.Bullethell;
import mc.sayda.bullethell.arena.DifficultyConfig;
import mc.sayda.bullethell.boss.CharacterDefinition;
import mc.sayda.bullethell.boss.CharacterLoader;
import mc.sayda.bullethell.forge.network.BHNetwork;
import mc.sayda.bullethell.forge.network.CharacterSelectPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Full-screen character selection.
 *
 * Layout per character column:
 *   ┌──────────────┐
 *   │  Portrait    │  96×96 PNG (no button overlay — click detection in mouseClicked)
 *   │  Name        │
 *   │  Description │
 *   │  Stats       │
 *   │  [  START  ] │  ← single button at bottom of column
 *   └──────────────┘
 *
 * Navigation: ← / → keys or click a portrait to select; Enter / START to confirm.
 */
public class CharacterSelectScreen extends Screen {

    static final int PORTRAIT_SIZE = 96;
    static final int INFO_H        = 38; // name + desc + stats rows
    static final int BTN_H         = 20;
    static final int BTN_PAD       = 6;
    static final int CARD_W        = PORTRAIT_SIZE + 16;
    static final int CARD_H        = PORTRAIT_SIZE + INFO_H + BTN_H + BTN_PAD * 3;
    static final int CARD_GAP      = 20;

    private final DifficultyConfig difficulty;
    private final String stageId;
    private final List<CharacterDefinition> characters;

    private int selectedIndex = 0;

    // Computed in init/render, used for mouse-hit testing
    private int cardStartX;
    private int cardTopY;

    public CharacterSelectScreen(DifficultyConfig difficulty, String stageId) {
        super(Component.literal("Select Character"));
        this.difficulty = difficulty;
        this.stageId    = stageId;
        this.characters = CharacterLoader.loadAll();
    }

    @Override
    protected void init() {
        super.init();
        rebuildButtons();
    }

    /** Recompute layout geometry and (re)add the START button(s). */
    private void rebuildButtons() {
        clearWidgets();
        int n = characters.size();
        int totalW = n * CARD_W + (n - 1) * CARD_GAP;
        cardStartX = (width  - totalW) / 2;
        cardTopY   = height / 2 - CARD_H / 2;

        for (int i = 0; i < n; i++) {
            final int idx = i;
            int bx   = cardStartX + i * (CARD_W + CARD_GAP);
            int btnX = bx + (CARD_W - 80) / 2;
            int btnY = cardTopY + PORTRAIT_SIZE + INFO_H + BTN_PAD * 2;

            addRenderableWidget(Button.builder(
                    Component.literal(i == selectedIndex ? "START" : "SELECT"),
                    btn -> { selectedIndex = idx; confirm(); })
                    .pos(btnX, btnY)
                    .size(80, BTN_H)
                    .build());
        }
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        gfx.fill(0, 0, width, height, 0xFF0A0A14);

        // Title
        gfx.drawCenteredString(font, "SELECT CHARACTER", width / 2, 20, 0xFFFFE600);

        // Difficulty indicator
        String diffLabel = difficulty.name();
        int diffCol = switch (difficulty) {
            case EASY    -> 0xFF88FF88;
            case NORMAL  -> 0xFF00FFE0;
            case HARD    -> 0xFFFFAA00;
            case LUNATIC -> 0xFFFF3344;
        };
        gfx.drawCenteredString(font, "Difficulty: " + diffLabel, width / 2, 33, diffCol);
        gfx.drawCenteredString(font, "\u2190 / \u2192  browse     Enter  confirm",
                width / 2, 44, 0xFF445566);

        int n = characters.size();
        for (int i = 0; i < n; i++) {
            CharacterDefinition ch = characters.get(i);
            int bx       = cardStartX + i * (CARD_W + CARD_GAP);
            boolean sel  = (i == selectedIndex);

            // Card background + border
            gfx.fill(bx, cardTopY, bx + CARD_W, cardTopY + CARD_H,
                    sel ? 0xFF1C1C36 : 0xFF0D0D20);
            int brd = sel ? 0xFFFFE600 : 0xFF334466;
            gfx.hLine(bx, bx + CARD_W - 1, cardTopY,              brd);
            gfx.hLine(bx, bx + CARD_W - 1, cardTopY + CARD_H - 1, brd);
            gfx.vLine(bx,          cardTopY, cardTopY + CARD_H,    brd);
            gfx.vLine(bx + CARD_W - 1, cardTopY, cardTopY + CARD_H, brd);

            // Portrait PNG — drawn directly, no button overlay
            int px = bx + (CARD_W - PORTRAIT_SIZE) / 2;
            int py = cardTopY + BTN_PAD;
            renderPortrait(gfx, ch, px, py, PORTRAIT_SIZE);

            // Thin divider
            gfx.hLine(bx + 4, bx + CARD_W - 5,
                    cardTopY + PORTRAIT_SIZE + BTN_PAD + 2, 0x33FFFFFF);

            // Character info rows
            int infoY = cardTopY + PORTRAIT_SIZE + BTN_PAD + 5;
            int cx    = bx + CARD_W / 2;
            gfx.drawCenteredString(font, ch.name,        cx, infoY,                   sel ? 0xFFFFDD00 : 0xFFCCCCCC);
            gfx.drawCenteredString(font, ch.description, cx, infoY + font.lineHeight + 2, 0xFF8888AA);
            String stats = "\u2665" + ch.startingLives + "  \u2736" + ch.startingBombs
                         + "  Spd:" + (int) ch.speedNormal;
            gfx.drawCenteredString(font, stats,          cx, infoY + font.lineHeight * 2 + 4, 0xFF7799CC);

            // Selection arrow above button
            if (sel) {
                gfx.drawCenteredString(font, "\u25bc", cx,
                        cardTopY + PORTRAIT_SIZE + INFO_H + BTN_PAD - 1, 0xFFFFE600);
            }
        }

        super.render(gfx, mouseX, mouseY, partialTick); // renders buttons
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Let normal widget handling run first (catches button clicks)
        if (super.mouseClicked(mx, my, button)) return true;
        // Clicking on a portrait area changes selection without confirming
        int n = characters.size();
        for (int i = 0; i < n; i++) {
            int bx = cardStartX + i * (CARD_W + CARD_GAP);
            int py = cardTopY + BTN_PAD;
            if (mx >= bx && mx < bx + CARD_W && my >= py && my < py + PORTRAIT_SIZE + INFO_H) {
                if (selectedIndex != i) {
                    selectedIndex = i;
                    rebuildButtons();
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Render idle frame 0 of the character sprite sheet (256×296, each sprite 32×47).
     * Scales to fit within the given {@code size}×{@code size} area while preserving
     * the 32:47 aspect ratio, then centres the result horizontally.
     * Falls back to a tinted placeholder if the texture is missing.
     */
    static void renderPortrait(GuiGraphics gfx, CharacterDefinition ch, int x, int y, int size) {
        ResourceLocation tex = new ResourceLocation(Bullethell.MODID, ch.texture);
        // Scale so height fills `size`; width is narrower (32:47 ratio)
        int dstH  = size;
        int dstW  = size * 32 / 47;
        int offX  = (size - dstW) / 2; // centre in the allocated square
        try {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            gfx.blit(tex, x + offX, y, dstW, dstH, 0f, 0f, 32, 47, 256, 296);
            RenderSystem.disableBlend();
        } catch (Exception ignored) {
            // Placeholder — coloured rect with first initial
            gfx.fill(x, y, x + size, y + size, (ch.tintColor & 0x00FFFFFF) | 0xAA000000);
            String init = ch.name.substring(0, 1);
            gfx.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font,
                    init, x + size / 2, y + size / 2 - 4, 0xFFFFFFFF);
        }
    }

    private void confirm() {
        if (characters.isEmpty()) return;
        BHNetwork.CHANNEL.sendToServer(
                new CharacterSelectPacket(characters.get(selectedIndex).id, difficulty, stageId));
        onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 263 && selectedIndex > 0)                       { selectedIndex--; rebuildButtons(); return true; }
        if (keyCode == 262 && selectedIndex < characters.size() - 1)   { selectedIndex++; rebuildButtons(); return true; }
        if (keyCode == 257 || keyCode == 335)                          { confirm(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
