package mc.sayda.bullethell.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
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

    private final Screen parent;
    private int selectedIndex = 0; // 0=No, 1=Invite, 2=Yes

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

        // Render options
        String[] options = {"Resume", "Invite Player", "Quit Arena"};
        int[] yPos = {cy + 30, cy + 50, cy + 70};

        for (int i = 0; i < options.length; i++) {
            String opt = options[i];
            int optW = font.width(opt);
            int optX = cx - optW / 2;
            int optY = yPos[i];

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
            selectedIndex--;
            if (selectedIndex < 0) selectedIndex = 2;
            return true;
        } else if (key == GLFW.GLFW_KEY_DOWN) {
            selectedIndex++;
            if (selectedIndex > 2) selectedIndex = 0;
            return true;
        } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_Z) {
            confirm();
            return true;
        } else if (key == GLFW.GLFW_KEY_ESCAPE) {
            cancel();
            return true;
        }
        return true;
    }

    private void confirm() {
        if (selectedIndex == 0) {
            cancel();
        } else if (selectedIndex == 1) {
            Minecraft.getInstance().setScreen(new InvitePlayerScreen(this));
        } else if (selectedIndex == 2) {
            BHPackets.sendQuitArena();
            Minecraft.getInstance().setScreen(null); // Return to game
        }
    }

    private void cancel() {
        Minecraft.getInstance().setScreen(parent); // Return to ArenaPlayScreen
    }
}
