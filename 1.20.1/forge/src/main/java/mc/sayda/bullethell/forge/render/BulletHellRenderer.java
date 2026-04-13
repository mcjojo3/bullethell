package mc.sayda.bullethell.forge.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import mc.sayda.bullethell.arena.LaserPool;
import mc.sayda.bullethell.Bullethell;
import mc.sayda.bullethell.arena.BulletPool;
import mc.sayda.bullethell.arena.EnemyPool;
import mc.sayda.bullethell.arena.EnemyType;
import mc.sayda.bullethell.arena.GameEvent;
import mc.sayda.bullethell.arena.ItemPool;
import mc.sayda.bullethell.forge.client.BHMusicManager;
import mc.sayda.bullethell.forge.client.ClientArenaState;
import mc.sayda.bullethell.forge.client.ScreenFXQueue;
import mc.sayda.bullethell.pattern.BulletType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.HashMap;
import java.util.Map;

/**
 * Full-screen bullet hell arena overlay.
 *
 * Rendering order (painter's algorithm):
 *   1. Gutters + background
 *   2. Grid + PoC line
 *   3. Items
 *   4. Enemy bullets
 *   5. Player bullets
 *   6. Boss
 *   7. Player
 *   8. Screen FX tint (hit flash, bomb flash, etc.)
 *   9. HUD (HP bar, spell timer, bottom strip)
 *  10. Arena border
 */
public class BulletHellRenderer implements IGuiOverlay {

    public static final BulletHellRenderer INSTANCE = new BulletHellRenderer();

    private static final int GRID_STEP    = 80;
    /** Pixel height reserved at the bottom of the screen for the boss indicator strip. */
    private static final int INDICATOR_H  = 28;

    // ---- Character / player textures -------------------------------------------
    private static final Map<String, ResourceLocation> CHAR_TEX_CACHE = new HashMap<>();
    private static final Map<String, ResourceLocation> BOSS_TEX_CACHE = new HashMap<>();

    private static ResourceLocation charTex(String characterId) {
        return CHAR_TEX_CACHE.computeIfAbsent(characterId,
                id -> new ResourceLocation(Bullethell.MODID, "textures/characters/" + id + ".png"));
    }

    private static ResourceLocation bossTex(String bossId) {
        return BOSS_TEX_CACHE.computeIfAbsent(bossId,
                id -> new ResourceLocation(Bullethell.MODID, "textures/bosses/" + id + ".png"));
    }

    // ---- Item textures (16×16 sprites) -----------------------------------------
    // Place the actual PNG files at: assets/bullethell/textures/item/<name>.png
    // The renderer falls back to a tinted rectangle if the texture is missing.
    private static final ResourceLocation[] ITEM_TEXTURES = {
        new ResourceLocation(Bullethell.MODID, "textures/item/power.png"),       // TYPE_POWER
        new ResourceLocation(Bullethell.MODID, "textures/item/point.png"),       // TYPE_POINT
        new ResourceLocation(Bullethell.MODID, "textures/item/full_power.png"),  // TYPE_FULL_POWER
        new ResourceLocation(Bullethell.MODID, "textures/item/one_up.png"),      // TYPE_ONE_UP
        new ResourceLocation(Bullethell.MODID, "textures/item/bomb.png"),        // TYPE_BOMB
    };

    // ---- Enemy textures (32×32 sprites) ----------------------------------------
    // Place PNGs at: assets/bullethell/textures/enemies/<name>.png
    // Falls back to a tinted diamond/square if the texture is missing.
    private static final ResourceLocation[] ENEMY_TEXTURES = {
        new ResourceLocation(Bullethell.MODID, "textures/enemies/blue_fairy.png"),   // BLUE_FAIRY
        new ResourceLocation(Bullethell.MODID, "textures/enemies/red_fairy.png"),    // RED_FAIRY
        new ResourceLocation(Bullethell.MODID, "textures/enemies/yellow_fairy.png"),    // YELLOW_FAIRY
        new ResourceLocation(Bullethell.MODID, "textures/enemies/green_fairy.png"),    // GREEN_FAIRY
    };

    // Fallback tint colors per texture index (used when PNG is missing)
    private static final int[] ENEMY_COLORS = {
        0xFF88AAFF,  // 0 blue
        0xFFFF6666,  // 1 red
        0xFFFFDD44,  // 2 yellow
        0xFF66EE88,  // 3 green
    };

