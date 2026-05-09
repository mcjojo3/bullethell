package mc.sayda.bullethell.client.screen;

import mc.sayda.bullethell.client.BHKeyMappings;
import mc.sayda.bullethell.client.ClientArenaState;
import mc.sayda.bullethell.client.TestModeHud;
import mc.sayda.bullethell.network.BHPackets;
import mc.sayda.bullethell.network.TestSelectPacket;
import mc.sayda.bullethell.render.BulletHellRenderer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import javax.annotation.Nonnull;
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
    public void render(@Nonnull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        // Transparent. Render nothing here, the BulletHellRenderer overlay draws
        // underneath it.
    }

    @Override
    public void renderBackground(@Nonnull GuiGraphics gfx) {
        // Do not render any background tint so the game world remains visible in the
        // margins
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        ClientArenaState state = ClientArenaState.INSTANCE;
        if (state.testMode && TestModeHud.isInLeftPanel(mouseX, mouseY)
                && mouseY >= TestModeHud.TAB_H) {
            int maxOff = Math.max(0, TestModeHud.getPageList(state).size() - 1);
            TestModeHud.setPageScroll(state,
                    Math.max(0, Math.min(maxOff,
                            TestModeHud.getPageScroll(state) - (int) Math.signum(delta))));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ClientArenaState state = ClientArenaState.INSTANCE;
        if (!state.testMode)
            return super.mouseClicked(mouseX, mouseY, button);

        // Tab bar click
        int tabIdx = TestModeHud.tabIndexFromClick(mouseX, mouseY);
        if (tabIdx >= 0) {
            state.testPage = tabIdx;
            return true;
        }

        // List area click
        if (TestModeHud.isInLeftPanel(mouseX, mouseY)) {
            int idx = TestModeHud.leftPanelHitIndex(mouseY, TestModeHud.getPageScroll(state));
            java.util.List<String> ids = TestModeHud.getPageList(state);
            if (idx >= 0 && idx < ids.size()) {
                String selected = ids.get(idx);
                TestModeHud.setPageSelected(state, idx);
                if (state.testPage == TestModeHud.PAGE_CHAR) {
                    // Character selection: update locally and send refresh
                    state.testCurrentCharId = selected;
                    state.testShotTypeIds.clear();
                    state.testCurrentShotTypeIdx = 0;
                    // Send refresh and restart to apply new character immediately
                    BHPackets.sendTestSelect(new TestSelectPacket(
                            TestSelectPacket.TYPE_CHAR_REFRESH, selected, 0,
                            state.testCurrentDifficulty, selected, state.testCurrentShotTypeIdx));
                } else if (state.testPage == TestModeHud.PAGE_BOSS) {
                    BHPackets.sendTestSelect(new TestSelectPacket(
                            TestSelectPacket.TYPE_BOSS, selected, 0,
                            state.testCurrentDifficulty, state.testCurrentCharId, state.testCurrentShotTypeIdx));
                } else if (state.testPage == TestModeHud.PAGE_STAGE) {
                    BHPackets.sendTestSelect(new TestSelectPacket(
                            TestSelectPacket.TYPE_STAGE, selected, 0,
                            state.testCurrentDifficulty, state.testCurrentCharId, state.testCurrentShotTypeIdx));
                } else if (state.testPage == TestModeHud.PAGE_WAVE) {
                    BHPackets.sendTestSelect(new TestSelectPacket(
                            TestSelectPacket.TYPE_WAVE, selected, 0,
                            state.testCurrentDifficulty, state.testCurrentCharId, state.testCurrentShotTypeIdx));
                }
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        // Always allow ESC to open the quit menu as a fail-safe
        if (key == GLFW.GLFW_KEY_ESCAPE || BHKeyMappings.QUIT.matches(key, scanCode)) {
            BHPackets.sendPauseState(true);
            Minecraft.getInstance().setScreen(new ArenaQuitScreen(this));
            return true;
        }

        ClientArenaState state = ClientArenaState.INSTANCE;

        // Test-mode shortcuts
        if (state.testMode) {
            // Tab cycles through pages
            if (key == GLFW.GLFW_KEY_TAB) {
                if (hasShiftDown()) {
                    state.testPage = (state.testPage - 1 + TestModeHud.PAGE_COUNT) % TestModeHud.PAGE_COUNT;
                } else {
                    state.testPage = (state.testPage + 1) % TestModeHud.PAGE_COUNT;
                }
                return true;
            }

            if (key == GLFW.GLFW_KEY_R) {
                // Reload all client-side JSON caches immediately (bullet types, textures)
                BulletHellRenderer.reloadBulletTypes();
                // Reload + restart server-side with the current page's selection
                if (state.testPage == TestModeHud.PAGE_BOSS) {
                    String bossId = state.bossId.isEmpty() ? state.testCurrentBossId : state.bossId;
                    BHPackets.sendTestSelect(new TestSelectPacket(
                            TestSelectPacket.TYPE_BOSS, bossId, state.bossPhase,
                            state.testCurrentDifficulty, state.testCurrentCharId, state.testCurrentShotTypeIdx));
                } else if (state.testPage == TestModeHud.PAGE_STAGE) {
                    BHPackets.sendTestSelect(new TestSelectPacket(
                            TestSelectPacket.TYPE_STAGE, state.testCurrentStageId, 0,
                            state.testCurrentDifficulty, state.testCurrentCharId, state.testCurrentShotTypeIdx));
                } else if (state.testPage == TestModeHud.PAGE_WAVE) {
                    BHPackets.sendTestSelect(new TestSelectPacket(
                            TestSelectPacket.TYPE_WAVE, state.testCurrentWaveId, 0,
                            state.testCurrentDifficulty, state.testCurrentCharId, state.testCurrentShotTypeIdx));
                } else if (state.testPage == TestModeHud.PAGE_CHAR) {
                    String bossId = state.bossId.isEmpty() ? state.testCurrentBossId : state.bossId;
                    BHPackets.sendTestSelect(new TestSelectPacket(
                            TestSelectPacket.TYPE_BOSS, bossId, state.bossPhase,
                            state.testCurrentDifficulty, state.testCurrentCharId, state.testCurrentShotTypeIdx));
                }
                return true;
            }
            if (key == GLFW.GLFW_KEY_PAGE_UP) {
                BHPackets.sendTestSelect(new TestSelectPacket(
                        TestSelectPacket.TYPE_BOSS, state.bossId, state.bossPhase + 1,
                        state.testCurrentDifficulty, state.testCurrentCharId, state.testCurrentShotTypeIdx));
                return true;
            }
            if (key == GLFW.GLFW_KEY_PAGE_DOWN) {
                int prev = Math.max(0, state.bossPhase - 1);
                BHPackets.sendTestSelect(new TestSelectPacket(
                        TestSelectPacket.TYPE_BOSS, state.bossId, prev,
                        state.testCurrentDifficulty, state.testCurrentCharId, state.testCurrentShotTypeIdx));
                return true;
            }
            if (key == GLFW.GLFW_KEY_H) {
                state.testHitboxVisible = !state.testHitboxVisible;
                return true;
            }
            if (key == GLFW.GLFW_KEY_G) {
                BHPackets.sendTestControl(new mc.sayda.bullethell.network.TestControlPacket(
                        mc.sayda.bullethell.network.TestControlPacket.TYPE_TOGGLE_GODMODE, 0));
                return true;
            }
            if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_4) {
                int diff = key - GLFW.GLFW_KEY_1;
                state.testCurrentDifficulty = diff;
                // Restart current page's selection with new difficulty
                if (state.testPage == TestModeHud.PAGE_STAGE) {
                    BHPackets.sendTestSelect(new TestSelectPacket(
                            TestSelectPacket.TYPE_STAGE, state.testCurrentStageId, 0,
                            diff, state.testCurrentCharId, state.testCurrentShotTypeIdx));
                } else if (state.testPage == TestModeHud.PAGE_WAVE) {
                    BHPackets.sendTestSelect(new TestSelectPacket(
                            TestSelectPacket.TYPE_WAVE, state.testCurrentWaveId, 0,
                            diff, state.testCurrentCharId, state.testCurrentShotTypeIdx));
                } else {
                    BHPackets.sendTestSelect(new TestSelectPacket(
                            TestSelectPacket.TYPE_BOSS, state.bossId, state.bossPhase,
                            diff, state.testCurrentCharId, state.testCurrentShotTypeIdx));
                }
                return true;
            }
            // Shot type cycling with 5 (previous) and 6 (next)
            if (key == GLFW.GLFW_KEY_5) {
                if (!state.testShotTypeIds.isEmpty()) {
                    state.testCurrentShotTypeIdx = (state.testCurrentShotTypeIdx - 1 + state.testShotTypeIds.size())
                            % state.testShotTypeIds.size();
                    // Immediate restart with new shot
                    BHPackets.sendTestSelect(new TestSelectPacket(
                            TestSelectPacket.TYPE_BOSS, state.bossId, state.bossPhase,
                            state.testCurrentDifficulty, state.testCurrentCharId, state.testCurrentShotTypeIdx));
                }
                return true;
            }
            if (key == GLFW.GLFW_KEY_6) {
                if (!state.testShotTypeIds.isEmpty()) {
                    state.testCurrentShotTypeIdx = (state.testCurrentShotTypeIdx + 1) % state.testShotTypeIds.size();
                    // Immediate restart with new shot
                    BHPackets.sendTestSelect(new TestSelectPacket(
                            TestSelectPacket.TYPE_BOSS, state.bossId, state.bossPhase,
                            state.testCurrentDifficulty, state.testCurrentCharId, state.testCurrentShotTypeIdx));
                }
                return true;
            }
            if (key == GLFW.GLFW_KEY_7) {
                BHPackets.sendTestControl(new mc.sayda.bullethell.network.TestControlPacket(
                        mc.sayda.bullethell.network.TestControlPacket.TYPE_SET_POWER, Math.max(0, state.power - 1)));
                return true;
            }
            if (key == GLFW.GLFW_KEY_8) {
                BHPackets.sendTestControl(new mc.sayda.bullethell.network.TestControlPacket(
                        mc.sayda.bullethell.network.TestControlPacket.TYPE_SET_POWER, Math.min(128, state.power + 1)));
                return true;
            }
        }
        
        // Dialog handling
        if (!state.dialogSpeaker.isEmpty()) {
            if (BHKeyMappings.SHOOT.matches(key, scanCode)) {
                BHPackets.sendSkipDialog(false);
            } else if (BHKeyMappings.SKIP_DIALOG.matches(key, scanCode)) {
                BHPackets.sendSkipDialog(true);
            }
        }

        // IMPORTANT: Returning false for arena keys allows Minecraft's KeyboardHandler
        // to call KeyMapping.set(key, true), which enables constant polling via
        // .isDown().
        if (BHKeyMappings.isArenaKey(key, scanCode)) {
            return false;
        }

        // Consume all other keys (E, T, etc.) while the arena is active
        return true;
    }

    private int powerHoldTicks = 0;

    @Override
    public void tick() {
        super.tick();
        ClientArenaState state = ClientArenaState.INSTANCE;
        if (state.testMode) {
            long window = Minecraft.getInstance().getWindow().getWindow();
            boolean down7 = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_7) == GLFW.GLFW_PRESS;
            boolean down8 = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_8) == GLFW.GLFW_PRESS;
            if (down7 || down8) {
                powerHoldTicks++;
                if (powerHoldTicks > 10 && (powerHoldTicks % 2 == 0)) {
                    int delta = down8 ? 1 : -1;
                    int next = Math.max(0, Math.min(128, state.power + delta));
                    if (next != state.power) {
                        BHPackets.sendTestControl(new mc.sayda.bullethell.network.TestControlPacket(
                                mc.sayda.bullethell.network.TestControlPacket.TYPE_SET_POWER, next));
                    }
                }
            } else {
                powerHoldTicks = 0;
            }
        }
    }

    @Override
    public boolean keyReleased(int key, int scanCode, int modifiers) {
        if (BHKeyMappings.isArenaKey(key, scanCode)) {
            return false; // Allow KeyMapping.set(key, false) to be called
        }
        return true;
    }
}
