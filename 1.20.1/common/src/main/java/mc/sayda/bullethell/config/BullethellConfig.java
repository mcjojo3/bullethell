package mc.sayda.bullethell.config;

import java.util.function.Supplier;

import mc.sayda.bullethell.arena.DifficultyConfig;

/**
 * Runtime gameplay tuning mirrored from Forge TOML / Fabric JSON (see sister
 * project creraces).
 * Defaults are used until the platform loader calls {@code apply} and rebinds
 * the suppliers.
 * <p>
 * <b>Major groups (Forge TOML / Fabric JSON)</b>: {@code WaveTiming},
 * {@code DifficultyTuning},
 * {@code FairyEnemyAi}, {@code FairyRush}, {@code BossDifficulty},
 * {@code PatternDefaults},
 * {@code ItemCollectibles}, {@code Combat}, {@code VictoryXp}.
 * <p>
 * <b>Wave schedule (non-redundant roles)</b>
 * <ul>
 * <li>{@link #waveTimingMult} - divisor on stage designer ticks
 * ({@code spawnTick}, {@code startTick}, and the
 * baked gap after each wave). Higher values compress the whole schedule (waves
 * land sooner in arena time).</li>
 * <li>{@link #effectiveSpeedMult} / {@link #effectiveDensityMult} - scale the
 * base {@link DifficultyConfig} enum
 * multipliers per difficulty (global "retune Lunatic" without editing
 * code).</li>
 * <li>Procedural fairy rush only: {@link #fairyRushDurationHintScale} scales
 * how long each catalog wave is assumed to
 * run before the next slot; {@link #fairyRushGapBreathingScale} scales the
 * catalog-derived <em>rest gap</em>
 * after that (from the stage's gap curve). Together they change the mix of
 * "wave time" vs "gap time" without
 * editing JSON; they do not replace {@link #waveTimingMult}.</li>
 * <li>{@link #fairyRushIntensityBias} shifts which catalog intensity band
 * (0-10) is targeted.
 * {@link #fairyCatalogIntensityWeightMultiplier} on Hard+ boosts <em>selection
 * weight</em> of high-intensity
 * entries when intensity is already above the threshold - complementary, not
 * duplicate.</li>
 * </ul>
 */
public final class BullethellConfig {

    private BullethellConfig() {
    }

    /**
     * Divisor when baking designer ticks into the live schedule
     * ({@link mc.sayda.bullethell.arena.ArenaContext#buildScheduledList}).
     * Higher = more compression (lower tick values), so wave spawns and procedural
     * slots land sooner. Separate from
     * {@link DifficultyConfig} bullet density/speed.
     */
    public static final float DEF_WAVE_TIMING_EASY = 3.0f;
    public static final float DEF_WAVE_TIMING_NORMAL = 3.0f;
    public static final float DEF_WAVE_TIMING_HARD = 3.0f;
    public static final float DEF_WAVE_TIMING_LUNATIC = 3.0f;

    public static Supplier<Float> WAVE_TIMING_EASY = () -> DEF_WAVE_TIMING_EASY;
    public static Supplier<Float> WAVE_TIMING_NORMAL = () -> DEF_WAVE_TIMING_NORMAL;
    public static Supplier<Float> WAVE_TIMING_HARD = () -> DEF_WAVE_TIMING_HARD;
    public static Supplier<Float> WAVE_TIMING_LUNATIC = () -> DEF_WAVE_TIMING_LUNATIC;

