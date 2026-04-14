package mc.sayda.bullethell.arena;

/** Difficulty settings passed into PatternEngine calls. */
public enum DifficultyConfig {

    EASY   (0.70f, 4,  0.60f),
    NORMAL (1.00f, 6,  1.00f),
    HARD   (1.35f, 8,  1.48f),
    LUNATIC(1.85f, 14, 2.48f);

    /** Multiplier applied to bullet speed. */
    public final float speedMult;

    /** Number of spiral arms on the default spiral pattern. */
    public final int spiralArms;

    /** Multiplier applied to bullet density / count in spread patterns. */
    public final float densityMult;

    DifficultyConfig(float speedMult, int spiralArms, float densityMult) {
        this.speedMult   = speedMult;
        this.spiralArms  = spiralArms;
        this.densityMult = densityMult;
    }

    private static final DifficultyConfig[] VALUES = values();

    public static DifficultyConfig fromId(int id) {
        return (id >= 0 && id < VALUES.length) ? VALUES[id] : NORMAL;
    }
}
