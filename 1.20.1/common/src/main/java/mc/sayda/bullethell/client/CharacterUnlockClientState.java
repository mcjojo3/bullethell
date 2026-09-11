package mc.sayda.bullethell.client;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import mc.sayda.bullethell.arena.DifficultyConfig;

/** Client mirror of server-side character unlock progression. */
public final class CharacterUnlockClientState {

    public static final CharacterUnlockClientState INSTANCE = new CharacterUnlockClientState();

    /** Kept in sync with {@code CharacterUnlocks.DEFAULT_UNLOCKED_CHAR_IDS} on the server. */
    private static final Set<String> DEFAULT_UNLOCKED_CHAR_IDS = Set.of("reimu", "marisa");
    private final ConcurrentHashMap<String, Integer> maxDifficultyByCharacter = new ConcurrentHashMap<>();

    private CharacterUnlockClientState() {
        resetToDefaults();
    }

    private int defaultMax(String characterId) {
        return DEFAULT_UNLOCKED_CHAR_IDS.contains(characterId) ? DifficultyConfig.LUNATIC.ordinal() : -1;
    }

    public void resetToDefaults() {
        maxDifficultyByCharacter.clear();
        for (String id : DEFAULT_UNLOCKED_CHAR_IDS)
            maxDifficultyByCharacter.put(id, DifficultyConfig.LUNATIC.ordinal());
    }

    public void applyFromNetwork(Map<String, Integer> map) {
        maxDifficultyByCharacter.clear();
        if (map != null)
            maxDifficultyByCharacter.putAll(map);
        for (String id : DEFAULT_UNLOCKED_CHAR_IDS)
            maxDifficultyByCharacter.putIfAbsent(id, DifficultyConfig.LUNATIC.ordinal());
    }

    public int getMaxDifficultyOrdinal(String characterId) {
        if (characterId == null || characterId.isBlank())
            return -1;
        return maxDifficultyByCharacter.getOrDefault(characterId, defaultMax(characterId));
    }

    public boolean isUnlockedAny(String characterId) {
        return getMaxDifficultyOrdinal(characterId) >= 0;
    }

    public boolean isUnlockedFor(String characterId, DifficultyConfig difficulty) {
        if (difficulty == null)
            return isUnlockedAny(characterId);
        return getMaxDifficultyOrdinal(characterId) >= difficulty.ordinal();
    }
}
