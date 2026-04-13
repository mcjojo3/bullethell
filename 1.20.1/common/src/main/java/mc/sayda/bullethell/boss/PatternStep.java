package mc.sayda.bullethell.boss;

/**
 * One attack step in a phase's attack sequence.
 * Loaded from boss JSON via Gson - all fields are public for direct population.
 *
 * JSON example:
 * <pre>
 * { "pattern": "SPIRAL", "cooldown": 8, "bulletType": "ORB", "arms": 6, "speed": 2.5 }
 * { "pattern": "AIMED",  "cooldown": 12, "bulletType": "STAR", "arms": 5, "speed": 3.0, "spread": 0.20 }
 * </pre>
 */
public class PatternStep {

    /**
     * Pattern type to fire.
     * Valid values: SPIRAL, AIMED, RING, SPREAD, DENSE_RING
     */
    public String pattern = "RING";

    /** Ticks to wait after this step fires before the next step is executed. */
    public int cooldown = 20;

    /**
     * Bullet visual type name (matches {@code BulletType} enum, case-insensitive).
     * Valid values: ORB, STAR, RICE, LASER_HEAD, BUBBLE
     */
    public String bulletType = "ORB";

    /**
     * Number of arms (SPIRAL), bullets in ring (RING / DENSE_RING),
     * or fan count (AIMED / SPREAD).
     */
    public int arms = 8;

    /** Bullet speed in arena units per tick, before difficulty scaling. */
    public float speed = 2.5f;

    /** Fan spread in radians between adjacent shots. Only used by AIMED. */
    public float spread = 0.20f;

    // ---- Laser-specific fields (only used by LASER / LASER_ROTATING patterns) ----

    /** Half-width of the laser beam in arena units. Master Spark ≈ 30, thin laser ≈ 4. */
    public float laserHalfWidth = 5f;

    /** Ticks the warning indicator is shown before the beam fires. Default 40 (~2 s at 20 tps). */
    public int warnTicks = 40;

    /** Ticks the beam is active (dealing damage). Default 60 (~3 s at 20 tps). */
    public int activeTicks = 60;
}
