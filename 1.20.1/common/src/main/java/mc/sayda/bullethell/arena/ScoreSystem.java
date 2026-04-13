package mc.sayda.bullethell.arena;

/**
 * Tracks score, high score, and graze chain multiplier.
 *
 * Graze chain: incremented by grazing bullets. Decays to 0 if no graze
 * occurs for CHAIN_DECAY_TICKS. Higher chain = higher point-item value.
 */
public class ScoreSystem {

    private static final int  CHAIN_DECAY_TICKS = 60; // ~3 seconds
    private static final long GRAZE_BASE_SCORE  = 50L;
    private static final long POINT_ITEM_BASE   = 100L;
    private static final long POWER_ITEM_SCORE  = 200L;

    private long score     = 0L;
    private long highScore = 0L;
    private int  grazeChain     = 0;
    private int  grazeCooldown  = 0; // ticks until chain resets

    // ---------------------------------------------------------------- server tick

    public void tick() {
        if (grazeCooldown > 0) {
            grazeCooldown--;
            if (grazeCooldown == 0) grazeChain = 0;
        }
    }

    // ---------------------------------------------------------------- events

    public void onGraze() {
        grazeChain++;
        grazeCooldown = CHAIN_DECAY_TICKS;
        addScore(GRAZE_BASE_SCORE + grazeChain * 5L);
    }

    /** Collect a POINT item - value scales with graze chain. */
    public void onPointItemPickup() {
        long value = POINT_ITEM_BASE + (grazeChain / 5) * 50L;
        addScore(value);
    }

    /** Collect a POWER item - fixed score bonus. */
    public void onPowerItemPickup() {
        addScore(POWER_ITEM_SCORE);
    }

    /** Spell capture bonus applied when a phase is cleared cleanly. */
    public void onSpellCapture(long bonus) {
        addScore(bonus);
    }

    // ---------------------------------------------------------------- generic

    public void addScore(long pts) {
        score += pts;
        if (score > highScore) highScore = score;
    }

    // ---------------------------------------------------------------- getters

    public long getScore()      { return score; }
    public long getHighScore()  { return highScore; }
    public int  getGrazeChain() { return grazeChain; }
}
