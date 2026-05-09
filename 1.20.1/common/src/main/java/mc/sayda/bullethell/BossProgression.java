package mc.sayda.bullethell;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import mc.sayda.bullethell.arena.DifficultyConfig;
import mc.sayda.bullethell.boss.BossLoader;
import mc.sayda.bullethell.boss.BossProgressionLoader;
import mc.sayda.bullethell.boss.StageLoader;
import mc.sayda.bullethell.debug.BHDebugMode;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.advancements.Advancement;

/**
 * Per-player boss clear progression + difficulty-gated challenge checks.
 *
 * Source of truth: Minecraft advancements ({@code bullethell:progression/<bossId>_<difficulty>}).
 * No scoreboard tags are written — advancements are the only persistent state.
 * Character unlock checks (CharacterUnlocks) use the identical advancement naming scheme
 * so beating a boss immediately unlocks the matching character at that difficulty.
 */
public final class BossProgression {

    private static final String ROOT_ADV = "bullethell:progression/root";
    private static final String ADV_PREFIX = "bullethell:progression/";


    private BossProgression() {
    }

    public static int getMaxClearedDifficultyOrdinal(ServerPlayer player, String bossId) {
        if (player == null || bossId == null || bossId.isBlank())
            return -1;
        int best = -1;
        DifficultyConfig[] diffs = DifficultyConfig.values();
        for (int i = 0; i < diffs.length; i++) {
            String advId = ADV_PREFIX + bossId + "_" + diffs[i].name().toLowerCase();
            if (hasAdvancement(player, advId))
                best = Math.max(best, i);
        }
        return best;
    }

    private static boolean hasAdvancement(ServerPlayer player, String advId) {
        if (advId == null) return false;
        ResourceLocation rl = new ResourceLocation(advId);
        Advancement adv = player.server.getAdvancements().getAdvancement(rl);
        if (adv == null)
            return false;
        return player.getAdvancements().getOrStartProgress(adv).isDone();
    }

    /** Record a boss clear and grant all advancement tiers up to that difficulty. */
    public static boolean grantClearThroughDifficulty(ServerPlayer player, String bossId, DifficultyConfig difficulty) {
        if (player == null || difficulty == null || bossId == null || bossId.isBlank())
            return false;
        int cur = getMaxClearedDifficultyOrdinal(player, bossId);
        int next = Math.max(cur, difficulty.ordinal());
        if (next == cur)
            return false;
        grantAdvancementsThrough(player, bossId, next);
        return true;
    }

    private static void grantAdvancementsThrough(ServerPlayer player, String bossId, int maxOrdinal) {
        grantAdvancement(player, ROOT_ADV);
        DifficultyConfig[] diffs = DifficultyConfig.values();
        for (int i = 0; i <= maxOrdinal && i < diffs.length; i++) {
            String id = ADV_PREFIX + bossId + "_" + diffs[i].name().toLowerCase();
            grantAdvancement(player, id);
        }
    }

    /** Ensures the progression tab is visible in the advancement UI. */
    public static void ensureRootAdvancement(ServerPlayer player) {
        if (player == null)
            return;
        grantAdvancement(player, ROOT_ADV);
    }

    private static void grantAdvancement(ServerPlayer player, String advId) {
        ResourceLocation rl = new ResourceLocation(advId);
        Advancement adv = player.server.getAdvancements().getAdvancement(rl);
        if (adv == null)
            return;
        PlayerAdvancements pa = player.getAdvancements();
        for (String criterion : pa.getOrStartProgress(adv).getRemainingCriteria()) {
            pa.award(adv, criterion);
        }
    }

    /** Highest difficulty allowed for challenging this boss by prerequisite clears. */
    public static int maxAllowedDifficultyOrdinal(ServerPlayer player, String bossId) {
        if (player != null && BHDebugMode.isGodMode(player.getUUID()))
            return DifficultyConfig.LUNATIC.ordinal();
        List<String> required = BossProgressionLoader.prerequisites(bossId);
        if (required == null || required.isEmpty())
            return DifficultyConfig.LUNATIC.ordinal();
        int cap = DifficultyConfig.LUNATIC.ordinal();
        for (String reqBoss : required) {
            cap = Math.min(cap, getMaxClearedDifficultyOrdinal(player, reqBoss));
        }
        return cap;
    }

    public static DifficultyConfig maxAllowedDifficulty(ServerPlayer player, String bossId) {
        int ord = maxAllowedDifficultyOrdinal(player, bossId);
        if (ord < 0)
            return null;
        return DifficultyConfig.fromId(ord);
    }

    public static boolean canChallengeBoss(ServerPlayer player, String bossId, DifficultyConfig difficulty) {
        if (difficulty == null)
            return true;
        return difficulty.ordinal() <= maxAllowedDifficultyOrdinal(player, bossId);
    }

    public static boolean canChallengeStage(ServerPlayer player, String stageId, DifficultyConfig difficulty) {
        String bossId = null;
        try {
            bossId = StageLoader.load(stageId).bossId;
        } catch (Exception ignored) {
        }
        if (bossId == null || bossId.isBlank())
            return true;
        return canChallengeBoss(player, bossId, difficulty);
    }

    public static String requirementSummary(String bossId) {
        return BossProgressionLoader.requirementSummary(bossId);
    }

    /** Debug/status helper if needed for command/UI later. */
    public static Map<String, Integer> snapshot(ServerPlayer player) {
        Map<String, Integer> out = new LinkedHashMap<>();
        Set<String> bosses = new LinkedHashSet<>(BossLoader.allBossIds());
        for (String stageId : StageLoader.REGISTERED_IDS) {
            try {
                bosses.add(StageLoader.load(stageId).bossId);
            } catch (Exception ignored) {
            }
        }
        for (String bossId : bosses) {
            out.put(bossId, getMaxClearedDifficultyOrdinal(player, bossId));
        }
        return out;
    }
}
