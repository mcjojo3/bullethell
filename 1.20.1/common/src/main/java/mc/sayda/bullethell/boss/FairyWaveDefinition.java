package mc.sayda.bullethell.boss;

import java.util.ArrayList;
import java.util.List;

/**
 * A reusable fairy wave template, loaded from
 * {@code data/bullethell/fairy_waves/<id>.json}.
 *
 * Stage JSON can reference a template via {@link WaveDefinition#waveRef}
 * instead of listing enemies inline, making stage files DRY and letting
 * designers build a library of tested wave shapes.
 *
 * JSON example:
 * <pre>
 * {
 *   "id": "five_top_straight",
 *   "description": "5 fairies equally spaced across the top, dropping straight down",
 *   "enemies": [
 *     { "x": 60,  "y": -20, "vx": 0, "vy": 4.0, "type": "BLUE_FAIRY" },
 *     { "x": 150, "y": -20, "vx": 0, "vy": 4.0, "type": "BLUE_FAIRY" }
 *   ]
 * }
 * </pre>
 */
public class FairyWaveDefinition {

    /** Unique ID - must match the filename (without .json). */
    public String id = "";

    /** Human-readable note; not used at runtime. */
    public String description = "";

    /** The enemies that make up this wave. */
    public List<WaveEnemy> enemies = new ArrayList<>();
}
