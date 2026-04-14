package mc.sayda.bullethell.client.screen;

import mc.sayda.bullethell.client.BHKeyMappings;
import mc.sayda.bullethell.client.ClientArenaState;
import mc.sayda.bullethell.network.BHPackets;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class ArenaPlayScreen extends Screen {

    public ArenaPlayScreen() {
        super(Component.empty());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        // Transparent. Render nothing here, the BulletHellRenderer overlay draws underneath it.
    }

    @Override
    public void renderBackground(GuiGraphics gfx) {
        // Do not render any background tint so the game world remains visible in the margins
    }
    
    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        // Always allow ESC to open the quit menu as a fail-safe
        if (key == GLFW.GLFW_KEY_ESCAPE || BHKeyMappings.QUIT.matches(key, scanCode)) {
            Minecraft.getInstance().setScreen(new ArenaQuitScreen(this));
            return true; 
        }

        ClientArenaState state = ClientArenaState.INSTANCE;

        // Dialog handling (Discrete events)
        if (!state.dialogSpeaker.isEmpty()) {
            if (BHKeyMappings.SHOOT.matches(key, scanCode)) {
                BHPackets.sendSkipDialog(false);
            } else if (BHKeyMappings.SKIP_DIALOG.matches(key, scanCode)) {
                BHPackets.sendSkipDialog(true);
            }
        }

        // IMPORTANT: Returning false for arena keys allows Minecraft's KeyboardHandler 
        // to call KeyMapping.set(key, true), which enables constant polling via .isDown().
        if (BHKeyMappings.isArenaKey(key, scanCode)) {
            return false;
        }

        // Consume all other keys (E, T, etc.) while the arena is active
        return true;
    }

    @Override
    public boolean keyReleased(int key, int scanCode, int modifiers) {
        if (BHKeyMappings.isArenaKey(key, scanCode)) {
            return false; // Allow KeyMapping.set(key, false) to be called
        }
        return true; 
    }
}
