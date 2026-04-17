package mc.sayda.bullethell.boss;

import java.util.ArrayList;
import java.util.List;

/**
 * Top-level definition of a full stage: pre-boss enemy waves, the boss fight,
 * music, and gameplay ruleset.
 *
 * Place JSON files at:
 *   {@code data/bullethell/stages/<id>.json}
 *
 * A stage proceeds as follows:
 *   1. Waves spawn in {@link #waves} order, driven by their {@code spawnTick}.
 *   2. Once all waves have spawned AND the enemy pool is empty, the boss fight
 *      begins, loading the {@link BossDefinition} identified by {@link #bossId}.
 *   3. The boss fight runs until defeated or the player runs out of lives.
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
     * Music track ID played during the pre-boss wave section.
     * Must match a key in {@code assets/bullethell/sounds.json}.
     * Null or empty = no music during waves.
     */
    public String stageMusic = null;

    /**
     * ID of the boss definition to load when all waves are cleared.
     * Must match a file at {@code data/bullethell/bosses/<bossId>.json}.
     */
    public String bossId = "marisa_boss";

    /**
     * Optional next stage ID for continuous battles/campaign chaining.
     * If set (non-empty), clearing this stage can immediately start that stage.
     */
    public String nextStageId = "";

    /**
     * Ticks to wait after the last wave clears before starting the boss intro
     * dialogue (or boss fight if no intro dialogue is defined).
     * Gives the player a brief breathing moment and cleans the screen.
     * Default 120 ticks = 6 seconds.
     */
    public int bossIntroDelayTicks = 120;

    /**
     * Ordered list of enemy waves.  Waves are spawned when the stage tick
     * reaches each wave's {@code spawnTick}.  Must be sorted by ascending
     * {@code spawnTick}.
     */
    public List<WaveDefinition> waves = new ArrayList<>();

    /**
     * Optional procedural fairy-rush segment: random picks from
     * {@code fairy_waves/catalog.json} with ramped intensity and spacing. When
     * non-null, preamble waves should be inline only (no {@code waveRef} rows).
     */
    public FairyRushDefinition fairyRush = null;

    /**
     * Gameplay rules for this stage.  Controls drop patterns, PoC behaviour,
     * scoring, death penalty, etc.  All fields have sensible defaults - only
     * override what you want to change.
     */
    public RulesetConfig rules = new RulesetConfig();
}
