package mc.sayda.bullethell.boss;

import java.util.List;
import java.util.Locale;

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

    // TH19-style controls (Z shoot, X charge): passive build when X is not held.
    /** Multiplier for passive charge while shooting (smaller = slower). */
    public float chargeRateShooting = 1.0f;
    /** Multiplier for passive charge while idle (not shooting). */
    public float chargeRateIdle = 3.0f;
    /**
     * Extra multiplier while holding X after PoFV startup - scales how fast each
     * level fills (see {@link #chargeSpeedFrames}).
     */
    public float chargeRateCharging = 5.0f;

    /**
     * Touhou 9 PoFV: frames to fill one charge level while holding X (after the
     * 9-frame startup where the bar does not move). Wiki values, e.g. Reimu 25,
     * Marisa 31.
     */
    public double chargeSpeedFrames = 31.0;

    /**
     * Touhou 9 PoFV: ticks after releasing a charge skill before a new charge can
     * build (Lv.1 attack recovery). Reimu 41, Marisa 36, etc.
     */
    public int chargeDelayAfterSkill = 41;

    /** Lives (continues) at the start of a run. */
    public int startingLives = 3;

    /** Bomb stocks at the start of a run. */
    public int startingBombs = 3;

    /**
     * Short flavour text shown in the select screen below the character name.
     * Keep to ≤40 characters so it fits without wrapping.
     */
    public String description = "Balanced - small hitbox";

    /**
     * Legacy field kept for datapacks; playable patterns are {@link #shotOptions} in the character JSON.
     */
    public String shotStyle = "generic";

    /**
     * Touhou-style shot options (A/B, …). Parallel to optional {@link #shotTypeLabels}.
     * Two or more non-blank entries enable the shot-type menu; otherwise index {@code 0} only.
     */
    public List<String> shotTypes;

    /** Display strings for the shot-type menu; indices match {@link #shotTypes}. */
    public List<String> shotTypeLabels;

    /** Short flavour text per shot type for the shot select screen; indices match {@link #shotTypes}. */
    public List<String> shotTypeDescriptions;

    /**
     * Shot layouts: define in {@code data/bullethell/characters/&lt;id&gt;.json}. If missing or empty,
     * {@link CharacterLoader} uses {@link HardcodedPlayerShots}.
     */
    public List<PlayerShotOptionJson> shotOptions;

    public boolean usesDataDrivenShots() {
        return shotOptions != null && !shotOptions.isEmpty();
    }

    /**
     * Resolves the pattern id for the given shot selection ({@code 0} = first type).
     */
    public String effectiveShotStyle(int shotTypeIndex) {
        if (shotTypes != null && !shotTypes.isEmpty()) {
            int idx = Math.max(0, Math.min(shotTypeIndex, shotTypes.size() - 1));
            String s = shotTypes.get(idx);
            if (s != null && !s.isBlank())
                return s.trim().toLowerCase(Locale.ROOT);
        }
        return shotStyle == null ? "generic" : shotStyle.trim().toLowerCase(Locale.ROOT);
    }

    /** Number of shot types offered in the select UI ({@code 1} = skip the extra screen). */
    public int shotTypeOptionCount() {
        if (usesDataDrivenShots()) {
            int n = shotOptions.size();
            return n >= 2 ? n : 1;
        }
        if (shotTypes == null || shotTypes.isEmpty())
            return 1;
        int n = 0;
        for (String s : shotTypes) {
            if (s != null && !s.isBlank())
                n++;
        }
        return n >= 2 ? n : 1;
    }

    /** Label for the shot-type menu or stats; falls back to generic A/B text. */
    public String shotTypeLabel(int shotTypeIndex) {
        if (usesDataDrivenShots() && shotTypeIndex >= 0 && shotTypeIndex < shotOptions.size()) {
            PlayerShotOptionJson o = shotOptions.get(shotTypeIndex);
            if (o != null && o.label != null && !o.label.isBlank())
                return o.label;
        }
        if (shotTypeLabels != null && shotTypeIndex >= 0 && shotTypeIndex < shotTypeLabels.size()) {
            String l = shotTypeLabels.get(shotTypeIndex);
            if (l != null && !l.isBlank())
                return l;
        }
        if (shotTypeIndex <= 0)
            return "Shot Type A";
        if (shotTypeIndex == 1)
            return "Shot Type B";
        return "Shot Type " + (shotTypeIndex + 1);
    }

    /** Description for the shot-type UI; empty when unset. */
    public String shotTypeDescription(int shotTypeIndex) {
        if (usesDataDrivenShots() && shotTypeIndex >= 0 && shotTypeIndex < shotOptions.size()) {
            PlayerShotOptionJson o = shotOptions.get(shotTypeIndex);
            if (o != null && o.description != null)
                return o.description;
        }
        if (shotTypeDescriptions != null && shotTypeIndex >= 0 && shotTypeIndex < shotTypeDescriptions.size()) {
            String d = shotTypeDescriptions.get(shotTypeIndex);
            return d != null ? d : "";
        }
        return "";
    }

    /**
     * When true, this character can move freely while time stop is active.
     * Sakuya is the canonical example; all other characters are frozen.
     */
    public boolean immuneToTimeStop = false;

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
