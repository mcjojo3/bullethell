package mc.sayda.bullethell.arena;

import java.util.Locale;

import mc.sayda.bullethell.boss.CharacterDefinition;
import mc.sayda.bullethell.pattern.BulletType;

/**
 * TH-inspired playable shot layouts (power tiers 0–4 from {@link PlayerState2D#powerLevel()}).
 * Style ids are set in {@code data/bullethell/characters/&lt;id&gt;.json} as {@link CharacterDefinition#shotStyle}.
 * See wiki/player-shots-and-references.md for Touhou sources.
 * <p>
 * High power tiers intentionally cap at ~5 unfocused / ~5 focused main streams (plus homing
 * extras) so fairy-heavy stages do not saturate {@link BulletPool#PLAYER_CAPACITY} or
 * obscure enemy bullets; pair with {@link PlayerState2D#SHOT_COOLDOWN_NORMAL}.
 * <p>
 * Vertical shot speed is scaled (~1.92×) toward TH06-style fast streams (e.g. Reimu ofuda /
 * Marisa missile pairs at 0 power).
 */
public final class PlayerShotPatterns {

    private PlayerShotPatterns() {}

    /** Scales arena-units/tick velocities to approximate TH06 player shot speed on 20 tps arena. */
    private static final float SHOT_SPEED_SCALE = 1.92f;

    /**
     * Visual scale for player shots: {@code 1} so sprites match the same {@link BulletType} as
     * enemy bullets; collision uses {@link BulletPool} {@code hitScale} only (Touhou-style
     * generous hitboxes) — see {@link #PLAYER_HIT_BROAD} / {@link #PLAYER_HIT_NARROW}.
     */
    private static final float PLAYER_VIS = 1f;
    /** Default hit scale for most player patterns (vis unchanged). */
    private static final float PLAYER_HIT_BROAD = 2.75f;
    /** Narrow streams (needles): smaller collision than {@link #PLAYER_HIT_BROAD}. */
    private static final float PLAYER_HIT_NARROW = 1.22f;

    private static void shot(BulletPool pb, float px, float py, float vx, float vy, int type, int life) {
        shot(pb, px, py, vx, vy, type, life, PLAYER_VIS, PLAYER_HIT_BROAD);
    }

    private static void shot(BulletPool pb, float px, float py, float vx, float vy, int type, int life,
            float visScale, float hitScale) {
        pb.spawn(px, py, vx * SHOT_SPEED_SCALE, vy * SHOT_SPEED_SCALE, type, life, visScale, hitScale, 0f);
    }

    public static void fire(PlayerState2D ps, BulletPool pb, CharacterDefinition def) {
        String style = def.shotStyle == null ? "generic" : def.shotStyle.trim().toLowerCase(Locale.ROOT);
        int lv = ps.powerLevel();
        float px = ps.x;
        float py = ps.y - 4f;

        if (ps.focused) {
            switch (style) {
                case "th06_reimu_homing" -> fireReimuHomingFocus(px, py, lv, pb);
                case "th06_reimu_needle" -> fireReimuNeedleFocus(px, py, lv, pb);
                case "th06_marisa_missile" -> fireMarisaMissileFocus(px, py, lv, pb);
                case "th06_marisa_laser" -> fireMarisaLaserFocus(px, py, lv, pb);
                case "sakuya_knife" -> fireSakuyaFocus(px, py, lv, pb);
                case "cirno_ice" -> fireCirnoFocus(px, py, lv, pb);
                case "sanae_wide" -> fireSanaeFocus(px, py, lv, pb);
                default -> fireGenericFocus(px, py, lv, pb);
            }
        } else {
            switch (style) {
                case "th06_reimu_homing" -> fireReimuHomingNormal(px, py, lv, pb);
                case "th06_reimu_needle" -> fireReimuNeedleNormal(px, py, lv, pb);
                case "th06_marisa_missile" -> fireMarisaMissileNormal(px, py, lv, pb);
                case "th06_marisa_laser" -> fireMarisaLaserNormal(px, py, lv, pb);
                case "sakuya_knife" -> fireSakuyaNormal(px, py, lv, pb);
                case "cirno_ice" -> fireCirnoNormal(px, py, lv, pb);
                case "sanae_wide" -> fireSanaeNormal(px, py, lv, pb);
                default -> fireGenericNormal(px, py, lv, pb);
            }
        }
    }

