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
 * The host owns the run settings (stage + difficulty); every member owns their own
 * character choice and ready flag. Nothing here simulates - the arena is built from
 * this only once {@link #canStart()} holds and the host presses start.
 *
 * Replaces the old invisible pending-invite list: members can see each other, see what
 * is about to be played, and opt in explicitly.
 */
public final class LobbySession {

    public static final class Member {
        public final UUID uuid;
        public String name;
        public String characterId = "";
        public boolean ready;

        Member(UUID uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }

        /** A member counts as ready only once they have actually picked someone. */
        public boolean isReady() {
            return ready && !characterId.isBlank();
        }
    }

    public final UUID id = UUID.randomUUID();
    public final UUID hostUuid;

    /** Insertion-ordered so the roster is stable on screen; host is always first. */
    private final Map<UUID, Member> members = new LinkedHashMap<>();

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

    // ---------------------------------------------------------------- start gate

    /** Everyone has picked a character and pressed ready. */
    public boolean canStart() {
        if (starting || members.isEmpty()) return false;
        if (stageId == null || stageId.isBlank()) return false;
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
