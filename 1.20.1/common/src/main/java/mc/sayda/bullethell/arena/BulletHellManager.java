package mc.sayda.bullethell.arena;

import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side registry of all active ArenaContexts.
 * One context per participating player; multiple contexts = splitscreen /
 * multiplayer.
 *
 * Co-op: a participant joins the host's arena. playerToMatch maps participant
 * UUID → host UUID.
 */
public class BulletHellManager {

    public static final BulletHellManager INSTANCE = new BulletHellManager();

    private final Map<UUID, ArenaContext> arenas = new ConcurrentHashMap<>();
    /** participant UUID → host UUID (not present for hosts themselves). */
    private final Map<UUID, UUID> playerToMatch = new ConcurrentHashMap<>();
    /** host UUID → list of accepted participant info (pre-arena lobby). */
    private final Map<UUID, List<ParticipantInfo>> pendingInvites = new ConcurrentHashMap<>();

    public record ParticipantInfo(UUID uuid, mc.sayda.bullethell.boss.CharacterDefinition charDef) {}

    private BulletHellManager() {
    }

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

    /**
     * Starts an arena for the host and applies their {@link mc.sayda.bullethell.entity.BHAttributes}
     * bonuses to starting lives/bombs.
     */
    public ArenaContext startArena(ServerPlayer host, DifficultyConfig difficulty,
            String stageId, String characterId) {
        ArenaContext ctx = new ArenaContext(host.getUUID(), difficulty, stageId, characterId, host);
        arenas.put(host.getUUID(), ctx);
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
     * 
     * @param participantUuid the joining player's UUID
     * @param hostUuid        the host player's UUID (must already have an active
     *                        arena)
     * @param charDef         character definition for the joining player
     * @param participant     joining player (for attribute bonuses)
     */
    public void joinMatch(UUID participantUuid, UUID hostUuid,
            mc.sayda.bullethell.boss.CharacterDefinition charDef, ServerPlayer participant) {
        ArenaContext ctx = arenas.get(hostUuid);
        if (ctx == null)
            return;
        ctx.addCoopPlayer(participantUuid, charDef, participant);
        playerToMatch.put(participantUuid, hostUuid);
        // Grant spawn invulnerability so the joining player doesn't die instantly
        mc.sayda.bullethell.arena.PlayerState2D ps = ctx.getPlayerState(participantUuid);
        if (ps != null)
            ps.invulnTicks = mc.sayda.bullethell.arena.PlayerState2D.INVULN_TICKS;
    }

    /** Remove a co-op participant from their current match. */
    public void leaveMatch(UUID participantUuid) {
        UUID hostUuid = playerToMatch.remove(participantUuid);
        if (hostUuid != null) {
            ArenaContext ctx = arenas.get(hostUuid);
            if (ctx != null)
                ctx.removeCoopPlayer(participantUuid);
        }
    }

    // ---------------------------------------------------------------- lobby (pre-arena)

    public void addPendingInvite(UUID hostUuid, ParticipantInfo info) {
        pendingInvites.computeIfAbsent(hostUuid, k -> new ArrayList<>()).add(info);
    }

    public List<ParticipantInfo> getAndClearPendingInvites(UUID hostUuid) {
        return pendingInvites.remove(hostUuid);
    }

    public void removePendingInvite(UUID participantUuid) {
        for (var list : pendingInvites.values()) {
            list.removeIf(p -> p.uuid().equals(participantUuid));
        }
    }

    // ---------------------------------------------------------------- query

    /**
     * Get the arena for any participant - host or co-op player.
     * Returns the host's own arena if they are the host, or the host's arena
     * if this UUID is a co-op participant.
     */
    public ArenaContext getArenaForPlayer(UUID uuid) {
        ArenaContext direct = arenas.get(uuid);
        if (direct != null)
            return direct;
        UUID hostUuid = playerToMatch.get(uuid);
        return hostUuid != null ? arenas.get(hostUuid) : null;
    }

    /**
     * True if the UUID is either a host with an active arena OR a co-op
     * participant.
     */
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