    @Override
    public void render(ForgeGui gui, GuiGraphics gfx, float partialTick,
                       int screenW, int screenH) {

        ClientArenaState state = ClientArenaState.INSTANCE;
        if (!state.active) return;

        // ---- Compute display rect (3:4, fills available height above indicator strip) ----
        // INDICATOR_H pixels are reserved at the very bottom for the boss tracker.
        int dispH = screenH - INDICATOR_H;
        int dispW = (int) (dispH * BulletPool.ARENA_W / BulletPool.ARENA_H);
        if (dispW > screenW) {
            dispW = screenW;
            dispH = (int) (screenW * BulletPool.ARENA_H / BulletPool.ARENA_W);
        }
        final int   ox = (screenW - dispW) / 2;
        final int   oy = 0; // arena always anchored to top of screen
        final float sx = (float) dispW / BulletPool.ARENA_W;
        final float sy = (float) dispH / BulletPool.ARENA_H;

        // ---- 1. Arena background ----
        gfx.fill(ox, oy, ox + dispW, oy + dispH, 0xFF000015);

        // ---- 2. Grid ----
        for (int g = 1; g < (int)(BulletPool.ARENA_W / GRID_STEP) + 1; g++)
            gfx.vLine(ox + (int)(g * GRID_STEP * sx), oy, oy + dispH, 0x0800FFE0);
        for (int g = 1; g < (int)(BulletPool.ARENA_H / GRID_STEP) + 1; g++)
            gfx.hLine(ox, ox + dispW, oy + (int)(g * GRID_STEP * sy), 0x0800FFE0);

        // ---- 2. PoC line (Point of Collection — items auto-attract above this line) ----
        int pocY = oy + (int)(BulletPool.ARENA_H * 0.20f * sy);
        gfx.hLine(ox, ox + dispW, pocY, 0x3300FFE0);
        // Small "PoC" label at right edge — subtle, doesn't clutter the arena
        {
            Font pocFont = Minecraft.getInstance().font;
            gfx.drawString(pocFont, "PoC", ox + dispW - pocFont.width("PoC") - 2, pocY - pocFont.lineHeight - 1,
                    0x2200FFE0, false);
        }

        // ---- 3. Items - partial-tick Y extrapolation ----
        for (int i = 0; i < ItemPool.CAPACITY; i++) {
            if (!state.items.isActive(i)) continue;
            float ix = state.items.getX(i);
            float iy = state.items.getY(i) + state.items.getVy(i) * partialTick;
            if (outOfArena(ix, iy)) continue;
            int type = state.items.getType(i);
            int six  = ox + (int)(ix * sx);
            int siy  = oy + (int)(iy * sy);
            int sz   = Math.max(4, (int)(ItemPool.sizeOf(type) * (sx + sy) * 0.5f));
            renderItem(gfx, type, six, siy, sz);
        }

        // ---- 4. Enemies - partial-tick extrapolation ----
        // Generous cull bounds so off-screen entrants are visible; mask is applied later.
        for (int i = 0; i < EnemyPool.CAPACITY; i++) {
            if (!state.enemies.isActive(i)) continue;
            float ex = state.enemies.getX(i) + state.enemies.getVx(i) * partialTick;
            float ey = state.enemies.getY(i) + state.enemies.getVy(i) * partialTick;
            if (ex < -64 || ex > BulletPool.ARENA_W + 64 || ey < -64 || ey > BulletPool.ARENA_H + 64) continue;
            int typeId = state.enemies.getType(i);
            int sex    = ox + (int)(ex * sx);
            int sey    = oy + (int)(ey * sy);
            renderEnemy(gfx, typeId, sex, sey, (sx + sy) * 0.5f);
        }

        // ---- 4b. Laser beams (warning + active) ----
        gfx.enableScissor(ox, oy, ox + dispW, oy + dispH);
        renderLasers(gfx, state, ox, oy, sx, sy);
        gfx.disableScissor();

        // ---- 5. Enemy bullets - partial-tick extrapolation ----
        for (int i = 0; i < BulletPool.ENEMY_CAPACITY; i++) {
            if (!state.bullets.isActive(i)) continue;
            float bx = state.bullets.getX(i) + state.bullets.getVx(i) * partialTick;
            float by = state.bullets.getY(i) + state.bullets.getVy(i) * partialTick;
            if (outOfArena(bx, by)) continue;
            BulletType type = BulletType.fromId(state.bullets.getType(i));
            int sbx = ox + (int)(bx * sx);
            int sby = oy + (int)(by * sy);
            int r   = Math.max(1, (int)(type.radius * (sx + sy) * 0.5f));
            renderBullet(gfx, type, sbx, sby, r);
        }

        // ---- 6. Player bullets - partial-tick extrapolation ----
        for (int i = 0; i < BulletPool.PLAYER_CAPACITY; i++) {
            if (!state.playerBullets.isActive(i)) continue;
            float bx = state.playerBullets.getX(i) + state.playerBullets.getVx(i) * partialTick;
            float by = state.playerBullets.getY(i) + state.playerBullets.getVy(i) * partialTick;
            if (outOfArena(bx, by)) continue;
            int sbx = ox + (int)(bx * sx);
            int sby = oy + (int)(by * sy);
            int r   = Math.max(1, (int)(3 * (sx + sy) * 0.5f));
            gfx.fill(sbx - r, sby - r, sbx + r, sby + r, BulletType.PLAYER_SHOT.color);
        }

        // ---- 7. Boss (only visible during boss phase) ----
        int phase    = state.bossPhase;
        int phaseCol = phaseColour(phase);
        if (state.bossMaxHp > 0) {
            int bx  = ox + (int)(state.bossX * sx);
            int by2 = oy + (int)(state.bossY * sy);
            int sz  = Math.max(24, (int)(44 * (sx + sy) * 0.5f));
            renderBossSprite(gfx, state.bossId, bx, by2, sz);
        }

        // ---- 8. Player sprite + optional hitbox (shift-only) ----
        boolean showHitbox = Minecraft.getInstance().options.keyShift.isDown();
        {
            int px   = ox + (int)(state.player.x * sx);
            int py   = oy + (int)(state.player.y * sy);
            int spriteHalf = Math.max(10, (int)(16 * (sx + sy) * 0.5f));
            renderCharacterSprite(gfx, state.characterId, px, py, spriteHalf);
            if (showHitbox) {
                // Small red dot at the exact collision point — size independent of sprite
                gfx.fill(px - 2, py - 2, px + 2, py + 2, 0xCCFF2222);
                gfx.fill(px - 1, py - 1, px + 1, py + 1, 0xFFFF6666);
            }
        }

        // ---- 8b. Co-op players (other participants) ----
        for (mc.sayda.bullethell.forge.network.CoopPlayersSyncPacket.Entry cp : state.coopPlayers) {
            if (cp.lives() < 0) continue; // eliminated
            int px   = ox + (int)(cp.x() * sx);
            int py   = oy + (int)(cp.y() * sy);
            int half = Math.max(6, (int)(10 * (sx + sy) * 0.5f));
            // TODO: pass each coop player's characterId once CoopPlayersSyncPacket carries it
            // For now fall back to a tinted square in the player's colour
            int col = cp.tintColor() | 0xFF000000;
            gfx.fill(px - half, py - half, px + half, py + half, col);
            if (showHitbox) {
                gfx.fill(px - 1, py - 1, px + 1, py + 1, 0xFFFFFFFF);
            }
        }

        // ---- 8c. Screen FX tints ----
        renderFX(gfx, ox, oy, dispW, dispH);

        // ---- 8d. Spell card declaration overlay (above FX, below HUD) ----
        if (state.declaring && !state.spellName.isEmpty()) {
            renderDeclaration(gfx, ox, oy, dispW, dispH, state.declarationFrame);
        }

        // ---- 9. HUD overlays ----
        renderHUD(gfx, ox, oy, dispW, dispH, sx, sy, phase, phaseCol);

        // ---- 9b. Pre-boss intro dialog ----
        if (!state.dialogSpeaker.isEmpty()) {
            renderDialog(gfx, state, ox, oy, dispW, dispH);
        }

        // ---- 9c. Now-playing banner ----
        BHMusicManager mgr = BHMusicManager.INSTANCE;
        if (mgr.npTick < BHMusicManager.NP_TOTAL_TICKS) {
            renderNowPlaying(gfx, ox, oy, dispW, dispH, mgr.npTitle, mgr.npArtist, mgr.npTick);
        }

        // ---- 10. Mask - solid fill over every pixel outside the arena box ----
        gfx.fill(0,          0, ox,          dispH, 0xFF000000); // left gutter
        gfx.fill(ox + dispW, 0, screenW,     dispH, 0xFF000000); // right gutter
        // When arena is letterboxed horizontally (very wide screen), fill the top band too
        if (oy > 0) gfx.fill(ox, 0, ox + dispW, oy, 0xFF000000);

        // ---- 10b. Indicator strip (drawn over the black bottom area) ----
        gfx.fill(0, dispH, screenW, screenH, 0xFF000000); // dark background for strip

        // ---- 11. Arena border (top layer, drawn over the mask) ----
        int borderCol = ScreenFXQueue.INSTANCE.isActive(GameEvent.PHASE_CHANGE) ? phaseCol : 0x8800FFE0;
        if (oy > 0) gfx.hLine(ox, ox + dispW, oy, borderCol);
        gfx.hLine(ox,         ox + dispW, oy + dispH, borderCol);
        gfx.vLine(ox,         oy,         oy + dispH, borderCol);
        gfx.vLine(ox + dispW, oy,         oy + dispH, borderCol);

        // ---- 12. Boss indicator strip at screen bottom ----
        if (state.bossMaxHp > 0) {
            int bossScrX = ox + (int)(state.bossX * sx);
            renderBossIndicator(gfx, state, bossScrX, ox, dispH, dispW, screenW, phaseCol);
        }
    }

