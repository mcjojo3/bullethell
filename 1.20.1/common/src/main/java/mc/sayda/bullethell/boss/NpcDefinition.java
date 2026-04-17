package mc.sayda.bullethell.boss;

/**
 * Data class for a BulletHell NPC, loaded from
 * {@code data/bullethell/npcs/<id>.json}.
 *
 * Example JSON:
 * <pre>
 * {
 *   "id": "marisa",
 *   "displayName": "Kirisame Marisa",
 *   "challengeText": "Heh, wanna see my ordinary magic? Come at me, ze!",
 *   "stageId": "marisa_stage",
 *   "invulnerable": false,
 *   "knockbackOnHit": 0,
 *   "knockbackResistance": -1,
 *   "seeksShade": false
 * }
 * </pre>
 */
public class NpcDefinition {

    /** Unique ID - must match the filename (without .json). */
    public String id = "";

    /** Name shown above the NPC head and in the challenge dialog. */
    public String displayName = "";

    /**
     * Challenge dialog text shown when the player right-clicks the NPC.
     * Keep under ~80 characters for clean wrapping.
     */
    public String challengeText = "I challenge you to a danmaku duel!";

    /** Stage ID that starts when the player accepts the challenge. */
    public String stageId = "marisa_stage";

    /**
     * When true, {@code hurt} applies no damage (optional knockback only if {@link #knockbackOnHit} &gt; 0).
     * Default {@code false} so NPCs are killable like normal mobs.
     */
    public boolean invulnerable = false;

    /**
     * Knockback strength when {@link #invulnerable} and damage is cancelled (melee with a direct attacker).
     * Default {@code 0} matches legacy behavior (no knockback). Set positive to enable.
     */
    public double knockbackOnHit = 0.0;

    /**
     * Base knockback resistance ({@code 0} = full knockback, {@code 1} = immune to knockback).
     * Negative means use the mod default ({@code 0}).
     */
    public float knockbackResistance = -1f;

    /**
     * When true, during bright daytime the NPC pathfinds toward lower sky light (shade).
     * Also suppresses vanilla sun-burn ignition for that NPC (not lava/campfires).
     */
    public boolean seeksShade = false;
}
