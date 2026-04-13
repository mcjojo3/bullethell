package mc.sayda.bullethell.forge.event;

import com.mojang.blaze3d.platform.InputConstants;
import mc.sayda.bullethell.Bullethell;
import mc.sayda.bullethell.forge.client.BHMusicManager;
import mc.sayda.bullethell.forge.client.ClientArenaState;
import mc.sayda.bullethell.forge.client.ScreenFXQueue;
import mc.sayda.bullethell.forge.network.BHNetwork;
import mc.sayda.bullethell.forge.network.BombPacket;
import mc.sayda.bullethell.forge.network.PlayerPos2DPacket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Client-side input handling for the bullet hell arena.
 *
 * Movement: WASD or Arrow keys (arrow keys avoid conflicting with Minecraft movement
 * while WASD suppression via Mixin is not yet implemented).
 * Focus (slow):  Left Shift
 * Bomb:          X key
 *
 * TODO: add a MixinLocalPlayerInput to suppress Minecraft WASD movement while
 *       the arena is active so players don't accidentally walk during a fight.
 */
@Mod.EventBusSubscriber(modid = Bullethell.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class BulletHellClientEvents {

    // ---------------------------------------------------------------- movement (per-tick polling)

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // Always tick FX so effects expire even if the arena just ended
        ScreenFXQueue.INSTANCE.tick();

        ClientArenaState state = ClientArenaState.INSTANCE;

        // Music ticked outside the active guard so it can stop when the arena ends
        BHMusicManager.INSTANCE.tick(state);

        if (!state.active) return;

        // Extrapolate positions each client tick so rendering is smooth between
        // server corrections.
        state.bullets.clientTick();
        state.playerBullets.clientTick();
        state.items.clientTick();
        state.enemies.clientTick();

        // Advance declaration animation frame counter
        if (state.declaring) state.declarationFrame++;
        else                  state.declarationFrame = 0;

        // Advance dialog slide-in tick (used for smooth slide-in animation each render frame)
        if (!state.dialogSpeaker.isEmpty()) state.dialogSlideInTick++;
        else                                state.dialogSlideInTick = 0;

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return; // don't steal input while a GUI is open

        long window = mc.getWindow().getWindow();

        float dx = 0f, dy = 0f;

        // Arrow keys (no Minecraft movement conflict)
        if (isDown(window, GLFW.GLFW_KEY_LEFT)  || isDown(window, GLFW.GLFW_KEY_A)) dx -= 1f;
        if (isDown(window, GLFW.GLFW_KEY_RIGHT) || isDown(window, GLFW.GLFW_KEY_D)) dx += 1f;
        if (isDown(window, GLFW.GLFW_KEY_UP)    || isDown(window, GLFW.GLFW_KEY_W)) dy -= 1f;
        if (isDown(window, GLFW.GLFW_KEY_DOWN)  || isDown(window, GLFW.GLFW_KEY_S)) dy += 1f;

        boolean focused   = isDown(window, GLFW.GLFW_KEY_LEFT_SHIFT)
                         || isDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
        boolean shooting  = isDown(window, GLFW.GLFW_KEY_Z);

        // Drive directional lean animation from horizontal input
        state.updateAnimation(dx);

        // Advance boss animation frame counter while a boss is present
        if (state.bossMaxHp > 0) state.bossAnimCounter++;

        // Always send on every tick so the server gets the "idle" (0,0) signal too.
        // The server applies movement; the client trusts the ArenaStatePacket echo-back.
        BHNetwork.CHANNEL.sendToServer(new PlayerPos2DPacket(dx, dy, focused, shooting));
    }

    // ---------------------------------------------------------------- bomb (single key-press)

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (!ClientArenaState.INSTANCE.active) return;
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        if (event.getKey() == GLFW.GLFW_KEY_X) {
            BHNetwork.CHANNEL.sendToServer(new BombPacket());
        }
    }

    // ---------------------------------------------------------------- logout (cleanup)

    @SubscribeEvent
    public static void onLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        // Force cleanup when leaving a server/world
        ClientArenaState.INSTANCE.reset();
        BHMusicManager.INSTANCE.stopMusic();
    }

    // ---------------------------------------------------------------- helpers

    private static boolean isDown(long window, int key) {
        return InputConstants.isKeyDown(window, key);
    }
}
