package mc.sayda.bullethell.config;

import java.util.function.Supplier;

import mc.sayda.bullethell.arena.DifficultyConfig;

/**
 * Runtime gameplay tuning mirrored from Forge TOML / Fabric JSON (see sister
 * project creraces).
 * Defaults are used until the platform loader calls {@code apply} and rebinds
 * the suppliers.
 * <p>
 * <b>Major groups (Forge TOML / Fabric JSON)</b>: {@code DifficultyTuning},
 * {@code BossDifficulty}, {@code PatternDefaults},
 * {@code ItemCollectibles}, {@code Combat}, {@code VictoryXp}.
 * <p>
 * {@link #effectiveSpeedMult} / {@link #effectiveDensityMult} scale the base
 * {@link DifficultyConfig} enum multipliers per difficulty (global "retune
 * Lunatic" without editing code).
 */
public final class BullethellConfig {

    private BullethellConfig() {
    }

    // ---- Difficulty multipliers (applied on top of {@link DifficultyConfig} enum
    // values everywhere tuning is used) ----

    public static final float DEF_DIFFICULTY_SPEED_TUNER_EASY = 1.0f;
    public static final float DEF_DIFFICULTY_SPEED_TUNER_NORMAL = 1.0f;
    public static final float DEF_DIFFICULTY_SPEED_TUNER_HARD = 1.0f;
    public static final float DEF_DIFFICULTY_SPEED_TUNER_LUNATIC = 1.0f;

    public static final float DEF_DIFFICULTY_DENSITY_TUNER_EASY = 1.0f;
    public static final float DEF_DIFFICULTY_DENSITY_TUNER_NORMAL = 1.0f;
    public static final float DEF_DIFFICULTY_DENSITY_TUNER_HARD = 1.0f;
    public static final float DEF_DIFFICULTY_DENSITY_TUNER_LUNATIC = 1.0f;

    public static Supplier<Float> DIFFICULTY_SPEED_TUNER_EASY = () -> DEF_DIFFICULTY_SPEED_TUNER_EASY;
    public static Supplier<Float> DIFFICULTY_SPEED_TUNER_NORMAL = () -> DEF_DIFFICULTY_SPEED_TUNER_NORMAL;
    public static Supplier<Float> DIFFICULTY_SPEED_TUNER_HARD = () -> DEF_DIFFICULTY_SPEED_TUNER_HARD;
    public static Supplier<Float> DIFFICULTY_SPEED_TUNER_LUNATIC = () -> DEF_DIFFICULTY_SPEED_TUNER_LUNATIC;

    public static Supplier<Float> DIFFICULTY_DENSITY_TUNER_EASY = () -> DEF_DIFFICULTY_DENSITY_TUNER_EASY;
    public static Supplier<Float> DIFFICULTY_DENSITY_TUNER_NORMAL = () -> DEF_DIFFICULTY_DENSITY_TUNER_NORMAL;
    public static Supplier<Float> DIFFICULTY_DENSITY_TUNER_HARD = () -> DEF_DIFFICULTY_DENSITY_TUNER_HARD;
    public static Supplier<Float> DIFFICULTY_DENSITY_TUNER_LUNATIC = () -> DEF_DIFFICULTY_DENSITY_TUNER_LUNATIC;

    public static float difficultySpeedTuner(DifficultyConfig difficulty) {
        if (difficulty == DifficultyConfig.EASY) {
            return DIFFICULTY_SPEED_TUNER_EASY.get();
        }
        if (difficulty == DifficultyConfig.NORMAL) {
            return DIFFICULTY_SPEED_TUNER_NORMAL.get();
        }
        if (difficulty == DifficultyConfig.HARD) {
            return DIFFICULTY_SPEED_TUNER_HARD.get();
        }
        if (difficulty == DifficultyConfig.LUNATIC) {
            return DIFFICULTY_SPEED_TUNER_LUNATIC.get();
        }
        return DIFFICULTY_SPEED_TUNER_NORMAL.get();
    }

    public static float difficultyDensityTuner(DifficultyConfig difficulty) {
        if (difficulty == DifficultyConfig.EASY) {
            return DIFFICULTY_DENSITY_TUNER_EASY.get();
        }
        if (difficulty == DifficultyConfig.NORMAL) {
            return DIFFICULTY_DENSITY_TUNER_NORMAL.get();
        }
        if (difficulty == DifficultyConfig.HARD) {
            return DIFFICULTY_DENSITY_TUNER_HARD.get();
        }
        if (difficulty == DifficultyConfig.LUNATIC) {
            return DIFFICULTY_DENSITY_TUNER_LUNATIC.get();
        }
        return DIFFICULTY_DENSITY_TUNER_NORMAL.get();
    }

