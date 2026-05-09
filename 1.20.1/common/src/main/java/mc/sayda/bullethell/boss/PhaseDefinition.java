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
 * 
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
     * Survival spell: boss does not take damage; the phase ends when the spell
     * timer
     * expires (capture if the player did not bomb or die). Ignores HP depletion.
     */
    public boolean survival = false;

    /**
     * Name shown in the HUD. Wrap in escaped quotes for display: {@code "\"Name\""}
     */
    public String spellName = "";

    /**
     * Spellcard timer length in ticks for each difficulty level.
     * Index order: [EASY, NORMAL, HARD, LUNATIC]. Must have exactly 4 entries.
     * Use [0, 0, 0, 0] for non-spell phases (no timer bar).
     */
    public int[] spellDurationTicks = { 800, 600, 400, 200 };

    /**
     * Generic per-difficulty overrides: keys match scalar JSON field names (e.g.
     * {@code "hp"},
     * {@code "patternTempo"}); each value is {@code [EASY, NORMAL, HARD, LUNATIC]}.
     * Populated at load time by {@link TierJson#promoteUnionTierFieldsOnBoss} when
     * a field holds an inline array (e.g. {@code "hp": [2100, 2300, 2550, 2800]}).
     */
    public JsonObject byDifficulty = null;

    /**
     * Bonus score awarded when the spellcard is captured without dying or bombing.
     */
    public long spellBonus = 50_000L;

    public int resolveHp(int difficultyOrdinal) {
        return TierJson.pickInt(byDifficulty, "hp", difficultyOrdinal, hp);
    }

    public float resolveMoveSpeed(int difficultyOrdinal) {
        return TierJson.pickFloat(byDifficulty, "moveSpeed", difficultyOrdinal, moveSpeed);
    }

    public float resolvePatternTempo(int difficultyOrdinal) {
        return TierJson.pickFloat(byDifficulty, "patternTempo", difficultyOrdinal, patternTempo);
    }

    public long resolveSpellBonus(int difficultyOrdinal) {
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
        if (musicPool != null && !musicPool.isEmpty())
            return musicPool.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(musicPool.size()));
        String base = music != null ? music : "";
        if (TierJson.hasTierArray(byDifficulty, "music"))
            return TierJson.pickString(byDifficulty, "music", difficultyOrdinal, base);
        return base;
    }

    public int resolveReposShootTicks(int difficultyOrdinal) {
        int base = reposShootTicks != null ? reposShootTicks : 40;
        return TierJson.pickInt(byDifficulty, "reposShootTicks", difficultyOrdinal, base);
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
     * Optional override for this phase's REPOS_TOP shoot duration.
     * If null, falls back to the boss's {@link MovementConfig#reposShootTicks}.
     */
    public Integer reposShootTicks = null;

    /**
     * When true (default), difficulty multipliers from {@link DifficultyConfig} are
     * applied to
     * all attacks in this phase.
     */
    public boolean dynamicDifficulty = true;

    /**
     * When set, overrides boss X when this phase begins (arena units, e.g.
     * {@link mc.sayda.bullethell.arena.BulletPool#ARENA_W} / 2).
     * Omit or null to keep the position from the inter-phase centre lerp.
     */
    public Float bossPhaseAnchorX = null;

    /**
     * When set, overrides boss Y when this phase begins (arena units). Use with
     * {@link #bossPhaseAnchorX}
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
     * Boss pattern timing multiplier for this phase ({@code 1} = default). Above
     * {@code 1} speeds up
     * attack rotation, burst gaps, laser telegraphs, and emitter angular advances
     * (e.g. SPRINKLER
     * {@link mc.sayda.bullethell.boss.PatternStep#sprinklerAdvanceRad}); below
     * {@code 1} slows them.
     * Does <strong>not</strong> scale bullet travel
     * {@link mc.sayda.bullethell.boss.PatternStep#speed}.
     */
    public float patternTempo = 1f;

    /**
     * Music track ID to play during this phase. Use a plain string for a single
     * track, or an array of strings to pick one at random each time the phase
     * starts: {@code "music": ["track_a", "track_b"]}.
     * {@code null}, omitted, or {@code ""} = keep playing whatever was already
     * running.
     */
    public String music = null;

    /**
     * Populated by {@link mc.sayda.bullethell.boss.BossLoader} when the JSON
     * {@code "music"} field is an array. {@link #resolveMusic} picks randomly.
     */
    public List<String> musicPool = null;

    /**
     * HP fraction (0-1) at which this phase ends early and the next phase declares.
     * 0.20 = spell card is declared when 20 % HP remains (boss becomes invincible).
     * 0.0 = must deplete HP to zero (standard for spell card phases themselves).
     */
    public float hpThresholdFraction = 0.0f;

    /**
     * Ordered list of attack steps, cycled repeatedly while this phase is active
     * (unless a step
     * sets {@link PatternStep#everyTickWhilePhase}, in which case that step runs
     * every tick outside
     * the rotation).
     */
    public List<PatternStep> attacks = new ArrayList<>();

    /**
     * Optional extra stationary emitters for this phase (Flandre clones/traps,
     * etc.).
     * Each emitter runs its own PatternStep list independently, aimed at the
     * current boss target.
     */
    public List<BossEmitterDefinition> emitters = new ArrayList<>();

    /**
     * Cached subset of {@link #attacks} used by the main rotation: excludes
     * {@code everyTickWhilePhase} steps and {@code PENTAGRAM_RITUAL} steps.
     * Computed once on first access; safe to use per-tick without allocation.
     */
    private transient List<PatternStep> cachedMainRotation;

    public List<PatternStep> getMainRotation() {
        if (cachedMainRotation != null) return cachedMainRotation;
        List<PatternStep> out = new ArrayList<>();
        if (attacks != null) {
            for (PatternStep s : attacks) {
                if (s == null || s.everyTickWhilePhase) continue;
                if ("PENTAGRAM_RITUAL".equals(s.getPatternUpper())) continue;
                out.add(s);
            }
        }
        cachedMainRotation = java.util.Collections.unmodifiableList(out);
        return cachedMainRotation;
    }
}
