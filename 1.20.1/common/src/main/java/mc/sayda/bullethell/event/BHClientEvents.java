package mc.sayda.bullethell.event;

import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import mc.sayda.bullethell.client.BHKeyMappings;
import mc.sayda.bullethell.client.BHMusicManager;
import mc.sayda.bullethell.client.BHScaleManager;
import mc.sayda.bullethell.client.ClientArenaState;
import mc.sayda.bullethell.client.ScreenFXQueue;
import mc.sayda.bullethell.client.screen.ArenaPlayScreen;
import mc.sayda.bullethell.network.BHPackets;
import mc.sayda.bullethell.render.BulletHellRenderer;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

@Environment(EnvType.CLIENT)
public class BHClientEvents {

    private static boolean isDown(KeyMapping mapping) {
        if (mapping.isDown()) return true;
        InputConstants.Key key = ((mc.sayda.bullethell.mixin.client.KeyMappingAccessor) mapping).getKey();
        if (key.getType() == InputConstants.Type.KEYSYM) {
            return org.lwjgl.glfw.GLFW.glfwGetKey(
                    Minecraft.getInstance().getWindow().getWindow(), key.getValue())
                    == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        }
        return false;
    }

    private static boolean prevXDown = false;
    private static boolean prevBombDown = false;

    public static void register() {
        ClientTickEvent.CLIENT_POST.register(mc -> {
            ScreenFXQueue.INSTANCE.tick();
            ClientArenaState state = ClientArenaState.INSTANCE;
            BHMusicManager.INSTANCE.tick(state);

            if (!state.active) {
                // If the match isn't active, check if we're in a pre-game menu
                if (BHScaleManager.isOverridden()) {
                    if (!isBulletHellUI(mc.screen)) {
                        BHScaleManager.restoreOriginalScale();
                    }
                }
                return;
            }

            boolean timeStopped = state.abilityType == 1;

            if (!timeStopped) {
                state.bullets.clientTick();
                state.enemies.clientTick();
            }
            state.playerBullets.clientTick();
            state.items.clientTick(state.player.x, state.player.y, timeStopped);

            if (state.declaring)
                state.declarationFrame++;
            else
                state.declarationFrame = 0;

            if (!state.dialogSpeaker.isEmpty())
                state.dialogSlideInTick++;
            else
                state.dialogSlideInTick = 0;

            if (mc.screen != null && !(mc.screen instanceof ArenaPlayScreen))
                return;

            // Spectating players see the arena but cannot send input
            if (state.spectating) {
                prevXDown = false;
                prevBombDown = false;
                return;
            }

            float dx = 0f, dy = 0f;

            if (isDown(BHKeyMappings.MOVE_LEFT))
                dx -= 1f;
            if (isDown(BHKeyMappings.MOVE_RIGHT))
                dx += 1f;
            if (isDown(BHKeyMappings.MOVE_UP))
                dy -= 1f;
            if (isDown(BHKeyMappings.MOVE_DOWN))
                dy += 1f;

            boolean focused = isDown(BHKeyMappings.FOCUS);
            boolean shooting = isDown(BHKeyMappings.SHOOT);
            boolean charging = isDown(BHKeyMappings.SKILL);
            boolean bombing = isDown(BHKeyMappings.BOMB);

            boolean xReleased = !charging && prevXDown;
            boolean bombJustPressed = bombing && !prevBombDown;

            prevXDown = charging;
            prevBombDown = bombing;

            if (xReleased) {
                BHPackets.sendSkill();
            }
            if (bombJustPressed) {
                BHPackets.sendBomb();
            }

            state.updateAnimation(dx);

            if (state.bossMaxHp > 0)
                state.bossAnimCounter++;

            BHPackets.sendPlayerPos(dx, dy, focused, shooting, charging);
        });

        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> {
            ClientArenaState.INSTANCE.reset();
            BHMusicManager.INSTANCE.stopMusic();
            prevXDown = false;
            prevBombDown = false;
        });

        ClientGuiEvent.RENDER_HUD.register((gfx, partialTick) -> {
            BulletHellRenderer.INSTANCE.render(gfx, partialTick);
        });
    }
    
    public static boolean isBulletHellUI(net.minecraft.client.gui.screens.Screen screen) {
        if (screen == null) return false;
        return screen instanceof mc.sayda.bullethell.client.screen.LevelSelectScreen ||
               screen instanceof mc.sayda.bullethell.client.screen.DifficultySelectScreen ||
               screen instanceof mc.sayda.bullethell.client.screen.CharacterSelectScreen ||
               screen instanceof mc.sayda.bullethell.client.screen.ArenaPlayScreen ||
               screen instanceof mc.sayda.bullethell.client.screen.ArenaQuitScreen ||
               screen instanceof mc.sayda.bullethell.client.screen.InvitePlayerScreen;
    }
}