    // ---------------------------------------------------------------- TH06 Reimu A — homing amulet style (AMULET + tickHomingBullets)

    private static void fireReimuHomingNormal(float px, float py, int lv, BulletPool pb) {
        int h = BulletType.AMULET.getId();
        int life = 70;
        switch (lv) {
            case 0 -> shot(pb,px, py, 0f, -14f, h, life);
            case 1 -> {
                shot(pb,px, py, 0f, -14f, h, life);
                shot(pb,px - 10, py, -1.2f, -13f, h, life);
                shot(pb,px + 10, py, 1.2f, -13f, h, life);
            }
            case 2 -> {
                shot(pb,px, py, 0f, -14f, h, life);
                shot(pb,px - 12, py, -1.4f, -13f, h, life);
                shot(pb,px + 12, py, 1.4f, -13f, h, life);
                shot(pb,px - 22, py, -2.2f, -12f, h, life);
                shot(pb,px + 22, py, 2.2f, -12f, h, life);
            }
            case 3 -> {
                shot(pb,px, py, 0f, -14f, h, life);
                shot(pb,px - 12, py, -1.4f, -13f, h, life);
                shot(pb,px + 12, py, 1.4f, -13f, h, life);
                shot(pb,px - 22, py, -2.2f, -12f, h, life);
                shot(pb,px + 22, py, 2.2f, -12f, h, life);
            }
            default -> {
                shot(pb,px, py, 0f, -14f, h, life);
                shot(pb,px - 12, py, -1.4f, -13f, h, life);
                shot(pb,px + 12, py, 1.4f, -13f, h, life);
                shot(pb,px - 22, py, -2.2f, -12f, h, life);
                shot(pb,px + 22, py, 2.2f, -12f, h, life);
            }
        }
    }

    private static void fireReimuHomingFocus(float px, float py, int lv, BulletPool pb) {
        int h = BulletType.AMULET.getId();
        int life = 85;
        switch (lv) {
            case 0 -> shot(pb,px, py, 0f, -18f, h, life);
            case 1 -> {
                shot(pb,px, py, 0f, -18f, h, life);
                shot(pb,px - 6, py, 0f, -17f, h, life);
                shot(pb,px + 6, py, 0f, -17f, h, life);
            }
            case 2 -> {
                shot(pb,px, py, 0f, -18f, h, life);
                shot(pb,px - 6, py, 0f, -18f, h, life);
                shot(pb,px + 6, py, 0f, -18f, h, life);
                shot(pb,px - 12, py, 0f, -17f, h, life);
                shot(pb,px + 12, py, 0f, -17f, h, life);
            }
            case 3 -> {
                shot(pb,px, py, 0f, -18f, h, life);
                shot(pb,px - 6, py, 0f, -18f, h, life);
                shot(pb,px + 6, py, 0f, -18f, h, life);
                shot(pb,px - 12, py, 0f, -17f, h, life);
                shot(pb,px + 12, py, 0f, -17f, h, life);
            }
            default -> {
                shot(pb,px, py, 0f, -18f, h, life);
                shot(pb,px - 6, py, 0f, -18f, h, life);
                shot(pb,px + 6, py, 0f, -18f, h, life);
                shot(pb,px - 12, py, 0f, -17f, h, life);
                shot(pb,px + 12, py, 0f, -17f, h, life);
            }
        }
    }

    // ---------------------------------------------------------------- TH06 Reimu B — persuasion needle (forward power, narrow)

