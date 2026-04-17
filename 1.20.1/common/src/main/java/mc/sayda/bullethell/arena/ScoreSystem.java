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

    /** 0 = disabled. Next extend triggers when {@code score >= nextExtendScoreAt}. */
    private long scoreExtendEvery = 0L;
    private long nextExtendScoreAt = Long.MAX_VALUE;

    // ---------------------------------------------------------------- server tick

    public void tick() {
        if (grazeCooldown > 0) {
            grazeCooldown--;
            if (grazeCooldown == 0) grazeChain = 0;
        }
    }

    // ---------------------------------------------------------------- score extends

    /**
     * Call when a new arena starts at score 0. Configures TH-style score extends
     * without consuming milestones already represented by {@code score}.
     */
    public void configureExtendsEvery(long every) {
        this.scoreExtendEvery = Math.max(0L, every);
        if (this.scoreExtendEvery > 0L) {
            if (this.score <= 0L) {
                this.nextExtendScoreAt = this.scoreExtendEvery;
            } else {
                recomputeNextExtendThreshold();
            }
        } else {
            this.nextExtendScoreAt = Long.MAX_VALUE;
        }
    }

    /**
     * Chained stages: set absolute carried score and realign extend milestones
     * so extends are not granted again for thresholds already passed.
     */
    public void importCarriedScore(long carriedScore, long extendEvery) {
        this.score = carriedScore;
        if (this.score > this.highScore) {
            this.highScore = this.score;
        }
        this.scoreExtendEvery = Math.max(0L, extendEvery);
        if (this.scoreExtendEvery > 0L) {
            recomputeNextExtendThreshold();
        } else {
            this.nextExtendScoreAt = Long.MAX_VALUE;
        }
    }

    private void recomputeNextExtendThreshold() {
        if (scoreExtendEvery <= 0L) {
            nextExtendScoreAt = Long.MAX_VALUE;
            return;
        }
        long q = score / scoreExtendEvery;
        nextExtendScoreAt = (q + 1L) * scoreExtendEvery;
    }

    private int consumeExtendsFromScore() {
        if (scoreExtendEvery <= 0L) {
            return 0;
        }
        int n = 0;
        while (score >= nextExtendScoreAt) {
            n++;
            nextExtendScoreAt += scoreExtendEvery;
        }
        return n;
    }

    // ---------------------------------------------------------------- events

    public int onGraze() {
        grazeChain++;
        grazeCooldown = CHAIN_DECAY_TICKS;
        return addScore(GRAZE_BASE_SCORE + grazeChain * 5L);
    }

    /** Collect a POINT item - value scales with graze chain. */
    public int onPointItemPickup() {
        long value = POINT_ITEM_BASE + (grazeChain / 5) * 50L;
        return addScore(value);
    }

    /** Collect a POWER item - fixed score bonus. */
    public int onPowerItemPickup() {
        return addScore(POWER_ITEM_SCORE);
    }

    /** Spell capture bonus applied when a phase is cleared cleanly. */
    public int onSpellCapture(long bonus) {
        return addScore(bonus);
    }

    // ---------------------------------------------------------------- generic

    /**
     * Adds score and returns how many score-based extends were crossed this tick
     * (each grants +1 life in {@link ArenaContext}).
     */
    public int addScore(long pts) {
        score += pts;
        if (score > highScore) {
            highScore = score;
        }
        return consumeExtendsFromScore();
    }

    // ---------------------------------------------------------------- getters

    public long getScore()      { return score; }
    public long getHighScore()  { return highScore; }
    public int  getGrazeChain() { return grazeChain; }
}
