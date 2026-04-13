package mc.sayda.bullethell.boss;

/**
 * Gameplay ruleset configuration embedded in a stage JSON file.
 *
 * Different Touhou games have meaningfully different mechanics.  Rather than
 * hardcoding one set of rules, each stage declares which behaviours it wants.
 * This lets a stage feel like TH6, TH7, or TH9 just by editing JSON.
 *
 * ─────────────────────────────────────────────────────────────────
 * Reference mapping (approximate):
 *
 *   TH6 (EoSD)  - itemDropEveryNthKill=3, pocAutoCollect=true,
 *                  deathResetspower=true, grazeScoringEnabled=false
 *
 *   TH7 (PCB)   - itemDropEveryNthKill=1, pocAutoCollect=true,
 *                  deathResetspower=true, cherrySystemEnabled=true
 *
 *   TH9 (PoFV)  - itemDropEveryNthKill=1, pocAutoCollect=false,
 *                  versusKillSendsBullets=true
 * ─────────────────────────────────────────────────────────────────
 *
 * All fields have sensible defaults - omit any field in JSON to keep the
 * default.
 */
public class RulesetConfig {

    // ---------------------------------------------------------------- item drops

    /**
     * How often enemies drop items: 1 = every kill, 3 = every third kill
     * (TH6 style), etc.
     * Drop type still follows the {@code dropCyclePattern} sequence.
     */
    public int itemDropEveryNthKill = 1;

    /**
     * Deterministic drop cycle for small/normal enemies (fairies).
     * TH-accurate: only POWER and POINT — no bombs, full-power, or 1-ups
     * from small fairies.
     * Example: "POWER,POINT,POWER,POWER,POINT,POWER,POINT,POWER,POINT,POWER"
     */
    public String dropCyclePattern =
            "POWER,POINT,POWER,POWER,POINT,POWER,POINT,POWER,POINT,POWER";

    /**
     * Drop cycle used for large (midboss-tier) enemies only.
     * Large fairies can occasionally drop FULL_POWER items (~1 in 6).
     * Leave empty to fall back to {@link #dropCyclePattern}.
     */
    public String largeEnemyDropCyclePattern =
            "POWER,POINT,POWER,FULL_POWER,POINT,POWER,POWER,POINT";

    /**
     * Probability (0–1) that a drop slot is replaced by a bomb item instead.
     * Classic TH value is 1/512 ≈ 0.00195.  Set 0 to disable.
     */
    public float bombDropChance = 0.002f;

    // ---------------------------------------------------------------- point of collection

    /**
     * Y fraction from the top of the arena that defines the PoC line.
     * 0.20 = top 20 % of the screen (classic Touhou).
     */
    public float pocFraction = 0.20f;

    /**
     * When true, all items auto-collect if the player is above the PoC line.
     * When false, items must be individually touched.
     */
    public boolean pocAutoCollect = true;

    /**
     * Speed at which items are attracted toward the player when auto-collecting.
     * Expressed as arena units per tick.  Ignored when pocAutoCollect = false.
     */
    public float itemAttractionSpeed = 8.0f;

    // ---------------------------------------------------------------- power & death

    /**
     * Power lost when the player dies.
     * 0 = keep full power (TH13+ style), 16 = classic penalty, 128 = reset to 0.
     */
    public int deathPowerLoss = 16;

    /**
     * Whether grazing enemy bullets earns score / contributes to a chain.
     * Disable for a pure TH9 vs mode feel.
     */
    public boolean grazeScoringEnabled = true;

    /**
     * Multiplier applied to graze score (base graze score * this value).
     */
    public float grazeScoreMultiplier = 1.0f;

    // ---------------------------------------------------------------- point item scoring

    /**
     * Point item full value (at the top of the screen / when above PoC).
     */
    public int pointItemMaxValue = 100;

    /**
     * Point item minimum value (at the very bottom of the screen).
     * Intermediate heights interpolate linearly between this and
     * {@code pointItemMaxValue}.
     */
    public int pointItemMinValue = 50;

    // ---------------------------------------------------------------- cherry system (TH7 style)

    /**
     * Enable the Cherry Point system (TH7 / Perfect Cherry Blossom style).
     * Shooting enemies accumulates Cherry Points; reaching the threshold
     * grants a temporary graze shield and multiplies point item values.
     * All cherry fields are ignored when this is false.
     */
    public boolean cherrySystemEnabled = false;

    /** Cherry Points gained per player bullet that hits an enemy. */
    public int cherryPerHit = 8;

    /** Cherry threshold at which the cherry shield triggers. */
    public int cherryShieldThreshold = 50_000;

    /** Duration of the cherry shield in ticks after activation. */
    public int cherryShieldDuration = 60;

    // ---------------------------------------------------------------- versus mode (TH9 style)

    /**
     * When true, enemies killed during a combo chain send their bullets to the
     * opponent's field (TH9 Phantasmagoria mode).
     * Has no mechanical effect in single-player arenas currently; reserved for
     * splitscreen.
     */
    public boolean versusKillSendsBullets = false;

    // ---------------------------------------------------------------- on-kill death burst

    /**
     * On Lunatic mode some games fire aimed bullets from enemies when they are
     * killed.  Set > 0 to enable death-burst bullets.
     */
    public int onKillDeathBurstCount = 0;

    /** Speed of the on-kill death burst bullets. */
    public float onKillDeathBurstSpeed = 2.0f;

    // ---------------------------------------------------------------- starting conditions

    /**
     * Override the player's starting lives for this stage.
     * -1 = use the selected character's default ({@code startingLives} in the character JSON).
     * Applies to every participant in co-op.
     */
    public int startingLives = -1;

    /**
     * Override the player's starting bombs for this stage.
     * -1 = use the selected character's default ({@code startingBombs} in the character JSON).
     * Applies to every participant in co-op.
     */
    public int startingBombs = -1;
}