    private static void fireReimuNeedleNormal(float px, float py, int lv, BulletPool pb) {
        int n = BulletType.NEEDLE.getId();
        int life = 55;
        float vs = PLAYER_VIS;
        float hs = PLAYER_HIT_NARROW;
        switch (lv) {
            case 0 -> shot(pb,px, py, 0f, -17f, n, life, vs, hs);
            case 1 -> {
                shot(pb,px, py, 0f, -17f, n, life, vs, hs);
                shot(pb,px - 7, py, -1.8f, -16f, n, life, vs, hs);
                shot(pb,px + 7, py, 1.8f, -16f, n, life, vs, hs);
            }
            case 2 -> {
                shot(pb,px, py, 0f, -17f, n, life, vs, hs);
                shot(pb,px - 8, py, -2f, -16f, n, life, vs, hs);
                shot(pb,px + 8, py, 2f, -16f, n, life, vs, hs);
                shot(pb,px - 16, py, -3.5f, -15f, n, life, vs, hs);
                shot(pb,px + 16, py, 3.5f, -15f, n, life, vs, hs);
            }
            case 3 -> {
                shot(pb,px, py, 0f, -17f, n, life, vs, hs);
                shot(pb,px - 8, py, -2f, -16f, n, life, vs, hs);
                shot(pb,px + 8, py, 2f, -16f, n, life, vs, hs);
                shot(pb,px - 16, py, -3.5f, -15f, n, life, vs, hs);
                shot(pb,px + 16, py, 3.5f, -15f, n, life, vs, hs);
            }
            default -> {
                shot(pb,px, py, 0f, -17f, n, life, vs, hs);
                shot(pb,px - 8, py, -2f, -16f, n, life, vs, hs);
                shot(pb,px + 8, py, 2f, -16f, n, life, vs, hs);
                shot(pb,px - 16, py, -3.5f, -15f, n, life, vs, hs);
                shot(pb,px + 16, py, 3.5f, -15f, n, life, vs, hs);
            }
        }
    }

    private static void fireReimuNeedleFocus(float px, float py, int lv, BulletPool pb) {
        int n = BulletType.NEEDLE.getId();
        int life = 48;
        float vs = PLAYER_VIS;
        float hs = PLAYER_HIT_NARROW;
        switch (lv) {
            case 0 -> shot(pb,px, py, 0f, -21f, n, life, vs, hs);
            case 1 -> {
                shot(pb,px, py, 0f, -21f, n, life, vs, hs);
                shot(pb,px - 4, py, 0f, -20f, n, life, vs, hs);
                shot(pb,px + 4, py, 0f, -20f, n, life, vs, hs);
            }
            case 2 -> {
                shot(pb,px, py, 0f, -21f, n, life, vs, hs);
                shot(pb,px - 4, py, 0f, -21f, n, life, vs, hs);
                shot(pb,px + 4, py, 0f, -21f, n, life, vs, hs);
                shot(pb,px - 9, py, 0f, -20f, n, life, vs, hs);
                shot(pb,px + 9, py, 0f, -20f, n, life, vs, hs);
            }
            case 3 -> {
                shot(pb,px, py, 0f, -21f, n, life, vs, hs);
                shot(pb,px - 4, py, 0f, -21f, n, life, vs, hs);
                shot(pb,px + 4, py, 0f, -21f, n, life, vs, hs);
                shot(pb,px - 9, py, 0f, -20f, n, life, vs, hs);
                shot(pb,px + 9, py, 0f, -20f, n, life, vs, hs);
            }
            default -> {
                shot(pb,px, py, 0f, -21f, n, life, vs, hs);
                shot(pb,px - 4, py, 0f, -21f, n, life, vs, hs);
                shot(pb,px + 4, py, 0f, -21f, n, life, vs, hs);
                shot(pb,px - 9, py, 0f, -20f, n, life, vs, hs);
                shot(pb,px + 9, py, 0f, -20f, n, life, vs, hs);
            }
        }
    }

    // ---------------------------------------------------------------- TH06 Marisa A — illusion missile (wide magic missiles)

    private static void fireMarisaMissileNormal(float px, float py, int lv, BulletPool pb) {
        int g = BulletType.GOLD.getId();
        int life = 58;
        switch (lv) {
            case 0 -> shot(pb,px, py, 0f, -16f, g, life);
            case 1 -> {
                shot(pb,px, py, 0f, -16f, g, life);
                shot(pb,px - 9, py, -2.2f, -15f, g, life);
                shot(pb,px + 9, py, 2.2f, -15f, g, life);
            }
            case 2 -> {
                shot(pb,px, py, 0f, -16f, g, life);
                shot(pb,px - 11, py, -2.6f, -15f, g, life);
                shot(pb,px + 11, py, 2.6f, -15f, g, life);
                shot(pb,px - 24, py, -5f, -13f, g, life);
                shot(pb,px + 24, py, 5f, -13f, g, life);
            }
            case 3 -> {
                shot(pb,px, py, 0f, -16f, g, life);
                shot(pb,px - 11, py, -2.6f, -15f, g, life);
                shot(pb,px + 11, py, 2.6f, -15f, g, life);
                shot(pb,px - 24, py, -5f, -13f, g, life);
                shot(pb,px + 24, py, 5f, -13f, g, life);
            }
            default -> {
                shot(pb,px, py, 0f, -16f, g, life);
                shot(pb,px - 11, py, -2.6f, -15f, g, life);
                shot(pb,px + 11, py, 2.6f, -15f, g, life);
                shot(pb,px - 24, py, -5f, -13f, g, life);
                shot(pb,px + 24, py, 5f, -13f, g, life);
            }
        }
    }

