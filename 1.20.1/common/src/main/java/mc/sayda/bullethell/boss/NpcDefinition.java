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
 *   "stageId": "marisa_stage"
 * }
 * </pre>
 */
public class NpcDefinition {

    /** Unique ID — must match the filename (without .json). */
    public String id = "";

    /** Name shown above the NPC head and in the challenge dialog. */
    public String displayName = "";

    /**
     * Challenge dialog text shown when the player right-clicks the NPC.
     * Keep under ~80 characters for clean wrapping.
     */
    public String challengeText = "I challenge you to a danmaku duel!";

    /** Stage ID that starts when the player accepts the challenge. */
    public String stageId = "stage_1";
}
