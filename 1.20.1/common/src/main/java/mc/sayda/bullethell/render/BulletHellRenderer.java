package mc.sayda.bullethell.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import mc.sayda.bullethell.arena.LaserPool;
import mc.sayda.bullethell.Bullethell;
import mc.sayda.bullethell.arena.BulletPool;
import mc.sayda.bullethell.arena.GameEvent;
import mc.sayda.bullethell.arena.ItemPool;
import mc.sayda.bullethell.client.BHMusicManager;
import mc.sayda.bullethell.client.ClientArenaState;
import mc.sayda.bullethell.client.ScreenFXQueue;
import mc.sayda.bullethell.mixin.client.KeyMappingAccessor;
import mc.sayda.bullethell.pattern.BulletType;
import mc.sayda.bullethell.pattern.BulletTypeData;
import mc.sayda.bullethell.pattern.BulletTypeLoader;
import mc.sayda.bullethell.sound.BHSounds;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Full-screen bullet hell arena overlay.
 *
 * Rendering order (painter's algorithm):
 * 1. Gutters + background
 * 2. Grid + PoC line
 * 3. Items
 * 4. Player
 * 5. Enemies
 * 6. Enemy bullets
 * 7. Player bullets
 * 8. Boss (bosses generally render under bullets but we want it below bullets
 * too, we'll keep its order relative)
 * 9. Screen FX tint (hit flash, bomb flash, etc.)
 * 10. HUD (HP bar, spell timer, bottom strip)
 * 11. Arena border
 */
public class BulletHellRenderer {

    public static final BulletHellRenderer INSTANCE = new BulletHellRenderer();

    private static final int GRID_STEP = 80;

    /**
     * Pre-allocated index buffer for counting-sort of enemy bullets by BulletType.
     * Avoids per-frame allocation and groups same-type bullets together so each
     * texture is bound only once per render pass instead of once per bullet.
     */
    private final int[] bulletSortBuf = new int[BulletPool.ENEMY_CAPACITY];
    private int[] bulletTypeCounts = new int[BulletType.values().length];
    private int[] bulletTypeStarts = new int[BulletType.values().length];
    /**
     * Pixel height reserved at the bottom of the screen for the boss indicator
     * strip.
     */
    private static final int INDICATOR_H = 28;

    /**
     * Must match {@link mc.sayda.bullethell.arena.ArenaContext} boss hurtbox radius
     * for debug draw.
     */
    private static final float DEBUG_BOSS_HIT_RADIUS = 24f;

    // ---- Bullet textures (16x16 recommended) ------------------------------------
    // Texture names, scales, source sizes and tint flags are loaded from
    // data/bullethell/bullet_types.json via BulletTypeLoader. Call
    // reloadBulletTypes()
    // (e.g. from the test-mode B key) to pick up edits without restarting.
    private static final ResourceLocation BULLET_FALLBACK_TEXTURE = new ResourceLocation(Bullethell.MODID,
            "textures/bullets/dot.png");
    // Lazy ResourceLocation cache rebuilt whenever BulletTypeLoader is invalidated.
    private static Map<BulletType, ResourceLocation> bulletTexCache = null;

    /**
     * Invalidates the bullet-type loader and texture cache so the next render
     * re-reads JSON.
     */
    public static void reloadBulletTypes() {
        BulletTypeLoader.invalidate();
        bulletTexCache = null;
    }

    private static ResourceLocation bulletTexture(BulletType type) {
        if (bulletTexCache == null) {
            bulletTexCache = new HashMap<>();
            for (BulletType t : BulletType.values()) {
                String name = BulletTypeLoader.get(t).texture;
                bulletTexCache.put(t, name != null
                        ? new ResourceLocation(Bullethell.MODID, "textures/bullets/" + name + ".png")
                        : BULLET_FALLBACK_TEXTURE);
            }
        }
        return bulletTexCache.getOrDefault(type, BULLET_FALLBACK_TEXTURE);
    }

    // ---- Character / player textures -------------------------------------------
    private static final Map<String, ResourceLocation> CHAR_TEX_CACHE = new HashMap<>();
    private static final Map<String, ResourceLocation> BOSS_TEX_CACHE = new HashMap<>();

    private static ResourceLocation charTex(String characterId) {
        String id = characterId == null || characterId.isEmpty() ? "reimu" : characterId;
        return CHAR_TEX_CACHE.computeIfAbsent(id,
                cid -> new ResourceLocation(Bullethell.MODID, "textures/characters/" + cid + ".png"));
    }

    private static ResourceLocation bossTex(String bossId) {
        String id = bossId == null || bossId.isEmpty() ? "missing" : bossId;
        return BOSS_TEX_CACHE.computeIfAbsent(id,
                bid -> new ResourceLocation(Bullethell.MODID, "textures/bosses/" + bid + ".png"));
    }

    // ---- Item textures (16×16 sprites) -----------------------------------------
    // Place the actual PNG files at: assets/bullethell/textures/item/<name>.png
    // The renderer falls back to a tinted rectangle if the texture is missing.
    private static final ResourceLocation[] ITEM_TEXTURES = {
            new ResourceLocation(Bullethell.MODID, "textures/item/power.png"), // TYPE_POWER
            new ResourceLocation(Bullethell.MODID, "textures/item/point.png"), // TYPE_POINT
            new ResourceLocation(Bullethell.MODID, "textures/item/full_power.png"), // TYPE_FULL_POWER
            new ResourceLocation(Bullethell.MODID, "textures/item/one_up.png"), // TYPE_ONE_UP
            new ResourceLocation(Bullethell.MODID, "textures/item/bomb.png"), // TYPE_BOMB
            new ResourceLocation(Bullethell.MODID, "textures/item/power_large.png"), // TYPE_POWER_LARGE
            new ResourceLocation(Bullethell.MODID, "textures/item/point.png"),       // TYPE_LIFE_PIECE (placeholder)
            new ResourceLocation(Bullethell.MODID, "textures/item/bomb.png"),        // TYPE_BOMB_PIECE (placeholder)
            new ResourceLocation(Bullethell.MODID, "textures/item/point_green.png"), // TYPE_POINT_GREEN
    };

    public void render(GuiGraphics gfx, float partialTick) {

        int screenW = gfx.guiWidth();
        int screenH = gfx.guiHeight();

        ClientArenaState state = ClientArenaState.INSTANCE;
        if (!state.active)
            return;

        // Ensure bullet types are loaded before we start accessing registry/sorting
        // buffers
        mc.sayda.bullethell.pattern.BulletType.ensureLoaded();

        // ---- Compute display rect (3:4, fills available height above indicator strip)
        // ----
        // INDICATOR_H pixels are reserved at the very bottom for the boss tracker.
        int dispH = screenH - INDICATOR_H;
        int dispW = (int) (dispH * BulletPool.ARENA_W / BulletPool.ARENA_H);
        if (dispW > screenW) {
            dispW = screenW;
            dispH = (int) (screenW * BulletPool.ARENA_H / BulletPool.ARENA_W);
        }
        final int ox = (screenW - dispW) / 2;
        final int oy = (screenH - INDICATOR_H - dispH) / 2; // vertically centered in available space
        final float sx = (float) dispW / BulletPool.ARENA_W;
        final float sy = (float) dispH / BulletPool.ARENA_H;

        // ---- 1. Arena background ----
        gfx.fill(ox, oy, ox + dispW, oy + dispH, 0xFF000015);

        // ---- 2. Grid ----
        for (int g = 1; g < (int) (BulletPool.ARENA_W / GRID_STEP) + 1; g++)
            gfx.vLine(ox + (int) (g * GRID_STEP * sx), oy, oy + dispH, 0x0800FFE0);
        for (int g = 1; g < (int) (BulletPool.ARENA_H / GRID_STEP) + 1; g++)
            gfx.hLine(ox, ox + dispW, oy + (int) (g * GRID_STEP * sy), 0x0800FFE0);

        // ---- 2. PoC line (Point of Collection - items auto-attract above this line)
        // ----
        if (state.pocAutoCollect) {
            int pocY = oy + (int) (BulletPool.ARENA_H * state.pocFraction * sy);
            gfx.hLine(ox, ox + dispW, pocY, 0x3300FFE0);
            Font pocFont = Minecraft.getInstance().font;
            gfx.drawString(pocFont, "PoC", ox + dispW - pocFont.width("PoC") - 2, pocY - pocFont.lineHeight - 1,
                    0x2200FFE0, false);
        }

        // ---- 3. Items - partial-tick interpolation ----
        for (int i = 0; i < ItemPool.CAPACITY; i++) {
            if (!state.items.isActive(i))
                continue;
            float cx = state.items.getX(i), cy = state.items.getY(i);
            float px = state.items.getPrevX(i), py = state.items.getPrevY(i);
            float ix = px + (cx - px) * partialTick;
            float iy = py + (cy - py) * partialTick;
            boolean attracting = state.items.isAttracting(i);
            if (outOfArena(ix, iy))
                continue;
            int type = state.items.getType(i);
            int six = ox + (int) (ix * sx);
            int siy = oy + (int) (iy * sy);
            int sz = Math.max(4, (int) (ItemPool.sizeOf(type) * (sx + sy) * 0.5f));
            if (attracting) {
                // Pulse a bright white glow around attracted items so it's visually obvious
                int glowR = sz + 3;
                gfx.fill(six - glowR, siy - glowR, six + glowR, siy + glowR,
                        0x55FFFFFF);
            }
            renderItem(gfx, type, six, siy, sz);
        }

        // ---- 4. Player sprite + optional hitbox (focus / vanilla Shift-sneak) ----
        // Sub-tick extrapolation: predX/predY already advanced one full tick ahead by
        // BHClientEvents; add partialTick fraction of the current-tick movement on top.
        float pSpeed = state.inputFocused ? state.player.speedFocused : state.player.speedNormal;
        float eDx = state.inputDx, eDy = state.inputDy;
        if (eDx != 0 && eDy != 0)
            pSpeed *= 0.7071f;
        final float epx = Math.max(8f, Math.min(BulletPool.ARENA_W - 8f,
                state.predX + eDx * pSpeed * partialTick));
        final float epy = Math.max(8f, Math.min(BulletPool.ARENA_H - 8f,
                state.predY + eDy * pSpeed * partialTick));
        boolean showFocusDiamond = isDown(mc.sayda.bullethell.client.BHKeyMappings.FOCUS);
        boolean showHitbox = shouldShowArenaHitboxOverlay();
        if (!state.spectating) {
            int px = ox + (int) (epx * sx);
            int py = oy + (int) (epy * sy);
            int spriteHalf = Math.max(10, (int) (16 * (sx + sy) * 0.5f));
            renderCharacterSprite(gfx, state.characterId, px, py, spriteHalf, state.anim);
            // Focus hitbox dot: small cyan disc centered on the true hitbox when focused
            if (showFocusDiamond && !showHitbox) {
                int r = Math.max(2, Math.round(state.player.hitRadius * (sx + sy) * 0.5f));
                drawFilledCircleWithOutline(gfx, px, py, r, 0x9900FFFF, 0xCC00FFFF);
            }

            // Only show P1/P2... indicator if in a multiplayer session
            if (!state.coopPlayers.isEmpty()) {
                renderPlayerMarker(gfx, px, py, state.playerIndex, true); // Local player
            }
        }

        // ---- 4b. Co-op players (other participants) ----
        for (mc.sayda.bullethell.network.CoopPlayersSyncPacket.Entry cp : state.coopPlayers) {
            if (cp.lives() < 0)
                continue; // eliminated
            int px = ox + (int) (cp.x() * sx);
            int py = oy + (int) (cp.y() * sy);
            int spriteHalf = Math.max(10, (int) (16 * (sx + sy) * 0.5f));
            renderCharacterSprite(gfx, cp.characterId(), px, py, spriteHalf,
                    state.coopAnim(cp.playerIndex()));
            renderPlayerMarker(gfx, px, py, cp.playerIndex(), false);
        }

        // ---- 5. Laser beams (warning + active) ----
        gfx.enableScissor(ox, oy, ox + dispW, oy + dispH);
        renderLasers(gfx, state, ox, oy, sx, sy, partialTick);
        gfx.disableScissor();

        // ---- 6. Enemy bullets - counting-sort by BulletType for texture-batch
        // efficiency ----
        // Grouping same-type bullets together means each texture is bound at most once
        // per frame instead of once per bullet when types are interleaved.
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        {
            int numTypesInRegistry = BulletType.values().length;
            // Ensure sorting arrays are large enough for all registered types AND have a
            // safe minimum to avoid OOB if an unregistered type ID is encountered.
            int requiredSize = Math.max(numTypesInRegistry, 32);
            if (bulletTypeCounts.length < requiredSize) {
                bulletTypeCounts = new int[requiredSize];
                bulletTypeStarts = new int[requiredSize];
            }
            int numTypes = bulletTypeCounts.length;
            java.util.Arrays.fill(bulletTypeCounts, 0);

            // Pass 1: count active bullets per type
            for (int i = 0; i < BulletPool.ENEMY_CAPACITY; i++) {
                if (state.bullets.isActive(i)) {
                    int typeId = state.bullets.getType(i);
                    if (typeId >= 0 && typeId < numTypes) {
                        bulletTypeCounts[typeId]++;
                    }
                }
            }
            // Pass 2: prefix sums → start index per type in sorted buffer
            int total = 0;
            for (int t = 0; t < numTypes; t++) {
                bulletTypeStarts[t] = total;
                total += bulletTypeCounts[t];
            }
            // Pass 3: fill sorted buffer (re-use starts as cursor)
            int[] cursor = bulletTypeStarts; // will be advanced; refilled next frame anyway
            for (int i = 0; i < BulletPool.ENEMY_CAPACITY; i++) {
                if (state.bullets.isActive(i)) {
                    int t = state.bullets.getType(i);
                    bulletSortBuf[cursor[t]++] = i;
                }
            }
            // Render in type order - consecutive same-type bullets share texture/color
            for (int si = 0; si < total; si++) {
                int i = bulletSortBuf[si];
                float cx = state.bullets.getX(i), cy = state.bullets.getY(i);
                if (outOfArena(cx, cy))
                    continue;
                float bvx = state.bullets.getVx(i);
                float bvy = state.bullets.getVy(i);
                float bpx = state.bullets.getPrevX(i), bpy = state.bullets.getPrevY(i);
                float bx = bpx + (cx - bpx) * partialTick;
                float by = bpy + (cy - bpy) * partialTick;
                BulletType type = BulletType.fromId(state.bullets.getType(i));
                int sbx = ox + (int) (bx * sx);
                int sby = oy + (int) (by * sy);
                float vis = state.bullets.getVisScale(i);
                if (type.isShortLaserLineHit()) {
                    float hit = state.bullets.getHitScale(i);
                    renderShortLaserBullet(gfx, type, sbx, sby, vis, hit, bvx, bvy, sx, sy);
                } else {
                    int r = Math.max(1, (int) (type.getRadius() * vis * (sx + sy) * 0.5f));
                    renderBullet(gfx, type, sbx, sby, r, bvx, bvy);
                }
            }
        }
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        // ---- 7. All Player bullets - lerp between prev and current position
        for (mc.sayda.bullethell.arena.BulletPool pool : state.allPlayerBullets.values()) {
            for (int i = 0; i < mc.sayda.bullethell.arena.BulletPool.PLAYER_CAPACITY; i++) {
                if (!pool.isActive(i))
                    continue;
                float cx = pool.getX(i), cy = pool.getY(i);
                float vx = pool.getVx(i);
                float vy = pool.getVy(i);
                float bpx = pool.getPrevX(i), bpy = pool.getPrevY(i);
                float bx = bpx + (cx - bpx) * partialTick;
                float by = bpy + (cy - bpy) * partialTick;
                if (outOfArena(bx, by))
                    continue;
                BulletType type = BulletType.fromId(pool.getType(i));
                int sbx = ox + (int) (bx * sx);
                int sby = oy + (int) (by * sy);
                float vis = pool.getVisScale(i);
                if (type.isShortLaserLineHit()) {
                    float hit = pool.getHitScale(i);
                    renderShortLaserBullet(gfx, type, sbx, sby, vis, hit, vx, vy, sx, sy);
                } else {
                    int r = Math.max(1, (int) (type.getRadius() * vis * (sx + sy) * 0.5f));
                    renderBullet(gfx, type, sbx, sby, r, vx, vy);
                }
            }
        }
        // Must reset after player bullets: blitTintedBullet sets shader color per type
        // (e.g. Cirno ice, Sakuya knives); leaving the last tint affects boss/HUD/FX.
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        // ---- 8. Boss (visible during boss phase and during dialog intro animation)
        // ----
        int phase = state.bossPhase;
        int phaseCol = phaseColour(phase);
        if (state.bossMaxHp > 0 || state.bossIntroVisible) {
            float bxf = state.prevBossX + (state.bossX - state.prevBossX) * partialTick;
            float byf = state.prevBossY + (state.bossY - state.prevBossY) * partialTick;
            int bx = ox + (int) (bxf * sx);
            int by2 = oy + (int) (byf * sy);
            int sz = Math.max(24, (int) (44 * (sx + sy) * 0.5f));
            renderBossAura(gfx, state, bx, by2, sz, partialTick);
            String bossSpriteName = (state.bossTexture != null && !state.bossTexture.isEmpty())
                    ? state.bossTexture : state.bossId;
            renderBossSprite(gfx, bossSpriteName, bx, by2, sz);
        }

        // ---- 8c. Screen FX tints ----
        renderFX(gfx, ox, oy, dispW, dispH);

        // ---- 8c2. Character cut-in (bomb / boss spell card) ----
        renderSplash(gfx, ox, oy, dispW, dispH, partialTick);

        // ---- 8d. Spell card declaration overlay (above FX, below HUD) ----
        if (state.declaring && !state.spellName.isEmpty()) {
            renderDeclaration(gfx, ox, oy, dispW, dispH, state.declarationFrame);
        }

        // ---- 9. HUD overlays ----
        renderHUD(gfx, ox, oy, dispW, dispH, screenW, screenH, sx, sy, phase, phaseCol);

        // ---- 9b. Pre-boss intro dialog ----
        if (!state.dialogSpeaker.isEmpty() || state.dialogReadyCount < state.dialogTotalCount) {
            renderDialog(gfx, state, ox, oy, dispW, dispH);
        }

        // ---- 9c. Now-playing banner ----
        BHMusicManager mgr = BHMusicManager.INSTANCE;
        if (mgr.npTick < BHMusicManager.NP_TOTAL_TICKS) {
            renderNowPlaying(gfx, ox, oy, dispW, dispH, mgr.npTitle, mgr.npArtist, mgr.npTick);
        }

        // ---- 10. Mask - solid fill over every pixel outside the arena box ----
        gfx.fill(0, 0, ox, dispH + oy, 0xFF000000); // left gutter
        gfx.fill(ox + dispW, 0, screenW, dispH + oy, 0xFF000000); // right gutter
        // When arena is letterboxed vertically, fill the top and bottom bands
        if (oy > 0) {
            gfx.fill(ox, 0, ox + dispW, oy, 0xFF000000); // top band
            gfx.fill(ox, oy + dispH, ox + dispW, screenH - INDICATOR_H, 0xFF000000); // bottom band
        }

        // ---- 10b. Indicator strip (drawn over the black bottom area) ----
        gfx.fill(0, dispH, screenW, screenH, 0xFF000000); // dark background for strip

        // ---- 11. Arena border (top layer, drawn over the mask) ----
        int borderCol = ScreenFXQueue.INSTANCE.isActive(GameEvent.PHASE_CHANGE) ? phaseCol : 0x8800FFE0;
        gfx.hLine(ox, ox + dispW, oy, borderCol);
        gfx.hLine(ox, ox + dispW, oy + dispH, borderCol);
        gfx.vLine(ox, oy, oy + dispH, borderCol);
        gfx.vLine(ox + dispW, oy, oy + dispH, borderCol);

        // ---- 12. Boss indicator strip at screen bottom ----
        if (state.bossMaxHp > 0) {
            float bossXInterp = state.prevBossX + (state.bossX - state.prevBossX) * partialTick;
            int bossScrX = ox + (int) (bossXInterp * sx);
            renderBossIndicator(gfx, state, bossScrX, ox, dispH + oy, dispW, screenW, phaseCol);
        }

        // ---- 13. Hardened Hitbox Pass (Absolute Top) ----
        if (showFocusDiamond) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableDepthTest();
            gfx.pose().pushPose();
            gfx.pose().translate(0, 0, 500);

            int lpx = ox + (int) (epx * sx);
            int lpy = oy + (int) (epy * sy);

            // Pulsing logic
            float pulse = (float) Math.sin(System.currentTimeMillis() / 100.0) * 0.5f + 0.5f;
            int coreAlpha = 200 + (int) (pulse * 55);

            // Draw a diamond/crosshair shape that is much harder to miss
            // Outer white diamond
            drawDiamond(gfx, lpx, lpy, 4, 0xFFFFFFFF);
            // Inner red diamond
            drawDiamond(gfx, lpx, lpy, 2, (coreAlpha << 24) | 0xFF0000);

            for (mc.sayda.bullethell.network.CoopPlayersSyncPacket.Entry cp : state.coopPlayers) {
                if (cp.lives() < 0)
                    continue;
                int cpx = ox + (int) (cp.x() * sx);
                int cpy = oy + (int) (cp.y() * sy);
                gfx.fill(cpx - 1, cpy - 1, cpx + 1, cpy + 1, 0xCCFFFFFF);
            }

            gfx.pose().popPose();
            RenderSystem.enableDepthTest();
        }

        // ---- 16. Spectating Overlay ----
        if (state.spectating) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableDepthTest();
            gfx.pose().pushPose();
            gfx.pose().translate(0, 0, 600); // Topmost layer

            // Dim the entire screen heavily
            gfx.fill(0, 0, screenW, screenH, 0xAA000000);

            // Draw floating spectator text
            Font bigFont = Minecraft.getInstance().font;
            gfx.pose().scale(2.0f, 2.0f, 1.0f);
            String title = "SPECTATING";
            gfx.drawCenteredString(bigFont, title, (screenW / 2) / 2, (screenH / 4) / 2, 0xFFFF5555);
            gfx.pose().scale(0.5f, 0.5f, 1.0f);

            String subtitle = "Waiting for partner to finish...";
            gfx.drawCenteredString(bigFont, subtitle, screenW / 2, screenH / 4 + 20, 0xFFAAAAAA);

            gfx.pose().popPose();
            RenderSystem.enableDepthTest();
        }

        // ---- 17. Debug projectile/laser hitboxes (absolute top-most; focus-held) ----
        renderDebugHitboxOverlay(gfx, state, ox, oy, sx, sy, partialTick, showHitbox, epx, epy);
    }

    /**
     * True when hitboxes should be drawn: test-mode H-key toggle only.
     * Normal play never shows the full debug overlay.
     */
    private static boolean shouldShowArenaHitboxOverlay() {
        ClientArenaState s = ClientArenaState.INSTANCE;
        if (s.testMode)
            return s.testHitboxVisible;
        return false;
    }

    private static void drawDiamond(GuiGraphics gfx, int x, int y, int radius, int color) {
        for (int i = -radius; i <= radius; i++) {
            int w = radius - Math.abs(i);
            gfx.fill(x - w, y + i, x + w + 1, y + i + 1, color);
        }
    }

    /**
     * Debug-mode overlay: player, boss, enemy bullets, player bullets, and laser
     * collision shapes
     * (matches server math). Shown while focus or Shift (sneak) is held.
     */
    private static void renderDebugHitboxOverlay(GuiGraphics gfx, ClientArenaState state,
            int ox, int oy, float sx, float sy, float partialTick, boolean focusHeld,
            float epx, float epy) {
        if (!state.active || !focusHeld)
            return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        gfx.pose().pushPose();
        // Keep this above regular arena rendering and HUD overlays while staying in a
        // conservative GUI depth range.
        gfx.pose().translate(0, 0, 900);

        // Player true hitbox (filled disc) - use extrapolated position to match sprite
        if (!state.spectating) {
            int px = ox + (int) (epx * sx);
            int py = oy + (int) (epy * sy);
            int pr = Math.max(1, Math.round(state.player.hitRadius * (sx + sy) * 0.5f));
            drawFilledCircleWithOutline(gfx, px, py, pr, 0xAA00FFFF, 0xFF00FFFF);
        }

        // Boss hurtbox (matches server BOSS_HIT_RADIUS + player bullet check)
        if (state.bossMaxHp > 0 || state.bossIntroVisible) {
            int bx = ox + (int) (state.bossX * sx);
            int byb = oy + (int) (state.bossY * sy);
            int bossR = Math.max(1, Math.round(DEBUG_BOSS_HIT_RADIUS * (sx + sy) * 0.5f));
            drawFilledCircleWithOutline(gfx, bx, byb, bossR, 0xAAFFAA00, 0xFFFFCC44);
        }

        // Enemy bullet hitboxes (server uses type.radius * hitScale)
        for (int i = 0; i < BulletPool.ENEMY_CAPACITY; i++) {
            if (!state.bullets.isActive(i))
                continue;
            float cx = state.bullets.getX(i), cy = state.bullets.getY(i);
            if (outOfArena(cx, cy))
                continue;
            float vx = state.bullets.getVx(i);
            float vy = state.bullets.getVy(i);
            float bpx = state.bullets.getPrevX(i), bpy = state.bullets.getPrevY(i);
            float bx = bpx + (cx - bpx) * partialTick;
            float by = bpy + (cy - bpy) * partialTick;
            BulletType bt = BulletType.fromId(state.bullets.getType(i));
            int sbx = ox + (int) (bx * sx);
            int sby = oy + (int) (by * sy);
            float scale = (sx + sy) * 0.5f;
            if (bt.isShortLaserLineHit()) {
                float halfLen = bt.lineHitCollisionHalfLength(state.bullets.getVisScale(i));
                float thick = bt.lineHitCollisionHalfWidth(state.bullets.getHitScale(i));
                int hw = Math.max(0, Math.round(halfLen * scale));
                int ht = Math.max(0, Math.round(thick * scale));
                float rotDeg = bulletRotationDegrees(bt, vx, vy);
                gfx.pose().pushPose();
                gfx.pose().translate(sbx, sby, 0.0);
                gfx.pose().mulPose(Objects.requireNonNull(Axis.ZP.rotationDegrees(rotDeg)));
                gfx.fill(-hw - 1, -ht - 1, hw + 2, ht + 2, 0xFFFF8080);
                gfx.fill(-hw, -ht, hw + 1, ht + 1, 0xAAFF5050);
                gfx.pose().popPose();
            } else {
                float bulletR = bt.getRadius() * state.bullets.getHitScale(i) * bt.getHitboxMul();
                int br = Math.max(1, Math.round(bulletR * scale));
                if (br <= 1) {
                    gfx.fill(sbx - 1, sby - 1, sbx + 2, sby + 2, 0xFFFF8080);
                    gfx.fill(sbx, sby, sbx + 1, sby + 1, 0xAAFF5050);
                } else {
                    drawFilledCircleWithOutline(gfx, sbx, sby, br, 0xAAFF5050, 0xFFFF8080);
                }
            }
        }

        // Player bullet hitboxes (all co-op pools)
        for (mc.sayda.bullethell.arena.BulletPool pool : state.allPlayerBullets.values()) {
            for (int i = 0; i < mc.sayda.bullethell.arena.BulletPool.PLAYER_CAPACITY; i++) {
                if (!pool.isActive(i))
                    continue;
                float cx = pool.getX(i), cy = pool.getY(i);
                float vx2 = pool.getVx(i), vy2 = pool.getVy(i);
                float bpx = pool.getPrevX(i), bpy = pool.getPrevY(i);
                float bx = bpx + (cx - bpx) * partialTick;
                float by = bpy + (cy - bpy) * partialTick;
                if (outOfArena(bx, by))
                    continue;
                BulletType bt = BulletType.fromId(pool.getType(i));
                float bulletR = bt.getRadius() * pool.getHitScale(i) * bt.getHitboxMul();
                int br = Math.max(1, Math.round(bulletR * (sx + sy) * 0.5f));
                int sbx = ox + (int) (bx * sx);
                int sby = oy + (int) (by * sy);
                drawFilledCircleWithOutline(gfx, sbx, sby, br, 0xAA44FF88, 0xFF66FFAA);
            }
        }

        // Laser firing hitboxes (filled beam cross-section)
        LaserPool pool = state.lasers;
        float diag = (float) Math.sqrt((double) (BulletPool.ARENA_W * BulletPool.ARENA_W)
                + (double) (BulletPool.ARENA_H * BulletPool.ARENA_H));
        int length = (int) (diag * (sx + sy) * 0.5f) + 8;
        for (int i = 0; i < LaserPool.CAPACITY; i++) {
            if (!pool.isFiring(i))
                continue;
            int screenX = ox + (int) (pool.getX(i) * sx);
            int screenY = oy + (int) (pool.getY(i) * sy);
            int hw = Math.max(1, Math.round(pool.getHalfWidth(i) * (sx + sy) * 0.5f));
            float rotDeg = (float) Math.toDegrees(pool.getAngle(i)) - 90f;
            int yStart = pool.isBidir(i) ? -length : 0;

            gfx.pose().pushPose();
            gfx.pose().translate(screenX, screenY, 0.0);
            gfx.pose().mulPose(Objects.requireNonNull(Axis.ZP.rotationDegrees(rotDeg)));
            int yTop = Math.min(yStart, length);
            int yBot = Math.max(yStart, length);
            gfx.fill(-hw - 1, yTop - 1, hw + 2, yBot + 2, 0xFF99FFFF);
            gfx.fill(-hw, yTop, hw + 1, yBot + 1, 0xAA66FFFF);
            gfx.pose().popPose();
        }

        gfx.pose().popPose();
        RenderSystem.enableDepthTest();
    }

    /** Filled circle via horizontal scanlines (axis-aligned hit disc on screen). */
    private static void drawFilledCircle(GuiGraphics gfx, int cx, int cy, int r, int color) {
        if (r <= 0) {
            gfx.fill(cx, cy, cx + 1, cy + 1, color);
            return;
        }
        int rr = r * r;
        for (int dy = -r; dy <= r; dy++) {
            int dy2 = dy * dy;
            if (dy2 > rr)
                continue;
            int halfW = (int) Math.floor(Math.sqrt(rr - dy2));
            gfx.fill(cx - halfW, cy + dy, cx + halfW + 1, cy + dy + 1, color);
        }
    }

    private static void drawFilledCircleWithOutline(GuiGraphics gfx, int cx, int cy, int r, int fillColor,
            int outlineColor) {
        drawFilledCircle(gfx, cx, cy, r + 1, outlineColor);
        drawFilledCircle(gfx, cx, cy, r, fillColor);
    }

    private static boolean isDown(KeyMapping mapping) {
        if (mapping.isDown())
            return true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null)
            return false;
        InputConstants.Key key = ((KeyMappingAccessor) mapping).getKey();
        if (key.getType() == InputConstants.Type.KEYSYM) {
            return InputConstants.isKeyDown(mc.getWindow().getWindow(), key.getValue());
        } else if (key.getType() == InputConstants.Type.MOUSE) {
            return org.lwjgl.glfw.GLFW.glfwGetMouseButton(mc.getWindow().getWindow(),
                    key.getValue()) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        }
        return false;
    }

    // ---------------------------------------------------------------- screen FX

    private static void renderFX(GuiGraphics gfx, int ox, int oy, int dw, int dh) {
        ScreenFXQueue fx = ScreenFXQueue.INSTANCE;

        if (fx.isActive(GameEvent.DEATH)) {
            int alpha = (int) (fx.intensity(GameEvent.DEATH) * 0xAA);
            gfx.fill(ox, oy, ox + dw, oy + dh, (alpha << 24) | 0xFF0000);
        } else if (fx.isActive(GameEvent.HIT)) {
            int alpha = (int) (fx.intensity(GameEvent.HIT) * 0x66);
            gfx.fill(ox, oy, ox + dw, oy + dh, (alpha << 24) | 0xFF2200);
        }

        if (fx.isActive(GameEvent.BOMB_USED)) {
            int alpha = (int) (fx.intensity(GameEvent.BOMB_USED) * 0x99);
            gfx.fill(ox, oy, ox + dw, oy + dh, (alpha << 24) | 0xFFFFFF);
        }

        if (fx.isActive(GameEvent.SPELL_CAPTURED)) {
            int alpha = (int) (fx.intensity(GameEvent.SPELL_CAPTURED) * 0x55);
            gfx.fill(ox, oy, ox + dw, oy + dh, (alpha << 24) | 0x00FFE0);
        }

        // Boss phase 0 entrance: brief white flash
        if (fx.isActive(GameEvent.BOSS_INTRO)) {
            int alpha = (int) (fx.intensity(GameEvent.BOSS_INTRO) * 0xBB);
            gfx.fill(ox, oy, ox + dw, oy + dh, (alpha << 24) | 0xFFFFFF);
        }

        // Deathbomb cancel: bright cyan-white confirmation flash
        if (fx.isActive(GameEvent.DEATH_BOMB)) {
            int alpha = (int) (fx.intensity(GameEvent.DEATH_BOMB) * 0x88);
            gfx.fill(ox, oy, ox + dw, oy + dh, (alpha << 24) | 0x88FFFF);
        }
    }

    // ---------------------------------------------------------------- HUD

    private static void renderHUD(GuiGraphics gfx, int ox, int oy, int dw, int dh,
            int screenW, int screenH,
            float sx, float sy, int phase, int phaseCol) {
        ClientArenaState state = ClientArenaState.INSTANCE;
        Font font = Minecraft.getInstance().font;
        int lh = font.lineHeight; // typically 9 px

        // ---- Held for a paused player ----
        // Everyone else just sees the fight stop dead otherwise, with no way to tell a
        // pause from a hang - so say whose menu it is.
        if (state.globallyPaused) {
            String who = state.pausedBy.isBlank() ? "Someone" : state.pausedBy;
            String line = who + " paused the game";
            int bw = font.width(line) + 16;
            int bx = ox + (dw - bw) / 2;
            int by = oy + dh / 2 - 10;
            gfx.fill(bx, by, bx + bw, by + 20, 0xCC000018);
            gfx.hLine(bx, bx + bw - 1, by, 0xFFFFE600);
            gfx.hLine(bx, bx + bw - 1, by + 19, 0xFFFFE600);
            gfx.vLine(bx, by, by + 20, 0xFFFFE600);
            gfx.vLine(bx + bw - 1, by, by + 20, 0xFFFFE600);
            gfx.drawCenteredString(font, line, ox + dw / 2, by + 6, 0xFFFFE600);
        }

        // ---- Top bar stack (stacked rows, no overlap) ----
        int cursor = oy;

        // Row 0: Boss name (small, dimmed gold) + phase label (right) - only during
        // boss fight
        if (state.bossMaxHp > 0 && !state.bossName.isEmpty()) {
            String scoreStr = state.getCombinedScoreStr();
            gfx.drawString(font, Objects.requireNonNull(state.bossName), ox + 4, cursor, 0x88FFDD88, false);
            gfx.drawString(font, scoreStr, ox + dw - font.width(scoreStr) - 4, cursor, 0xFFFFE600, false);
            cursor += lh + 1;
            // thin dim separator
            gfx.hLine(ox, ox + dw, cursor, 0x22FFFFFF);
            cursor += 2;
        }

        // Row 1: Boss HP bar
        int hpBarH = Math.max(4, (int) (5 * sy));
        if (state.bossMaxHp > 0) {
            int fill = (int) ((float) dw * state.bossHp / state.bossMaxHp);
            int barCol = state.activeSpellCard ? 0xCCFFDD00 : 0xCCFF44FF;
            gfx.fill(ox, cursor, ox + dw, cursor + hpBarH, 0xFF0D0D1A);
            gfx.fill(ox, cursor, ox + fill, cursor + hpBarH, barCol);
            gfx.fill(ox, cursor, ox + fill, cursor + 1, phaseCol);
        }
        cursor += hpBarH + 1;

        // Row 2: Spell timer bar (only during spell cards)
        int timerBarH = Math.max(2, (int) (3 * sy));
        if (state.spellTimerTotal > 0) {
            float frac = (float) state.spellTimerTicks / state.spellTimerTotal;
            int fill = (int) (dw * frac);
            int col = frac > 0.5f ? 0xFF00FFE0 : frac > 0.25f ? 0xFFFFE600 : 0xFFFF4400;
            gfx.fill(ox, cursor, ox + dw, cursor + timerBarH, 0xFF0A0A14);
            gfx.fill(ox, cursor, ox + fill, cursor + timerBarH, col);
        }
        cursor += timerBarH + 1;

        // Row 3: Power bar (thin, tinted by power level)
        int powerBarH = Math.max(2, (int) (2 * sy));
        {
            int powerFill = (int) ((float) dw * state.power / 128f);
            int powerCol = state.power >= 128 ? 0xFFFF88FF
                    : state.power >= 96 ? 0xFFFF44CC
                            : state.power >= 32 ? 0xFFFF4488
                                    : 0xFFAA4477;
            gfx.fill(ox, cursor, ox + dw, cursor + powerBarH, 0xFF0A0A14);
            gfx.fill(ox, cursor, ox + powerFill, cursor + powerBarH, powerCol);
        }
        cursor += powerBarH + 2;

        // Row 4: Phase label (left, only when no boss name row above) + score if no
        // boss
        if (state.bossMaxHp <= 0) {
            String phLabel = state.getPhLabel();
            String scoreStr = state.getCombinedScoreStr();
            gfx.drawString(font, phLabel, ox + 4, cursor, phaseCol, false);
            gfx.drawString(font, scoreStr, ox + dw - font.width(scoreStr) - 4, cursor, 0xFFFFE600, false);
            cursor += lh + 2;
        } else {
            // phase label stays (score already shown in row 0)
            gfx.drawString(font, state.getPhLabel(), ox + 4, cursor, phaseCol, false);
            cursor += lh + 2;
        }

        // Row 5: Spell card name - only shown during active spell card
        if (state.activeSpellCard && !state.spellName.isEmpty()) {
            gfx.drawString(font, Objects.requireNonNull(state.spellName), ox + 4, cursor, 0xFFFFDD00, false);
        }

        // ---- Player Stats Strip (Right side) - suppressed in test mode (test panel
        // covers it) ----
        if (state.testMode)
            return;
        int margin = 8;
        int statPanelW = 80;
        int hudX = ox + dw + margin;

        // If the play area is too wide and pushes stats off-screen, pin to the right
        // edge
        if (hudX + statPanelW > screenW) {
            hudX = screenW - statPanelW - margin;
        }

        int hudY = oy + 40; // Drop down below boss HP bar
        int rowStep = lh + 6;
        boolean coopHud = !state.coopPlayers.isEmpty();
        int statRows = 4 + (coopHud ? 1 : 0) + (state.debugGodMode ? 4 : 0);
        int panelH = rowStep * statRows + 4;

        // Semi-transparent background panel for readability
        gfx.fill(hudX - 4, hudY - 4, hudX + statPanelW, hudY + panelH, 0x88000018);
        gfx.hLine(hudX - 4, hudX + statPanelW, hudY - 4, 0x44FFFFFF); // subtle top border

        String livesStr = getLivesStr(state);
        String bombsStr = getBombsStr(state);
        String pwrStr   = state.getPwrStr();
        String grazeStr = state.getGrazeStr();
        String ptsStr   = state.getPtsStr();

        int pwrColor = state.power >= 128 ? 0xFFFF88FF : state.power >= 64 ? 0xFFFF44CC : 0xFFFF4488;

        gfx.drawString(font, livesStr, hudX, hudY, 0xFF00FFE0, true);
        gfx.drawString(font, bombsStr, hudX, hudY + rowStep, 0xFFFF3FA4, true);
        int grazeRow = 2;
        if (coopHud) {
            gfx.drawString(font, ptsStr, hudX, hudY + rowStep * 2, 0xFFFFE600, true);
            grazeRow = 3;
        }
        int grazeColor = state.grazeChain >= 50 ? 0xFFFFAA00 : state.grazeChain > 0 ? 0xFFFFE644 : 0xFFFFE600;
        gfx.drawString(font, grazeStr, hudX, hudY + rowStep * grazeRow, grazeColor, true);
        gfx.drawString(font, pwrStr, hudX, hudY + rowStep * (grazeRow + 1), pwrColor, true);

        if (state.debugGodMode) {
            int dbgY = hudY + rowStep * (grazeRow + 2);
            int dbgCol = 0xFF66FF66;
            gfx.drawString(font, "DBG GOD", hudX, dbgY, dbgCol, true);
            gfx.drawString(font, "tick " + state.debugArenaTick, hudX, dbgY + rowStep, 0xFFAAFFAA, true);
            gfx.drawString(font, "pat " + state.debugPatternCooldown, hudX, dbgY + rowStep * 2, 0xFFAAFFAA, true);
            gfx.drawString(font, "eBul " + state.debugEnemyBulletCount, hudX, dbgY + rowStep * 3, 0xFFAAFFAA, true);
        }

        // ---- Teammate HUD (Left side) ----
        if (!state.coopPlayers.isEmpty()) {
            int thudX = ox - statPanelW - margin;
            if (thudX < margin)
                thudX = margin;
            int thudY = oy + 40;

            int matePanelH = rowStep * 3 + lh + 8;
            for (int i = 0; i < state.coopPlayers.size(); i++) {
                var cp = state.coopPlayers.get(i);
                int pNum = cp.playerIndex();
                int cpY = thudY + i * (matePanelH + 10);

                gfx.fill(thudX - 4, cpY - 4, thudX + statPanelW, cpY + matePanelH, 0x88000018);
                gfx.hLine(thudX - 4, thudX + statPanelW, cpY - 4, 0x44FFFFFF);

                String pLabel = "P" + pNum + " ("
                        + Objects.requireNonNull(mc.sayda.bullethell.boss.CharacterLoader.load(cp.characterId())).name
                        + ")";
                gfx.drawString(font, pLabel, thudX, cpY, cp.tintColor() | 0xFF000000, true);
                gfx.drawString(font, "\u2665 " + cp.lives(), thudX, cpY + rowStep, 0xFF00FFE0, true);
                gfx.drawString(font, getCoopScoreStr(pNum, cp.score()), thudX, cpY + rowStep * 2, 0xFFFFE600, true);
            }
        }
    }

    // ---------------------------------------------------------------- Pre-boss
    // intro dialog

    /**
     * Touhou-style dialog box that slides in from the top of the arena.
     *
     * Layout:
     * ╔══ SPEAKER NAME ═════════════════════════════════════╗
     * ║ [portrait] "Dialog text, possibly wrapping ║
     * ║ across two lines if needed." ▼ ║
     * ╚═════════════════════════════════════════════════════╝
     *
     * Slide-in: the box starts fully above oy and eases down to oy + 4 over 20
     * ticks.
     * Each new dialog line resets dialogSlideInTick to 0 so it re-enters from
     * above.
     *
     * BOSS speaker → gold border + gold name
     * PLAYER speaker → cyan border + cyan name
     */
    /**
     * Shared dialog-box renderer used by both the in-game pre-boss intro and the
     * post-arena end overlay. Positions the box in arena space.
     *
     * @param ox          arena display rect left edge
     * @param oy          arena display rect top edge
     * @param dw          arena display width
     * @param speakerName name shown in the header row
     * @param portraitId  bossId or characterId for the portrait on the right
     * @param bodyText    dialog body (word-wrapped to 3 lines)
     * @param hint        key-hint line rendered below the box
     * @param slideInTick ticks elapsed in this dialog phase (drives ease-in)
     * @param isBoss      true = gold border + boss portrait; false = cyan +
     *                    character portrait
     */
    private static void renderDialogBox(GuiGraphics gfx, Font font,
            int ox, int oy, int dw,
            String speakerName, String portraitId, String bodyText, String hint,
            int slideInTick, boolean isBoss) {
        int lh = font.lineHeight;
        int portSz = lh * 3 + 6;
        int padX = 6;
        int boxW = (int) (dw * 0.90f);
        int textLines = 3;
        int boxH = lh + 8 + textLines * (lh + 2) + 8;
        int boxX = ox + (dw - boxW) / 2;

        float t = Math.min(1f, slideInTick / 20f);
        float ease = 1f - (1f - t) * (1f - t);
        int slideEnd = oy + 4;
        int boxY = (int) ((oy - boxH - 8) * (1f - ease) + slideEnd * ease);

        int borderCol = isBoss ? 0xFFFFDD44 : 0xFF44FFEE;
        int nameCol = isBoss ? 0xFFFFDD44 : 0xFF44FFEE;

        // Background
        gfx.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xEE000018);
        gfx.fill(boxX + 1, boxY + 1, boxX + boxW - 1, boxY + lh + 6,
                0x33000000 | (borderCol & 0x00FFFFFF));

        // Border
        gfx.hLine(boxX, boxX + boxW - 1, boxY, borderCol);
        gfx.hLine(boxX, boxX + boxW - 1, boxY + boxH - 1, borderCol);
        gfx.vLine(boxX, boxY, boxY + boxH, borderCol);
        gfx.vLine(boxX + boxW - 1, boxY, boxY + boxH, borderCol);
        gfx.hLine(boxX + 1, boxX + boxW - 2, boxY + lh + 5, (0x55 << 24) | (borderCol & 0x00FFFFFF));

        // Speaker name
        gfx.drawString(font, speakerName, boxX + padX, boxY + 3, nameCol, false);

        // Portrait (right side)
        int portX = boxX + boxW - portSz - padX;
        int portY = boxY + lh + 8;
        int portHalf = portSz / 2;
        if (isBoss) {
            drawBossPortrait(gfx, portraitId, portX + portHalf, portY + portHalf, portHalf);
        } else {
            drawCharacterPortrait(gfx, portraitId, portX + portHalf, portY + portHalf, portHalf);
        }

        // Dialog text
        int textX = boxX + padX;
        int textY = boxY + lh + 9;
        int textW = boxW - portSz - padX * 3;
        if (!bodyText.equals(cachedSplitText) || textW != cachedSplitW) {
            cachedSplitText = bodyText;
            cachedSplitW = textW;
            cachedSplitLines = font.split(Component.literal(bodyText), textW);
        }
        List<FormattedCharSequence> lines = cachedSplitLines;
        for (int i = 0; i < Math.min(lines.size(), textLines); i++) {
            gfx.drawString(font, lines.get(i), textX, textY + i * (lh + 2), 0xFFEEEEEE, false);
        }

        // ▼ blink indicator
        if ((slideInTick / 10) % 2 == 0) {
            gfx.drawString(font, "\u25BC", boxX + boxW - font.width("\u25BC") - padX,
                    boxY + boxH - lh - 3, (0x99 << 24) | 0xFFFFFF, false);
        }

        // Key hint below the box
        gfx.drawString(font, hint, boxX + padX, boxY + boxH + 3, (0x55 << 24) | 0xFFFFFF, false);
    }

    private static void renderDialog(GuiGraphics gfx, ClientArenaState state,
            int ox, int oy, int dw, int dh) {
        Font font = Minecraft.getInstance().font;
        boolean waitingForOthers = state.dialogReadyCount < state.dialogTotalCount
                && state.dialogSpeaker.isEmpty();
        boolean isBoss = "BOSS".equalsIgnoreCase(state.dialogSpeaker);

        String speakerName;
        String portraitId;
        if (waitingForOthers) {
            speakerName = "Ready";
            portraitId = state.characterId;
        } else if (isBoss) {
            speakerName = state.bossName.isEmpty()
                    ? Objects.requireNonNullElse(state.dialogSpeaker, "???")
                    : state.bossName;
            portraitId = state.bossId;
        } else {
            mc.sayda.bullethell.boss.CharacterDefinition cd = mc.sayda.bullethell.boss.CharacterLoader
                    .load(state.characterId);
            speakerName = (cd != null && !cd.name.isEmpty()) ? cd.name
                    : Objects.requireNonNullElse(state.dialogSpeaker, "???");
            portraitId = state.characterId;
        }

        String bodyText = waitingForOthers
                ? "Waiting for other players... " + state.dialogReadyCount + "/" + state.dialogTotalCount
                : Objects.requireNonNullElse(state.dialogText, "");
        String hint = waitingForOthers
                ? "Ready: " + state.dialogReadyCount + "/" + state.dialogTotalCount
                : "[Z] Next  [Ctrl] Skip";

        renderDialogBox(gfx, font, ox, oy, dw, speakerName, portraitId, bodyText, hint,
                state.dialogSlideInTick, isBoss);
    }

    // ---------------------------------------------------------------- Spell card
    // declaration overlay

    /**
     * Full-screen declaration animation shown when the boss declares a spell card.
     * Inspired by TH8 (IN): white flash fades to a darkened arena, then the spell
     * card name fades in centred on screen.
     *
     * Frame 0-8: bright white flash (alpha 255→0)
     * Frame 9-35: dark vignette fades in while name alpha rises
     * Frame 36+: name fully visible, vignette holds
     */
    static void renderDeclaration(GuiGraphics gfx, int ox, int oy, int dw, int dh, int frame) {
        if (frame == 0) {
            Minecraft.getInstance().getSoundManager()
                    .play(SimpleSoundInstance.forUI(BHSounds.ATTACK_CHARGE.get(), 1.0f));
        }

        Font font = Minecraft.getInstance().font;
        String name = ClientArenaState.INSTANCE.spellName;
        if (name.isEmpty())
            return;

        // White flash at the moment of declaration
        if (frame < 10) {
            int flashAlpha = (int) ((1f - frame / 10f) * 200);
            gfx.fill(ox, oy, ox + dw, oy + dh, (flashAlpha << 24) | 0xFFFFFF);
        }

        // Dark vignette behind the name
        int vignetteAlpha = Math.min(140, (frame - 5) * 8);
        if (vignetteAlpha > 0) {
            gfx.fill(ox, oy, ox + dw, oy + dh, (vignetteAlpha << 24) | 0x000008);
        }

        // Spell card name fades in at centre
        float nameAlpha = Math.min(1f, Math.max(0f, (frame - 8) / 20f));
        if (nameAlpha > 0) {
            int alpha = (int) (nameAlpha * 255);
            int cx = ox + dw / 2;
            int cy = oy + dh / 2 - font.lineHeight;

            int nameW = font.width(name);
            // Gold border text (offset 1 px in each direction)
            int borderCol = (alpha << 24) | 0x885500;
            gfx.drawString(font, name, cx - nameW / 2 + 1, cy + 1, borderCol, false);
            // Main text in bright gold
            int mainCol = (alpha << 24) | 0xFFDD00;
            gfx.drawString(font, Objects.requireNonNull(name), cx - nameW / 2, cy, mainCol, false);

            // Thin decorative lines flanking the name (like TH8 decoration)
            int lineAlpha = (int) (nameAlpha * 100);
            int lineY = cy + font.lineHeight / 2;
            gfx.hLine(ox + 8, cx - nameW / 2 - 8, lineY, (lineAlpha << 24) | 0xFFDD00);
            gfx.hLine(cx + nameW / 2 + 8, ox + dw - 8, lineY, (lineAlpha << 24) | 0xFFDD00);
        }
    }

    // ---------------------------------------------------------------- now-playing
    // banner

    /**
     * Touhou-style track announcement displayed when a new music track starts.
     *
     * Layout (bottom-right of arena, above HUD strip):
     * ┃ ♪ Track Title
     * ┃ Artist Name
     *
     * Timing at 20 tps:
     * ticks 0-10 : fade in
     * ticks 10-110 : hold
     * ticks 110-140: fade out
     */
    private static void renderNowPlaying(GuiGraphics gfx, int ox, int oy, int dw, int dh,
            String title, String artist, int tick) {
        Font font = Minecraft.getInstance().font;
        int lh = font.lineHeight; // typically 9

        // Alpha envelope
        float alpha;
        if (tick < 10)
            alpha = tick / 10f;
        else if (tick < 110)
            alpha = 1f;
        else
            alpha = (140 - tick) / 30f;
        alpha = Math.max(0f, Math.min(1f, alpha));
        int a = (int) (alpha * 255);
        if (a <= 0)
            return;

        // Measure box dimensions -- cache string concatenations across frames.
        if (!title.equals(cachedNpTitle) || !artist.equals(cachedNpArtist)) {
            cachedNpTitle = title;
            cachedNpArtist = artist;
            cachedNpTitleLine = "\u266a " + title;
            cachedNpArtistLine = "  " + artist;
        }
        String titleLine = cachedNpTitleLine; // ♪
        String artistLine = cachedNpArtistLine;
        boolean hasArtist = !artist.isEmpty();
        int contentW = Math.max(font.width(titleLine),
                hasArtist ? font.width(artistLine) : 0);
        int pad = 5;
        int boxW = contentW + pad * 2 + 3; // +3 for left accent bar
        int boxH = (hasArtist ? lh * 2 + 4 : lh + 2) + pad;

        // Position: bottom-right, just inside the arena bounding box
        int bx = ox + dw - boxW - 4;
        int by = oy + dh - boxH - 36; // Clear charge bar (bar at -12, label at -13, font ~9px tall)

        // Dark translucent background
        int bgA = (int) (alpha * 170);
        gfx.fill(bx, by, bx + boxW, by + boxH, (bgA << 24) | 0x000010);

        // Gold left-edge accent bar
        int barA = (int) (alpha * 220);
        gfx.fill(bx, by, bx + 2, by + boxH, (barA << 24) | 0xFFE600);

        // Track title in gold
        int titleCol = (a << 24) | 0xFFE600;
        gfx.drawString(font, titleLine, bx + pad, by + pad / 2 + 1, titleCol, false);

        // Artist name in soft blue-white, indented
        if (hasArtist) {
            int artistCol = (a << 24) | 0x99CCEE;
            gfx.drawString(font, artistLine, bx + pad, by + pad / 2 + 1 + lh + 2, artistCol, false);
        }
    }

    // ---------------------------------------------------------------- boss sprite
    // rendering

    /**
     * Renders the boss using row 0 (idle) of the boss sheet - four frames across
     * the top.
     * Most bosses use a 256×256 sheet (64×64 cells). Sakuya uses 256×255 with 64×85
     * cells
     * ({@link BossSheetLayout}). Destination size preserves cell aspect ratio.
     */
    /**
     * Public accessor: renders a boss portrait at idle frame 0.
     * Safe to call outside an active arena (e.g. from ArenaEndScreen).
     */
    public static void drawBossPortrait(GuiGraphics gfx, String bossId, int cx, int cy, int halfSz) {
        if (bossId == null || bossId.isEmpty())
            return;
        BossSheetLayout lay = BossSheetLayout.forBoss(bossId);
        float u = lay.uForFrame(0);
        float v = lay.idleRowV();
        ResourceLocation tex = bossTex(bossId);
        int destW = halfSz * 2;
        int destH = lay.destHeightForWidth(destW);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            gfx.blit(Objects.requireNonNull(tex), cx - destW / 2, cy - destH / 2, destW, destH,
                    u, v, lay.cellW, lay.cellH, lay.texW, lay.texH);
        } catch (Exception e) {
            gfx.fill(cx - halfSz, cy - halfSz, cx + halfSz, cy + halfSz, 0xFFFF44FF);
        } finally {
            RenderSystem.disableBlend();
        }
    }

    /**
     * Public accessor: renders a character portrait at idle frame 0.
     * Safe to call outside an active arena (e.g. from ArenaEndScreen).
     */
    public static void drawCharacterPortrait(GuiGraphics gfx, String characterId, int cx, int cy, int halfSz) {
        ResourceLocation tex = charTex(characterId);
        int dstW = halfSz * 2;
        int dstH = (int) (dstW * 47f / 32f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            gfx.blit(Objects.requireNonNull(tex), cx - halfSz, cy - dstH / 2, dstW, dstH, 0f, 0f, 32, 47, 256, 296);
        } catch (Exception e) {
            gfx.fill(cx - halfSz, cy - halfSz, cx + halfSz, cy + halfSz, 0xCCFFFFFF);
        } finally {
            RenderSystem.disableBlend();
        }
    }

    /**
     * Renders the end-of-arena dialog box over the frozen arena.
     * Delegates to the shared {@link #renderDialogBox} core so the style is
     * identical
     * to the in-game pre-boss intro dialog. Called by ArenaEndScreen.
     */
    public static void drawEndDialogBox(GuiGraphics gfx, int screenW, int screenH,
            String speakerName, String portraitId, String dialogText,
            int slideInTick, boolean isBoss) {
        Font font = Minecraft.getInstance().font;
        int dispH = screenH - INDICATOR_H;
        int dispW = (int) (dispH * BulletPool.ARENA_W / BulletPool.ARENA_H);
        if (dispW > screenW) {
            dispW = screenW;
            dispH = (int) (screenW * BulletPool.ARENA_H / BulletPool.ARENA_W);
        }
        int ox = (screenW - dispW) / 2;
        int oy = (screenH - INDICATOR_H - dispH) / 2;
        renderDialogBox(gfx, font, ox, oy, dispW,
                speakerName, portraitId, dialogText, "[Z] Continue  [Ctrl] Skip",
                slideInTick, isBoss);
    }

    private static final java.util.Map<String, ResourceLocation> AURA_TEX_CACHE = new java.util.HashMap<>();

    /**
     * Generic spinning background aura driven by the boss JSON {@code aura} block.
     * Hidden during dialog intro; grows from nothing to full size over
     * {@code growTicks}
     * once the battle begins.
     */
    private static void renderBossAura(GuiGraphics gfx, ClientArenaState state,
            int bx, int by2, int sz, float partialTick) {
        if (state.bossIntroVisible)
            return; // invisible during dialog intro
        if (state.bossId == null || state.bossId.isEmpty())
            return;

        mc.sayda.bullethell.boss.BossDefinition bossDef = mc.sayda.bullethell.boss.BossLoader.load(state.bossId);
        mc.sayda.bullethell.boss.BossAuraConfig cfg = bossDef.aura;
        if (!cfg.isEnabled())
            return;

        // Grow from 0 once bossAnimCounter starts (battle began)
        float tCounter = state.bossAnimCounter + partialTick;
        float growT = cfg.growTicks > 0 ? Math.min(1f, tCounter / (float) cfg.growTicks) : 1f;
        float grow = 1f - (1f - growT) * (1f - growT); // quadratic ease-out

        if (grow <= 0.001f)
            return;

        float breath = 1f + cfg.breathAmplitude * (float) Math.sin(tCounter * cfg.breathFrequency);
        float spinDeg = tCounter * cfg.spinDegsPerTick;

        int half = Math.max(4, (int) (sz * cfg.baseScale * grow * breath));
        int ts = cfg.textureSize;

        ResourceLocation tex = AURA_TEX_CACHE.computeIfAbsent(cfg.texture,
                t -> new ResourceLocation(Bullethell.MODID, t));

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(cfg.tintR, cfg.tintG, cfg.tintB, cfg.tintA);
        gfx.pose().pushPose();
        gfx.pose().translate(bx, by2, 0f);
        gfx.pose().mulPose(Axis.ZP.rotationDegrees(spinDeg));
        gfx.blit(tex, -half, -half, half * 2, half * 2, 0f, 0f, ts, ts, ts, ts);
        gfx.pose().popPose();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    private static void renderBossSprite(GuiGraphics gfx, String bossId,
            int cx, int cy, int halfSz) {
        if (bossId == null || bossId.isEmpty()) {
            gfx.fill(cx - halfSz, cy - halfSz, cx + halfSz, cy + halfSz, 0xFFFF44FF);
            return;
        }
        BossSheetLayout lay = BossSheetLayout.forBoss(bossId);
        int frame = bossFrame(ClientArenaState.INSTANCE);
        float u = lay.uForFrame(frame);
        float v = lay.idleRowV();
        ResourceLocation tex = bossTex(bossId);
        int destW = halfSz * 2;
        int destH = lay.destHeightForWidth(destW);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            gfx.blit(Objects.requireNonNull(tex),
                    cx - destW / 2, cy - destH / 2, destW, destH,
                    u, v, lay.cellW, lay.cellH, lay.texW, lay.texH);
        } catch (Exception e) {
            gfx.fill(cx - halfSz, cy - halfSz, cx + halfSz, cy + halfSz, 0xFFFF44FF);
        } finally {
            RenderSystem.disableBlend();
        }
    }

    /**
     * Top-row idle: four frames, same timing for every boss (Cirno no longer uses
     * side columns).
     */
    private static int bossFrame(ClientArenaState state) {
        return (state.bossAnimCounter / 4) & 3;
    }

    /**
     * Renders the boss indicator strip in the {@link #INDICATOR_H}-pixel band at
     * the bottom
     * of the screen. A small boss portrait slides horizontally with the boss X
     * position
     * along a track spanning the arena width, giving the player a quick positional
     * readout.
     *
     * Layout:
     * │ phase-colour top border line │
     * │ [arena left]───────[BOSS ICON]──────────[arena right] HP │
     */
    /**
     * Renders the boss position indicator in the {@link #INDICATOR_H}-pixel strip
     * at the
     * bottom of the screen.
     *
     * Layout: a compact panel centred on the arena, containing:
     * - A horizontal track line (arena left → right)
     * - The boss mini-sprite sliding along the track to show boss X
     * - A small HP bar under the icon
     * - A phase-coloured border box around the whole panel (no full-screen line)
     */
    private static void renderBossIndicator(GuiGraphics gfx, ClientArenaState state,
            int bossScrX, int ox, int stripY,
            int dispW, int screenW, int phaseCol) {
        Font font = Minecraft.getInstance().font;
        int iconSz = INDICATOR_H / 2 - 2; // half-size of the mini sprite (~10 px)
        int centerY = stripY + INDICATOR_H / 2;

        // Panel bounds - confined to the arena width, 2 px padding on each side
        int panelLeft = ox;
        int panelRight = ox + dispW;
        int panelTop = stripY + 1;
        int panelBot = stripY + INDICATOR_H - 1;

        // Subtle inner fill so the panel stands out from the raw background
        gfx.fill(panelLeft, panelTop, panelRight, panelBot, 0x22000022);

        // Phase-coloured border box around the panel
        gfx.hLine(panelLeft, panelRight, panelTop, phaseCol);
        gfx.hLine(panelLeft, panelRight, panelBot, phaseCol);
        gfx.vLine(panelLeft, panelTop, panelBot, phaseCol);
        gfx.vLine(panelRight, panelTop, panelBot, phaseCol);

        // Horizontal track line
        int trackLeft = panelLeft + iconSz + 6;
        int trackRight = panelRight - iconSz - 6;
        gfx.hLine(trackLeft, trackRight, centerY, 0x33FFFFFF);
        gfx.vLine(trackLeft, centerY - 2, centerY + 2, 0x55FFFFFF);
        gfx.vLine(trackRight, centerY - 2, centerY + 2, 0x55FFFFFF);

        // Clamp icon to track
        int iconX = Math.max(trackLeft + iconSz, Math.min(trackRight - iconSz, bossScrX));

        // Mini boss sprite
        String bossId = state.bossId;
        int iconHalfW = iconSz;
        int iconHalfH = iconSz;
        if (bossId != null && !bossId.isEmpty()) {
            BossSheetLayout lay = BossSheetLayout.forBoss(bossId);
            int frame = bossFrame(state);
            float u = lay.uForFrame(frame);
            float v = lay.idleRowV();
            int destW = iconSz * 2;
            int destH = lay.destHeightForWidth(destW);
            iconHalfW = destW / 2;
            iconHalfH = destH / 2;
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            try {
                gfx.blit(Objects.requireNonNull(bossTex(bossId)),
                        iconX - iconHalfW, centerY - iconHalfH,
                        destW, destH,
                        u, v, lay.cellW, lay.cellH, lay.texW, lay.texH);
            } catch (Exception e) {
                gfx.fill(iconX - 3, centerY - 3, iconX + 3, centerY + 3, phaseCol);
            } finally {
                RenderSystem.disableBlend();
            }
        } else {
            gfx.fill(iconX - 3, centerY - 3, iconX + 3, centerY + 3, phaseCol);
        }

        // Subtle phase-coloured outline only around the moving icon (not the whole
        // panel)
        gfx.hLine(iconX - iconHalfW - 1, iconX + iconHalfW, centerY - iconHalfH - 1, phaseCol & 0xAAFFFFFF);
        gfx.hLine(iconX - iconHalfW - 1, iconX + iconHalfW, centerY + iconHalfH, phaseCol & 0xAAFFFFFF);
        gfx.vLine(iconX - iconHalfW - 1, centerY - iconHalfH - 1, centerY + iconHalfH, phaseCol & 0xAAFFFFFF);
        gfx.vLine(iconX + iconHalfW, centerY - iconHalfH - 1, centerY + iconHalfH, phaseCol & 0xAAFFFFFF);

        // HP bar under the icon (fills only within its icon-width slot)
        if (state.bossMaxHp > 0) {
            float frac = Math.max(0f, (float) state.bossHp / state.bossMaxHp);
            int barW = iconHalfW * 2;
            int barY = centerY + iconHalfH + 1;
            gfx.fill(iconX - iconHalfW, barY, iconX + iconHalfW, barY + 2, 0xFF0A0A18);
            gfx.fill(iconX - iconHalfW, barY, iconX - iconHalfW + (int) (barW * frac), barY + 2, phaseCol);
        }

        // Active spell name - right-aligned inside the panel, small and dim
        if (state.activeSpellCard && !state.spellName.isEmpty()) {
            int maxW = panelRight - iconX - iconHalfW - 8;
            String label;
            if (state.spellName.equals(cachedTruncSpell) && maxW == cachedTruncMaxW) {
                label = cachedTruncResult;
            } else {
                label = state.spellName;
                if (maxW > 0 && font.width(label) > maxW) {
                    int lo = 1, hi = label.length() - 1, best = 0;
                    while (lo <= hi) {
                        int mid = (lo + hi) >>> 1;
                        if (font.width(label.substring(0, mid) + "…") <= maxW) { best = mid; lo = mid + 1; }
                        else hi = mid - 1;
                    }
                    label = label.substring(0, best) + "…";
                }
                cachedTruncSpell = state.spellName;
                cachedTruncMaxW = maxW;
                cachedTruncResult = label;
            }
            gfx.drawString(font, label, panelRight - font.width(label) - 4,
                    centerY - font.lineHeight / 2, 0x88FFDD88, false);
        }
    }

    // ---------------------------------------------------------------- laser
    // rendering

    /**
     * Renders all active lasers. Each laser is drawn as a rotated rectangle using
     * the
     * GuiGraphics PoseStack so the beam can point in any direction.
     *
     * Warning phase: thin semi-transparent line with pulsing alpha - player-safe.
     * Firing phase: wide opaque beam with soft outer glow and bright white core.
     *
     * The scissor region must be set by the caller to clip to the arena bounds.
     */
    private static void renderLasers(GuiGraphics gfx, ClientArenaState state,
            int ox, int oy, float sx, float sy, float partialTick) {
        LaserPool pool = state.lasers;
        // Length long enough to always reach the far edge of the arena from any origin
        float diag = (float) Math.sqrt((double) (BulletPool.ARENA_W * BulletPool.ARENA_W)
                + (double) (BulletPool.ARENA_H * BulletPool.ARENA_H));
        int length = (int) (diag * (sx + sy) * 0.5f) + 8;

        for (int i = 0; i < LaserPool.CAPACITY; i++) {
            if (!pool.isActive(i))
                continue;

            float angle = pool.getAngle(i);
            float hwArena = pool.getHalfWidth(i);
            boolean warn = pool.isWarning(i);
            int typeId = pool.getTypeId(i);
            int baseColor = BulletTypeLoader.get(BulletType.fromId(typeId)).color & 0x00FFFFFF;

            float lx = pool.getPrevX(i) + (pool.getX(i) - pool.getPrevX(i)) * partialTick;
            float ly = pool.getPrevY(i) + (pool.getY(i) - pool.getPrevY(i)) * partialTick;
            int screenX = ox + (int) (lx * sx);
            int screenY = oy + (int) (ly * sy);

            // Scale half-width from arena units to screen pixels
            float hwScreen = Math.max(warn ? 1f : 2f, hwArena * (sx + sy) * 0.5f);
            int hw = (int) hwScreen;

            int color;
            if (warn) {
                // Pulse: flicker the alpha using warnTicksLeft
                int warnLeft = pool.getWarnLeft(i);
                int pulse = (warnLeft / 4) % 2 == 0 ? 0x99 : 0x44;
                color = (pulse << 24) | baseColor;
            } else {
                color = 0xFF000000 | baseColor;
            }

            // Rotation math (screen-Y-down, ZP axis):
            // ZP.rotationDegrees(d): local +X → (cos d, sin d), local +Y → (-sin d, cos d).
            // Beam extends in local +Y. We want local +Y → (cos a, sin a) in screen.
            // -sin(d)=cos(a) and cos(d)=sin(a) → d = angleDeg - 90.
            float rotDeg = (float) Math.toDegrees(angle) - 90f;
            boolean isNDL = pool.isBidir(i);
            // NDL beams extend in both directions; Spark/directional only extend forward.
            int yStart = isNDL ? -length : 0;

            gfx.pose().pushPose();
            gfx.pose().translate(screenX, screenY, 0.0);
            gfx.pose().mulPose(Objects.requireNonNull(Axis.ZP.rotationDegrees(rotDeg)));

            if (!warn) {
                // Outer glow - proportional to beam width so it doesn't swamp thin NDL beams
                int glow = Math.max(2, hw / 2);
                gfx.fill(-(hw + glow), yStart, hw + glow, length, (0x33 << 24) | baseColor);
                // Main beam (this width directly matches the hitbox halfWidth)
                gfx.fill(-hw, yStart, hw, length, color);
                // Bright core
                int coreHw = Math.max(1, hw / 3);
                gfx.fill(-coreHw, yStart, coreHw, length, 0xCCFFFFFF);
            } else {
                // Warning: thin pulsing line
                gfx.fill(-hw, yStart, hw, length, color);
            }

            gfx.pose().popPose();
        }
    }

    // ---------------------------------------------------------------- bullet
    // rendering

    /**
     * Renders a single enemy bullet.
     *
     * Most types use the bullet texture + {@link BulletType#color} tint (ARGB alpha
     * respected). {@link BulletType#RED_OFUDA} and orb-family types ({@code dot} /
     * {@code orb_blue}
     * / {@code orb_red}) are drawn untinted so PNG color and alpha are preserved.
     * No axis-aligned
     * {@code gfx.fill} halos - those read as solid
     * "boxes" around stars/orbs and fight PNG transparency.
     *
     * <p>
     * Call only between {@link RenderSystem#enableBlend()} and a final
     * {@link RenderSystem#setShaderColor(float, float, float, float)} reset - see
     * the enemy and player bullet loops in {@link #render}.
     */
    private static void renderBullet(GuiGraphics gfx, BulletType type, int cx, int cy, int r, float vx, float vy) {
        BulletTypeData def = BulletTypeLoader.get(type);
        int texR = Math.max(1, Math.round(r * def.textureScale));
        float rotDeg = bulletRotationDegreesFromDef(def, vx, vy);
        blitTintedBulletFromDef(gfx, type, def, cx, cy, texR, rotDeg);
    }

    /**
     * Non-square blit: texture X = beam along velocity (after rotation), Y = thick
     * laser slab.
     */
    private static void renderShortLaserBullet(GuiGraphics gfx, BulletType type, int cx, int cy,
            float vis, float hit, float vx, float vy, float sx, float sy) {
        BulletTypeData def = BulletTypeLoader.get(type);
        float scale = (sx + sy) * 0.5f;
        float halfLenA = type.lineHitDrawHalfLength(vis);
        float halfThickA = type.lineHitDrawHalfWidth(hit);
        int halfW = Math.max(0, Math.round(halfLenA * scale));
        int halfH = Math.max(0, Math.round(halfThickA * scale));
        if (halfW <= 0 && halfH <= 0)
            return;
        float rotDeg = bulletRotationDegreesFromDef(def, vx, vy);
        blitTintedBulletRectFromDef(gfx, type, def, cx, cy, halfW, halfH, rotDeg);
    }

    // Fast path: def already fetched - single BulletTypeLoader.get() per bullet per frame.
    private static void blitTintedBulletFromDef(GuiGraphics gfx, BulletType type, BulletTypeData def,
            int cx, int cy, int r, float rotDeg) {
        ResourceLocation texture = bulletTexture(type);
        if (!def.applyTint) {
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        } else {
            int argb = def.color;
            float a = ((argb >>> 24) & 0xFF) / 255f;
            float cr = ((argb >>> 16) & 0xFF) / 255f;
            float cg = ((argb >>> 8) & 0xFF) / 255f;
            float cb = (argb & 0xFF) / 255f;
            RenderSystem.setShaderColor(cr, cg, cb, a);
        }
        int srcW = def.sourceSize;
        int srcH = def.sourceHeight > 0 ? def.sourceHeight : srcW;
        int destW = r * 2;
        int destH = (srcH != srcW) ? (r * 2 * srcH / srcW) : r * 2;
        if (rotDeg * rotDeg < 1e-4f) {
            gfx.blit(texture, cx - r, cy - destH / 2, destW, destH, 0, 0, srcW, srcH, srcW, srcH);
        } else {
            gfx.pose().pushPose();
            gfx.pose().translate(cx, cy, 0.0);
            gfx.pose().mulPose(Axis.ZP.rotationDegrees(rotDeg));
            gfx.blit(texture, -r, -destH / 2, destW, destH, 0, 0, srcW, srcH, srcW, srcH);
            gfx.pose().popPose();
        }
    }

    private static void blitTintedBulletRectFromDef(GuiGraphics gfx, BulletType type, BulletTypeData def,
            int cx, int cy, int halfW, int halfH, float rotDeg) {
        ResourceLocation texture = bulletTexture(type);
        if (!def.applyTint) {
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        } else {
            int argb = def.color;
            float a = ((argb >>> 24) & 0xFF) / 255f;
            float cr = ((argb >>> 16) & 0xFF) / 255f;
            float cg = ((argb >>> 8) & 0xFF) / 255f;
            float cb = (argb & 0xFF) / 255f;
            RenderSystem.setShaderColor(cr, cg, cb, a);
        }
        int srcSize = def.sourceSize;
        gfx.pose().pushPose();
        gfx.pose().translate(cx, cy, 0.0);
        gfx.pose().mulPose(Axis.ZP.rotationDegrees(rotDeg));
        gfx.blit(texture, -halfW, -halfH, halfW * 2, halfH * 2, 0, 0, srcSize, srcSize, srcSize, srcSize);
        gfx.pose().popPose();
    }

    private static float bulletRotationDegreesFromDef(BulletTypeData def, float vx, float vy) {
        Float base = def.baseAngleDeg;
        if (base == null)
            return 0f;
        if (Math.abs(vx) < 0.0001f && Math.abs(vy) < 0.0001f)
            return 0f;
        return (float) Math.toDegrees(Math.atan2(vy, vx)) - base;
    }

    // Used by debug hitbox path only (infrequent).
    private static float bulletRotationDegrees(BulletType type, float vx, float vy) {
        return bulletRotationDegreesFromDef(BulletTypeLoader.get(type), vx, vy);
    }


    // ---------------------------------------------------------------- character
    // sprite rendering

    /**
     * Renders the player character from the 256×296 sprite sheet centred on (cx,
     * cy).
     * Sheet layout: 8 columns × 32 px wide, rows are 47 px tall.
     * Row 0 (v=0): idle animation (8 frames)
     * Row 1 (v=47): left lean transition (frame 0 = near-idle, frame 7 = full left)
     * Row 2 (v=94): right lean transition
     * The animation is passed in: each player on screen has their own, so the sprites do
     * not all mirror whoever is local.
     * Falls back to a white square if the texture is missing.
     */
    private static void renderCharacterSprite(GuiGraphics gfx, String characterId,
            int cx, int cy, int halfSz, mc.sayda.bullethell.client.CharacterAnim anim) {
        ResourceLocation tex = charTex(characterId);
        float u = anim.column() * 32f;
        float v = anim.row * 47f;
        // Preserve the 32:47 aspect ratio; width = halfSz*2, height scaled accordingly
        int dstW = halfSz * 2;
        int dstH = (int) (dstW * 47f / 32f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            gfx.blit(Objects.requireNonNull(tex), cx - halfSz, cy - dstH / 2, dstW, dstH, u, v, 32, 47, 256, 296);
        } catch (Exception e) {
            // Texture missing - plain white square fallback
            gfx.fill(cx - halfSz, cy - halfSz, cx + halfSz, cy + halfSz, 0xCCFFFFFF);
        } finally {
            RenderSystem.disableBlend();
        }
    }

    // ---------------------------------------------------------------- item
    // rendering

    /**
     * Renders a single item using its texture sprite (16×16 PNG) if available,
     * falling back to a tinted rectangle so the game is playable without textures.
     *
     * Texture paths: {@code assets/bullethell/textures/item/<type>.png}
     * Add the actual PNGs to enable textured rendering.
     */
    private static void renderItem(GuiGraphics gfx, int type, int cx, int cy, int halfSz) {
        if (type >= 0 && type < ITEM_TEXTURES.length) {
            // Draw 16×16 sprite centred on (cx, cy), scaled by halfSz
            int size = halfSz * 2;
            gfx.blit(Objects.requireNonNull(ITEM_TEXTURES[type]),
                    cx - halfSz, cy - halfSz, size, size,
                    0, 0, 16, 16, 16, 16);
        } else {
            // Unknown type - plain tinted square fallback
            int color = ItemPool.colorOf(type);
            gfx.fill(cx - halfSz, cy - halfSz, cx + halfSz, cy + halfSz, color);
            gfx.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xCCFFFFFF);
        }
    }

    /** Splash source aspect is 400x500; height is sized to the arena and width follows. */
    private static final float SPLASH_ASPECT = 400f / 500f;
    /** Portrait height as a fraction of the arena height. */
    private static final float SPLASH_HEIGHT_FRACTION = 0.72f;

    /**
     * Draws the bomb / spell-card cut-in. Player portraits enter from the left, boss
     * portraits from the right; both keep drifting inward while they fade.
     */
    private static void renderSplash(GuiGraphics gfx, int ox, int oy, int dispW, int dispH, float partialTick) {
        mc.sayda.bullethell.client.SplashState splash = mc.sayda.bullethell.client.SplashState.INSTANCE;
        if (!splash.isActive()) return;

        float alpha = splash.alpha(partialTick);
        if (alpha <= 0f) return;

        int h = (int) (dispH * SPLASH_HEIGHT_FRACTION);
        int w = (int) (h * SPLASH_ASPECT);
        int y = oy + dispH - h;

        // 0 = fully off its own edge, 1 = fully arrived just inside the arena.
        float p = splash.progress(partialTick);
        int x;
        if (splash.isFromLeft()) {
            x = (int) (ox - w + p * w);
        } else {
            x = (int) (ox + dispW - p * w);
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        gfx.blit(splash.texture(), x, y, 0, 0f, 0f, w, h, w, h);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Strict cull - used for bullets and items which should vanish at the arena
     * edge.
     */
    private static boolean outOfArena(float x, float y) {
        return x < 0 || x > BulletPool.ARENA_W || y < 0 || y > BulletPool.ARENA_H;
    }

    private static final String[] PIP_STRINGS;
    static {
        int max = mc.sayda.bullethell.arena.PlayerState2D.PIECES_PER_EXTEND;
        PIP_STRINGS = new String[max + 1];
        for (int i = 0; i <= max; i++) {
            StringBuilder sb = new StringBuilder(max);
            for (int j = 0; j < max; j++) sb.append(j < i ? '●' : '○');
            PIP_STRINGS[i] = sb.toString();
        }
    }

    /** Returns a pip string like "●●○○○" showing pieces/max. */
    private static String buildPiecePips(int pieces, int max) {
        if (max == mc.sayda.bullethell.arena.PlayerState2D.PIECES_PER_EXTEND
                && pieces >= 0 && pieces < PIP_STRINGS.length)
            return PIP_STRINGS[pieces];
        StringBuilder sb = new StringBuilder(max);
        for (int i = 0; i < max; i++) sb.append(i < pieces ? '●' : '○');
        return sb.toString();
    }

    // HUD stat string caches - recomputed only when source values change.
    private static int prevLives = -1, prevLifePieces = -1;
    private static String cachedLivesStr = "♥ 0";
    private static int prevBombs = -1, prevBombPieces = -1;
    private static String cachedBombsStr = "✶ 0";
    // Co-op player score caches, indexed by playerIndex (1-based, slot 0 unused).
    private static final long[] prevCoopScores = new long[5];
    private static final String[] cachedCoopScoreStrs = {"", "", "", "", ""};
    // Player marker labels - pIdx is 1-4.
    private static final String[] PLAYER_LABELS = {"P0", "P1", "P2", "P3", "P4"};

    // Spell name truncation cache - keyed on (spellName, maxW).
    private static String cachedTruncSpell = "";
    private static String cachedTruncResult = "";
    private static int cachedTruncMaxW = -1;

    // Now-playing banner string caches.
    private static String cachedNpTitle = "";
    private static String cachedNpArtist = "";
    private static String cachedNpTitleLine = "";
    private static String cachedNpArtistLine = "";

    // Dialog body split cache - keyed on (bodyText, textW).
    private static String cachedSplitText = "";
    private static int cachedSplitW = -1;
    private static java.util.List<net.minecraft.util.FormattedCharSequence> cachedSplitLines = java.util.List.of();

    private static String getLivesStr(ClientArenaState state) {
        int lives = state.player.lives;
        int pieces = state.lifePieces;
        if (lives != prevLives || pieces != prevLifePieces) {
            prevLives = lives;
            prevLifePieces = pieces;
            String pips = buildPiecePips(pieces, mc.sayda.bullethell.arena.PlayerState2D.PIECES_PER_EXTEND);
            cachedLivesStr = pieces > 0 ? "♥ " + lives + " " + pips : "♥ " + lives;
        }
        return cachedLivesStr;
    }

    private static String getBombsStr(ClientArenaState state) {
        int bombs = state.player.bombs;
        int pieces = state.bombPieces;
        if (bombs != prevBombs || pieces != prevBombPieces) {
            prevBombs = bombs;
            prevBombPieces = pieces;
            String pips = buildPiecePips(pieces, mc.sayda.bullethell.arena.PlayerState2D.PIECES_PER_EXTEND);
            cachedBombsStr = pieces > 0 ? "✶ " + bombs + " " + pips : "✶ " + bombs;
        }
        return cachedBombsStr;
    }

    private static String getCoopScoreStr(int pNum, long score) {
        if (pNum >= 1 && pNum <= 4 && prevCoopScores[pNum] == score) return cachedCoopScoreStrs[pNum];
        String s = "PTS " + String.format("%,d", score);
        if (pNum >= 1 && pNum <= 4) { prevCoopScores[pNum] = score; cachedCoopScoreStrs[pNum] = s; }
        return s;
    }

    private static void renderPlayerMarker(GuiGraphics gfx, int px, int py, int pIdx, boolean isLocal) {
        Font font = Minecraft.getInstance().font;
        String label = pIdx >= 0 && pIdx < PLAYER_LABELS.length ? PLAYER_LABELS[pIdx] : "P" + pIdx;
        int color = pIdx == 1 ? 0xFF00FFE0 : pIdx == 2 ? 0xFFFFDD44 : pIdx == 3 ? 0xFFFF44CC : 0xFFAA4477;
        int textW = font.width(label);
        int halfW = textW / 2;

        // Marker typically sits ~20-25px above player centre
        int markerY = py - 24;

        // Pulsing arrow triangle (bobbing slightly)
        float bob = (float) Math.sin(ClientArenaState.INSTANCE.bossAnimCounter * 0.2f) * 2f;
        int arrowY = markerY + (int) bob;

        // Draw the triangle arrow
        gfx.fill(px - 3, arrowY, px + 3, arrowY + 1, color);
        gfx.fill(px - 2, arrowY + 1, px + 2, arrowY + 2, color);
        gfx.fill(px - 1, arrowY + 2, px + 1, arrowY + 3, color);

        // Draw the text with a dark shadow
        gfx.drawString(font, label, px - halfW, markerY - 10, color, true);
    }



    private static int phaseColour(int phase) {
        return switch (phase) {
            case 0 -> 0xFF00FFE0;
            case 1 -> 0xFFFFE600;
            case 2 -> 0xFFFF7700;
            default -> 0xFFFF3344;
        };
    }
}