    private static void fireMarisaMissileFocus(float px, float py, int lv, BulletPool pb) {
        int g = BulletType.GOLD.getId();
        int s = BulletType.STAR.getId();
        int life = 50;
        switch (lv) {
            case 0 -> shot(pb,px, py, 0f, -20f, g, life);
            case 1 -> {
                shot(pb,px, py, 0f, -20f, g, life);
                shot(pb,px - 5, py, 0f, -19f, g, life);
                shot(pb,px + 5, py, 0f, -19f, g, life);
            }
            case 2 -> {
                shot(pb,px, py, 0f, -20f, g, life);
                shot(pb,px - 5, py, 0f, -20f, g, life);
                shot(pb,px + 5, py, 0f, -20f, g, life);
                shot(pb,px - 11, py, 0f, -19f, s, life);
                shot(pb,px + 11, py, 0f, -19f, s, life);
            }
            case 3 -> {
                shot(pb,px, py, 0f, -20f, g, life);
                shot(pb,px - 5, py, 0f, -20f, g, life);
                shot(pb,px + 5, py, 0f, -20f, g, life);
                shot(pb,px - 11, py, 0f, -19f, s, life);
                shot(pb,px + 11, py, 0f, -19f, s, life);
            }
            default -> {
                shot(pb,px, py, 0f, -20f, g, life);
                shot(pb,px - 5, py, 0f, -20f, g, life);
                shot(pb,px + 5, py, 0f, -20f, g, life);
                shot(pb,px - 11, py, 0f, -19f, s, life);
                shot(pb,px + 11, py, 0f, -19f, s, life);
            }
        }
    }

    // ---------------------------------------------------------------- TH06 Marisa B — narrow “laser” stream (fast STAR columns)

    private static void fireMarisaLaserNormal(float px, float py, int lv, BulletPool pb) {
        int s = BulletType.STAR.getId();
        int life = 52;
        switch (lv) {
            case 0 -> shot(pb,px, py, 0f, -19f, s, life);
            case 1 -> {
                shot(pb,px - 3, py, 0f, -19f, s, life);
                shot(pb,px + 3, py, 0f, -19f, s, life);
            }
            case 2 -> {
                shot(pb,px - 5, py, 0f, -19f, s, life);
                shot(pb,px, py, 0f, -19.5f, s, life);
                shot(pb,px + 5, py, 0f, -19f, s, life);
            }
            case 3 -> {
                shot(pb,px - 8, py, 0f, -19f, s, life);
                shot(pb,px - 3, py, 0f, -19.5f, s, life);
                shot(pb,px + 3, py, 0f, -19.5f, s, life);
                shot(pb,px + 8, py, 0f, -19f, s, life);
            }
            default -> {
                shot(pb,px - 10, py, 0f, -19f, s, life);
                shot(pb,px - 4, py, 0f, -19.5f, s, life);
                shot(pb,px + 4, py, 0f, -19.5f, s, life);
                shot(pb,px + 10, py, 0f, -19f, s, life);
            }
        }
    }

    private static void fireMarisaLaserFocus(float px, float py, int lv, BulletPool pb) {
        int s = BulletType.STAR.getId();
        int life = 46;
        switch (lv) {
            case 0 -> shot(pb,px, py, 0f, -22f, s, life);
            case 1 -> {
                shot(pb,px - 2, py, 0f, -22f, s, life);
                shot(pb,px + 2, py, 0f, -22f, s, life);
            }
            case 2 -> {
                shot(pb,px - 4, py, 0f, -22f, s, life);
                shot(pb,px, py, 0f, -22.5f, s, life);
                shot(pb,px + 4, py, 0f, -22f, s, life);
            }
            case 3 -> {
                shot(pb,px - 6, py, 0f, -22f, s, life);
                shot(pb,px - 2, py, 0f, -22.5f, s, life);
                shot(pb,px + 2, py, 0f, -22.5f, s, life);
                shot(pb,px + 6, py, 0f, -22f, s, life);
            }
            default -> {
                shot(pb,px - 8, py, 0f, -22f, s, life);
                shot(pb,px - 3, py, 0f, -22.5f, s, life);
                shot(pb,px + 3, py, 0f, -22.5f, s, life);
                shot(pb,px + 8, py, 0f, -22f, s, life);
            }
        }
    }

