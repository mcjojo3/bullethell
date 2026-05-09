package mc.sayda.bullethell.boss;

/**
 * Gameplay ruleset configuration embedded in a stage JSON file.
 *
 * Different Touhou games have meaningfully different mechanics. Rather than
 * hardcoding one set of rules, each stage declares which behaviours it wants.
 * This lets a stage feel like TH6, TH7, or TH9 just by editing JSON.
 *
 * ─────────────────────────────────────────────────────────────────
 * Reference mapping (approximate):
 *
 * TH6 (EoSD) - itemDropEveryNthKill=3, bombDropChance=1/512, score extends,
 * large fairies always drop, pocAutoCollect=true, deathResetspower=true,
 * grazeScoringEnabled=false
 *
 * TH7 (PCB) - itemDropEveryNthKill=1, pocAutoCollect=true,
 * deathResetspower=true, cherrySystemEnabled=true
 *
 * TH9 (PoFV) - itemDropEveryNthKill=1, pocAutoCollect=false,
 * versusKillSendsBullets=true
 * ─────────────────────────────────────────────────────────────────
 *
 * All fields have sensible defaults - omit any field in JSON to keep the
 * default.
 */
public class RulesetConfig {
    public enum GameplayPreset {
        /** Touhou 6: Screen clear on 128 power, items don't always vacuum. */
        TH6,
        /** Touhou 7: Cherry system, different scoring. */
        TH7,
        /** Touhou 9: No screen clear on power, versus focus. */
        TH9,
        /** Touhou 8: Standard modern feel. */
        TH8,
        /** Touhou 10 (Mountain of Faith): Modern item/PoC, no cherry, 10M score extend. */
        TH10
    }

    /**
     * High-level template for this stage. Individual fields override these
     * defaults.
     */
    public GameplayPreset preset = GameplayPreset.TH6;

