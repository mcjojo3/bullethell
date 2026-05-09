package mc.sayda.bullethell.boss;

/**
 * Root-level movement tuning block for a boss, set once in the boss JSON.
 * All fields are optional — omit any to use the defaults shown here.
 *
 * JSON example:
 * <pre>
 * "movementConfig": {
 *   "reposShootTicks": 160,
 *   "reposDashTicks":  25,
 *   "reposBreathTicks": 10,
 *   "reposXMargin": 80,
 *   "reposBossY": 80,
 *   "sineFrequency": 0.018,
 *   "circleFrequency": 0.018,
 *   "circleYRatio": 0.35,
 *   "introLandY": 160,
 *   "introSlideRate": 0.09,
 *   "fightEntryTicks": 40
 * }
 * </pre>
 */
public class MovementConfig {

    // ---------------------------------------------------------------- REPOS_TOP

    /** Ticks spent shooting before repositioning. Default ~8 s. */
    public int reposShootTicks = 160;

    /** Ticks for the horizontal dash to the new position. */
    public int reposDashTicks = 25;

    /** Pause ticks after landing before shooting resumes. */
    public int reposBreathTicks = 10;

    /**
     * Explicit left bound for the random landing X (arena units).
     * {@code -1} = derive from {@link #reposXMargin}.
     */
    public float reposMinX = -1f;

    /**
     * Explicit right bound for the random landing X (arena units).
     * {@code -1} = derive from {@link #reposXMargin}: {@code ARENA_W - reposXMargin}.
     */
    public float reposMaxX = -1f;

    /** Margin from each side used when {@link #reposMinX}/{@link #reposMaxX} are {@code -1}. */
    public float reposXMargin = 80f;

    /** Boss Y during REPOS_TOP (arena units from top). */
    public float reposBossY = 80f;

    // ---------------------------------------------------------------- SINE_WAVE

    /** Angular frequency for SINE_WAVE horizontal oscillation (rad/tick). */
    public float sineFrequency = 0.018f;

    // ---------------------------------------------------------------- CIRCLE

    /** Angular frequency for CIRCLE orbit (rad/tick). */
    public float circleFrequency = 0.018f;

    /** Vertical amplitude as a fraction of moveSpeed (CIRCLE only). */
    public float circleYRatio = 0.35f;

    // ---------------------------------------------------------------- Intro animation

    /** Arena Y the boss settles at during the dialog intro. Should clear the dialog box at the bottom. */
    public float introLandY = 210f;

    /**
     * Per-tick lerp rate toward {@link #introLandY} during dialog intro.
     * Higher = faster slide-in. {@code 0.09} ≈ fully landed in ~40 ticks.
     */
    public float introSlideRate = 0.09f;

    /**
     * Ticks to smooth the boss Y from the dialog landing position into
     * the fight start position when the boss phase begins.
     */
    public int fightEntryTicks = 40;
}
