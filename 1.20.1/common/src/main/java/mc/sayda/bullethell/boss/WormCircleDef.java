package mc.sayda.bullethell.boss;

/** Per-ring configuration for the {@code WORM_CIRCLE} pattern step. */
public class WormCircleDef {
    /** Number of knives in this ring (1–20). */
    public int knifeCount = 8;
    /** Starting angle in degrees for the first knife in the ring. */
    public float startAngleDeg = 0f;
    /** Total angular span in degrees the knives cover. 360 = full circle. */
    public float spanDeg = 360f;
    /** Rotation speed in degrees per boss-AI tick. Positive = CCW, negative = CW. */
    public float spinSpeedDeg = 2f;
    /** Distance from boss centre to each knife (arena units). */
    public float orbitRadius = 80f;
    /** Bullet type name for the knives. */
    public String bulletType = "KUNAI";
    /** Visual scale multiplier vs type base radius (0 = 1). */
    public float bulletScale = 1f;
    /** Hit-radius multiplier (0 = 1). */
    public float hitboxScale = 1f;
}
