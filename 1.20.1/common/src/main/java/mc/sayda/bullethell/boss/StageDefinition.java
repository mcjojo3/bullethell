package mc.sayda.bullethell.boss;

/**
 * Top-level definition of a full stage: the boss fight, music, and gameplay
 * ruleset.
 *
 * Place JSON files at:
 *   {@code data/bullethell/stages/<id>.json}
 *
 * A stage opens on the boss intro dialogue, then runs the {@link BossDefinition}
 * identified by {@link #bossId} until it is defeated or the player runs out of
 * lives.
 *
 * The {@link RulesetConfig} embedded in this definition controls all tunable
 * gameplay mechanics so you can replicate TH6, TH7, or TH9 rules.
 */
public class StageDefinition {

    /** Unique ID - must match the JSON file name (without .json). */
    public String id = "unknown";

    /** Human-readable stage title shown in HUD or menus. */
    public String title = "Stage ?";

    /**
     * Music track ID played over the boss intro dialogue.
     * Must match a key in {@code assets/bullethell/sounds.json}.
     * Null or empty = no music until the first boss phase sets its own.
     */
    public String stageMusic = null;

    /**
     * ID of the boss definition to load.
     * Must match a file at {@code data/bullethell/bosses/<bossId>.json}.
     */
    public String bossId = "marisa_boss";

    /**
     * Optional next stage ID for continuous battles/campaign chaining.
     * If set (non-empty), clearing this stage can immediately start that stage.
     */
    public String nextStageId = "";

    /**
     * Gameplay rules for this stage.  Controls drop patterns, PoC behaviour,
     * scoring, death penalty, etc.  All fields have sensible defaults - only
     * override what you want to change.
     */
    public RulesetConfig rules = new RulesetConfig();

    /**
     * Optional commands executed on the server when the arena ends.
     * Supports {player}, {score}, {difficulty} placeholders.
     * Omit entirely to run no reward commands.
     */
    public StageRewards rewards = new StageRewards();
}