    // ---------------------------------------------------------------- screen FX

    private static void renderFX(GuiGraphics gfx, int ox, int oy, int dw, int dh) {
        ScreenFXQueue fx = ScreenFXQueue.INSTANCE;

        if (fx.isActive(GameEvent.DEATH)) {
            int alpha = (int)(fx.intensity(GameEvent.DEATH) * 0xAA);
            gfx.fill(ox, oy, ox + dw, oy + dh, (alpha << 24) | 0xFF0000);
        } else if (fx.isActive(GameEvent.HIT)) {
            int alpha = (int)(fx.intensity(GameEvent.HIT) * 0x66);
            gfx.fill(ox, oy, ox + dw, oy + dh, (alpha << 24) | 0xFF2200);
        }

        if (fx.isActive(GameEvent.BOMB_USED)) {
            int alpha = (int)(fx.intensity(GameEvent.BOMB_USED) * 0x99);
            gfx.fill(ox, oy, ox + dw, oy + dh, (alpha << 24) | 0xFFFFFF);
        }

        if (fx.isActive(GameEvent.SPELL_CAPTURED)) {
            int alpha = (int)(fx.intensity(GameEvent.SPELL_CAPTURED) * 0x55);
            gfx.fill(ox, oy, ox + dw, oy + dh, (alpha << 24) | 0x00FFE0);
        }
    }

    // ---------------------------------------------------------------- HUD