    /**
     * {@link DifficultyConfig#speedMult} × per-difficulty tuner (before global
     * enemy bullet mult).
     */
    public static float effectiveSpeedMult(DifficultyConfig difficulty) {
        return difficulty.speedMult * difficultySpeedTuner(difficulty);
    }

    /** {@link DifficultyConfig#densityMult} × per-difficulty tuner. */
    public static float effectiveDensityMult(DifficultyConfig difficulty) {
        return difficulty.densityMult * difficultyDensityTuner(difficulty);
    }

    // ---- Boss pattern scaling (on top of {@link DifficultyConfig#densityMult} /
    // {@link DifficultyConfig#speedMult}; those stay in the enum)

    /** Max additive phase creep for boss bullet density (added to 1.0). */
    public static final float DEF_BOSS_PHASE_DENSITY_CAP = 0.30f;
    /** Boss phase index multiplier before capping density creep. */
    public static final float DEF_BOSS_PHASE_DENSITY_PER_PHASE = 0.034f;
    public static final float DEF_BOSS_PHASE_SPEED_CAP = 0.22f;
    public static final float DEF_BOSS_PHASE_SPEED_PER_PHASE = 0.026f;
    /**
     * @deprecated No longer used - LUNATIC boss scaling is now raw-JSON baseline
     *             (Option D). Kept only so existing config files don't break.
     */
    @Deprecated
    public static final float DEF_BOSS_LUNATIC_DENSITY_EXTRA = 1.12f;
    /** @deprecated See {@link #DEF_BOSS_LUNATIC_DENSITY_EXTRA}. */
    @Deprecated
    public static final float DEF_BOSS_LUNATIC_SPEED_EXTRA = 1.10f;
    /** Clamp for ring-arm scaling vs density in AIMED_RING boss attacks. */
    public static final float DEF_BOSS_RING_DENSITY_CAP = 1.35f;
    public static final int DEF_BOSS_RING_ARMS_MAX = 20;
    /** Floor for LASER_BEAM boss pattern cooldown after density scaling. */
    public static final int DEF_BOSS_LASER_BEAM_MIN_COOLDOWN = 8;

    public static Supplier<Float> BOSS_PHASE_DENSITY_CAP = () -> DEF_BOSS_PHASE_DENSITY_CAP;
    public static Supplier<Float> BOSS_PHASE_DENSITY_PER_PHASE = () -> DEF_BOSS_PHASE_DENSITY_PER_PHASE;
    public static Supplier<Float> BOSS_PHASE_SPEED_CAP = () -> DEF_BOSS_PHASE_SPEED_CAP;
    public static Supplier<Float> BOSS_PHASE_SPEED_PER_PHASE = () -> DEF_BOSS_PHASE_SPEED_PER_PHASE;
    /** @deprecated No longer read by boss scaling. Config key retained for backward compat. */
    @Deprecated
    public static Supplier<Float> BOSS_LUNATIC_DENSITY_EXTRA = () -> DEF_BOSS_LUNATIC_DENSITY_EXTRA;
    /** @deprecated See {@link #BOSS_LUNATIC_DENSITY_EXTRA}. */
    @Deprecated
    public static Supplier<Float> BOSS_LUNATIC_SPEED_EXTRA = () -> DEF_BOSS_LUNATIC_SPEED_EXTRA;
    public static Supplier<Float> BOSS_RING_DENSITY_CAP = () -> DEF_BOSS_RING_DENSITY_CAP;
    public static Supplier<Integer> BOSS_RING_ARMS_MAX = () -> DEF_BOSS_RING_ARMS_MAX;
    public static Supplier<Integer> BOSS_LASER_BEAM_MIN_COOLDOWN = () -> DEF_BOSS_LASER_BEAM_MIN_COOLDOWN;

    // ---- Pattern defaults ({@link mc.sayda.bullethell.pattern.PatternEngine} when
    // JSON omits lifetime / spread) ----

    public static final int DEF_PATTERN_DEFAULT_LIFE_RING = 200;
    public static final int DEF_PATTERN_DEFAULT_LIFE_AIMED = 220;
    public static final int DEF_PATTERN_DEFAULT_LIFE_RAIN = 230;
    public static final float DEF_PATTERN_DEFAULT_LASER_BEAM_SPREAD_RAD = 0.04f;

    public static Supplier<Integer> PATTERN_DEFAULT_LIFE_RING = () -> DEF_PATTERN_DEFAULT_LIFE_RING;
    public static Supplier<Integer> PATTERN_DEFAULT_LIFE_AIMED = () -> DEF_PATTERN_DEFAULT_LIFE_AIMED;
    public static Supplier<Integer> PATTERN_DEFAULT_LIFE_RAIN = () -> DEF_PATTERN_DEFAULT_LIFE_RAIN;
    public static Supplier<Float> PATTERN_DEFAULT_LASER_BEAM_SPREAD_RAD = () -> DEF_PATTERN_DEFAULT_LASER_BEAM_SPREAD_RAD;