    /**
     * Re-applies defaults from the chosen {@link #preset}. Called after JSON
     * loading
     * so that the template is applied but individual field overrides in the JSON
     * still win.
     */
    public void applyPreset() {
        switch (preset) {
            case TH6 -> {
                if (itemDropEveryNthKill == null)
                    itemDropEveryNthKill = 3;
                if (largeEnemyAlwaysDrops == null)
                    largeEnemyAlwaysDrops = true;
                if (pocAutoCollect == null)
                    pocAutoCollect = true;
                if (deathPowerLoss == null)
                    deathPowerLoss = 16;
                if (bulletClearOnMaxPower == null)
                    bulletClearOnMaxPower = true;
                if (bulletClearVacuum == null)
                    bulletClearVacuum = false; // TH6 items don't always vacuum
                if (grazeScoringEnabled == null)
                    grazeScoringEnabled = true;
                if (pointItemMaxValue == null)
                    pointItemMaxValue = 100000;
                if (pointItemMinValue == null)
                    pointItemMinValue = 25000;
                if (scoreExtendEvery == null)
                    scoreExtendEvery = 10000000L;
                if (forceControlScheme == null)
                    forceControlScheme = "classic";
            }
            case TH7 -> {
                if (itemDropEveryNthKill == null)
                    itemDropEveryNthKill = 1;
                if (largeEnemyAlwaysDrops == null)
                    largeEnemyAlwaysDrops = true;
                if (pocAutoCollect == null)
                    pocAutoCollect = true;
                if (deathPowerLoss == null)
                    deathPowerLoss = 16;
                if (bulletClearOnMaxPower == null)
                    bulletClearOnMaxPower = false;
                if (bulletClearVacuum == null)
                    bulletClearVacuum = true;
                if (cherrySystemEnabled == null)
                    cherrySystemEnabled = true;
                if (grazeScoringEnabled == null)
                    grazeScoringEnabled = true;
                if (pointItemMaxValue == null)
                    pointItemMaxValue = 50000;
                if (pointItemMinValue == null)
                    pointItemMinValue = 10000;
                if (scoreExtendEvery == null)
                    scoreExtendEvery = 20000000L;
            }
            case TH9 -> {
                if (itemDropEveryNthKill == null)
                    itemDropEveryNthKill = 1;
                if (largeEnemyAlwaysDrops == null)
                    largeEnemyAlwaysDrops = true;
                if (pocAutoCollect == null)
                    pocAutoCollect = false;
                if (deathPowerLoss == null)
                    deathPowerLoss = 0;
                if (bulletClearOnMaxPower == null)
                    bulletClearOnMaxPower = false;
                if (bulletClearVacuum == null)
                    bulletClearVacuum = true;
                if (versusKillSendsBullets == null)
                    versusKillSendsBullets = true;
                if (grazeScoringEnabled == null)
                    grazeScoringEnabled = false;
                if (pointItemMaxValue == null)
                    pointItemMaxValue = 100000;
                if (pointItemMinValue == null)
                    pointItemMinValue = 100000; // PoFV items are fixed value
                if (scoreExtendEvery == null)
                    scoreExtendEvery = 10000000L;
                if (forceControlScheme == null)
                    forceControlScheme = "th9";
            }
            case TH8 -> {
                if (itemDropEveryNthKill == null)
                    itemDropEveryNthKill = 1;
                if (largeEnemyAlwaysDrops == null)
                    largeEnemyAlwaysDrops = true;
                if (pocAutoCollect == null)
                    pocAutoCollect = true;
                if (deathPowerLoss == null)
                    deathPowerLoss = 16;
                if (bulletClearOnMaxPower == null)
                    bulletClearOnMaxPower = false;
                if (bulletClearVacuum == null)
                    bulletClearVacuum = true;
                if (grazeScoringEnabled == null)
                    grazeScoringEnabled = true;
                if (pointItemMaxValue == null)
                    pointItemMaxValue = 100000;
                if (pointItemMinValue == null)
                    pointItemMinValue = 20000;
                if (scoreExtendEvery == null)
                    scoreExtendEvery = 15000000L;
            }
            case TH10 -> {
                if (itemDropEveryNthKill == null)
                    itemDropEveryNthKill = 1;
                if (largeEnemyAlwaysDrops == null)
                    largeEnemyAlwaysDrops = true;
                if (pocAutoCollect == null)
                    pocAutoCollect = true;
                if (deathPowerLoss == null)
                    deathPowerLoss = 16;
                if (bulletClearOnMaxPower == null)
                    bulletClearOnMaxPower = false;
                if (bulletClearVacuum == null)
                    bulletClearVacuum = true;
                if (grazeScoringEnabled == null)
                    grazeScoringEnabled = true;
                if (pointItemMaxValue == null)
                    pointItemMaxValue = 100000;
                if (pointItemMinValue == null)
                    pointItemMinValue = 20000;
                if (scoreExtendEvery == null)
                    scoreExtendEvery = 10000000L;
            }
        }
        // Final fallback for any still null (Modern defaults)
        if (itemDropEveryNthKill == null)
            itemDropEveryNthKill = 1;
        if (largeEnemyAlwaysDrops == null)
            largeEnemyAlwaysDrops = true;
        if (bombDropChance == null)
            bombDropChance = 1f / 512f;
        if (scoreExtendEvery == null)
            scoreExtendEvery = 10000000L;
        if (scoreExtendAwardAllCoopPlayers == null)
            scoreExtendAwardAllCoopPlayers = false;
        if (pocFraction == null)
            pocFraction = 0.20f;
        if (pocAutoCollect == null)
            pocAutoCollect = true;
        if (itemAttractionSpeed == null)
            itemAttractionSpeed = 16.0f;
        if (deathPowerLoss == null)
            deathPowerLoss = 16;
        if (bulletClearOnMaxPower == null)
            bulletClearOnMaxPower = false;
        if (bulletClearVacuum == null)
            bulletClearVacuum = true;
        if (grazeScoringEnabled == null)
            grazeScoringEnabled = true;
        if (grazeScoreMultiplier == null)
            grazeScoreMultiplier = 1.0f;
        if (pointItemMaxValue == null)
            pointItemMaxValue = 100000;
        if (pointItemMinValue == null)
            pointItemMinValue = 50000;
        if (cherrySystemEnabled == null)
            cherrySystemEnabled = false;
        if (cherryPerHit == null)
            cherryPerHit = 8;
        if (cherryShieldThreshold == null)
            cherryShieldThreshold = 50000;
        if (cherryShieldDuration == null)
            cherryShieldDuration = 60;
        if (versusKillSendsBullets == null)
            versusKillSendsBullets = false;
        if (onKillDeathBurstCount == null)
            onKillDeathBurstCount = 0;
        if (onKillDeathBurstSpeed == null)
            onKillDeathBurstSpeed = 2.0f;
        if (startingLives == null)
            startingLives = -1;
        if (startingBombs == null)
            startingBombs = -1;
    }

    // ---------------------------------------------------------------- item drops

    /**
     * How often <em>small</em> fairies drop items (1 = PCB/IN-style, 3 = TH6).
     * When {@link #largeEnemyAlwaysDrops} is true, large fairies still drop every
     * kill using {@link #largeEnemyDropCyclePattern}.
     */
    public Integer itemDropEveryNthKill = null;

    /**
     * Deterministic drop cycle for small/normal enemies (fairies).
     * TH-accurate: only POWER and POINT - no bombs, full-power, or 1-ups
     * from small fairies.
     * Example: "POWER,POINT,POWER,POWER,POINT,POWER,POINT,POWER,POINT,POWER"
     */
    public String dropCyclePattern = "POWER,POINT,POWER,POWER,POINT,POWER,POINT,POWER,POINT,POWER";

    /**
     * Drop cycle used for large (midboss-tier) enemies only.
     * Large fairies drop POWER_LARGE (+8) and occasionally FULL_POWER.
     * Leave empty to fall back to {@link #dropCyclePattern}.
     */
    public String largeEnemyDropCyclePattern = "POWER_LARGE,POINT,POWER_LARGE,FULL_POWER,POINT,POWER_LARGE,POWER_LARGE,POINT";

