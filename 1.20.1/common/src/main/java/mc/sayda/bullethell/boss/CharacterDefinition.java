package mc.sayda.bullethell.boss;

/**
 * Data class for a playable character loaded from
 * {@code data/bullethell/characters/<id>.json}.
 *
 * All fields have sensible defaults matching Reimu's stats so missing JSON
 * keys fall back gracefully.
 */
public class CharacterDefinition {

    /** Internal ID — must match the JSON filename (without .json). */
    public String id           = "reimu";

    /** Display name shown in the character select screen. */
    public String name         = "Reimu Hakurei";

    /**
     * Texture path relative to {@code assets/bullethell/}.
     * E.g. {@code "textures/character/reimu.png"}.
     * The renderer falls back to a coloured rectangle if the file is missing.
     */
    public String texture      = "textures/character/reimu.png";

    /**
     * Tint colour (ARGB) used when the texture PNG is missing.
     * Lets the select screen show something even without art assets.
     */
    public int    tintColor    = 0xFFFF4488;

    /** Player bullet hitbox radius in arena units. Smaller = fairer. */
    public float  hitRadius    = 2.5f;

    /** Graze detection radius in arena units. */
    public float  grazeRadius  = 12.0f;

    /**
     * Item pickup radius in arena units.
     * Should be roughly the visual size of the sprite — larger than hitRadius,
     * smaller than or equal to grazeRadius.
     * Defaults to 20 (matches the previous hardcoded value).
     */
    public float  pickupRadius = 20.0f;

    /** Movement speed (arena units / tick) when not focused. */
    public float  speedNormal  = 10.0f;

    /** Movement speed (arena units / tick) while holding focus (Shift). */
    public float  speedFocused = 4.5f;

    /** Lives (continues) at the start of a run. */
    public int    startingLives = 3;

    /** Bomb stocks at the start of a run. */
    public int    startingBombs = 3;

    /**
     * Short flavour text shown in the select screen below the character name.
     * Keep to ≤40 characters so it fits without wrapping.
     */
    public String description  = "Balanced — small hitbox";
}
