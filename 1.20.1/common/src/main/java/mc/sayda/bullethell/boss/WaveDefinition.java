package mc.sayda.bullethell.boss;

import java.util.ArrayList;
import java.util.List;

/**
 * One enemy wave - a group of enemies that spawn simultaneously at a
 * specified tick offset from the start of the stage.
 *
 * JSON example:
 * <pre>
 * {
 *   "spawnTick": 120,
 *   "enemies": [
 *     { "x": 80,  "y": -20, "vx": 0,  "vy": 1.5, "type": "BLUE_FAIRY" },
 *     { "x": 240, "y": -20, "vx": 0,  "vy": 1.5, "type": "BLUE_FAIRY" }
 *   ]
 * }
 * </pre>
 */
public class WaveDefinition {

    /**
     * Tick (from arena start) at which all enemies in this wave are spawned.
     * Waves must be listed in ascending spawnTick order.
     */
    public int spawnTick = 0;

    /** All enemies that spawn at the same time. */
    public List<WaveEnemy> enemies = new ArrayList<>();
}
