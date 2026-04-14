package mc.sayda.bullethell.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import mc.sayda.bullethell.Bullethell;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class BHKeyMappings {

    public static final String CATEGORY = "key.categories." + Bullethell.MODID;

    public static final KeyMapping MOVE_UP = new KeyMapping(
            "key." + Bullethell.MODID + ".move_up",
            GLFW.GLFW_KEY_UP,
            CATEGORY);

    public static final KeyMapping MOVE_DOWN = new KeyMapping(
            "key." + Bullethell.MODID + ".move_down",
            GLFW.GLFW_KEY_DOWN,
            CATEGORY);

    public static final KeyMapping MOVE_LEFT = new KeyMapping(
            "key." + Bullethell.MODID + ".move_left",
            GLFW.GLFW_KEY_LEFT,
            CATEGORY);

    public static final KeyMapping MOVE_RIGHT = new KeyMapping(
            "key." + Bullethell.MODID + ".move_right",
            GLFW.GLFW_KEY_RIGHT,
            CATEGORY);

    public static final KeyMapping SHOOT = new KeyMapping(
            "key." + Bullethell.MODID + ".shoot",
            GLFW.GLFW_KEY_Z,
            CATEGORY);

    public static final KeyMapping BOMB = new KeyMapping(
            "key." + Bullethell.MODID + ".bomb",
            GLFW.GLFW_KEY_C,
            CATEGORY);

    public static final KeyMapping SKILL = new KeyMapping(
            "key." + Bullethell.MODID + ".skill",
            GLFW.GLFW_KEY_X,
            CATEGORY);

    public static final KeyMapping FOCUS = new KeyMapping(
            "key." + Bullethell.MODID + ".focus",
            GLFW.GLFW_KEY_LEFT_SHIFT,
            CATEGORY);

    public static final KeyMapping SKIP_DIALOG = new KeyMapping(
            "key." + Bullethell.MODID + ".skip_dialog",
            GLFW.GLFW_KEY_LEFT_CONTROL,
            CATEGORY);

    public static final KeyMapping QUIT = new KeyMapping(
            "key." + Bullethell.MODID + ".quit",
            GLFW.GLFW_KEY_Q,
            CATEGORY);

    public static void register() {
        KeyMappingRegistry.register(MOVE_UP);
        KeyMappingRegistry.register(MOVE_DOWN);
        KeyMappingRegistry.register(MOVE_LEFT);
        KeyMappingRegistry.register(MOVE_RIGHT);
        KeyMappingRegistry.register(SHOOT);
        KeyMappingRegistry.register(BOMB);
        KeyMappingRegistry.register(SKILL);
        KeyMappingRegistry.register(FOCUS);
        KeyMappingRegistry.register(SKIP_DIALOG);
        KeyMappingRegistry.register(QUIT);
    }

    public static boolean isArenaKey(int key, int scanCode) {
        return MOVE_UP.matches(key, scanCode) ||
               MOVE_DOWN.matches(key, scanCode) ||
               MOVE_LEFT.matches(key, scanCode) ||
               MOVE_RIGHT.matches(key, scanCode) ||
               SHOOT.matches(key, scanCode) ||
               BOMB.matches(key, scanCode) ||
               SKILL.matches(key, scanCode) ||
               FOCUS.matches(key, scanCode) ||
               SKIP_DIALOG.matches(key, scanCode) ||
               QUIT.matches(key, scanCode);
    }
}
