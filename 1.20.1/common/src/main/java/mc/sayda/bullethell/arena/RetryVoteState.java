package mc.sayda.bullethell.arena;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A finished co-op run that its participants can restart together.
 *
 * Retrying alone is immediate, but a party's rematch belongs to everyone in it, so the
 * end screen's RETRY button collects a vote instead: the run restarts once every
 * participant has asked for it. Anyone leaving the results screen dissolves the group
 * and whoever is left falls back to the ordinary solo retry, so nobody waits on a vote
 * that is never coming.
 */
public final class RetryVoteState {

    /** One finished run, its roster frozen at the moment it ended. */
    public static final class Group {

        public final UUID hostUuid;
        /** Everyone who played, host first. */
        public final List<UUID> members;
        /** Who played what, so the rematch deals out the same characters. */
        public final Map<UUID, String> characters;
        public final String stageId;
        public final DifficultyConfig difficulty;

        private final Set<UUID> votes = ConcurrentHashMap.newKeySet();

        private Group(UUID hostUuid, List<UUID> members, Map<UUID, String> characters,
                String stageId, DifficultyConfig difficulty) {
            this.hostUuid = hostUuid;
            this.members = List.copyOf(members);
            this.characters = Map.copyOf(characters);
            this.stageId = stageId;
            this.difficulty = difficulty;
        }

        public int total() { return members.size(); }

        public int voteCount() { return votes.size(); }

        public boolean hasVoted(UUID uuid) { return votes.contains(uuid); }

        public boolean everyoneVoted() { return votes.size() >= members.size(); }

        /** @return true when this was a new vote rather than a repeated press */
        public boolean vote(UUID uuid) { return members.contains(uuid) && votes.add(uuid); }
    }

    private static final Map<UUID, Group> BY_MEMBER = new ConcurrentHashMap<>();

    private RetryVoteState() {}

    /**
     * Opens a vote for a finished run.
     *
     * @return the group, or null when there was no party to vote - a solo run retries
     *         on the spot and needs none of this
     */
    public static Group open(UUID hostUuid, List<UUID> members, Map<UUID, String> characters,
            String stageId, DifficultyConfig difficulty) {
        if (hostUuid == null || members == null || members.size() < 2) return null;
        Group group = new Group(hostUuid, members, characters, stageId, difficulty);
        for (UUID member : group.members) BY_MEMBER.put(member, group);
        return group;
    }

    public static Group get(UUID uuid) {
        return uuid == null ? null : BY_MEMBER.get(uuid);
    }

    /**
     * Forgets this group. Members who have since joined a newer one keep it - only
     * entries still pointing at this exact group are removed.
     */
    public static void drop(Group group) {
        if (group == null) return;
        for (UUID member : group.members) BY_MEMBER.remove(member, group);
    }
}