    // ---------------------------------------------------------------- Sakuya — knives (time-stop interaction unchanged)

    private static void fireSakuyaNormal(float px, float py, int lv, BulletPool pb) {
        int k = BulletType.KNIFE.getId();
        int life = 56;
        switch (lv) {
            case 0 -> shot(pb,px, py, 0f, -16f, k, life);
            case 1 -> {
                shot(pb,px, py, 0f, -16f, k, life);
                shot(pb,px - 10, py, -1.8f, -15f, k, life);
                shot(pb,px + 10, py, 1.8f, -15f, k, life);
                // Second row directly under centre knife for the “two-row at lvl1” feel.
                shot(pb,px, py, 0f, -14f, k, life);
            }
            case 2 -> {
                shot(pb,px, py, 0f, -16f, k, life);
                shot(pb,px - 11, py, -2f, -15f, k, life);
                shot(pb,px + 11, py, 2f, -15f, k, life);
                shot(pb,px - 22, py, -4f, -14f, k, life);
                shot(pb,px + 22, py, 4f, -14f, k, life);
            }
            case 3 -> {
                shot(pb,px, py, 0f, -16f, k, life);
                shot(pb,px - 11, py, -2f, -15f, k, life);
                shot(pb,px + 11, py, 2f, -15f, k, life);
                shot(pb,px - 22, py, -4f, -14f, k, life);
                shot(pb,px + 22, py, 4f, -14f, k, life);
            }
            default -> {
                shot(pb,px, py, 0f, -16f, k, life);
                shot(pb,px - 11, py, -2f, -15f, k, life);
                shot(pb,px + 11, py, 2f, -15f, k, life);
                shot(pb,px - 22, py, -4f, -14f, k, life);
                shot(pb,px + 22, py, 4f, -14f, k, life);
            }
        }
    }

    private static void fireSakuyaFocus(float px, float py, int lv, BulletPool pb) {
        int k = BulletType.KNIFE.getId();
        int u = BulletType.KUNAI.getId();
        int life = 48;
        switch (lv) {
            case 0 -> shot(pb,px, py, 0f, -19f, k, life);
            case 1 -> {
                shot(pb,px - 4, py, 0f, -19f, k, life);
                shot(pb,px + 4, py, 0f, -19f, k, life);
                // Focused Sakuya also gets a subtle second row at tier 1.
                shot(pb,px, py, 0f, -18f, k, life);
            }
            case 2 -> {
                shot(pb,px - 5, py, 0f, -19f, k, life);
                shot(pb,px + 5, py, 0f, -19f, k, life);
                shot(pb,px, py, 0f, -20f, u, life);
            }
            case 3 -> {
                shot(pb,px - 6, py, 0f, -19f, k, life);
                shot(pb,px + 6, py, 0f, -19f, k, life);
                shot(pb,px - 12, py, 0f, -18f, u, life);
                shot(pb,px + 12, py, 0f, -18f, u, life);
            }
            default -> {
                shot(pb,px - 6, py, 0f, -19f, k, life);
                shot(pb,px + 6, py, 0f, -19f, k, life);
                shot(pb,px - 12, py, 0f, -19f, u, life);
                shot(pb,px + 12, py, 0f, -19f, u, life);
                shot(pb,px, py, 0f, -20f, k, life);
            }
        }
    }

    // ---------------------------------------------------------------- Cirno — ice shards

