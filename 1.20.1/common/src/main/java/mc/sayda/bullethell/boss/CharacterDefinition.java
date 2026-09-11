package mc.sayda.bullethell.boss;

import java.util.List;

/**
 * Data class for a playable character loaded from
 * {@code data/bullethell/characters/<id>.json}.
 *
 * All fields have sensible defaults matching Reimu's stats so missing JSON
 * keys fall back gracefully.
 */
public class CharacterDefinition {

    /** Internal ID - must match the JSON filename (without .json). */
    public String id = "reimu";

    /** Display name shown in the character select screen. */
    public String name = "Reimu Hakurei";

    /**
     * Texture path relative to {@code assets/bullethell/}.
     * E.g. {@code "textures/character/reimu.png"}.
     * The renderer falls back to a coloured rectangle if the file is missing.
     */
    public String texture = "textures/character/reimu.png";

    /**
     * Tint colour (ARGB) used when the texture PNG is missing.
     * Lets the select screen show something even without art assets.
     */
    public int tintColor = 0xFFFF4488;

    /** Player bullet hitbox radius in arena units. Smaller = fairer. */
    public float hitRadius = 2.5f;

    /** Graze detection radius in arena units. */
    public float grazeRadius = 12.0f;

    /**
     * Item pickup radius in arena units.
     * Should be roughly the visual size of the sprite - larger than hitRadius,
     * smaller than or equal to grazeRadius.
     * Defaults to 20 (matches the previous hardcoded value).
     */
    public float pickupRadius = 20.0f;

    /**
     * Movement speed (arena units / tick at 20 tps). Baseline matches TH06 Reimu
     * (4.0 / 2.0 px/frame @ 60fps) scaled into arena space; see
     * {@code wiki/player-shots-and-references.md} (movement section).
     */
    public float speedNormal = 13.0f;

    /** Movement speed (arena units / tick) while holding focus (Shift). */
    public float speedFocused = 6.5f;

    /** Lives (continues) at the start of a run. */
    public int startingLives = 3;

    /** Bomb stocks at the start of a run. */
    public int startingBombs = 3;

    /**
     * Flavour text shown in the select screen below the character name, as two
     * author-controlled lines rather than one string the screen wraps itself - the
     * card is narrow enough that automatic wrapping broke on longer text. Keep each
     * line to ~20 characters; {@code description2} may be left blank for a one-line
     * description.
     */
    public String description1 = "Desc missing";
    /** Second description line; blank for a one-line description. */
    public String description2 = "";

    /**
     * The character's shot layout, defined in {@code data/bullethell/characters/&lt;id&gt;.json}.
     * Each character has exactly one; the list shape is kept so a future redesign can
     * reintroduce selectable types without a schema change. Only index {@code 0} is used.
     * If missing or empty, {@link CharacterLoader} uses {@link HardcodedPlayerShots}.
     */
    public List<PlayerShotOptionJson> shotOptions;

    public boolean usesDataDrivenShots() {
        return shotOptions != null && !shotOptions.isEmpty();
    }

    /** This character's shot layout, or {@code null} when none is defined. */
    public PlayerShotOptionJson shot() {
        return usesDataDrivenShots() ? shotOptions.get(0) : null;
    }

    /** Display label for the shot, e.g. on the character select screen. */
    public String shotLabel() {
        PlayerShotOptionJson o = shot();
        return (o != null && o.label != null && !o.label.isBlank()) ? o.label : "";
    }

    /** Flavour text for the shot; empty when unset. */
    public String shotDescription() {
        PlayerShotOptionJson o = shot();
        return (o != null && o.description != null) ? o.description : "";
    }

    /**
     * Volley interval in ticks when unfocused; {@code 0} = use
     * {@link PlayerState2D#SHOT_COOLDOWN_NORMAL}.
     */
    public int shotCooldownNormal = 0;

    /**
     * Volley interval in ticks when focused; {@code 0} = use
     * {@link PlayerState2D#SHOT_COOLDOWN_FOCUSED}.
     */
    public int shotCooldownFocused = 0;
}