    /**
     * Probability (0-1) that an item drop slot becomes a bomb instead of the
     * cycled type. Classic value is {@code 1/512}. Set 0 to disable.
     */
    public Float bombDropChance = null;

    /**
     * When true (default), large fairies always release an item on death, while
     * {@link #itemDropEveryNthKill} applies only to small fairies - matches how
     * veterans expect anchors to pay out even with TH6-style sparse small drops.
     */
    public Boolean largeEnemyAlwaysDrops = null;

    /**
     * Score milestone for an extra life (TH-style extend). 0 disables. Tuned for
     * this mod's scoring; raise for stricter survival marathons.
     */
    public Long scoreExtendEvery = null;

    /**
     * When {@link #scoreExtendEvery} is positive: if true, each co-op player gains
     * a
     * life at every extend; if false, only the arena host does (strict solo).
     */
    public Boolean scoreExtendAwardAllCoopPlayers = null;

    // ---------------------------------------------------------------- point of
    // collection

    /**
     * Y fraction from the top of the arena that defines the PoC line.
     * 0.20 = top 20 % of the screen (classic Touhou).
     */
    public Float pocFraction = null;

    /**
     * When true, all items auto-collect if the player is above the PoC line.
     * When false, items must be individually touched.
     */
    public Boolean pocAutoCollect = null;

    /**
     * Speed at which items are attracted toward the player when auto-collecting.
     * Expressed as arena units per tick. Ignored when pocAutoCollect = false.
     */
    public Float itemAttractionSpeed = null;

    // ---------------------------------------------------------------- power &
    // death

    /**
     * Power lost when the player dies.
     * 0 = keep full power (TH13+ style), 16 = classic penalty, 128 = reset to 0.
     */
    public Integer deathPowerLoss = null;

    /**
     * When reaching 128 power, should the screen clear and convert bullets into
     * items?
     * TH6 = true; MODERN = false.
     */
    public Boolean bulletClearOnMaxPower = null;

    /**
     * When bullets turn into items (Phase clear / Max power), should they fly to
     * the player?
     * TH6 = false (except PoC); MODERN = true.
     */
    public Boolean bulletClearVacuum = null;

    /**
     * If set, forces all participants to use this control scheme during the match.
     * Useful for "Classic" or "PoFV" themed stages.
     */
    public String forceControlScheme = null;

    /**
     * Whether grazing enemy bullets earns score / contributes to a chain.
     * Disable for a pure TH9 vs mode feel.
     */
    public Boolean grazeScoringEnabled = null;

    /**
     * Multiplier applied to graze score (base graze score * this value).
     */
    public Float grazeScoreMultiplier = null;

    // ---------------------------------------------------------------- point item
    // scoring

    /**
     * Point item full value (at the top of the screen / when above PoC).
     */
    public Integer pointItemMaxValue = null;

    /**
     * Point item minimum value (at the very bottom of the screen).
     * Intermediate heights interpolate linearly between this and
     * {@code pointItemMaxValue}.
     */
    public Integer pointItemMinValue = null;

    // ---------------------------------------------------------------- cherry
    // system (TH7 style)

    /**
     * Enable the Cherry Point system (TH7 / Perfect Cherry Blossom style).
     * Shooting enemies accumulates Cherry Points; reaching the threshold
     * grants a temporary graze shield and multiplies point item values.
     * All cherry fields are ignored when this is false.
     */
    public Boolean cherrySystemEnabled = null;

    /** Cherry Points gained per player bullet that hits an enemy. */
    public Integer cherryPerHit = null;

    /** Cherry threshold at which the cherry shield triggers. */
    public Integer cherryShieldThreshold = null;

    /** Duration of the cherry shield in ticks after activation. */
    public Integer cherryShieldDuration = null;

    // ---------------------------------------------------------------- versus mode
    // (TH9 style)

    /**
     * When true, enemies killed during a combo chain send their bullets to the
     * opponent's field (TH9 Phantasmagoria mode).
     * Has no mechanical effect in single-player arenas currently; reserved for
     * splitscreen.
     */
    public Boolean versusKillSendsBullets = null;

    // ---------------------------------------------------------------- on-kill
    // death burst

    /**
     * On Lunatic mode some games fire aimed bullets from enemies when they are
     * killed. Set > 0 to enable death-burst bullets.
     */
    public Integer onKillDeathBurstCount = null;

    /** Speed of the on-kill death burst bullets. */
    public Float onKillDeathBurstSpeed = null;

    // ---------------------------------------------------------------- starting
    // conditions

    /**
     * Override the player's starting lives for this stage.
     * -1 = use the selected character's default ({@code startingLives} in the
     * character JSON).
     * Applies to every participant in co-op.
     */
    public Integer startingLives = null;

    /**
     * Override the player's starting bombs for this stage.
     * -1 = use the selected character's default ({@code startingBombs} in the
     * character JSON).
     * Applies to every participant in co-op.
     */
    public Integer startingBombs = null;
}