    private static void fireCirnoNormal(float px, float py, int lv, BulletPool pb) {
        int ice = BulletType.ICE.getId();
        int life = 54;
        switch (lv) {
            case 0 -> shot(pb,px, py, 0f, -16f, ice, life);
            case 1 -> {
                shot(pb,px, py, 0f, -16f, ice, life);
                shot(pb,px - 9, py, -2.4f, -15f, ice, life);
                shot(pb,px + 9, py, 2.4f, -15f, ice, life);
            }
            case 2 -> {
                shot(pb,px, py, 0f, -16f, ice, life);
                shot(pb,px - 10, py, -2.6f, -15f, ice, life);
                shot(pb,px + 10, py, 2.6f, -15f, ice, life);
                shot(pb,px - 20, py, -4.8f, -13f, ice, life);
                shot(pb,px + 20, py, 4.8f, -13f, ice, life);
            }
            case 3 -> {
                shot(pb,px, py, 0f, -16f, ice, life);
                shot(pb,px - 10, py, -2.6f, -15f, ice, life);
                shot(pb,px + 10, py, 2.6f, -15f, ice, life);
                shot(pb,px - 20, py, -4.8f, -13f, ice, life);
                shot(pb,px + 20, py, 4.8f, -13f, ice, life);
            }
            default -> {
                shot(pb,px, py, 0f, -16f, ice, life);
                shot(pb,px - 10, py, -2.6f, -15f, ice, life);
                shot(pb,px + 10, py, 2.6f, -15f, ice, life);
                shot(pb,px - 20, py, -4.8f, -13f, ice, life);
                shot(pb,px + 20, py, 4.8f, -13f, ice, life);
            }
        }
    }

    private static void fireCirnoFocus(float px, float py, int lv, BulletPool pb) {
        int ice = BulletType.ICE.getId();
        int life = 46;
        switch (lv) {
            case 0 -> shot(pb,px, py, 0f, -20f, ice, life);
            case 1 -> {
                shot(pb,px - 4, py, 0f, -20f, ice, life);
                shot(pb,px + 4, py, 0f, -20f, ice, life);
            }
            case 2 -> {
                shot(pb,px - 5, py, 0f, -20f, ice, life);
                shot(pb,px + 5, py, 0f, -20f, ice, life);
                shot(pb,px, py, 0f, -21f, ice, life);
            }
            case 3 -> {
                shot(pb,px - 6, py, 0f, -20f, ice, life);
                shot(pb,px + 6, py, 0f, -20f, ice, life);
                shot(pb,px - 11, py, 0f, -19f, ice, life);
                shot(pb,px + 11, py, 0f, -19f, ice, life);
            }
            default -> {
                shot(pb,px - 6, py, 0f, -20f, ice, life);
                shot(pb,px + 6, py, 0f, -20f, ice, life);
                shot(pb,px - 11, py, 0f, -19f, ice, life);
                shot(pb,px + 11, py, 0f, -19f, ice, life);
                shot(pb,px, py, 0f, -21f, ice, life);
            }
        }
    }

    // ---------------------------------------------------------------- Sanae — wide wind / snake inspired (not TH06; uses RICE + STAR)

    private static void fireSanaeNormal(float px, float py, int lv, BulletPool pb) {
        int r = BulletType.RICE.getId();
        int s = BulletType.STAR.getId();
        int life = 56;
        switch (lv) {
            case 0 -> shot(pb,px, py, 0f, -16f, r, life);
            case 1 -> {
                shot(pb,px - 6, py, -1.5f, -15f, r, life);
                shot(pb,px + 6, py, 1.5f, -15f, r, life);
                shot(pb,px, py, 0f, -16f, s, life);
            }
            case 2 -> {
                shot(pb,px - 12, py, -2.8f, -14f, r, life);
                shot(pb,px + 12, py, 2.8f, -14f, r, life);
                shot(pb,px, py, 0f, -16f, s, life);
                shot(pb,px - 24, py, -5f, -12f, r, life);
                shot(pb,px + 24, py, 5f, -12f, r, life);
            }
            case 3 -> {
                shot(pb,px - 12, py, -2.8f, -14f, r, life);
                shot(pb,px + 12, py, 2.8f, -14f, r, life);
                shot(pb,px, py, 0f, -16f, s, life);
                shot(pb,px - 24, py, -5f, -12f, r, life);
                shot(pb,px + 24, py, 5f, -12f, r, life);
            }
            default -> {
                shot(pb,px - 12, py, -2.8f, -14f, r, life);
                shot(pb,px + 12, py, 2.8f, -14f, r, life);
                shot(pb,px, py, 0f, -16f, s, life);
                shot(pb,px - 24, py, -5f, -12f, r, life);
                shot(pb,px + 24, py, 5f, -12f, r, life);
            }
        }
    }

