package mc.sayda.bullethell.arena;

/** Difficulty settings passed into PatternEngine calls. */
public enum DifficultyConfig {

    EASY   (1.5f, 1.5f),
    NORMAL (1.5f, 2.0f),
    HARD   (1.5f, 2.5f),
    LUNATIC(1.5f, 2.5f);

    /** Multiplier applied to bullet speed. */
    public final float speedMult;

    /** Multiplier applied to bullet density / count in spread patterns. */
    public final float densityMult;

    DifficultyConfig(float speedMult, float densityMult) {
        this.speedMult   = speedMult;
        this.densityMult = densityMult;
    }

    private static final DifficultyConfig[] VALUES = values();

    public static DifficultyConfig fromId(int id) {
        return (id >= 0 && id < VALUES.length) ? VALUES[id] : NORMAL;
    }
}
