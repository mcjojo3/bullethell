package mc.sayda.bullethell.arena;

/** Difficulty settings passed into PatternEngine calls. */
public enum DifficultyConfig {

    EASY   (0.90f, 0.50f, 2.5f, 1.5f, 0.85f),
    NORMAL (0.95f, 0.65f, 2.0f, 1.25f, 0.90f),
    HARD   (1.00f, 0.85f, 1.5f, 1.1f,  0.95f),
    LUNATIC(1.00f, 1.00f, 1.0f, 1.0f,  1.00f);

    /** Multiplier applied to bullet speed. */
    public final float speedMult;

    /** Multiplier applied to bullet density / count in spread patterns. */
    public final float densityMult;

    /**
     * Scale applied to scalar {@code warnTicks} values so lower difficulties
     * receive longer laser warning windows. Tier arrays in JSON override this.
     * LUNATIC = 1.0 (JSON value is used as-is).
     */
    public final float warnTicksScale;

    /**
     * Scale applied to scalar {@code spread} values so lower difficulties
     * receive wider aimed-pattern spreads (less punishing aim tracking).
     * Tier arrays in JSON override this. LUNATIC = 1.0 (JSON value is used as-is).
     */
    public final float spreadScale;

    /**
     * Scale applied to scalar {@code hp} values so lower difficulties
     * have reduced phase health. Tier arrays in JSON override this.
     * LUNATIC = 1.0 (JSON value is used as-is).
     */
    public final float healthScale;

    DifficultyConfig(float speedMult, float densityMult, float warnTicksScale, float spreadScale, float healthScale) {
        this.speedMult      = speedMult;
        this.densityMult    = densityMult;
        this.warnTicksScale = warnTicksScale;
        this.spreadScale    = spreadScale;
        this.healthScale    = healthScale;
    }

    private static final DifficultyConfig[] VALUES = values();

    public static DifficultyConfig fromId(int id) {
        return (id >= 0 && id < VALUES.length) ? VALUES[id] : NORMAL;
    }
}
