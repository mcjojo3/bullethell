package mc.sayda.bullethell.arena;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side registry of all active ArenaContexts.
 * One context per participating player; multiple contexts = splitscreen / multiplayer.
 *
 * Co-op: a participant joins the host's arena. playerToMatch maps participant UUID → host UUID.
 */
public class BulletHellManager {

    public static final BulletHellManager INSTANCE = new BulletHellManager();

    private final Map<UUID, ArenaContext> arenas        = new ConcurrentHashMap<>();
    /** participant UUID → host UUID (not present for hosts themselves). */
    private final Map<UUID, UUID>         playerToMatch = new ConcurrentHashMap<>();

    private BulletHellManager() {}

    // ---------------------------------------------------------------- lifecycle

    public ArenaContext startArena(UUID playerUuid, DifficultyConfig difficulty) {
        return startArena(playerUuid, difficulty, "stage_1", "reimu");
    }

    public ArenaContext startArena(UUID playerUuid, DifficultyConfig difficulty, String stageId) {
        return startArena(playerUuid, difficulty, stageId, "reimu");
    }

    public ArenaContext startArena(UUID playerUuid, DifficultyConfig difficulty,
                                   String stageId, String characterId) {
        ArenaContext ctx = new ArenaContext(playerUuid, difficulty, stageId, characterId);
        arenas.put(playerUuid, ctx);
        return ctx;
    }

    public void stopArena(UUID playerUuid) {
        ArenaContext ctx = arenas.remove(playerUuid);
        if (ctx != null) {
            // Evict any co-op participants that were in this arena
            playerToMatch.entrySet().removeIf(e -> e.getValue().equals(playerUuid));
        }
    }

    // ---------------------------------------------------------------- co-op

    /**
     * Join an existing arena as a co-op participant.
     * @param participantUuid  the joining player's UUID
     * @param hostUuid         the host player's UUID (must already have an active arena)
     * @param charDef          character definition for the joining player
     */
    public void joinMatch(UUID participantUuid, UUID hostUuid,
                          mc.sayda.bullethell.boss.CharacterDefinition charDef) {
        ArenaContext ctx = arenas.get(hostUuid);
        if (ctx == null) return;
        ctx.addCoopPlayer(participantUuid, charDef);
        playerToMatch.put(participantUuid, hostUuid);
    }

    /** Remove a co-op participant from their current match. */
    public void leaveMatch(UUID participantUuid) {
        UUID hostUuid = playerToMatch.remove(participantUuid);
        if (hostUuid != null) {
            ArenaContext ctx = arenas.get(hostUuid);
            if (ctx != null) ctx.removeCoopPlayer(participantUuid);
        }
    }

    // ---------------------------------------------------------------- query

    /**
     * Get the arena for any participant — host or co-op player.
     * Returns the host's own arena if they are the host, or the host's arena
     * if this UUID is a co-op participant.
     */
    public ArenaContext getArenaForPlayer(UUID uuid) {
        ArenaContext direct = arenas.get(uuid);
        if (direct != null) return direct;
        UUID hostUuid = playerToMatch.get(uuid);
        return hostUuid != null ? arenas.get(hostUuid) : null;
    }

    /** True if the UUID is either a host with an active arena OR a co-op participant. */
    public boolean isInMatch(UUID uuid) {
        return arenas.containsKey(uuid) || playerToMatch.containsKey(uuid);
    }

    /** @deprecated Use {@link #getArenaForPlayer(UUID)} for co-op-aware lookup. */
    public ArenaContext getArena(UUID playerUuid) {
        return arenas.get(playerUuid);
    }

    public boolean hasArena(UUID playerUuid) {
        return arenas.containsKey(playerUuid);
    }

    /** Live view of all arenas - used by the server tick event. */
    public Map<UUID, ArenaContext> getAll() {
        return arenas;
    }
}