    private static void renderHUD(GuiGraphics gfx, int ox, int oy, int dw, int dh,
                                   float sx, float sy, int phase, int phaseCol) {
        ClientArenaState state = ClientArenaState.INSTANCE;
        Font font = Minecraft.getInstance().font;
        int lh = font.lineHeight; // typically 9 px

        // ---- Top bar stack (stacked rows, no overlap) ----
        int cursor = oy;

        // Row 0: Boss name (small, dimmed gold) + phase label (right) — only during boss fight
        if (state.bossMaxHp > 0 && !state.bossName.isEmpty()) {
            String phLabel  = "PHASE " + (phase + 1);
            String scoreStr = String.format("%,d", state.score);
            gfx.drawString(font, state.bossName, ox + 4,                             cursor, 0x88FFDD88, false);
            gfx.drawString(font, scoreStr,        ox + dw - font.width(scoreStr) - 4, cursor, 0xFFFFE600, false);
            cursor += lh + 1;
            // thin dim separator
            gfx.hLine(ox, ox + dw, cursor, 0x22FFFFFF);
            cursor += 2;
        }

        // Row 1: Boss HP bar
        int hpBarH = Math.max(4, (int)(5 * sy));
        if (state.bossMaxHp > 0) {
            int fill   = (int)((float) dw * state.bossHp / state.bossMaxHp);
            int barCol = state.activeSpellCard ? 0xCCFFDD00 : 0xCCFF44FF;
            gfx.fill(ox, cursor, ox + dw,  cursor + hpBarH, 0xFF0D0D1A);
            gfx.fill(ox, cursor, ox + fill, cursor + hpBarH, barCol);
            gfx.fill(ox, cursor, ox + fill, cursor + 1,      phaseCol);
        }
        cursor += hpBarH + 1;

        // Row 2: Spell timer bar (only during spell cards)
        int timerBarH = Math.max(2, (int)(3 * sy));
        if (state.spellTimerTotal > 0) {
            float frac = (float) state.spellTimerTicks / state.spellTimerTotal;
            int fill   = (int)(dw * frac);
            int col    = frac > 0.5f ? 0xFF00FFE0 : frac > 0.25f ? 0xFFFFE600 : 0xFFFF4400;
            gfx.fill(ox, cursor, ox + dw,  cursor + timerBarH, 0xFF0A0A14);
            gfx.fill(ox, cursor, ox + fill, cursor + timerBarH, col);
        }
        cursor += timerBarH + 1;

        // Row 3: Power bar (thin, tinted by power level)
        int powerBarH = Math.max(2, (int)(2 * sy));
        {
            int powerFill = (int)((float) dw * state.power / 128f);
            int powerCol  = state.power >= 128 ? 0xFFFF88FF
                          : state.power >=  96 ? 0xFFFF44CC
                          : state.power >=  32 ? 0xFFFF4488
                          :                      0xFFAA4477;
            gfx.fill(ox, cursor, ox + dw,         cursor + powerBarH, 0xFF0A0A14);
            gfx.fill(ox, cursor, ox + powerFill,   cursor + powerBarH, powerCol);
        }
        cursor += powerBarH + 2;

        // Row 4: Phase label (left, only when no boss name row above) + score if no boss
        if (state.bossMaxHp <= 0) {
            String phLabel  = "PHASE " + (phase + 1);
            String scoreStr = String.format("%,d", state.score);
            gfx.drawString(font, phLabel,  ox + 4,                             cursor, phaseCol,   false);
            gfx.drawString(font, scoreStr, ox + dw - font.width(scoreStr) - 4, cursor, 0xFFFFE600, false);
            cursor += lh + 2;
        } else {
            // phase label stays (score already shown in row 0)
            String phLabel = "PHASE " + (phase + 1);
            gfx.drawString(font, phLabel, ox + 4, cursor, phaseCol, false);
            cursor += lh + 2;
        }

        // Row 5: Spell card name — only shown during active spell card
        if (state.activeSpellCard && !state.spellName.isEmpty()) {
            gfx.drawString(font, state.spellName, ox + 4, cursor, 0xFFFFDD00, false);
        }

        // ---- Bottom HUD strip — two rows so nothing overlaps on small screens ----
        //
        //  Row A (top):    BOMBS X (left)  |  PWR X/128 (right)
        //  Row B (bottom): LIVES X (left)  |  GRAZE X   (right)
        //
        int rowH    = lh + 4;
        int stripH  = rowH * 2 + 2;
        int hudY    = oy + dh - stripH;
        gfx.fill(ox, hudY, ox + dw, oy + dh, 0xBB000015);

        // Divider between the two rows
        gfx.hLine(ox, ox + dw, hudY + rowH, 0x33FFFFFF);

        int tyA = hudY + 2;           // y of row A text
        int tyB = hudY + rowH + 2;    // y of row B text

        String bombsStr = "\u2736 " + state.player.bombs;  // ✶ bombs
        String pwrStr   = "PWR " + state.power + "/128";
        String livesStr = "\u2665 " + state.player.lives;  // ♥ lives
        String grazeStr = "GRAZE " + state.player.graze;

        int pwrColor = state.power >= 128 ? 0xFFFF88FF : state.power >= 64 ? 0xFFFF44CC : 0xFFFF4488;

        // Row A
        gfx.drawString(font, bombsStr, ox + 4,                              tyA, 0xFFFF3FA4, false);
        gfx.drawString(font, pwrStr,   ox + dw - font.width(pwrStr) - 4,    tyA, pwrColor,   false);

        // Row B
        gfx.drawString(font, livesStr, ox + 4,                              tyB, 0xFF00FFE0, false);
        gfx.drawString(font, grazeStr, ox + dw - font.width(grazeStr) - 4,  tyB, 0xFFFFE600, false);
    }

    // ---------------------------------------------------------------- Pre-boss intro dialog

