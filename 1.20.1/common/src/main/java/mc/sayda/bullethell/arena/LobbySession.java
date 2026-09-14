package mc.sayda.bullethell.arena;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A party waiting to start a run together.
 *
 * The stage comes from the NPC challenge the host opened the party from; the host owns
 * the difficulty, and every member owns their own character choice and ready flag.
 * Nothing here simulates - the arena is built from this only once {@link #canStart()}
 * holds and the host presses start.
 *
 * Members can see each other, see what is about to be played, and opt in explicitly.
 */
public final class LobbySession {

    public static final class Member {
        public final UUID uuid;
        public String name;
        public String characterId = "";
        public boolean ready;
        /**
         * Highest difficulty this member has unlocked the party's stage on, or {@code -1}
         * when they cannot play it at all. Recomputed by the server whenever the roster or
         * stage changes, since only the server can read advancements.
         */
        public int maxDifficultyOrdinal = -1;

        Member(UUID uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }

        /** A member counts as ready only once they have actually picked someone. */
        public boolean isReady() {
            return ready && !characterId.isBlank();
        }
    }

    /**
     * Someone asking to be let in, waiting on the host. Deliberately not a member: the
     * start checks only ever look at {@link #members}, so an unanswered request can never
     * hold up or sneak into a run.
     */
    public static final class PendingJoin {
        public final UUID uuid;
        public final String name;

        PendingJoin(UUID uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }
    }

    public final UUID id = UUID.randomUUID();
    public final UUID hostUuid;

    /** Insertion-ordered so the roster is stable on screen; host is always first. */
    private final Map<UUID, Member> members = new LinkedHashMap<>();

    /** Insertion-ordered too, so the list does not reshuffle while the host reads it. */
    private final Map<UUID, PendingJoin> pendingJoins = new LinkedHashMap<>();

    public String stageId;
    public DifficultyConfig difficulty = DifficultyConfig.NORMAL;
    /** Set once the host commits, so a late packet cannot start it twice. */
    public boolean starting;

    public LobbySession(UUID hostUuid, String hostName, String stageId) {
        this.hostUuid = hostUuid;
        this.stageId = stageId;
        add(hostUuid, hostName);
    }

    // ---------------------------------------------------------------- membership

    public Member add(UUID uuid, String name) {
        return members.computeIfAbsent(uuid, u -> new Member(u, name));
    }

    public void remove(UUID uuid) {
        members.remove(uuid);
    }

    public boolean contains(UUID uuid) {
        return members.containsKey(uuid);
    }

    public Member get(UUID uuid) {
        return members.get(uuid);
    }

    public Collection<Member> members() {
        return members.values();
    }

    public List<UUID> memberIds() {
        return new ArrayList<>(members.keySet());
    }

    public int size() {
        return members.size();
    }

    public boolean isEmpty() {
        return members.isEmpty();
    }

    public boolean isHost(UUID uuid) {
        return hostUuid.equals(uuid);
    }

    // ---------------------------------------------------------------- join requests

    public void addPendingJoin(UUID uuid, String name) {
        if (!members.containsKey(uuid)) pendingJoins.put(uuid, new PendingJoin(uuid, name));
    }

    /** Removes and returns the request, so it can only be answered once. */
    public PendingJoin takePendingJoin(UUID uuid) {
        return pendingJoins.remove(uuid);
    }

    public void removePendingJoin(UUID uuid) {
        pendingJoins.remove(uuid);
    }

    /** Live view - callers may prune it. */
    public Collection<PendingJoin> pendingJoins() {
        return pendingJoins.values();
    }

    // ---------------------------------------------------------------- start gate

    /**
     * Highest difficulty every member can play the stage on - the party is only as far
     * along as its least-progressed member. {@code -1} if anyone cannot play it at all.
     */
    public int partyCapOrdinal() {
        if (members.isEmpty()) return -1;
        int cap = Integer.MAX_VALUE;
        for (Member m : members.values()) cap = Math.min(cap, m.maxDifficultyOrdinal);
        return cap;
    }

    /** Everyone has picked a character, pressed ready, and unlocked the chosen difficulty. */
    public boolean canStart() {
        if (starting || members.isEmpty()) return false;
        if (stageId == null || stageId.isBlank()) return false;
        if (difficulty.ordinal() > partyCapOrdinal()) return false;
        for (Member m : members.values()) {
            if (!m.isReady()) return false;
        }
        return true;
    }

    public int readyCount() {
        int n = 0;
        for (Member m : members.values()) {
            if (m.isReady()) n++;
        }
        return n;
    }
}