    private static void fireSanaeFocus(float px, float py, int lv, BulletPool pb) {
        int r = BulletType.RICE.getId();
        int s = BulletType.STAR.getId();
        int life = 48;
        switch (lv) {
            case 0 -> shot(pb,px, py, 0f, -19f, r, life);
            case 1 -> {
                shot(pb,px - 5, py, 0f, -19f, r, life);
                shot(pb,px + 5, py, 0f, -19f, r, life);
            }
            case 2 -> {
                shot(pb,px - 6, py, 0f, -19f, r, life);
                shot(pb,px + 6, py, 0f, -19f, r, life);
                shot(pb,px, py, 0f, -20f, s, life);
            }
            case 3 -> {
                shot(pb,px - 7, py, 0f, -19f, r, life);
                shot(pb,px + 7, py, 0f, -19f, r, life);
                shot(pb,px - 13, py, 0f, -18f, r, life);
                shot(pb,px + 13, py, 0f, -18f, r, life);
            }
            default -> {
                shot(pb,px - 7, py, 0f, -19f, r, life);
                shot(pb,px + 7, py, 0f, -19f, r, life);
                shot(pb,px - 13, py, 0f, -18f, r, life);
                shot(pb,px + 13, py, 0f, -18f, r, life);
                shot(pb,px, py, 0f, -20f, s, life);
            }
        }
    }

    // ---------------------------------------------------------------- Generic (legacy green shot)

    private static void fireGenericNormal(float px, float py, int lv, BulletPool pb) {
        int t = BulletType.PLAYER_SHOT.getId();
        switch (lv) {
            case 0 -> shot(pb,px, py, 0f, -16f, t, 55);
            case 1 -> {
                shot(pb,px, py, 0f, -16f, t, 55);
                shot(pb,px - 8, py, -1.6f, -16f, t, 55);
                shot(pb,px + 8, py, 1.6f, -16f, t, 55);
            }
            case 2 -> {
                shot(pb,px, py, 0f, -16f, t, 55);
                shot(pb,px - 10, py, -2.0f, -16f, t, 55);
                shot(pb,px + 10, py, 2.0f, -16f, t, 55);
                shot(pb,px - 22, py, -4.4f, -14f, t, 55);
                shot(pb,px + 22, py, 4.4f, -14f, t, 55);
            }
            case 3 -> {
                shot(pb,px, py, 0f, -16f, t, 55);
                shot(pb,px - 10, py, -2.0f, -16f, t, 55);
                shot(pb,px + 10, py, 2.0f, -16f, t, 55);
                shot(pb,px - 22, py, -4.4f, -14f, t, 55);
                shot(pb,px + 22, py, 4.4f, -14f, t, 55);
            }
            default -> {
                shot(pb,px, py, 0f, -16f, t, 55);
                shot(pb,px - 10, py, -2.0f, -16f, t, 55);
                shot(pb,px + 10, py, 2.0f, -16f, t, 55);
                shot(pb,px - 22, py, -4.4f, -14f, t, 55);
                shot(pb,px + 22, py, 4.4f, -14f, t, 55);
            }
        }
    }

    private static void fireGenericFocus(float px, float py, int lv, BulletPool pb) {
        int t = BulletType.PLAYER_SHOT.getId();
        switch (lv) {
            case 0 -> shot(pb,px, py, 0f, -20f, t, 45);
            case 1 -> {
                shot(pb,px, py, 0f, -20f, t, 45);
                shot(pb,px - 5, py, 0f, -18f, t, 45);
                shot(pb,px + 5, py, 0f, -18f, t, 45);
            }
            case 2 -> {
                shot(pb,px, py, 0f, -20f, t, 45);
                shot(pb,px - 5, py, 0f, -20f, t, 45);
                shot(pb,px + 5, py, 0f, -20f, t, 45);
                shot(pb,px - 13, py, 0f, -18f, t, 45);
                shot(pb,px + 13, py, 0f, -18f, t, 45);
            }
            case 3 -> {
                shot(pb,px, py, 0f, -20f, t, 45);
                shot(pb,px - 5, py, 0f, -20f, t, 45);
                shot(pb,px + 5, py, 0f, -20f, t, 45);
                shot(pb,px - 13, py, 0f, -18f, t, 45);
                shot(pb,px + 13, py, 0f, -18f, t, 45);
            }
            default -> {
                shot(pb,px, py, 0f, -20f, t, 45);
                shot(pb,px - 5, py, 0f, -20f, t, 45);
                shot(pb,px + 5, py, 0f, -20f, t, 45);
                shot(pb,px - 13, py, 0f, -18f, t, 45);
                shot(pb,px + 13, py, 0f, -18f, t, 45);
            }
        }
    }
}