    /**
     * Touhou-style dialog box that slides in from the top of the arena.
     *
     * Layout:
     *   ╔══ SPEAKER NAME ═════════════════════════════════════╗
     *   ║  [portrait]  "Dialog text, possibly wrapping        ║
     *   ║               across two lines if needed."        ▼ ║
     *   ╚═════════════════════════════════════════════════════╝
     *
     * Slide-in: the box starts fully above oy and eases down to oy + 4 over 20 ticks.
     * Each new dialog line resets dialogSlideInTick to 0 so it re-enters from above.
     *
     * BOSS speaker  → gold border + gold name
     * PLAYER speaker → cyan border + cyan name
     */
    private static void renderDialog(GuiGraphics gfx, ClientArenaState state,
                                      int ox, int oy, int dw, int dh) {
        Font font = Minecraft.getInstance().font;
        int lh = font.lineHeight; // typically 9

        // Box geometry
        int portSz = lh * 3 + 4;   // portrait cell height (~31 px)
        int padX   = 6;
        int boxW   = (int)(dw * 0.90f);
        int boxH   = portSz + 12;   // portrait + top/bottom padding
        int boxX   = ox + (dw - boxW) / 2;

        // Ease-out slide-in from above the arena
        float t      = Math.min(1f, state.dialogSlideInTick / 20f);
        float ease   = 1f - (1f - t) * (1f - t);           // quadratic ease-out
        int slideEnd = oy + 4;
        int boxY     = (int)((oy - boxH - 8) * (1f - ease) + slideEnd * ease);

        boolean isBoss  = "BOSS".equalsIgnoreCase(state.dialogSpeaker);
        int borderCol   = isBoss ? 0xFFFFDD44 : 0xFF44FFEE;
        int nameCol     = isBoss ? 0xFFFFDD44 : 0xFF44FFEE;
        String speaker  = isBoss ? state.bossName
                                 : mc.sayda.bullethell.boss.CharacterLoader.load(state.characterId).name;
        if (speaker == null || speaker.isEmpty()) speaker = state.dialogSpeaker;

        // ---- Background ----
        gfx.fill(boxX,     boxY,           boxX + boxW,     boxY + boxH,     0xEE000018);
        // Subtle inner gradient tint on the speaker-name row
        gfx.fill(boxX + 1, boxY + 1,       boxX + boxW - 1, boxY + lh + 6,  0x33000000 | (borderCol & 0x00FFFFFF));

        // ---- Border ----
        gfx.hLine(boxX,         boxX + boxW - 1, boxY,             borderCol);
        gfx.hLine(boxX,         boxX + boxW - 1, boxY + boxH - 1,  borderCol);
        gfx.vLine(boxX,                           boxY, boxY + boxH, borderCol);
        gfx.vLine(boxX + boxW - 1,                boxY, boxY + boxH, borderCol);
        // Accent line under speaker name
        gfx.hLine(boxX + 1, boxX + boxW - 2, boxY + lh + 5, (0x55 << 24) | (borderCol & 0x00FFFFFF));

        // ---- Speaker name (top-left) ----
        gfx.drawString(font, speaker, boxX + padX, boxY + 3, nameCol, false);

        // ---- Portrait (right side of name row, or left of text body) ----
        int portX = boxX + boxW - portSz - padX;
        int portY = boxY + lh + 8;
        int portHalf = portSz / 2;
        if (isBoss && !state.bossId.isEmpty()) {
            // Use the boss sprite sheet (idle frame 0)
            renderBossSprite(gfx, state.bossId, portX + portHalf, portY + portHalf, portHalf);
        } else {
            renderCharacterSprite(gfx, state.characterId, portX + portHalf, portY + portHalf, portHalf);
        }

        // ---- Dialog text (left of portrait, word-wrapped to 2 lines) ----
        int textX  = boxX + padX;
        int textY  = boxY + lh + 9;
        int textW  = boxW - portSz - padX * 3;
        java.util.List<net.minecraft.util.FormattedCharSequence> lines =
                font.split(net.minecraft.network.chat.Component.literal(state.dialogText), textW);
        for (int i = 0; i < Math.min(lines.size(), 2); i++) {
            gfx.drawString(font, lines.get(i), textX, textY + i * (lh + 2), 0xFFEEEEEE, false);
        }

        // ---- ▼ auto-advance indicator (bottom-right of box) ----
        // Pulse: visible only every other second (blink at ~1 Hz)
        boolean blink = (state.dialogSlideInTick / 10) % 2 == 0;
        if (blink) {
            gfx.drawString(font, "\u25BC", boxX + boxW - font.width("\u25BC") - padX,
                    boxY + boxH - lh - 3, (0x99 << 24) | 0xFFFFFF, false);
        }
    }

    // ---------------------------------------------------------------- Spell card declaration overlay

    /**
     * Full-screen declaration animation shown when the boss declares a spell card.
     * Inspired by TH8 (IN): white flash fades to a darkened arena, then the spell
     * card name fades in centred on screen.
     *
     * Frame 0-8:   bright white flash (alpha 255→0)
     * Frame 9-35:  dark vignette fades in while name alpha rises
     * Frame 36+:   name fully visible, vignette holds
     *
     * TODO: play a spell-card declaration sound (windup chime) at frame 0.
     */
    static void renderDeclaration(GuiGraphics gfx, int ox, int oy, int dw, int dh, int frame) {
        Font font = Minecraft.getInstance().font;
        String name = ClientArenaState.INSTANCE.spellName;
        if (name.isEmpty()) return;

        // White flash at the moment of declaration
        if (frame < 10) {
            int flashAlpha = (int)((1f - frame / 10f) * 200);
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
            int alpha = (int)(nameAlpha * 255);
            int cx    = ox + dw / 2;
            int cy    = oy + dh / 2 - font.lineHeight;

            // Gold border text (offset 1 px in each direction)
            int borderCol = (alpha << 24) | 0x885500;
            gfx.drawString(font, name, cx - font.width(name) / 2 + 1, cy + 1, borderCol, false);
            // Main text in bright gold
            int mainCol = (alpha << 24) | 0xFFDD00;
            gfx.drawString(font, name, cx - font.width(name) / 2, cy, mainCol, false);

            // Thin decorative lines flanking the name (like TH8 decoration)
            int lineAlpha = (int)(nameAlpha * 100);
            int lineY     = cy + font.lineHeight / 2;
            int nameW     = font.width(name);
            gfx.hLine(ox + 8,                  cx - nameW / 2 - 8,  lineY, (lineAlpha << 24) | 0xFFDD00);
            gfx.hLine(cx + nameW / 2 + 8,      ox + dw - 8,         lineY, (lineAlpha << 24) | 0xFFDD00);
        }
    }

    // ---------------------------------------------------------------- now-playing banner

