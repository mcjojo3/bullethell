package mc.sayda.bullethell;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import mc.sayda.bullethell.arena.DifficultyConfig;
import mc.sayda.bullethell.boss.CharacterLoader;
import mc.sayda.bullethell.debug.BHDebugMode;
import net.minecraft.server.level.ServerPlayer;

/** Server-side character unlock state backed by progression advancements. */
public final class CharacterUnlocks {

    private static final String ADV_PREFIX = "bullethell:progression/";
    private static final String DEFAULT_CHAR_ID = "reimu";

    private CharacterUnlocks() {
    }

    private static int defaultMaxDifficultyOrdinal(String characterId) {
        return DEFAULT_CHAR_ID.equals(characterId) ? DifficultyConfig.LUNATIC.ordinal() : -1;
    }

    private static String advancementId(String characterId, DifficultyConfig difficulty) {
        return ADV_PREFIX + characterId + "_boss_" + difficulty.name().toLowerCase();
    }

    private static Advancement getAdvancement(ServerPlayer player, String advancementId) {
        if (player == null || player.server == null)
            return null;
        return player.server.getAdvancements().getAdvancement(new ResourceLocation(advancementId));
    }

    private static boolean hasAdvancement(ServerPlayer player, String advancementId) {
        Advancement adv = getAdvancement(player, advancementId);
        if (adv == null)
            return false;
        return player.getAdvancements().getOrStartProgress(adv).isDone();
    }

    private static void grantAdvancement(ServerPlayer player, String advancementId) {
        Advancement adv = getAdvancement(player, advancementId);
        if (adv == null)
            return;
        var pa = player.getAdvancements();
        for (String criterion : pa.getOrStartProgress(adv).getRemainingCriteria()) {
            pa.award(adv, criterion);
        }
    }

    private static void revokeAdvancement(ServerPlayer player, String advancementId) {
        Advancement adv = getAdvancement(player, advancementId);
        if (adv == null)
            return;
        var pa = player.getAdvancements();
        java.util.ArrayList<String> completed = new java.util.ArrayList<>();
        for (String criterion : pa.getOrStartProgress(adv).getCompletedCriteria()) {
            completed.add(criterion);
        }
        for (String criterion : completed) {
            pa.revoke(adv, criterion);
        }
    }

    public static int getMaxUnlockedDifficultyOrdinal(ServerPlayer player, String characterId) {
        if (player == null || characterId == null || characterId.isBlank())
            return -1;
        if (BHDebugMode.isGodMode(player.getUUID()))
            return DifficultyConfig.LUNATIC.ordinal();
        int best = defaultMaxDifficultyOrdinal(characterId);
        DifficultyConfig[] diffs = DifficultyConfig.values();
        for (int i = 0; i < diffs.length; i++) {
            if (hasAdvancement(player, advancementId(characterId, diffs[i]))) {
                best = Math.max(best, i);
            }
        }
        return best;
    }

    public static boolean isUnlockedAny(ServerPlayer player, String characterId) {
        return getMaxUnlockedDifficultyOrdinal(player, characterId) >= 0;
    }

    public static boolean isUnlockedFor(ServerPlayer player, String characterId, DifficultyConfig difficulty) {
        if (difficulty == null)
            return isUnlockedAny(player, characterId);
        return getMaxUnlockedDifficultyOrdinal(player, characterId) >= difficulty.ordinal();
    }

    /** Unlock this character for the given difficulty and all easier ones. */
    public static boolean grantThroughDifficulty(ServerPlayer player, String characterId, DifficultyConfig difficulty) {
        if (player == null || difficulty == null || characterId == null || characterId.isBlank())
            return false;
        int cur = getMaxUnlockedDifficultyOrdinal(player, characterId);
        int next = Math.max(cur, difficulty.ordinal());
        if (next == cur)
            return false;
        DifficultyConfig[] diffs = DifficultyConfig.values();
        for (int i = 0; i <= next && i < diffs.length; i++) {
            grantAdvancement(player, advancementId(characterId, diffs[i]));
        }
        BossProgression.ensureRootAdvancement(player);
        return true;
    }

    /** Admin helper: unlock (all difficulties) or lock (none) via advancement grant/revoke. */
    public static void setAdminUnlocked(ServerPlayer player, String characterId, boolean unlocked) {
        if (player == null || characterId == null || characterId.isBlank())
            return;
        if (DEFAULT_CHAR_ID.equals(characterId)) {
            if (unlocked) {
                grantThroughDifficulty(player, characterId, DifficultyConfig.LUNATIC);
            }
            return;
        }
        DifficultyConfig[] diffs = DifficultyConfig.values();
        if (unlocked) {
            for (DifficultyConfig diff : diffs) {
                grantAdvancement(player, advancementId(characterId, diff));
            }
            BossProgression.ensureRootAdvancement(player);
        } else {
            for (DifficultyConfig diff : diffs) {
                revokeAdvancement(player, advancementId(characterId, diff));
            }
        }
    }

    /** Snapshot all registered characters -> max unlocked difficulty ordinal. */
    public static Map<String, Integer> snapshot(ServerPlayer player) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String id : CharacterLoader.REGISTERED_IDS) {
            out.put(id, getMaxUnlockedDifficultyOrdinal(player, id));
        }
        return out;
    }
}
