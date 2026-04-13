package mc.sayda.bullethell.boss;

/**
 * One enemy entry inside a wave, as loaded from stage JSON.
 *
 * JSON example:
 * <pre>
 * { "x": 120, "y": -20, "vx": 0.0, "vy": 1.5, "type": "BLUE_FAIRY" }
 * </pre>
 */
public class WaveEnemy {

    /** Spawn X position in arena space. */
    public float x = 240;

    /** Spawn Y position (use negative values to spawn off the top edge). */
    public float y = -20;

    /** Initial X velocity in arena units per tick. */
    public float vx = 0;

    /** Initial Y velocity in arena units per tick. */
    public float vy = 1.5f;

    /**
     * EnemyType name (case-insensitive).
     * Valid values: BLUE_FAIRY, RED_FAIRY, YELLOW_FAIRY, GREEN_FAIRY,
     *               LARGE_FAIRY, LARGE_RED, LARGE_YELLOW, LARGE_GREEN
     */
    public String type = "BLUE_FAIRY";

    /** Angular velocity of the velocity vector in rad/tick; 0 = straight line. */
    public float angVel = 0f;

    /** Ticks to apply angVel before stopping rotation; 0 = no arc. */
    public int arcTicks = 0;
}
