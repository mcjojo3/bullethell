package mc.sayda.bullethell.arena;

/**
 * Per-phase countdown timer for spellcard capture bonuses.
 *
 * If the player defeats the boss phase before time runs out,
 * and has not died or bombed during this phase, they earn the capture bonus.
 */
public class SpellcardTimer {

    private int  totalTicks     = 0;
    private int  remainingTicks = 0;
    private boolean captured    = false;
    private boolean failed      = false;
    private long bonusValue     = 0L;
    private boolean active      = false;
    /** TH-style survival spell: timer expiring counts as a clean capture (if not failed). */
    private boolean survivalMode = false;
    /** Set when a survival spell's timer just reached 0; consumed by {@link #consumeSurvivalPhaseEnd()}. */
    private boolean survivalPhaseEndPending = false;

    // ---------------------------------------------------------------- lifecycle

    /**
     * Start (or restart) the timer for a new phase.
     * @param durationTicks  total allowed ticks
     * @param bonus          score awarded on clean capture
     * @param survival       if true, surviving until the timer expires counts as capture (boss HP ignored)
     */
    public void start(int durationTicks, long bonus, boolean survival) {
        this.totalTicks     = durationTicks;
        this.remainingTicks = durationTicks;
        this.bonusValue     = bonus;
        this.captured       = false;
        this.failed         = false;
        this.active         = true;
        this.survivalMode   = survival;
        this.survivalPhaseEndPending = false;
    }

    public void tick() {
        if (!active || failed || captured) return;
        if (remainingTicks > 0) remainingTicks--;
        if (remainingTicks == 0) {
            if (survivalMode) {
                active = false;
                if (!failed)
                    captured = true;
                survivalPhaseEndPending = true;
            } else {
                fail(); // time's up - normal spell failed
            }
        }
    }

    /**
     * Returns true once when a survival spell just finished by timer (success or fail state already set).
     * Caller should advance the boss phase when true.
     */
    public boolean consumeSurvivalPhaseEnd() {
        if (!survivalPhaseEndPending)
            return false;
        survivalPhaseEndPending = false;
        return true;
    }

    // ---------------------------------------------------------------- outcome signals

    /** Called when the player dies or bombs (spellcard is broken). */
    public void fail() {
        if (!active) return;
        failed  = true;
        active  = false;
    }

    /** Called when the boss phase ends (boss HP threshold crossed or phase cleared). */
    public GameEvent onPhaseCleared() {
        if (!active) {
            if (captured && !failed)
                return GameEvent.SPELL_CAPTURED;
            return GameEvent.SPELL_FAILED;
        }
        active = false;
        if (!failed) {
            captured = true;
            return GameEvent.SPELL_CAPTURED;
        }
        return GameEvent.SPELL_FAILED;
    }

    // ---------------------------------------------------------------- getters

    public int     getTotalTicks()     { return totalTicks; }
    public int     getRemainingTicks() { return remainingTicks; }
    public boolean isCaptured()        { return captured; }
    public boolean isFailed()          { return failed; }
    public long    getBonusValue()     { return bonusValue; }
    public boolean isActive()          { return active; }

    /** 0.0 – 1.0 fill fraction for the timer bar UI. */
    public float getFraction() {
        if (totalTicks == 0) return 0f;
        return (float) remainingTicks / totalTicks;
    }
}