    /**
     * Touhou-style track announcement displayed when a new music track starts.
     *
     * Layout (bottom-right of arena, above HUD strip):
     *   ┃ ♪ Track Title
     *   ┃   Artist Name
     *
     * Timing at 20 tps:
     *   ticks  0–10  : fade in
     *   ticks 10–110 : hold
     *   ticks 110–140: fade out
     */
    private static void renderNowPlaying(GuiGraphics gfx, int ox, int oy, int dw, int dh,
                                          String title, String artist, int tick) {
        Font font = Minecraft.getInstance().font;
        int lh = font.lineHeight; // typically 9

        // Alpha envelope
        float alpha;
        if      (tick < 10)  alpha = tick / 10f;
        else if (tick < 110) alpha = 1f;
        else                 alpha = (140 - tick) / 30f;
        alpha = Math.max(0f, Math.min(1f, alpha));
        int a = (int)(alpha * 255);
        if (a <= 0) return;

        // Measure box dimensions
        String titleLine  = "\u266a " + title;   // ♪
        String artistLine = "  " + artist;
        boolean hasArtist = !artist.isEmpty();
        int contentW = Math.max(font.width(titleLine),
                                hasArtist ? font.width(artistLine) : 0);
        int pad  = 5;
        int boxW = contentW + pad * 2 + 3; // +3 for left accent bar
        int boxH = (hasArtist ? lh * 2 + 4 : lh + 2) + pad;

        // HUD strip height (mirrors renderHUD calculation)
        int rowH   = lh + 4;
        int stripH = rowH * 2 + 2;

        // Position: bottom-right, just above the HUD strip
        int bx = ox + dw - boxW - 4;
        int by = oy + dh - stripH - boxH - 6;

        // Dark translucent background
        int bgA = (int)(alpha * 170);
        gfx.fill(bx, by, bx + boxW, by + boxH, (bgA << 24) | 0x000010);

        // Gold left-edge accent bar
        int barA = (int)(alpha * 220);
        gfx.fill(bx, by, bx + 2, by + boxH, (barA << 24) | 0xFFE600);

        // Track title in gold
        int titleCol  = (a << 24) | 0xFFE600;
        gfx.drawString(font, titleLine, bx + pad, by + pad / 2 + 1, titleCol, false);

        // Artist name in soft blue-white, indented
        if (hasArtist) {
            int artistCol = (a << 24) | 0x99CCEE;
            gfx.drawString(font, artistLine, bx + pad, by + pad / 2 + 1 + lh + 2, artistCol, false);
        }
    }

    // ---------------------------------------------------------------- boss sprite rendering

    /**
     * Renders the boss using row 0 (idle) of its 256×256 sprite sheet.
     * Sheet layout: 64×64 blocks, 4 columns per row. Only the idle row is used.
     * Frame cycles at ~5 fps: {@code frame = (bossAnimCounter / 4) % 4}.
     * Falls back to a magenta square if the texture is missing.
     */
    private static void renderBossSprite(GuiGraphics gfx, String bossId,
                                          int cx, int cy, int halfSz) {
        if (bossId == null || bossId.isEmpty()) {
            gfx.fill(cx - halfSz, cy - halfSz, cx + halfSz, cy + halfSz, 0xFFFF44FF);
            return;
        }
        int frame = (ClientArenaState.INSTANCE.bossAnimCounter / 4) % 4;
        float u   = frame * 64f;
        ResourceLocation tex = bossTex(bossId);
        int size = halfSz * 2;
        try {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            gfx.blit(tex, cx - halfSz, cy - halfSz, size, size, u, 0f, 64, 64, 256, 256);
            RenderSystem.disableBlend();
        } catch (Exception e) {
            gfx.fill(cx - halfSz, cy - halfSz, cx + halfSz, cy + halfSz, 0xFFFF44FF);
        }
    }