    public static float waveTimingMult(DifficultyConfig difficulty) {
        if (difficulty == DifficultyConfig.EASY) {
            return WAVE_TIMING_EASY.get();
        }
        if (difficulty == DifficultyConfig.NORMAL) {
            return WAVE_TIMING_NORMAL.get();
        }
        if (difficulty == DifficultyConfig.HARD) {
            return WAVE_TIMING_HARD.get();
        }
        if (difficulty == DifficultyConfig.LUNATIC) {
            return WAVE_TIMING_LUNATIC.get();
        }
        return WAVE_TIMING_NORMAL.get();
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

    // ---- Fairy / wave enemy attack AI ({@link
    // mc.sayda.bullethell.arena.ArenaContext#tickEnemyAI}) ----

    public static final int DEF_FAIRY_MIN_ATTACK_INTERVAL_TICKS = 10;
    public static final int DEF_FAIRY_AIMED_BURST_CAP = 3;
    public static final int DEF_FAIRY_AIMED_BURST_CAP_LUNATIC = 4;
    public static final int DEF_FAIRY_SPREAD_BURST_CAP = 4;
    public static final int DEF_FAIRY_SPREAD_BURST_CAP_LUNATIC = 5;
    public static final int DEF_FAIRY_STREAM_COOLDOWN_DIVISOR = 3;
    public static final int DEF_FAIRY_STREAM_COOLDOWN_MIN_TICKS = 5;
    public static final float DEF_FAIRY_BULLET_COUNT_MULT = 1.0f;
    public static final float DEF_FAIRY_ATTACK_INTERVAL_MULT = 1.0f;

    public static Supplier<Integer> FAIRY_MIN_ATTACK_INTERVAL_TICKS = () -> DEF_FAIRY_MIN_ATTACK_INTERVAL_TICKS;
    public static Supplier<Integer> FAIRY_AIMED_BURST_CAP = () -> DEF_FAIRY_AIMED_BURST_CAP;
    public static Supplier<Integer> FAIRY_AIMED_BURST_CAP_LUNATIC = () -> DEF_FAIRY_AIMED_BURST_CAP_LUNATIC;
    public static Supplier<Integer> FAIRY_SPREAD_BURST_CAP = () -> DEF_FAIRY_SPREAD_BURST_CAP;
    public static Supplier<Integer> FAIRY_SPREAD_BURST_CAP_LUNATIC = () -> DEF_FAIRY_SPREAD_BURST_CAP_LUNATIC;
    public static Supplier<Integer> FAIRY_STREAM_COOLDOWN_DIVISOR = () -> DEF_FAIRY_STREAM_COOLDOWN_DIVISOR;
    public static Supplier<Integer> FAIRY_STREAM_COOLDOWN_MIN_TICKS = () -> DEF_FAIRY_STREAM_COOLDOWN_MIN_TICKS;
    public static Supplier<Float> FAIRY_BULLET_COUNT_MULT = () -> DEF_FAIRY_BULLET_COUNT_MULT;
    public static Supplier<Float> FAIRY_ATTACK_INTERVAL_MULT = () -> DEF_FAIRY_ATTACK_INTERVAL_MULT;

    public static int fairyAimedBurstCap(DifficultyConfig difficulty) {
        return difficulty == DifficultyConfig.LUNATIC
                ? FAIRY_AIMED_BURST_CAP_LUNATIC.get()
                : FAIRY_AIMED_BURST_CAP.get();
    }

    public static int fairySpreadBurstCap(DifficultyConfig difficulty) {
        return difficulty == DifficultyConfig.LUNATIC
                ? FAIRY_SPREAD_BURST_CAP_LUNATIC.get()
                : FAIRY_SPREAD_BURST_CAP.get();
    }

    // ---- Procedural fairy rush ({@link
    // mc.sayda.bullethell.arena.ArenaContext#appendProceduralFairyRush}) ----

    /**
     * Scales the catalog gap curve's rest period between procedural waves (after
     * duration hint). Not the optional
     * breather mini-waves - only the gap ticks from {@code gapTicks*} /
     * {@link mc.sayda.bullethell.boss.FairyRushDefinition}.
     * Lower = shorter pauses. Defaults may match across difficulties; keys stay
     * split for per-difficulty tuning.
     */
    public static final float DEF_FAIRY_RUSH_GAP_BREATHING_EASY = 0.3f;
    public static final float DEF_FAIRY_RUSH_GAP_BREATHING_NORMAL = 0.3f;
    public static final float DEF_FAIRY_RUSH_GAP_BREATHING_HARD = 0.3f;
    public static final float DEF_FAIRY_RUSH_GAP_BREATHING_LUNATIC = 0.3f;

    /**
     * Scales the estimated "this wave is still going" window (catalog
     * {@code durationHintTicks} or a default from
     * enemy list) before the inter-wave gap is applied. Lower = assume waves clear
     * faster → next wave sooner.
     */
    public static final float DEF_FAIRY_RUSH_DURATION_HINT_EASY = 0.3f;
    public static final float DEF_FAIRY_RUSH_DURATION_HINT_NORMAL = 0.3f;
    public static final float DEF_FAIRY_RUSH_DURATION_HINT_HARD = 0.3f;
    public static final float DEF_FAIRY_RUSH_DURATION_HINT_LUNATIC = 0.3f;

    /**
     * Added to the stage's procedural intensity window {@code [lo,hi]} (clamped
     * 0-10) before catalog picks.
     */
    public static final int DEF_FAIRY_RUSH_INTENSITY_BIAS_EASY = -1;
    public static final int DEF_FAIRY_RUSH_INTENSITY_BIAS_NORMAL = 0;
    public static final int DEF_FAIRY_RUSH_INTENSITY_BIAS_HARD = 1;
    public static final int DEF_FAIRY_RUSH_INTENSITY_BIAS_LUNATIC = 2;

    /**
     * Catalog entry field {@code intensity}: at or above this value, and only on
     * Hard+, {@link #fairyCatalogIntensityWeightMultiplier}
     * multiplies pick weight (does not change the {@code [iLo,iHi]} window - use
     * {@link #fairyRushIntensityBias} for that).
     */
    public static final int DEF_FAIRY_CATALOG_INTENSITY_THRESHOLD = 5;
    /**
     * On Hard+, for entries with {@code intensity >= threshold}: weight multiplier
     * is {@code 1 + k * this} where
     * {@code k} is 1 on Hard, 2 on Lunatic (steeper bias toward intense rows on
     * higher difficulties).
     */
    public static final float DEF_FAIRY_CATALOG_INTENSITY_BOOST_PER_STEP = 0.08f;

    public static Supplier<Float> FAIRY_RUSH_GAP_BREATHING_EASY = () -> DEF_FAIRY_RUSH_GAP_BREATHING_EASY;
    public static Supplier<Float> FAIRY_RUSH_GAP_BREATHING_NORMAL = () -> DEF_FAIRY_RUSH_GAP_BREATHING_NORMAL;
    public static Supplier<Float> FAIRY_RUSH_GAP_BREATHING_HARD = () -> DEF_FAIRY_RUSH_GAP_BREATHING_HARD;
    public static Supplier<Float> FAIRY_RUSH_GAP_BREATHING_LUNATIC = () -> DEF_FAIRY_RUSH_GAP_BREATHING_LUNATIC;

    public static Supplier<Float> FAIRY_RUSH_DURATION_HINT_EASY = () -> DEF_FAIRY_RUSH_DURATION_HINT_EASY;
    public static Supplier<Float> FAIRY_RUSH_DURATION_HINT_NORMAL = () -> DEF_FAIRY_RUSH_DURATION_HINT_NORMAL;
    public static Supplier<Float> FAIRY_RUSH_DURATION_HINT_HARD = () -> DEF_FAIRY_RUSH_DURATION_HINT_HARD;
    public static Supplier<Float> FAIRY_RUSH_DURATION_HINT_LUNATIC = () -> DEF_FAIRY_RUSH_DURATION_HINT_LUNATIC;

    public static Supplier<Integer> FAIRY_RUSH_INTENSITY_BIAS_EASY = () -> DEF_FAIRY_RUSH_INTENSITY_BIAS_EASY;
    public static Supplier<Integer> FAIRY_RUSH_INTENSITY_BIAS_NORMAL = () -> DEF_FAIRY_RUSH_INTENSITY_BIAS_NORMAL;
    public static Supplier<Integer> FAIRY_RUSH_INTENSITY_BIAS_HARD = () -> DEF_FAIRY_RUSH_INTENSITY_BIAS_HARD;
    public static Supplier<Integer> FAIRY_RUSH_INTENSITY_BIAS_LUNATIC = () -> DEF_FAIRY_RUSH_INTENSITY_BIAS_LUNATIC;

    public static Supplier<Integer> FAIRY_CATALOG_INTENSITY_THRESHOLD = () -> DEF_FAIRY_CATALOG_INTENSITY_THRESHOLD;
    public static Supplier<Float> FAIRY_CATALOG_INTENSITY_BOOST_PER_STEP = () -> DEF_FAIRY_CATALOG_INTENSITY_BOOST_PER_STEP;

    public static float fairyRushGapBreathingScale(DifficultyConfig difficulty) {
        if (difficulty == DifficultyConfig.EASY) {
            return FAIRY_RUSH_GAP_BREATHING_EASY.get();
        }
        if (difficulty == DifficultyConfig.NORMAL) {
            return FAIRY_RUSH_GAP_BREATHING_NORMAL.get();
        }
        if (difficulty == DifficultyConfig.HARD) {
            return FAIRY_RUSH_GAP_BREATHING_HARD.get();
        }
        if (difficulty == DifficultyConfig.LUNATIC) {
            return FAIRY_RUSH_GAP_BREATHING_LUNATIC.get();
        }
        return FAIRY_RUSH_GAP_BREATHING_NORMAL.get();
    }

    public static float fairyRushDurationHintScale(DifficultyConfig difficulty) {
        if (difficulty == DifficultyConfig.EASY) {
            return FAIRY_RUSH_DURATION_HINT_EASY.get();
        }
        if (difficulty == DifficultyConfig.NORMAL) {
            return FAIRY_RUSH_DURATION_HINT_NORMAL.get();
        }
        if (difficulty == DifficultyConfig.HARD) {
            return FAIRY_RUSH_DURATION_HINT_HARD.get();
        }
        if (difficulty == DifficultyConfig.LUNATIC) {
            return FAIRY_RUSH_DURATION_HINT_LUNATIC.get();
        }
        return FAIRY_RUSH_DURATION_HINT_NORMAL.get();
    }

    public static int fairyRushIntensityBias(DifficultyConfig difficulty) {
        if (difficulty == DifficultyConfig.EASY) {
            return FAIRY_RUSH_INTENSITY_BIAS_EASY.get();
        }
        if (difficulty == DifficultyConfig.NORMAL) {
            return FAIRY_RUSH_INTENSITY_BIAS_NORMAL.get();
        }
        if (difficulty == DifficultyConfig.HARD) {
            return FAIRY_RUSH_INTENSITY_BIAS_HARD.get();
        }
        if (difficulty == DifficultyConfig.LUNATIC) {
            return FAIRY_RUSH_INTENSITY_BIAS_LUNATIC.get();
        }
        return FAIRY_RUSH_INTENSITY_BIAS_NORMAL.get();
    }

    /**
     * Multiplier on a catalog row's {@code weight} when difficulty is Hard or
     * Lunatic and {@code entryIntensity}
     * meets the configured threshold; Easy/Normal always get {@code 1}.
     */
    public static float fairyCatalogIntensityWeightMultiplier(DifficultyConfig difficulty, int entryIntensity) {
        int thr = FAIRY_CATALOG_INTENSITY_THRESHOLD.get();
        if (difficulty.ordinal() >= DifficultyConfig.HARD.ordinal() && entryIntensity >= thr) {
            float step = FAIRY_CATALOG_INTENSITY_BOOST_PER_STEP.get();
            return 1f + step * (difficulty.ordinal() - DifficultyConfig.HARD.ordinal() + 1);
        }
        return 1f;
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
     * @deprecated No longer used — LUNATIC boss scaling is now raw-JSON baseline
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
