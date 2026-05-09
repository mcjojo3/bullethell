package mc.sayda.bullethell.boss;

/**
 * Optional spinning background aura drawn behind the boss sprite.
 * Generalizes Sanae's Star of David into a per-boss JSON block.
 *
 * JSON example (inside a boss JSON file):
 * <pre>
 * "aura": {
 *   "texture": "textures/star_david.png",
 *   "spinDegsPerTick": 13.8,
 *   "baseScale": 2.85,
 *   "breathAmplitude": 0.10,
 *   "breathFrequency": 0.12,
 *   "growTicks": 20,
 *   "textureSize": 256,
 *   "tintR": 1.0,
 *   "tintG": 0.0,
 *   "tintB": 0.0,
 *   "tintA": 0.85
 * }
 * </pre>
 *
 * The aura is invisible during dialog intro and grows from nothing to full size
 * over {@link #growTicks} ticks once the battle begins.
 * Leave {@code texture} empty (the default) to disable the aura entirely.
 */
public class BossAuraConfig {

    /**
     * Texture path within the mod namespace, e.g. {@code "textures/star_david.png"}.
     * Empty string (default) disables the aura.
     */
    public String texture = "";

    /** Rotation speed in degrees per game tick. Positive = clockwise on screen. */
    public float spinDegsPerTick = 10f;

    /**
     * Size multiplier relative to the boss sprite half-size.
     * 2.85 makes the aura roughly 3× the boss sprite radius.
     */
    public float baseScale = 2.85f;

    /**
     * Amplitude of the breathing scale oscillation (0 = no breathing).
     * The rendered size is multiplied by {@code 1 + breathAmplitude * sin(t * breathFrequency)}.
     */
    public float breathAmplitude = 0.10f;

    /** Angular frequency of the breathing cycle in radians per tick. */
    public float breathFrequency = 0.12f;

    /**
     * Ticks to ease from invisible (scale 0) to full size once the battle starts.
     * Uses a quadratic ease-out curve.
     */
    public int growTicks = 20;

    /** Width and height of the source texture in pixels (must be a power of two). */
    public int textureSize = 256;

    // ---------------------------------------------------------------- tint

    public float tintR = 1f;
    public float tintG = 1f;
    public float tintB = 1f;
    public float tintA = 0.85f;

    /** Returns true when this aura is enabled (texture is set). */
    public boolean isEnabled() {
        return texture != null && !texture.isEmpty();
    }
}
