package mc.sayda.bullethell.boss;

import com.google.gson.JsonObject;

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
 *   "patternTempo": 1.25,
 *   "attacks": [ ... ],
 *   "byDifficulty": { "patternTempo": [1, 1, 1, 1.2] }
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

    /**
     * Survival spell: boss does not take damage; the phase ends when the spell timer
     * expires (capture if the player did not bomb or die). Ignores HP depletion.
     */
    public boolean survival = false;

    /** Name shown in the HUD. Wrap in escaped quotes for display: {@code "\"Name\""} */
    public String spellName = "";

    /**
     * Spellcard timer length in ticks for each difficulty level.
     * Index order: [EASY, NORMAL, HARD, LUNATIC]. Must have exactly 4 entries.
     * Use [0, 0, 0, 0] for non-spell phases (no timer bar).
     */
    public int[] spellDurationTicks = {800, 600, 400, 200};

    /**
     * Optional per-difficulty overrides for {@link #hp} (same index order as {@link #spellDurationTicks}).
     * Shorter arrays pad with the last value. {@code null} = use scalar {@link #hp} only.
     */
    public int[] hpByDifficulty = null;

    /**
     * Optional per-difficulty overrides for {@link #moveSpeed}.
     */
    public float[] moveSpeedByDifficulty = null;

    /**
     * Optional per-difficulty overrides for {@link #patternTempo}.
     */
    public float[] patternTempoByDifficulty = null;

    /**
     * Optional per-difficulty overrides for {@link #spellBonus} (spell capture score).
     */
    public long[] spellBonusByDifficulty = null;

    /**
     * Generic per-difficulty overrides: keys match scalar JSON field names (e.g. {@code "hp"},
     * {@code "patternTempo"}); each value is {@code [EASY, NORMAL, HARD, LUNATIC]}. Optional; omit for
     * scalar-only JSON. Legacy {@code *ByDifficulty} fields take precedence when both are set.
     */
    public JsonObject byDifficulty = null;

    /** Bonus score awarded when the spellcard is captured without dying or bombing. */
    public long spellBonus = 50_000L;

    /** @see #hpByDifficulty */
    public int resolveHp(int difficultyOrdinal) {
        if (DifficultyTierArray.isValid(hpByDifficulty))
            return DifficultyTierArray.pickInt(hpByDifficulty, difficultyOrdinal, hp);
        return TierJson.pickInt(byDifficulty, "hp", difficultyOrdinal, hp);
    }

    /** @see #moveSpeedByDifficulty */
    public float resolveMoveSpeed(int difficultyOrdinal) {
        if (DifficultyTierArray.isValid(moveSpeedByDifficulty))
            return DifficultyTierArray.pickFloat(moveSpeedByDifficulty, difficultyOrdinal, moveSpeed);
        return TierJson.pickFloat(byDifficulty, "moveSpeed", difficultyOrdinal, moveSpeed);
    }

    /** @see #patternTempoByDifficulty */
    public float resolvePatternTempo(int difficultyOrdinal) {
        if (DifficultyTierArray.isValid(patternTempoByDifficulty))
            return DifficultyTierArray.pickFloat(patternTempoByDifficulty, difficultyOrdinal, patternTempo);
        return TierJson.pickFloat(byDifficulty, "patternTempo", difficultyOrdinal, patternTempo);
    }

    /** @see #spellBonusByDifficulty */
    public long resolveSpellBonus(int difficultyOrdinal) {
        if (DifficultyTierArray.isValid(spellBonusByDifficulty))
            return DifficultyTierArray.pickLong(spellBonusByDifficulty, difficultyOrdinal, spellBonus);
        return TierJson.pickLong(byDifficulty, "spellBonus", difficultyOrdinal, spellBonus);
    }

    /** Spell timer for this difficulty, or {@code 0} when none. */
    public int resolveSpellDurationTicks(int difficultyOrdinal) {
        if (TierJson.hasTierArray(byDifficulty, "spellDurationTicks"))
            return TierJson.pickInt(byDifficulty, "spellDurationTicks", difficultyOrdinal, 0);
        if (spellDurationTicks == null || spellDurationTicks.length == 0)
            return 0;
        int i = Math.min(Math.max(0, difficultyOrdinal), spellDurationTicks.length - 1);
        return spellDurationTicks[i];
    }

    public float resolveHpThresholdFraction(int difficultyOrdinal) {
        if (TierJson.hasTierArray(byDifficulty, "hpThresholdFraction"))
            return TierJson.pickFloat(byDifficulty, "hpThresholdFraction", difficultyOrdinal, hpThresholdFraction);
        return hpThresholdFraction;
    }

    public boolean resolveSurvival(int difficultyOrdinal) {
        if (TierJson.hasTierArray(byDifficulty, "survival"))
            return TierJson.pickBoolean(byDifficulty, "survival", difficultyOrdinal, survival);
        return survival;
    }

    public boolean resolveIsSpellCard(int difficultyOrdinal) {
        if (TierJson.hasTierArray(byDifficulty, "isSpellCard"))
            return TierJson.pickBoolean(byDifficulty, "isSpellCard", difficultyOrdinal, isSpellCard);
        return isSpellCard;
    }

    public String resolveSpellName(int difficultyOrdinal) {
        String base = spellName != null ? spellName : "";
        if (TierJson.hasTierArray(byDifficulty, "spellName"))
            return TierJson.pickString(byDifficulty, "spellName", difficultyOrdinal, base);
        return base;
    }

    public String resolveMusic(int difficultyOrdinal) {
        String base = music != null ? music : "";
        if (TierJson.hasTierArray(byDifficulty, "music"))
            return TierJson.pickString(byDifficulty, "music", difficultyOrdinal, base);
        return base;
    }

    public String resolveMovement(int difficultyOrdinal) {
        String base = movement != null ? movement : "SINE_WAVE";
        if (TierJson.hasTierArray(byDifficulty, "movement"))
            return TierJson.pickString(byDifficulty, "movement", difficultyOrdinal, base);
        return base;
    }

    /**
     * Boss movement pattern during this phase.
     * Valid values: "SINE_WAVE", "STATIC", "CIRCLE"
     */
    public String movement = "SINE_WAVE";

    /**
     * When set, overrides boss X when this phase begins (arena units, e.g. {@link mc.sayda.bullethell.arena.BulletPool#ARENA_W} / 2).
     * Omit or null to keep the position from the inter-phase centre lerp.
     */
    public Float bossPhaseAnchorX = null;

    /**
     * When set, overrides boss Y when this phase begins (arena units). Use with {@link #bossPhaseAnchorX}
     * to place the boss in the playfield centre for a spell.
     */
    public Float bossPhaseAnchorY = null;

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
     * Boss pattern timing multiplier for this phase ({@code 1} = default). Above {@code 1} speeds up
     * attack rotation, burst gaps, laser telegraphs, and emitter angular advances (e.g. SPRINKLER
     * {@link mc.sayda.bullethell.boss.PatternStep#sprinklerAdvanceRad}); below {@code 1} slows them.
     * Does <strong>not</strong> scale bullet travel {@link mc.sayda.bullethell.boss.PatternStep#speed}.
     */
    public float patternTempo = 1f;

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

    /**
     * Ordered list of attack steps, cycled repeatedly while this phase is active (unless a step
     * sets {@link PatternStep#everyTickWhilePhase}, in which case that step runs every tick outside
     * the rotation).
     */
    public List<PatternStep> attacks = new ArrayList<>();

    /**
     * Optional extra stationary emitters for this phase (Flandre clones/traps, etc.).
     * Each emitter runs its own PatternStep list independently, aimed at the current boss target.
     */
    public List<BossEmitterDefinition> emitters = new ArrayList<>();
}
