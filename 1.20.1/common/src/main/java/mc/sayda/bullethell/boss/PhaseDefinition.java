package mc.sayda.bullethell.boss;

import java.util.ArrayList;
import java.util.List;

/**
 * One phase (spellcard or non-spell section) of a boss fight.
 *
 * Each phase has its own independent HP pool. The boss's HP bar resets to
 * this phase's {@link #hp} when the phase begins, giving an authentic
 * Touhou-style per-spellcard health bar.
 *
 * JSON example:
 * <pre>
 * {
 *   "hp": 600,
 *   "isSpellCard": true,
 *   "spellName": "\"Fantasy Seal\"",
 *   "spellDurationTicks": [800, 600, 400, 200],
 *   "spellBonus": 50000,
 *   "movement": "SINE_WAVE",
 *   "moveSpeed": 140.0,
 *   "attacks": [ ... ]
 * }
 * </pre>
 */
public class PhaseDefinition {

    /** HP pool for this phase. Resets the boss HP bar when this phase begins. */
    public int hp = 300;

    /**
     * Whether this phase is a spellcard (capture bonus eligible).
     * Non-spell sections should set this to false and spellBonus to 0.
     */
    public boolean isSpellCard = true;

    /** Name shown in the HUD. Wrap in escaped quotes for display: {@code "\"Name\""} */
    public String spellName = "";

    /**
     * Spellcard timer length in ticks for each difficulty level.
     * Index order: [EASY, NORMAL, HARD, LUNATIC]. Must have exactly 4 entries.
     * Use [0, 0, 0, 0] for non-spell phases (no timer bar).
     */
    public int[] spellDurationTicks = {800, 600, 400, 200};

    /** Bonus score awarded when the spellcard is captured without dying or bombing. */
    public long spellBonus = 50_000L;

    /**
     * Boss movement pattern during this phase.
     * Valid values: "SINE_WAVE", "STATIC", "CIRCLE"
     */
    public String movement = "SINE_WAVE";

    /**
     * Optional lower difficulty bound (inclusive) for this phase.
     * Empty/null = no lower bound.
     * Example values: "EASY", "NORMAL", "HARD", "LUNATIC".
     */
    public String minDifficulty = "";

    /**
     * Optional upper difficulty bound (inclusive) for this phase.
     * Empty/null = no upper bound.
     * Example values: "EASY", "NORMAL", "HARD", "LUNATIC".
     */
    public String maxDifficulty = "";

    /**
     * Amplitude of the boss movement, in arena units.
     * For SINE_WAVE: horizontal swing distance from centre.
     * For CIRCLE: orbit radius.
     */
    public float moveSpeed = 140f;

    /**
     * Music track ID to play during this phase.
     * Must match a key in {@code assets/bullethell/sounds.json}, e.g.
     * {@code "love_coloured_master_spark"}.
     * {@code null}, omitted, or {@code ""} = keep playing whatever was already running.
     */
    public String music = null;

    /**
     * HP fraction (0–1) at which this phase ends early and the next phase declares.
     * 0.20 = spell card is declared when 20 % HP remains (boss becomes invincible).
     * 0.0  = must deplete HP to zero (standard for spell card phases themselves).
     */
    public float hpThresholdFraction = 0.0f;

    /** Ordered list of attack steps, cycled repeatedly while this phase is active. */
    public List<PatternStep> attacks = new ArrayList<>();

    /**
     * Optional extra stationary emitters for this phase (Flandre clones/traps, etc.).
     * Each emitter runs its own PatternStep list independently, aimed at the current boss target.
     */
    public List<BossEmitterDefinition> emitters = new ArrayList<>();
}
