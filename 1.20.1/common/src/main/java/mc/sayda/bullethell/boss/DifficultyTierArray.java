package mc.sayda.bullethell.boss;

import java.util.List;

/**
 * Boss JSON tier lists in fixed order {@code [EASY, NORMAL, HARD, LUNATIC]}, same as
 * {@link PhaseDefinition#spellDurationTicks}. Optional per field: (1) a {@code [E,M,H,L]} array on the
 * scalar key (promoted at load by {@link TierJson#promoteUnionTierFieldsOnBoss}), (2) legacy {@code *ByDifficulty}
 * arrays, (3) generic {@code byDifficulty} object - see {@link TierJson}. Tier arrays win over the scalar
 * when present (after normalization).
 */
public final class DifficultyTierArray {

    public static final int COUNT = 4;

    private DifficultyTierArray() {}

    public static boolean isValid(int[] a) {
        return a != null && a.length >= COUNT;
    }

    public static boolean isValid(float[] a) {
        return a != null && a.length >= COUNT;
    }

    public static boolean isValid(long[] a) {
        return a != null && a.length >= COUNT;
    }

    public static int[] normalizeInt(int[] a) {
        return normalizeIntPad(a);
    }

    public static float[] normalizeFloat(float[] a) {
        return normalizeFloatPad(a);
    }

    public static long[] normalizeLong(long[] a) {
        return normalizeLongPad(a);
    }

    private static int[] normalizeIntPad(int[] a) {
        if (a == null || a.length == 0)
            return null;
        if (a.length >= COUNT)
            return java.util.Arrays.copyOf(a, COUNT);
        int[] out = new int[COUNT];
        int last = a[a.length - 1];
        for (int i = 0; i < COUNT; i++)
            out[i] = i < a.length ? a[i] : last;
        return out;
    }

    private static float[] normalizeFloatPad(float[] a) {
        if (a == null || a.length == 0)
            return null;
        if (a.length >= COUNT)
            return java.util.Arrays.copyOf(a, COUNT);
        float[] out = new float[COUNT];
        float last = a[a.length - 1];
        for (int i = 0; i < COUNT; i++)
            out[i] = i < a.length ? a[i] : last;
        return out;
    }

    private static long[] normalizeLongPad(long[] a) {
        if (a == null || a.length == 0)
            return null;
        if (a.length >= COUNT)
            return java.util.Arrays.copyOf(a, COUNT);
        long[] out = new long[COUNT];
        long last = a[a.length - 1];
        for (int i = 0; i < COUNT; i++)
            out[i] = i < a.length ? a[i] : last;
        return out;
    }

    public static int pickInt(int[] a, int difficultyOrdinal, int fallback) {
        if (!isValid(a))
            return fallback;
        int i = clampOrdinal(difficultyOrdinal);
        return a[i];
    }

    public static float pickFloat(float[] a, int difficultyOrdinal, float fallback) {
        if (!isValid(a))
            return fallback;
        int i = clampOrdinal(difficultyOrdinal);
        return a[i];
    }

    public static long pickLong(long[] a, int difficultyOrdinal, long fallback) {
        if (!isValid(a))
            return fallback;
        int i = clampOrdinal(difficultyOrdinal);
        return a[i];
    }

    static int clampOrdinal(int difficultyOrdinal) {
        if (difficultyOrdinal < 0)
            return 0;
        if (difficultyOrdinal > COUNT - 1)
            return COUNT - 1;
        return difficultyOrdinal;
    }

    public static int pickStepInt(PatternStep s, String key, int difficultyOrdinal, int fallback, int[] legacy) {
        if (isValid(legacy))
            return pickInt(legacy, difficultyOrdinal, fallback);
        return TierJson.pickInt(s == null ? null : s.byDifficulty, key, difficultyOrdinal, fallback);
    }

    public static float pickStepFloat(PatternStep s, String key, int difficultyOrdinal, float fallback,
            float[] legacy) {
        if (isValid(legacy))
            return pickFloat(legacy, difficultyOrdinal, fallback);
        return TierJson.pickFloat(s == null ? null : s.byDifficulty, key, difficultyOrdinal, fallback);
    }

    public static long pickStepLong(PatternStep s, String key, int difficultyOrdinal, long fallback, long[] legacy) {
        if (isValid(legacy))
            return pickLong(legacy, difficultyOrdinal, fallback);
        return TierJson.pickLong(s == null ? null : s.byDifficulty, key, difficultyOrdinal, fallback);
    }