    /**
     * Renders the boss indicator strip in the {@link #INDICATOR_H}-pixel band at the bottom
     * of the screen. A small boss portrait slides horizontally with the boss X position
     * along a track spanning the arena width, giving the player a quick positional readout.
     *
     * Layout:
     *   │ phase-colour top border line                                │
     *   │  [arena left]───────[BOSS ICON]──────────[arena right]  HP │
     */
    /**
     * Renders the boss position indicator in the {@link #INDICATOR_H}-pixel strip at the
     * bottom of the screen.
     *
     * Layout: a compact panel centred on the arena, containing:
     *   - A horizontal track line (arena left → right)
     *   - The boss mini-sprite sliding along the track to show boss X
     *   - A small HP bar under the icon
     *   - A phase-coloured border box around the whole panel (no full-screen line)
     */
    private static void renderBossIndicator(GuiGraphics gfx, ClientArenaState state,
                                             int bossScrX, int ox, int stripY,
                                             int dispW, int screenW, int phaseCol) {
        Font font   = Minecraft.getInstance().font;
        int iconSz  = INDICATOR_H / 2 - 2;   // half-size of the mini sprite (~10 px)
        int centerY = stripY + INDICATOR_H / 2;

        // Panel bounds — confined to the arena width, 2 px padding on each side
        int panelLeft  = ox;
        int panelRight = ox + dispW;
        int panelTop   = stripY + 1;
        int panelBot   = stripY + INDICATOR_H - 1;

        // Subtle inner fill so the panel stands out from the raw background
        gfx.fill(panelLeft, panelTop, panelRight, panelBot, 0x22000022);

        // Phase-coloured border box around the panel
        gfx.hLine(panelLeft,  panelRight, panelTop, phaseCol);
        gfx.hLine(panelLeft,  panelRight, panelBot, phaseCol);
        gfx.vLine(panelLeft,  panelTop,   panelBot, phaseCol);
        gfx.vLine(panelRight, panelTop,   panelBot, phaseCol);

        // Horizontal track line
        int trackLeft  = panelLeft  + iconSz + 6;
        int trackRight = panelRight - iconSz - 6;
        gfx.hLine(trackLeft, trackRight, centerY, 0x33FFFFFF);
        gfx.vLine(trackLeft,  centerY - 2, centerY + 2, 0x55FFFFFF);
        gfx.vLine(trackRight, centerY - 2, centerY + 2, 0x55FFFFFF);

        // Clamp icon to track
        int iconX = Math.max(trackLeft + iconSz, Math.min(trackRight - iconSz, bossScrX));

        // Mini boss sprite
        String bossId = state.bossId;
        if (bossId != null && !bossId.isEmpty()) {
            int frame = (state.bossAnimCounter / 4) % 4;
            try {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                gfx.blit(bossTex(bossId),
                        iconX - iconSz, centerY - iconSz,
                        iconSz * 2, iconSz * 2,
                        frame * 64f, 0f, 64, 64, 256, 256);
                RenderSystem.disableBlend();
            } catch (Exception e) {
                gfx.fill(iconX - 3, centerY - 3, iconX + 3, centerY + 3, phaseCol);
            }
        } else {
            gfx.fill(iconX - 3, centerY - 3, iconX + 3, centerY + 3, phaseCol);
        }

        // Subtle phase-coloured outline only around the moving icon (not the whole panel)
        gfx.hLine(iconX - iconSz - 1, iconX + iconSz, centerY - iconSz - 1, phaseCol & 0xAAFFFFFF);
        gfx.hLine(iconX - iconSz - 1, iconX + iconSz, centerY + iconSz,     phaseCol & 0xAAFFFFFF);
        gfx.vLine(iconX - iconSz - 1, centerY - iconSz - 1, centerY + iconSz, phaseCol & 0xAAFFFFFF);
        gfx.vLine(iconX + iconSz,     centerY - iconSz - 1, centerY + iconSz, phaseCol & 0xAAFFFFFF);

        // HP bar under the icon (fills only within its icon-width slot)
        if (state.bossMaxHp > 0) {
            float frac = Math.max(0f, (float) state.bossHp / state.bossMaxHp);
            int   barY = centerY + iconSz + 1;
            gfx.fill(iconX - iconSz,                        barY, iconX + iconSz, barY + 2, 0xFF0A0A18);
            gfx.fill(iconX - iconSz, barY, iconX - iconSz + (int)(iconSz * 2 * frac), barY + 2, phaseCol);
        }

        // Active spell name — right-aligned inside the panel, small and dim
        if (state.activeSpellCard && !state.spellName.isEmpty()) {
            String label  = state.spellName;
            int    maxW   = panelRight - iconX - iconSz - 8;
            while (maxW > 0 && font.width(label) > maxW && label.length() > 1)
                label = label.substring(0, label.length() - 1);
            if (!label.equals(state.spellName)) label += "…";
            gfx.drawString(font, label, panelRight - font.width(label) - 4,
                    centerY - font.lineHeight / 2, 0x88FFDD88, false);
        }
    }

    // ---------------------------------------------------------------- laser rendering

