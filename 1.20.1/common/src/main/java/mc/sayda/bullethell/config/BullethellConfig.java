package mc.sayda.bullethell.config;

import mc.sayda.bullethell.arena.DifficultyConfig;

import java.util.function.Supplier;

/**
 * Runtime gameplay tuning mirrored from Forge TOML / Fabric JSON (see sister project creraces).
 * Defaults are used until the platform loader calls {@code apply} and rebinds the suppliers.
 */
public final class BullethellConfig {

    private BullethellConfig() {
    }

    /** Wave spawn compression: higher = shorter gaps between fairy-wave spawns (see {@link mc.sayda.bullethell.arena.ArenaContext#buildScheduledList}). */
    public static final float DEF_WAVE_TIMING_EASY = 1.5f;
    public static final float DEF_WAVE_TIMING_NORMAL = 2.0f;
    public static final float DEF_WAVE_TIMING_HARD = 2.5f;
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

    // ---- Boss pattern scaling (on top of {@link DifficultyConfig#densityMult} / {@link DifficultyConfig#speedMult}; those stay in the enum)

    /** Max additive phase creep for boss bullet density (added to 1.0). */
    public static final float DEF_BOSS_PHASE_DENSITY_CAP = 0.30f;
    /** Boss phase index multiplier before capping density creep. */
    public static final float DEF_BOSS_PHASE_DENSITY_PER_PHASE = 0.034f;
    public static final float DEF_BOSS_PHASE_SPEED_CAP = 0.22f;
    public static final float DEF_BOSS_PHASE_SPEED_PER_PHASE = 0.026f;
    /** Extra density multiplier when difficulty is Lunatic only (Hard uses enum alone). */
    public static final float DEF_BOSS_LUNATIC_DENSITY_EXTRA = 1.12f;
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
    public static Supplier<Float> BOSS_LUNATIC_DENSITY_EXTRA = () -> DEF_BOSS_LUNATIC_DENSITY_EXTRA;
    public static Supplier<Float> BOSS_LUNATIC_SPEED_EXTRA = () -> DEF_BOSS_LUNATIC_SPEED_EXTRA;
    public static Supplier<Float> BOSS_RING_DENSITY_CAP = () -> DEF_BOSS_RING_DENSITY_CAP;
    public static Supplier<Integer> BOSS_RING_ARMS_MAX = () -> DEF_BOSS_RING_ARMS_MAX;
    public static Supplier<Integer> BOSS_LASER_BEAM_MIN_COOLDOWN = () -> DEF_BOSS_LASER_BEAM_MIN_COOLDOWN;

    /**
     * Multiplier on every enemy/fairy/boss bullet velocity (applied in
     * {@link mc.sayda.bullethell.pattern.PatternEngine} and direct
     * {@link mc.sayda.bullethell.arena.ArenaContext} spawns). Slightly &lt; 1.0
     * slows patterns globally without retuning every JSON speed field.
     */
    public static final float DEF_GLOBAL_ENEMY_BULLET_SPEED_MULT = 1.0f;
    public static Supplier<Float> GLOBAL_ENEMY_BULLET_SPEED_MULT = () -> DEF_GLOBAL_ENEMY_BULLET_SPEED_MULT;
}
