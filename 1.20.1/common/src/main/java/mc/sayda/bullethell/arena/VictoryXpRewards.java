package mc.sayda.bullethell.arena;

import mc.sayda.bullethell.config.BullethellConfig;

/**
 * Converts arena score + difficulty into Minecraft experience points.
 * Sub-linear (sqrt) so 2M+ runs stay modest; caps prevent excessive rewards.
 */
public final class VictoryXpRewards {

    private VictoryXpRewards() {
    }

    /**
     * @param personalScore this participant's score only (not team combined)
     */
    public static int computePoints(long personalScore, DifficultyConfig difficulty) {
        double mult = BullethellConfig.victoryXpDifficultyMult(difficulty);
        double raw = Math.sqrt(Math.max(0.0, (double) personalScore)) * BullethellConfig.VICTORY_XP_SQRT_MULT.get() * mult
                + BullethellConfig.VICTORY_XP_BASE.get();
        int v = (int) Math.floor(raw);
        int max = BullethellConfig.VICTORY_XP_MAX.get();
        if (v > max) {
            v = max;
        }
        if (v < 0) {
            v = 0;
        }
        return v;
    }
}