    /**
     * Renders all active lasers. Each laser is drawn as a rotated rectangle using the
     * GuiGraphics PoseStack so the beam can point in any direction.
     *
     * Warning phase: thin semi-transparent line with pulsing alpha — player-safe.
     * Firing phase:  wide opaque beam with soft outer glow and bright white core.
     *
     * The scissor region must be set by the caller to clip to the arena bounds.
     */
    private static void renderLasers(GuiGraphics gfx, ClientArenaState state,
                                      int ox, int oy, float sx, float sy) {
        LaserPool pool = state.lasers;
        // Length long enough to always reach the far edge of the arena from any origin
        float diag = (float) Math.sqrt((double)(BulletPool.ARENA_W * BulletPool.ARENA_W)
                                     + (double)(BulletPool.ARENA_H * BulletPool.ARENA_H));
        int length = (int)(diag * (sx + sy) * 0.5f) + 8;

        for (int i = 0; i < LaserPool.CAPACITY; i++) {
            if (!pool.isActive(i)) continue;

            float angle   = pool.getAngle(i);
            float hwArena = pool.getHalfWidth(i);
            boolean warn  = pool.isWarning(i);
            int typeId    = pool.getTypeId(i);
            int baseColor = BulletType.fromId(typeId).color & 0x00FFFFFF;

            int screenX = ox + (int)(pool.getX(i) * sx);
            int screenY = oy + (int)(pool.getY(i) * sy);

            // Scale half-width from arena units to screen pixels
            float hwScreen = Math.max(warn ? 1f : 2f, hwArena * (sx + sy) * 0.5f);
            int hw = (int) hwScreen;

            int color;
            if (warn) {
                // Pulse: flicker the alpha using warnTicksLeft
                int warnLeft  = pool.getWarnLeft(i);
                int pulse     = (warnLeft / 4) % 2 == 0 ? 0x99 : 0x44;
                color = (pulse << 24) | baseColor;
            } else {
                color = 0xFF000000 | baseColor;
            }

            // Rotation math (screen-Y-down, ZP axis):
            //   With ZP.rotationDegrees(d), local +Y maps to screen direction (sin d, cos d).
            //   To fire in arena direction (cos a, sin a) where a=0 is screen-right:
            //   solve sin(d)=cos(a) → d = 90 - angleDeg.
            float rotDeg  = 90f - (float) Math.toDegrees(angle);
            boolean isNDL = pool.isBidir(i);
            // NDL beams extend in both directions; Spark/directional only extend forward.
            int yStart = isNDL ? -length : 0;

            gfx.pose().pushPose();
            gfx.pose().translate(screenX, screenY, 0.0);
            gfx.pose().mulPose(Axis.ZP.rotationDegrees(rotDeg));

            if (!warn) {
                // Outer glow
                gfx.fill(-(hw + 4), yStart, hw + 4, length, (0x33 << 24) | baseColor);
                // Main beam
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

    // ---------------------------------------------------------------- bullet rendering

    /**
     * Renders a single enemy bullet. GOLD and SPARK types get a multi-layer glow effect;
     * all other types fall back to a plain filled square.
     */
    private static void renderBullet(GuiGraphics gfx, BulletType type, int cx, int cy, int r) {
        if (type == BulletType.SPARK) {
            // Bright spark: large outer glow + bright white core
            gfx.fill(cx - r - 2, cy - r - 2, cx + r + 2, cy + r + 2, 0x55FFEE44);
            gfx.fill(cx - r,     cy - r,     cx + r,     cy + r,     type.color);
            gfx.fill(cx - 1,     cy - 1,     cx + 1,     cy + 1,     0xFFFFFFFF);
        } else if (type == BulletType.GOLD) {
            // Gold star: subtle outer glow + solid centre
            gfx.fill(cx - r - 1, cy - r - 1, cx + r + 1, cy + r + 1, 0x44FFCC00);
            gfx.fill(cx - r,     cy - r,     cx + r,     cy + r,     type.color);
            gfx.fill(cx - 1,     cy - 1,     cx + 1,     cy + 1,     0xFFFFFF88);
        } else {
            gfx.fill(cx - r, cy - r, cx + r, cy + r, type.color);
        }
    }

    // ---------------------------------------------------------------- character sprite rendering

    /**
     * Renders the player character from the 256×296 sprite sheet centred on (cx, cy).
     * Sheet layout: 8 columns × 32 px wide, rows are 47 px tall.
     *   Row 0 (v=0):  idle animation (8 frames)
     *   Row 1 (v=47): left lean transition (frame 0 = near-idle, frame 7 = full left)
     *   Row 2 (v=94): right lean transition
     * Animation state is read from {@link ClientArenaState}.
     * Falls back to a white square if the texture is missing.
     */
    private static void renderCharacterSprite(GuiGraphics gfx, String characterId,
                                               int cx, int cy, int halfSz) {
        ResourceLocation tex = charTex(characterId);
        ClientArenaState state = ClientArenaState.INSTANCE;
        int col   = (state.animRow == 0) ? state.animIdleFrame : state.animLeanFrame;
        float u   = col * 32f;
        float v   = state.animRow * 47f;
        // Preserve the 32:47 aspect ratio; width = halfSz*2, height scaled accordingly
        int dstW  = halfSz * 2;
        int dstH  = (int)(dstW * 47f / 32f);
        try {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            gfx.blit(tex, cx - halfSz, cy - dstH / 2, dstW, dstH, u, v, 32, 47, 256, 296);
            RenderSystem.disableBlend();
        } catch (Exception e) {
            // Texture missing — plain white square fallback
            gfx.fill(cx - halfSz, cy - halfSz, cx + halfSz, cy + halfSz, 0xCCFFFFFF);
        }
    }

    // ---------------------------------------------------------------- item rendering

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
            gfx.blit(ITEM_TEXTURES[type],
                    cx - halfSz, cy - halfSz, size, size,
                    0, 0, 16, 16, 16, 16);
        } else {
            // Unknown type - plain tinted square fallback
            int color = ItemPool.colorOf(type);
            gfx.fill(cx - halfSz, cy - halfSz, cx + halfSz, cy + halfSz, color);
            gfx.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xCCFFFFFF);
        }
    }

    // ---------------------------------------------------------------- enemy rendering

    /**
     * Renders a single enemy.
     * Uses {@link EnemyType#textureIdx} to pick from the 4-entry texture array,
     * and {@link EnemyType#large} to scale the sprite up ~1.75× for large variants.
     * Falls back to a tinted rectangle when the PNG is missing.
     */
    private static void renderEnemy(GuiGraphics gfx, int typeId, int cx, int cy, float scale) {
        EnemyType type  = EnemyType.fromId(typeId);
        // Small fairies render at a minimum of 8 px halfSz (16×16 on screen).
        // Large variants scale up a further 1.75× with a 14 px minimum.
        // Hitbox is independent of visual size - only the player gets the tiny hitbox.
        float sizeMult  = type.large ? 1.75f : 1.0f;
        int minHalf     = type.large ? 14 : 8;
        int halfSz      = Math.max(minHalf, (int)(12 * sizeMult * scale));
        int texIdx      = type.textureIdx;

        if (texIdx >= 0 && texIdx < ENEMY_TEXTURES.length) {
            int size = halfSz * 2;
            gfx.blit(ENEMY_TEXTURES[texIdx],
                    cx - halfSz, cy - halfSz, size, size,
                    0, 0, 32, 32, 32, 32);
        } else {
            int color = (texIdx >= 0 && texIdx < ENEMY_COLORS.length)
                    ? ENEMY_COLORS[texIdx] : 0xFFAAAAAA;
            gfx.fill(cx - halfSz, cy - halfSz, cx + halfSz, cy + halfSz, color);
            gfx.fill(cx - 2, cy - 2, cx + 2, cy + 2, 0xCCFFFFFF);
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Strict cull - used for bullets and items which should vanish at the arena edge. */
    private static boolean outOfArena(float x, float y) {
        return x < 0 || x > BulletPool.ARENA_W || y < 0 || y > BulletPool.ARENA_H;
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
