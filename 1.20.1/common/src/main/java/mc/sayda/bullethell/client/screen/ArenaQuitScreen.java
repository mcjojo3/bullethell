package mc.sayda.bullethell.client.screen;

import mc.sayda.bullethell.client.BHSfx;
import mc.sayda.bullethell.network.BHPackets;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class ArenaQuitScreen extends Screen {

    private static final String[] OPTIONS = {"Resume", "Quit Arena"};

    private final Screen parent;
    private int selectedIndex = 0; // 0=Resume, 1=Quit
    private boolean pauseClaimed = true;

    public ArenaQuitScreen(Screen parent) {
        super(Component.empty());
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return true; // Pauses singleplayer server correctly
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        // Semi-transparent dark overlay to emphasize the pause
        gfx.fill(0, 0, width, height, 0x88000000);

        String prompt = "Arena Paused";
        int promptW = font.width(prompt);
        int cx = width / 2;
        int cy = height / 2 - 40;

        gfx.drawString(font, prompt, cx - promptW / 2, cy, 0xFFFFFFFF, true);

        for (int i = 0; i < OPTIONS.length; i++) {
            String opt = OPTIONS[i];
            int optW = font.width(opt);
            int optX = cx - optW / 2;
            int optY = cy + 30 + i * 20;

            boolean sel = (i == selectedIndex);
            gfx.drawString(font, opt, optX, optY, sel ? 0xFFFFFF00 : 0xFF888888, true);

            if (sel) {
                gfx.drawString(font, ">", optX - 12, optY, 0xFFFFFF00, true);
                gfx.drawString(font, "<", optX + optW + 6, optY, 0xFFFFFF00, true);
            }
        }
    }

    @Override
    public void renderBackground(GuiGraphics pGuiGraphics) {
        // Don't render default dirt background
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_UP) {
            selectedIndex = (selectedIndex + OPTIONS.length - 1) % OPTIONS.length;
            BHSfx.playSelect();
            return true;
        } else if (key == GLFW.GLFW_KEY_DOWN) {
            selectedIndex = (selectedIndex + 1) % OPTIONS.length;
            BHSfx.playSelect();
            return true;
        } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_Z) {
            confirm();
            return true;
        } else if (key == GLFW.GLFW_KEY_ESCAPE) {
            BHSfx.playBack();
            cancel();
            return true;
        }
        return true;
    }

    private void confirm() {
        if (selectedIndex == 0) {
            BHSfx.playBack();
            cancel();
        } else {
            BHSfx.playSelect();
            releasePause();
            BHPackets.sendQuitArena();
            Minecraft.getInstance().setScreen(null); // Return to game
        }
    }

    private void cancel() {
        releasePause();
        Minecraft.getInstance().setScreen(parent); // Return to ArenaPlayScreen
    }

    private void releasePause() {
        if (!pauseClaimed)
            return;
        pauseClaimed = false;
        BHPackets.sendPauseState(false);
    }

    @Override
    public void removed() {
        // Leaving this screen by other means (e.g. arena ended) should not lock pause.
        if (pauseClaimed && !(Minecraft.getInstance().screen instanceof ArenaQuitScreen)) {
            releasePause();
        }
        super.removed();
    }
}
