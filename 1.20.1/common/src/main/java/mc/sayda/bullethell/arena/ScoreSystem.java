package mc.sayda.bullethell.arena;

/**
 * Tracks score and high score.
 */
public class ScoreSystem {

    private static final long GRAZE_SCORE      = 500L;
    private static final long POWER_ITEM_SCORE = 200L;

    private long score     = 0L;
    private long highScore = 0L;

    /** 0 = disabled. Next extend triggers when {@code score >= nextExtendScoreAt}. */
    private long scoreExtendEvery = 0L;
    private long nextExtendScoreAt = Long.MAX_VALUE;

    // ---------------------------------------------------------------- server tick

    public void tick() {
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
        return addScore(GRAZE_SCORE);
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

    public long getScore()     { return score; }
    public long getHighScore() { return highScore; }
}
