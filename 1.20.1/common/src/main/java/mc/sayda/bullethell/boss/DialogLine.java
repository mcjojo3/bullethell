package mc.sayda.bullethell.boss;

/**
 * A single line of pre-boss intro dialogue shown before the fight starts.
 *
 * Place an {@code introDialog} array inside a boss JSON file:
 * <pre>
 *   "introDialog": [
 *     { "speaker": "BOSS",   "text": "You dare challenge me?" },
 *     { "speaker": "PLAYER", "text": "I've been looking for you!" },
 *     { "speaker": "BOSS",   "text": "Then prepare yourself!" }
 *   ]
 * </pre>
 *
 * Lines auto-advance after {@link #delayTicks} ticks (20 ticks = 1 second).
 */
public class DialogLine {

    /**
     * Who is speaking.  Use "BOSS" or "PLAYER".
     * Controls which side the portrait appears on and the border colour.
     */
    public String speaker = "BOSS";

    /** The text to display. Keep to ~60 characters to fit on two lines. */
    public String text = "";

    /**
     * Ticks before this line auto-advances to the next (20 tps).
     * Default 180 = 9 seconds.  Short lines can use 120; dramatic pauses 240.
     */
    public int delayTicks = 180;
}
