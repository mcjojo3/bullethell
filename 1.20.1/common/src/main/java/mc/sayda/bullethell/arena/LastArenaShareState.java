package mc.sayda.bullethell.arena;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Last finished arena result per player, for optional global chat share from select screens. */
public final class LastArenaShareState {

    private static final Map<UUID, ArenaEndShareSnapshot> LAST = new ConcurrentHashMap<>();

    private LastArenaShareState() {}

    public static void record(UUID playerId, ArenaEndShareSnapshot snapshot) {
        if (playerId != null && snapshot != null)
            LAST.put(playerId, snapshot);
    }

    public static ArenaEndShareSnapshot get(UUID playerId) {
        return playerId == null ? null : LAST.get(playerId);
    }
}
