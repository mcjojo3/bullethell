package mc.sayda.bullethell.arena;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server-authoritative parameters for the most recently finished arena (retry button). */
public final class LastArenaRetryState {

    public record Params(
            String stageId,
            DifficultyConfig difficulty,
            String characterId,
            int shotTypeOrdinal,
            boolean practice,
            boolean testMode) {}

    private static final Map<UUID, Params> LAST = new ConcurrentHashMap<>();

    private LastArenaRetryState() {}

    public static void record(UUID playerId, Params params) {
        if (playerId != null && params != null)
            LAST.put(playerId, params);
    }

    public static Params get(UUID playerId) {
        return playerId == null ? null : LAST.get(playerId);
    }

    public static void remove(UUID playerId) {
        if (playerId != null)
            LAST.remove(playerId);
    }
}