    public static boolean pickStepBoolean(PatternStep s, String key, int difficultyOrdinal, boolean fallback,
            boolean[] legacy) {
        if (legacy != null && legacy.length >= COUNT)
            return legacy[clampOrdinal(difficultyOrdinal)];
        return TierJson.pickBoolean(s == null ? null : s.byDifficulty, key, difficultyOrdinal, fallback);
    }

    public static String pickStepString(PatternStep s, String key, int difficultyOrdinal, String fallback,
            String[] legacy) {
        if (legacy != null && legacy.length >= COUNT) {
            String v = legacy[clampOrdinal(difficultyOrdinal)];
            return v != null ? v : (fallback == null ? "" : fallback);
        }
        return TierJson.pickString(s == null ? null : s.byDifficulty, key, difficultyOrdinal, fallback);
    }

    /** Normalize all tier arrays on a boss definition (classpath + dev load). */
    public static void normalizeBossDefinition(BossDefinition def) {
        if (def == null || def.phases == null)
            return;
        for (PhaseDefinition ph : def.phases) {
            if (ph == null)
                continue;
            TierJson.normalize(ph.byDifficulty);
            ph.hpByDifficulty = normalizeInt(ph.hpByDifficulty);
            ph.moveSpeedByDifficulty = normalizeFloat(ph.moveSpeedByDifficulty);
            ph.patternTempoByDifficulty = normalizeFloat(ph.patternTempoByDifficulty);
            ph.spellBonusByDifficulty = normalizeLong(ph.spellBonusByDifficulty);
            normalizePatternSteps(ph.attacks);
            if (ph.emitters != null) {
                for (BossEmitterDefinition em : ph.emitters) {
                    if (em != null)
                        normalizePatternSteps(em.attacks);
                }
            }
        }
    }

    private static void normalizePatternSteps(List<PatternStep> steps) {
        if (steps == null)
            return;
        for (PatternStep s : steps) {
            if (s == null)
                continue;
            TierJson.normalize(s.byDifficulty);
            s.armsByDifficulty = normalizeInt(s.armsByDifficulty);
            s.cooldownByDifficulty = normalizeInt(s.cooldownByDifficulty);
            s.minCooldownByDifficulty = normalizeInt(s.minCooldownByDifficulty);
            s.maxScaledArmsByDifficulty = normalizeInt(s.maxScaledArmsByDifficulty);
            s.burstCountByDifficulty = normalizeInt(s.burstCountByDifficulty);
            s.burstIntervalByDifficulty = normalizeInt(s.burstIntervalByDifficulty);
            s.bulletLifetimeTicksByDifficulty = normalizeInt(s.bulletLifetimeTicksByDifficulty);
            s.segmentDurationTicksByDifficulty = normalizeInt(s.segmentDurationTicksByDifficulty);
            s.segmentVolleyIntervalTicksByDifficulty = normalizeInt(s.segmentVolleyIntervalTicksByDifficulty);
            s.rayStackDepthByDifficulty = normalizeInt(s.rayStackDepthByDifficulty);
            s.speedByDifficulty = normalizeFloat(s.speedByDifficulty);
            s.spreadByDifficulty = normalizeFloat(s.spreadByDifficulty);
            s.rayStackSpacingByDifficulty = normalizeFloat(s.rayStackSpacingByDifficulty);
            s.laserHalfWidthByDifficulty = normalizeFloat(s.laserHalfWidthByDifficulty);
            s.laserRotateAdvanceRadByDifficulty = normalizeFloat(s.laserRotateAdvanceRadByDifficulty);
            s.sprinklerAdvanceRadByDifficulty = normalizeFloat(s.sprinklerAdvanceRadByDifficulty);
            s.combCountByDifficulty = normalizeInt(s.combCountByDifficulty);
            s.orbCRowSpacingScaleByDifficulty = normalizeFloat(s.orbCRowSpacingScaleByDifficulty);
            s.orbCRowCurvatureScaleByDifficulty = normalizeFloat(s.orbCRowCurvatureScaleByDifficulty);
            s.orbCRowSpeedSlopeByDifficulty = normalizeFloat(s.orbCRowSpeedSlopeByDifficulty);
        }
    }
}
