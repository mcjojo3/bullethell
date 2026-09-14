package mc.sayda.bullethell.event;

import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import mc.sayda.bullethell.client.ClientArenaSim;
import mc.sayda.bullethell.arena.BulletPool;
import mc.sayda.bullethell.client.BHKeyMappings;
import mc.sayda.bullethell.client.BHMusicManager;
import mc.sayda.bullethell.client.BHScaleManager;
import mc.sayda.bullethell.client.ClientArenaState;
import mc.sayda.bullethell.client.ScreenFXQueue;
import mc.sayda.bullethell.client.SplashState;
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
    /** Previous tick's spell-declaration state, for rising-edge detection. */
    private static boolean prevDeclaringSpell = false;

    public static void register() {
        ClientTickEvent.CLIENT_POST.register(mc -> {
            ScreenFXQueue.INSTANCE.tick();
            SplashState.INSTANCE.tick();
            ClientArenaState state = ClientArenaState.INSTANCE;
            BHMusicManager.INSTANCE.tick(state);

            // Boss cut-in fires on the rising edge of a spell-card declaration. Driven
            // client-side because everything it needs is already synced.
            boolean declaringSpell = state.declaring && !state.spellName.isEmpty();
            if (declaringSpell && !prevDeclaringSpell && !state.bossId.isEmpty()) {
                SplashState.INSTANCE.playFromRight(state.bossId);
            }
            prevDeclaringSpell = declaringSpell;

            if (!state.active) {
                // If the match isn't active, check if we're in a pre-game menu
                if (BHScaleManager.isOverridden()) {
                    if (!isBulletHellUI(mc.screen)) {
                        BHScaleManager.restoreOriginalScale();
                    }
                }
                prevXDown = false;
                prevBombDown = false;
                return;
            }

            state.arenaAnimTick++;

            boolean arenaScreenPaused = mc.screen != null && !(mc.screen instanceof ArenaPlayScreen);

            // Always snapshot prev-positions so lerp stays correct after returning from
            // any overlay (quit screen, chat, inventory). Without this, prevX/prevY
            // stale out during pause and the first post-pause frame lerps from
            // an old position, causing a visible bullet-warp flash.
            boolean simming = ClientArenaSim.INSTANCE.isActive();

            state.bullets.savePrevPositions();
            for (mc.sayda.bullethell.arena.BulletPool pool : state.allPlayerBullets.values()) {
                pool.savePrevPositions();
            }

            if (!arenaScreenPaused) {
                state.items.savePrevPositions();
                state.lasers.savePrevPositions();
                // clientTick is dead reckoning between server packets. A local sim moves
                // the real bullets itself, so running both would advance them twice.
                if (!simming) {
                    state.items.clientTick(state.player.x, state.player.y, false);
                    {
                        state.bullets.clientTick();
                        state.lasers.clientTick();
                        for (mc.sayda.bullethell.arena.BulletPool pool : state.allPlayerBullets.values()) {
                            pool.clientTick();
                        }
                    }
                }
            }

            if (state.declaring)
                state.declarationFrame++;
            else
                state.declarationFrame = 0;

            if (!state.dialogSpeaker.isEmpty())
                state.dialogSlideInTick++;
            else
                state.dialogSlideInTick = 0;

            if (mc.screen != null && !(mc.screen instanceof ArenaPlayScreen)) {
                ClientArenaSim.INSTANCE.tick(0f, 0f, false, false, false);
                return;
            }

            // Spectating players see the arena but cannot send input
            if (state.spectating) {
                prevXDown = false;
                prevBombDown = false;
                ClientArenaSim.INSTANCE.tick(0f, 0f, false, false, false);
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
            boolean zDown = isDown(BHKeyMappings.SHOOT);
            boolean xDown = isDown(BHKeyMappings.SKILL);

            boolean shooting = zDown;
            // With charging gone there is nothing for X to cast, so it is simply a
            // second bomb key on every layout.
            boolean bombPressed = isDown(BHKeyMappings.BOMB) || xDown;
            boolean bombJustPressed = bombPressed && !prevBombDown;
            prevBombDown = bombPressed;

            if (bombJustPressed) {
                // Locally first: invulnerability and the cut-in have to answer the press,
                // not the round trip.
                ClientArenaSim.INSTANCE.bomb();
                BHPackets.sendBomb();
            }

            prevXDown = xDown;

            state.inputDx = dx;
            state.inputDy = dy;
            state.inputFocused = focused;
            state.updateAnimation(dx);
            // Co-op sprites lean from their own movement, not the local player's.
            state.updateCoopAnimations();

            // Advance client-predicted position one tick ahead of server authority
            if (state.active && !state.spectating && !simming) {
                float predSpeed = focused ? state.player.speedFocused : state.player.speedNormal;
                float ndx = dx, ndy = dy;
                if (ndx != 0 && ndy != 0) predSpeed *= 0.7071f;
                state.predX = Math.max(8f, Math.min(BulletPool.ARENA_W - 8f, state.predX + ndx * predSpeed));
                state.predY = Math.max(8f, Math.min(BulletPool.ARENA_H - 8f, state.predY + ndy * predSpeed));
            }

            if (state.bossMaxHp > 0)
                state.bossAnimCounter++;

            if (simming) {
                // The sim applies input with no round trip and reports the result back,
                // so the per-tick input packet is redundant.
                ClientArenaSim.INSTANCE.tick(dx, dy, focused, shooting, true);
            } else {
                BHPackets.sendPlayerPos(dx, dy, focused, shooting);
            }
        });

        // The server also pushes on PLAYER_JOIN, but on a dedicated server that can fire
        // before this client can receive it - so ask again once we are definitely ready.
        ClientPlayerEvent.CLIENT_PLAYER_JOIN.register(player -> {
            BHPackets.sendRequestDataSync();
        });

        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> {
            ClientArenaSim.INSTANCE.stop();
            ClientArenaState.INSTANCE.reset();
            SplashState.INSTANCE.reset();
            prevDeclaringSpell = false;
            BHMusicManager.INSTANCE.stopMusic();
            prevXDown = false;
            prevBombDown = false;
        });

        ClientGuiEvent.RENDER_HUD.register((gfx, partialTick) -> {
            BulletHellRenderer.INSTANCE.render(gfx, partialTick);
            mc.sayda.bullethell.client.TestModeHud.draw(gfx, partialTick);
        });
    }
    
    public static boolean isBulletHellUI(net.minecraft.client.gui.screens.Screen screen) {
        if (screen == null) return false;
        return screen instanceof mc.sayda.bullethell.client.screen.DifficultySelectScreen ||
               screen instanceof mc.sayda.bullethell.client.screen.CharacterSelectScreen ||
               screen instanceof mc.sayda.bullethell.client.screen.ChallengeScreen ||
               screen instanceof mc.sayda.bullethell.client.screen.PlayModeScreen ||
               screen instanceof mc.sayda.bullethell.client.screen.ArenaPlayScreen ||
               screen instanceof mc.sayda.bullethell.client.screen.ArenaQuitScreen ||
               screen instanceof mc.sayda.bullethell.client.screen.ArenaEndScreen ||
               screen instanceof mc.sayda.bullethell.client.screen.InvitePlayerScreen ||
               screen instanceof mc.sayda.bullethell.client.screen.LobbyScreen;
    }
}
