package mc.sayda.bullethell.pattern;

/**
 * Runtime data for a single bullet type, loaded from {@code data/bullethell/bullet_types.json}.
 * All rendering and collision parameters live here so they can be hot-reloaded in test mode
 * without restarting the game.
 */
public final class BulletTypeData {

    /** ARGB tint color applied to the bullet sprite (only when {@link #applyTint} is true). */
    public final int color;
    /** Base draw/collision radius in arena units (non-{@link #lineHit} bullets). */
    public final float radius;
    /**
     * Multiplies {@code radius × pool hitScale} for round bullets only. Ignored for
     * {@link #lineHit} collision (use {@link #lineCollisionHalfWidth} instead).
     */
    public final float hitboxMul;
    /** Texture filename without path or extension, e.g. {@code "orb"} → {@code textures/bullets/orb.png}. */
    public final String texture;
    /**
     * For {@link #lineHit} types: multiplies both visual half-length and half-width after pool scales.
     * Does not affect collision.
     */
    public final float textureScale;
    /** Source PNG size in pixels (16 for all standard bullets, 32 for large orbs). */
    public final int sourceSize;
    /**
     * Rotation offset for directional sprites (degrees). {@code null} means the sprite is
     * non-directional and will always be drawn axis-aligned.
     */
    public final Float baseAngleDeg;
    /** When false the sprite is drawn untinted (PNG color preserved). */
    public final boolean applyTint;
    /** When true, collision uses a finite line-segment test along velocity instead of a circle. */
    public final boolean lineHit;
    /**
     * Half-length of the collision segment along bullet velocity (arena units). Effective length is
     * {@code lineCollisionHalfLength × pool visScale} (pattern {@code bulletScale}).
     */
    public final float lineCollisionHalfLength;
    /**
     * Half-width of the collision strip perpendicular to velocity (arena units). Effective width is
     * {@code lineCollisionHalfWidth × pool hitScale} (pattern {@code hitboxScale}).
     */
    public final float lineCollisionHalfWidth;
    /**
     * Visual half-length along velocity (arena units). If {@code 0}, {@link #lineCollisionHalfLength}
     * is used for drawing. Effective: {@code base × pool visScale × textureScale}.
     */
    public final float lineVisualHalfLength;
    /** Visual half-width perpendicular to velocity (arena units). If {@code 0},
     * {@link #lineCollisionHalfWidth} is used. Effective: {@code base × pool hitScale × textureScale}.
     */
    public final float lineVisualHalfWidth;

    /** When true, player focus-fire and bombs using HOMING_USE_TYPE_DEFAULT will steer towards enemies (e.g. Reimu amulets). */
    public final boolean homing;
    /** When true, this bullet will freeze in place during Sakuya's time stop. */
    public final boolean sakuyaBlade;

    public BulletTypeData(int color, float radius, float hitboxMul, String texture,
            float textureScale, int sourceSize, Float baseAngleDeg,
            boolean applyTint, boolean lineHit,
            float lineCollisionHalfLength, float lineCollisionHalfWidth,
            float lineVisualHalfLength, float lineVisualHalfWidth,
            boolean homing, boolean sakuyaBlade) {
        this.color = color;
        this.radius = radius;
        this.hitboxMul = hitboxMul;
        this.texture = texture;
        this.textureScale = textureScale;
        this.sourceSize = sourceSize;
        this.baseAngleDeg = baseAngleDeg;
        this.applyTint = applyTint;
        this.lineHit = lineHit;
        this.lineCollisionHalfLength = lineCollisionHalfLength;
        this.lineCollisionHalfWidth = lineCollisionHalfWidth;
        this.lineVisualHalfLength = lineVisualHalfLength;
        this.lineVisualHalfWidth = lineVisualHalfWidth;
        this.homing = homing;
        this.sakuyaBlade = sakuyaBlade;
    }
}
