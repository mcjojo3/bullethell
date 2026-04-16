package mc.sayda.bullethell.pattern;

/** Bullet visual type. id == ordinal - safe to store in BulletPool float array. */
public enum BulletType {

    ORB        (0xFFFF4444, 4f, 1f),  // red
    /** Blue star / curtain shots; hitbox smaller than drawn star footprint. */
    STAR       (0xFF44AAFF, 4f, 0.76f),
    /** Yellow fairy rice stream; forgiving hitbox vs thin sprite. */
    RICE       (0xFFFFFF44, 3f, 0.58f),
    LASER_HEAD (0xFFFF44FF, 6f, 1f),  // magenta
    BUBBLE     (0xFF44FFAA, 5f, 1f),  // teal
    PLAYER_SHOT(0xFF88FF44, 3f, 1f),  // green - player-fired bullets
    GOLD       (0xFFFFDD00, 4f, 1f),  // golden yellow - Marisa star bullets
    SPARK      (0xFFFFFF88, 8f, 1f),  // bright white-yellow - Master Spark / Final Spark
    HOMING_ORB (0xFFFF88FF, 6f, 1f),  // magenta/purple - Reimu's homing orbs
    /** Sakuya knives; slightly forgiving vs blade sprite. */
    KUNAI      (0xFF88CCFF, 4f, 0.82f),
    /** Thin stake sprite; keep hitbox much smaller than drawn needle. */
    NEEDLE     (0xFFFFDDDD, 2.5f, 0.48f),
    /** Deep scarlet mist / large droplets (Remilia Netherworld, curse blood). */
    SCARLET    (0xFFCC1020, 4.5f, 1f),
    /**
     * Large orb; ARGB alpha in {@link #color} is honored by the renderer (tint +
     * texture). Tune collision with JSON {@code hitboxScale} / pool hit scale.
     */
    SCARLET_LARGE (0xEECC1020, 7f, 1f),
    /** Small pill / mentos trail followers (Scarlet Meister tail pressure). */
    SCARLET_MENTOS(0xFFE03040, 2.5f, 1f),
    /**
     * Cirno / ice shards; {@code icicle.png} in renderer. Collision profile matches {@link #KUNAI}
     * (thin sprite, forgiving {@code hitboxCollisionMul}).
     */
    ICE        (0xFF66DDFF, 4f, 0.82f),
    /**
     * Sakuya-style throwing knives; {@code knife.png}. Same collision and tuning as
     * {@link #KUNAI}; tinted red in the renderer (TH-style) while {@link #KUNAI} stays cool-toned.
     */
    KNIFE      (0xFFFF5058, 4f, 0.82f);

    public final int   color;
    public final float radius;
    /**
     * Multiplies {@code BulletType#radius * BulletPool hitScale} for collision and
     * debug overlay only (not render size).
     */
    public final float hitboxCollisionMul;

    BulletType(int color, float radius, float hitboxCollisionMul) {
        this.color  = color;
        this.radius = radius;
        this.hitboxCollisionMul = hitboxCollisionMul;
    }

    private static final BulletType[] VALUES = values();

    public static BulletType fromId(int id) {
        return (id >= 0 && id < VALUES.length) ? VALUES[id] : ORB;
    }

    public int getId() { return ordinal(); }
}