    // ---- Collectible item drops ({@link mc.sayda.bullethell.arena.ItemPool}) ----

    public static final int DEF_ITEM_COLLECTIBLE_LIFE_TICKS = 400;
    public static final float DEF_ITEM_ATTRACT_SPEED = 16.0f;

    public static Supplier<Integer> ITEM_COLLECTIBLE_LIFE_TICKS = () -> DEF_ITEM_COLLECTIBLE_LIFE_TICKS;
    public static Supplier<Float> ITEM_ATTRACT_SPEED = () -> DEF_ITEM_ATTRACT_SPEED;

    /**
     * Multiplier on enemy / fairy / boss bullet speed after
     * {@link DifficultyConfig#speedMult} (and boss-specific
     * scaling). Applied in {@link mc.sayda.bullethell.pattern.PatternEngine} and
     * {@link mc.sayda.bullethell.arena.ArenaContext}
     * spawns. Slightly below {@code 1} slows all patterns without editing JSON.
     */
    public static final float DEF_GLOBAL_ENEMY_BULLET_SPEED_MULT = 1.0f;
    public static Supplier<Float> GLOBAL_ENEMY_BULLET_SPEED_MULT = () -> DEF_GLOBAL_ENEMY_BULLET_SPEED_MULT;

    /**
     * Speed factor for enemy / fairy / boss bullets: {@link #effectiveSpeedMult} ×
     * {@link #GLOBAL_ENEMY_BULLET_SPEED_MULT}.
     */
    public static float enemyBulletSpeedFactor(DifficultyConfig difficulty) {
        return effectiveSpeedMult(difficulty) * GLOBAL_ENEMY_BULLET_SPEED_MULT.get();
    }

    // ---- Victory XP (Minecraft experience points; tunable in Forge TOML / Fabric
    // JSON) ----

    /**
     * Clear reward XP:
     * {@code floor(min(max, base + sqrt(score) * sqrtMult * diffMult))} - see
     * {@link mc.sayda.bullethell.arena.VictoryXpRewards}.
     */
    public static final int DEF_VICTORY_XP_BASE = 12;
    public static final double DEF_VICTORY_XP_SQRT_MULT = 0.22;
    public static final int DEF_VICTORY_XP_MAX = 420;
    public static final double DEF_VICTORY_XP_MULT_EASY = 0.82;
    public static final double DEF_VICTORY_XP_MULT_NORMAL = 0.95;
    public static final double DEF_VICTORY_XP_MULT_HARD = 1.06;
    public static final double DEF_VICTORY_XP_MULT_LUNATIC = 1.18;

    public static Supplier<Integer> VICTORY_XP_BASE = () -> DEF_VICTORY_XP_BASE;
    public static Supplier<Double> VICTORY_XP_SQRT_MULT = () -> DEF_VICTORY_XP_SQRT_MULT;
    public static Supplier<Integer> VICTORY_XP_MAX = () -> DEF_VICTORY_XP_MAX;
    public static Supplier<Double> VICTORY_XP_MULT_EASY = () -> DEF_VICTORY_XP_MULT_EASY;
    public static Supplier<Double> VICTORY_XP_MULT_NORMAL = () -> DEF_VICTORY_XP_MULT_NORMAL;
    public static Supplier<Double> VICTORY_XP_MULT_HARD = () -> DEF_VICTORY_XP_MULT_HARD;
    public static Supplier<Double> VICTORY_XP_MULT_LUNATIC = () -> DEF_VICTORY_XP_MULT_LUNATIC;

    public static double victoryXpDifficultyMult(DifficultyConfig difficulty) {
        if (difficulty == DifficultyConfig.EASY) {
            return VICTORY_XP_MULT_EASY.get();
        }
        if (difficulty == DifficultyConfig.NORMAL) {
            return VICTORY_XP_MULT_NORMAL.get();
        }
        if (difficulty == DifficultyConfig.HARD) {
            return VICTORY_XP_MULT_HARD.get();
        }
        if (difficulty == DifficultyConfig.LUNATIC) {
            return VICTORY_XP_MULT_LUNATIC.get();
        }
        return VICTORY_XP_MULT_NORMAL.get();
    }

    // ---- Test-mode dev path ----

    /**
     * Filesystem directory from which {@code /bullethell test} loads boss/stage
     * JSONs.
     * Empty string (default) = classpath only. Set in Forge TOML / Fabric JSON to
     * point at
     * your working {@code src/main/resources/data/bullethell/bosses/} folder for
     * hot-reload.
     */
    public static final String DEF_TEST_DEV_PATH = "";
    public static java.util.function.Supplier<String> TEST_DEV_PATH = () -> DEF_TEST_DEV_PATH;
}
