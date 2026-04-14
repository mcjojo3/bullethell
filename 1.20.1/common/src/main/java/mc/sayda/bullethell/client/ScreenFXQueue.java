package mc.sayda.bullethell.client;

import mc.sayda.bullethell.arena.GameEvent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.EnumMap;
import java.util.Map;

/**
 * Client-side visual effect queue.
 * Ticked every client tick; effects expire automatically.
 * BulletHellRenderer reads this to draw overlay tints and border pulses.
 */
@Environment(EnvType.CLIENT)
public class ScreenFXQueue {

    public static final ScreenFXQueue INSTANCE = new ScreenFXQueue();

    // Duration (client ticks) for each effect
    private static final Map<GameEvent, Integer> DURATION = new EnumMap<>(GameEvent.class);
    static {
        DURATION.put(GameEvent.HIT,            12);
        DURATION.put(GameEvent.DEATH,          30);
        DURATION.put(GameEvent.BOMB_USED,      20);
        DURATION.put(GameEvent.PHASE_CHANGE,   40);
        DURATION.put(GameEvent.SPELL_CAPTURED, 60);
        DURATION.put(GameEvent.SPELL_FAILED,   25);
        DURATION.put(GameEvent.GRAZE_CHAIN,     8);
        DURATION.put(GameEvent.SKILL_USED,     10);
    }

    private final Map<GameEvent, Integer> active = new EnumMap<>(GameEvent.class);

    // ---------------------------------------------------------------- push / tick

    public void push(GameEvent event) {
        Integer dur = DURATION.get(event);
        if (dur != null) active.merge(event, dur, Math::max);
    }

    public void tick() {
        active.entrySet().removeIf(e -> {
            e.setValue(e.getValue() - 1);
            return e.getValue() <= 0;
        });
    }

    // ---------------------------------------------------------------- query

    public boolean isActive(GameEvent event) { return active.containsKey(event); }

    /**
     * 0.0–1.0 intensity of an effect (1 = just triggered, 0 = expired).
     */
    public float intensity(GameEvent event) {
        Integer rem = active.get(event);
        if (rem == null) return 0f;
        Integer dur = DURATION.getOrDefault(event, 1);
        return (float) rem / dur;
    }

    public void reset() { active.clear(); }
}
