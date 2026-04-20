package mc.sayda.bullethell.client;

import mc.sayda.bullethell.arena.DifficultyConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Draws the two debug panels (left: paged item list, right: live stats) over the arena HUD
 * when {@link ClientArenaState#testMode} is true. No Minecraft Screen is used - panels
 * are pure HUD overlays rendered from the RENDER_HUD event.
 *
 * Input (scroll / click / keys) is handled by {@link mc.sayda.bullethell.client.screen.ArenaPlayScreen}
 * which delegates here for hit-testing.
 */
@Environment(EnvType.CLIENT)
public final class TestModeHud {

    private TestModeHud() {}

    // Panel geometry (public so ArenaPlayScreen can use them)
    public static final int LEFT_W   = 160;
    public static final int RIGHT_W  = 188;
    public static final int ITEM_H   = 12;
    public static final int ITEM_PAD = 1;
    public static final int TAB_H    = 16; // height of the tab bar row

    // Page indices
    public static final int PAGE_BOSS  = 0;
    public static final int PAGE_STAGE = 1;
    public static final int PAGE_WAVE  = 2;
    public static final int PAGE_CHAR  = 3;
    public static final int PAGE_COUNT = 4;

    private static final String[] TAB_LABELS = { "BOSS", "STAGE", "WAVE", "CHAR" };

    // Colours
    private static final int PANEL_BG    = 0xBB0A0A18;
    private static final int DIVIDER     = 0xFF223355;
    private static final int ACCENT      = 0xFFAADDFF;
    private static final int TEXT        = 0xFFBBBBBB;
    private static final int TEXT_DIM    = 0xFF778899;
    private static final int SEL_BG      = 0xAA2244AA;
    private static final int HP_BAR_BG   = 0xFF2A2A2A;
    private static final int HP_BAR_FG   = 0xFF33BB55;
    private static final int TAB_BG_ACT  = 0xFF1A3060;
    private static final int TAB_BG_IDLE = 0xFF0A0A1A;
    private static final int TAB_BORDER  = 0xFF334466;

    // ---------------------------------------------------------------- public API

    public static void draw(GuiGraphics gfx, float partialTick) {
        ClientArenaState state = ClientArenaState.INSTANCE;
        if (!state.testMode) return;
        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        Font font = mc.font;
        drawLeftPanel(gfx, state, font, sh);
        drawRightPanel(gfx, state, font, sw, sh);
    }

    // ---------------------------------------------------------------- hit-testing helpers

    public static boolean isInLeftPanel(double x, double y) {
        return x >= 0 && x < LEFT_W;
    }

    public static boolean isInRightPanel(double x, double y, int screenW) {
        return x >= screenW - RIGHT_W;
    }

    /** Returns tab index [0, PAGE_COUNT) if the click is inside the tab bar, else -1. */
    public static int tabIndexFromClick(double x, double y) {
        if (y >= 0 && y < TAB_H && x >= 0 && x < LEFT_W) {
            int tabW = LEFT_W / PAGE_COUNT;
            int idx = (int)(x / tabW);
            return Math.min(idx, PAGE_COUNT - 1);
        }
        return -1;
    }

    /**
     * Converts a Y coordinate inside the left panel to a list index.
     * Returns -1 if the click is in the tab bar or above the list area.
     */
    public static int leftPanelHitIndex(double y, int scrollOffset) {
        int relY = (int) y - (TAB_H + 2); // below tab bar + divider
        if (relY < 0) return -1;
        return scrollOffset + relY / (ITEM_H + ITEM_PAD);
    }

    // ---------------------------------------------------------------- per-page state accessors

    public static List<String> getPageList(ClientArenaState s) {
        return switch (s.testPage) {
            case PAGE_STAGE -> s.testStageIds;
            case PAGE_WAVE  -> s.testWaveIds;
            case PAGE_CHAR  -> s.testCharIds;
            default         -> s.testBossIds;
        };
    }

    public static int getPageScroll(ClientArenaState s) {
        return switch (s.testPage) {
            case PAGE_STAGE -> s.testStageScrollOffset;
            case PAGE_WAVE  -> s.testWaveScrollOffset;
            case PAGE_CHAR  -> s.testCharScrollOffset;
            default         -> s.testScrollOffset;
        };
    }

    public static void setPageScroll(ClientArenaState s, int v) {
        switch (s.testPage) {
            case PAGE_STAGE -> s.testStageScrollOffset = v;
            case PAGE_WAVE  -> s.testWaveScrollOffset  = v;
            case PAGE_CHAR  -> s.testCharScrollOffset  = v;
            default         -> s.testScrollOffset      = v;
        }
    }

    public static int getPageSelected(ClientArenaState s) {
        return switch (s.testPage) {
            case PAGE_STAGE -> s.testStageSelectedIdx;
            case PAGE_WAVE  -> s.testWaveSelectedIdx;
            case PAGE_CHAR  -> s.testCharSelectedIdx;
            default         -> s.testSelectedIdx;
        };
    }

    public static void setPageSelected(ClientArenaState s, int v) {
        switch (s.testPage) {
            case PAGE_STAGE -> s.testStageSelectedIdx = v;
            case PAGE_WAVE  -> s.testWaveSelectedIdx  = v;
            case PAGE_CHAR  -> s.testCharSelectedIdx  = v;
            default         -> s.testSelectedIdx      = v;
        }
    }

    public static String getPageCurrentId(ClientArenaState s) {
        return switch (s.testPage) {
            case PAGE_STAGE -> s.testCurrentStageId;
            case PAGE_WAVE  -> s.testCurrentWaveId;
            case PAGE_CHAR  -> s.testCurrentCharId;
            default         -> s.testCurrentBossId;
        };
    }

    // ---------------------------------------------------------------- left panel

    private static void drawLeftPanel(GuiGraphics gfx, ClientArenaState state, Font font, int sh) {
        gfx.fill(0, 0, LEFT_W, sh, PANEL_BG);
        gfx.fill(LEFT_W - 1, 0, LEFT_W, sh, DIVIDER);

        // Tab bar
        int tabW = LEFT_W / PAGE_COUNT;
        for (int i = 0; i < PAGE_COUNT; i++) {
            int tx = i * tabW;
            int tw = (i == PAGE_COUNT - 1) ? LEFT_W - 1 - tx : tabW;
            boolean active = (state.testPage == i);
            gfx.fill(tx, 0, tx + tw, TAB_H, active ? TAB_BG_ACT : TAB_BG_IDLE);
            gfx.fill(tx + tw, 0, tx + tw + 1, TAB_H, TAB_BORDER);
            int labelX = tx + (tw - font.width(TAB_LABELS[i])) / 2;
            gfx.drawString(font, TAB_LABELS[i], labelX, (TAB_H - 8) / 2 + 1, active ? ACCENT : TEXT_DIM, false);
        }
        gfx.fill(0, TAB_H, LEFT_W - 1, TAB_H + 1, DIVIDER);

        List<String> ids   = getPageList(state);
        int scroll         = getPageScroll(state);
        int selectedIdx    = getPageSelected(state);
        String currentId   = getPageCurrentId(state);

        if (ids.isEmpty()) {
            gfx.drawString(font, "(none)", 5, TAB_H + 4, TEXT_DIM, false);
            return;
        }

        int listY0  = TAB_H + 2;
        int visCount = Math.max(1, (sh - listY0) / (ITEM_H + ITEM_PAD));

        // Clamp scroll so the selected item stays visible
        if (selectedIdx < scroll) scroll = selectedIdx;
        if (selectedIdx >= scroll + visCount) scroll = selectedIdx - visCount + 1;
        scroll = Math.max(0, Math.min(scroll, ids.size() - visCount));
        setPageScroll(state, scroll);

        int y = listY0;
        for (int i = scroll; i < ids.size() && i < scroll + visCount; i++) {
            String id  = ids.get(i);
            boolean sel = id.equals(currentId);
            if (sel) {
                gfx.fill(0, y - 1, LEFT_W - 1, y + ITEM_H, SEL_BG);
                setPageSelected(state, i);
            }
            int col    = sel ? 0xFFFFFFFF : TEXT;
            String prefix = sel ? "> " : "  ";
            String label  = fitWidth(prefix + id, LEFT_W - 8, font);
            gfx.drawString(font, label, 4, y, col, false);
            y += ITEM_H + ITEM_PAD;
        }

        // Scroll bar
        if (ids.size() > visCount) {
            int trackH = sh - listY0;
            int barH   = Math.max(8, trackH * visCount / ids.size());
            int barY   = listY0 + (trackH - barH) * scroll / Math.max(1, ids.size() - visCount);
            gfx.fill(LEFT_W - 3, barY, LEFT_W - 1, barY + barH, 0xFF446688);
        }
    }

    // ---------------------------------------------------------------- right panel

    private static void drawRightPanel(GuiGraphics gfx, ClientArenaState state, Font font, int sw, int sh) {
        int x0 = sw - RIGHT_W;
        gfx.fill(x0, 0, sw, sh, PANEL_BG);
        gfx.fill(x0, 0, x0 + 1, sh, DIVIDER);

        int x  = x0 + 5;
        int y  = 4;
        final int LH = 11;

        // Header
        gfx.drawString(font, "TEST MODE", x, y, ACCENT, false); y += LH;
        gfx.fill(x0 + 1, y, sw, y + 1, DIVIDER); y += 4;

        // Context rows - adapt label to current page
        if (state.testPage == PAGE_CHAR) {
            // Character page: show current char selection and note
            row(gfx, font, x, y, "char", state.testCurrentCharId, RIGHT_W - 10); y += LH;
            row(gfx, font, x, y, "diff", DifficultyConfig.fromId(state.testCurrentDifficulty).name(), RIGHT_W - 10); y += LH;
            row(gfx, font, x, y, "boss", state.bossId.isEmpty() ? state.testCurrentBossId : state.bossId, RIGHT_W - 10); y += LH;
            y += 2;
            gfx.fill(x0 + 1, y, sw, y + 1, DIVIDER); y += 4;
            gfx.drawString(font, "Click to set char for", x, y, TEXT_DIM, false); y += LH;
            gfx.drawString(font, "next test run.", x, y, TEXT_DIM, false); y += LH;
            gfx.drawString(font, "Hit [ R ] to restart", x, y, TEXT_DIM, false); y += LH;
            gfx.drawString(font, "with new char.", x, y, TEXT_DIM, false); y += LH;
        } else if (state.testPage == PAGE_STAGE) {
            String stageId = state.testCurrentStageId.isEmpty() ? "(none)" : state.testCurrentStageId;
            row(gfx, font, x, y, "stage", stageId, RIGHT_W - 10); y += LH;
            row(gfx, font, x, y, "diff", DifficultyConfig.fromId(state.testCurrentDifficulty).name(), RIGHT_W - 10); y += LH;
            row(gfx, font, x, y, "char", state.testCurrentCharId, RIGHT_W - 10); y += LH;
            row(gfx, font, x, y, "boss", state.bossId.isEmpty() ? "-" : state.bossId, RIGHT_W - 10); y += LH;
            row(gfx, font, x, y, "phase", String.valueOf(state.bossPhase + 1), RIGHT_W - 10); y += LH;
        } else if (state.testPage == PAGE_WAVE) {
            String waveId = state.testCurrentWaveId.isEmpty() ? "(none)" : state.testCurrentWaveId;
            row(gfx, font, x, y, "wave", waveId, RIGHT_W - 10); y += LH;
            row(gfx, font, x, y, "diff", DifficultyConfig.fromId(state.testCurrentDifficulty).name(), RIGHT_W - 10); y += LH;
            row(gfx, font, x, y, "char", state.testCurrentCharId, RIGHT_W - 10); y += LH;
            y += LH; // blank line where boss/phase would be
        } else { // BOSS
            String bossId = state.bossId.isEmpty() ? state.testCurrentBossId : state.bossId;
            row(gfx, font, x, y, "boss", bossId, RIGHT_W - 10); y += LH;
            row(gfx, font, x, y, "diff", DifficultyConfig.fromId(state.testCurrentDifficulty).name(), RIGHT_W - 10); y += LH;
            row(gfx, font, x, y, "char", state.testCurrentCharId, RIGHT_W - 10); y += LH;
            row(gfx, font, x, y, "phase", String.valueOf(state.bossPhase + 1), RIGHT_W - 10); y += LH;
        }

        // Spell name
        if (!state.spellName.isEmpty()) {
            gfx.drawString(font, fitWidth(state.spellName, RIGHT_W - 8, font), x, y, 0xFFDDDD88, false);
        } else {
            gfx.drawString(font, "(nonspell)", x, y, TEXT_DIM, false);
        }
        y += LH;

        // HP bar (all non-char pages)
        if (state.testPage != PAGE_CHAR && state.bossMaxHp > 0) {
            row(gfx, font, x, y, "hp", state.bossHp + "/" + state.bossMaxHp, RIGHT_W - 10); y += LH;
            int barW   = RIGHT_W - 12;
            gfx.fill(x, y, x + barW, y + 4, HP_BAR_BG);
            int filled = barW * Math.max(0, state.bossHp) / state.bossMaxHp;
            gfx.fill(x, y, x + filled, y + 4, HP_BAR_FG);
            y += 7;
        }

        y += 2;
        gfx.fill(x0 + 1, y, sw, y + 1, DIVIDER); y += 4;

        // Live counters
        int fps = Minecraft.getInstance().getFps();
        int fpsColor = fps >= 55 ? 0xFF44FF88 : fps >= 30 ? 0xFFFFDD44 : 0xFFFF4444;
        gfx.drawString(font, "fps: ", x, y, TEXT_DIM, false);
        gfx.drawString(font, String.valueOf(fps), x + font.width("fps: "), y, fpsColor, false); y += LH;

        int pBullets = 0;
        for (mc.sayda.bullethell.arena.BulletPool pool : state.allPlayerBullets.values())
            pBullets += pool.getActiveCount();
        row(gfx, font, x, y, "e.bullets", String.valueOf(state.debugEnemyBulletCount), RIGHT_W - 10); y += LH;
        row(gfx, font, x, y, "p.bullets", String.valueOf(pBullets),                    RIGHT_W - 10); y += LH;
        row(gfx, font, x, y, "tick",      String.valueOf(state.debugArenaTick),        RIGHT_W - 10); y += LH;
        row(gfx, font, x, y, "pat cd",    String.valueOf(state.debugPatternCooldown),  RIGHT_W - 10); y += LH;

        if (state.active && state.spellTimerTotal > 0) {
            int remSec = state.spellTimerTicks / 20;
            row(gfx, font, x, y, "timer", remSec + "s / " + (state.spellTimerTotal / 20) + "s", RIGHT_W - 10); y += LH;
        }

        y += 2;
        gfx.fill(x0 + 1, y, sw, y + 1, DIVIDER); y += 4;

        // Player state
        if (state.active) {
            row(gfx, font, x, y, "lives",  String.valueOf(state.player.lives), RIGHT_W - 10); y += LH;
            row(gfx, font, x, y, "bombs",  String.valueOf(state.player.bombs), RIGHT_W - 10); y += LH;
            row(gfx, font, x, y, "power",  String.valueOf(state.power),        RIGHT_W - 10); y += LH;
            row(gfx, font, x, y, "graze",  String.valueOf(state.player.graze), RIGHT_W - 10); y += LH;
            row(gfx, font, x, y, "pos",
                    String.format("%.0f,%.0f", state.player.x, state.player.y), RIGHT_W - 10); y += LH;
        }

        y += 2;
        gfx.fill(x0 + 1, y, sw, y + 1, DIVIDER); y += 4;

        // Key hints (adapted per page)
        gfx.drawString(font, "[ R ]       reload all + restart", x, y, ACCENT, false); y += LH;
        if (state.testPage == PAGE_BOSS) {
            gfx.drawString(font, "[PgUp/Dn]   next/prev phase", x, y, TEXT_DIM, false); y += LH;
        }
        gfx.drawString(font, "[ 1-4 ]     difficulty", x, y, TEXT_DIM, false); y += LH;
        gfx.drawString(font, "[ Tab ]     next page", x, y, TEXT_DIM, false); y += LH;
        gfx.drawString(font, "[ H ]       hitboxes", x, y, TEXT_DIM, false); y += LH;
        gfx.drawString(font, "scroll      list", x, y, TEXT_DIM, false); y += LH;
        gfx.drawString(font, "click       select", x, y, TEXT_DIM, false);
    }

    // ---------------------------------------------------------------- helpers

    private static void row(GuiGraphics gfx, Font font, int x, int y, String label, String value, int maxW) {
        String key = label + ": ";
        gfx.drawString(font, key, x, y, TEXT_DIM, false);
        int valX = x + font.width(key);
        String val = fitWidth(value, maxW - font.width(key), font);
        gfx.drawString(font, val, valX, y, TEXT, false);
    }

    private static String fitWidth(String s, int maxPx, Font font) {
        if (font.width(s) <= maxPx) return s;
        while (s.length() > 1 && font.width(s + "\u2026") > maxPx)
            s = s.substring(0, s.length() - 1);
        return s + "\u2026";
    }
}
