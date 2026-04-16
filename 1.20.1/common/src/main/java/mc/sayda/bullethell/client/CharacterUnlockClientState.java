package mc.sayda.bullethell.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import mc.sayda.bullethell.arena.DifficultyConfig;

/** Client mirror of server-side character unlock progression. */
public final class CharacterUnlockClientState {

    public static final CharacterUnlockClientState INSTANCE = new CharacterUnlockClientState();

    private static final String DEFAULT_CHAR_ID = "reimu";
    private final ConcurrentHashMap<String, Integer> maxDifficultyByCharacter = new ConcurrentHashMap<>();

    private CharacterUnlockClientState() {
        resetToDefaults();
    }

    private int defaultMax(String characterId) {
        return DEFAULT_CHAR_ID.equals(characterId) ? DifficultyConfig.LUNATIC.ordinal() : -1;
    }

    public void resetToDefaults() {
        maxDifficultyByCharacter.clear();
        maxDifficultyByCharacter.put(DEFAULT_CHAR_ID, DifficultyConfig.LUNATIC.ordinal());
    }

    public void applyFromNetwork(Map<String, Integer> map) {
        maxDifficultyByCharacter.clear();
        if (map != null)
            maxDifficultyByCharacter.putAll(map);
        maxDifficultyByCharacter.putIfAbsent(DEFAULT_CHAR_ID, DifficultyConfig.LUNATIC.ordinal());
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
