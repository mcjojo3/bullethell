package mc.sayda.bullethell.boss;

import java.util.ArrayList;
import java.util.List;

/**
 * Stationary extra emitter used during a boss phase.
 *
 * <p>Intended for faithful ports of ECL subs that spawn helper objects (e.g. Flandre clones/traps).
 * Emitters are purely logical: they render no sprite and exist only to fire PatternSteps.</p>
 */
public class BossEmitterDefinition {

    /** Absolute arena X position (0..ARENA_W). Can be outside for off-screen spawners. */
    public float x = 0f;

    /** Absolute arena Y position (0..ARENA_H). Can be outside for off-screen spawners. */
    public float y = 0f;

    /** Ordered list of attack steps, cycled repeatedly while this emitter is active. */
    public List<PatternStep> attacks = new ArrayList<>();
}

