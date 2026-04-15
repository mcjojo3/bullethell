package mc.sayda.bullethell.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import mc.sayda.bullethell.network.OpenChallengePacket;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Challenge dialog shown when a player right-clicks a {@link mc.sayda.bullethell.entity.BHNpc}.
 *
 * Layout:
 *   ┌────────────────────────────────────┐
 *   │  [portrait]  NPC Name              │
 *   │              Challenge text here   │
 *   │                                    │
 *   │   [Accept Challenge]  [Decline]    │
 *   └────────────────────────────────────┘
 *
 * Accept → opens {@link DifficultySelectScreen} with the NPC's stage ID.
 * Decline → closes this screen.
 */
@Environment(EnvType.CLIENT)
public class ChallengeScreen extends Screen {

    private static final int PANEL_W = 340;
    private static final int PANEL_H = 160;
    private static final int PORTRAIT_SIZE = 64;
    private static final int PADDING = 16;

    private final OpenChallengePacket pkt;

    public ChallengeScreen(OpenChallengePacket pkt) {
        super(Component.literal("Challenge"));
        this.pkt = pkt;
    }

    @Override
    protected void init() {
        super.init();
        mc.sayda.bullethell.client.BHScaleManager.applyIdealScale();

        int panelX = (width  - PANEL_W) / 2;
        int panelY = (height - PANEL_H) / 2;

        int btnW  = 120;
        int btnH  = 20;
        int btnY  = panelY + PANEL_H - btnH - PADDING;
        int totalBtnW = btnW * 2 + 12;
        int btnStartX = panelX + (PANEL_W - totalBtnW) / 2;

        addRenderableWidget(Button.builder(
                Component.literal("Accept Challenge"),
                btn -> {
                    onClose();
                    Minecraft.getInstance().setScreen(new DifficultySelectScreen(pkt.stageId));
                })
                .bounds(btnStartX, btnY, btnW, btnH)
                .build());

        addRenderableWidget(Button.builder(
                Component.literal("Decline"),
                btn -> onClose())
                .bounds(btnStartX + btnW + 12, btnY, btnW, btnH)
                .build());
    }

    @Override
    public void render(GuiGraphics gfx, int mx, int my, float delta) {
        // Dim the world behind the panel
        renderBackground(gfx);

        int panelX = (width  - PANEL_W) / 2;
        int panelY = (height - PANEL_H) / 2;

        // Panel background
        gfx.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, 0xDD000000);
        gfx.renderOutline(panelX, panelY, PANEL_W, PANEL_H, 0xFF555577);

        // NPC portrait — drawn as a sprite from the entity texture
        int portX = panelX + PADDING;
        int portY = panelY + PADDING;
        drawPortrait(gfx, portX, portY);

        // Name (title row)
        int textX = portX + PORTRAIT_SIZE + PADDING;
        int nameY  = portY + 4;
        gfx.drawString(font, pkt.npcName, textX, nameY, 0xFFFFDD44, true);

        // Challenge text — word-wrap to fit the remaining panel width
        int textW = PANEL_W - PORTRAIT_SIZE - PADDING * 3;
        int lineY = nameY + 14;
        for (var line : font.split(Component.literal(pkt.challengeText), textW)) {
            gfx.drawString(font, line, textX, lineY, 0xFFDDDDDD, false);
            lineY += font.lineHeight + 2;
        }

        super.render(gfx, mx, my, delta);
    }

    /**
     * Draws the boss portrait using the shared boss sprite sheet convention:
     *   {@code assets/bullethell/textures/bosses/<base>_boss.png}
     * where {@code <base>} is the NPC id with any trailing {@code _npc} stripped.
     *
     * Sheet layout matches the arena renderer: 256×256, 4×4 grid, 64×64 per frame.
     * Frame 0 (top-left) is used as a static idle portrait.
     */
    private void drawPortrait(GuiGraphics gfx, int x, int y) {
        String base = pkt.npcId.endsWith("_npc")
                ? pkt.npcId.substring(0, pkt.npcId.length() - 4)
                : pkt.npcId;
        ResourceLocation tex = new ResourceLocation("bullethell", "textures/bosses/" + base + "_boss.png");

        // Border + background
        gfx.fill(x - 2, y - 2, x + PORTRAIT_SIZE + 2, y + PORTRAIT_SIZE + 2, 0xFF555577);
        gfx.fill(x, y, x + PORTRAIT_SIZE, y + PORTRAIT_SIZE, 0xFF111133);

        // Frame 0, row 0 of the 256×256 sprite sheet scaled to PORTRAIT_SIZE×PORTRAIT_SIZE
        try {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            gfx.blit(tex, x, y, PORTRAIT_SIZE, PORTRAIT_SIZE, 0f, 0f, 64, 64, 256, 256);
            RenderSystem.disableBlend();
        } catch (Exception ignored) {
            // Texture missing — background placeholder is already drawn above
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
