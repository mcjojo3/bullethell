package mc.sayda.bullethell.pattern;

/** Bullet visual type. id == ordinal - safe to store in BulletPool float array. */
public enum BulletType {

    ORB        (0xFFFF4444, 4f),  // red
    STAR       (0xFF44AAFF, 4f),  // blue
    RICE       (0xFFFFFF44, 3f),  // yellow (thin)
    LASER_HEAD (0xFFFF44FF, 6f),  // magenta
    BUBBLE     (0xFF44FFAA, 5f),  // teal
    PLAYER_SHOT(0xFF88FF44, 3f),  // green - player-fired bullets
    GOLD       (0xFFFFDD00, 4f),  // golden yellow - Marisa star bullets
    SPARK      (0xFFFFFF88, 8f),  // bright white-yellow - Master Spark / Final Spark
    HOMING_ORB (0xFFFF88FF, 6f),  // magenta/purple - Reimu's homing orbs
    KUNAI      (0xFF88CCFF, 4f);  // light blue - Sakuya's knives

    public final int   color;
    public final float radius;

    BulletType(int color, float radius) {
        this.color  = color;
        this.radius = radius;
    }

    private static final BulletType[] VALUES = values();

    public static BulletType fromId(int id) {
        return (id >= 0 && id < VALUES.length) ? VALUES[id] : ORB;
    }

    public int getId() { return ordinal(); }
}
