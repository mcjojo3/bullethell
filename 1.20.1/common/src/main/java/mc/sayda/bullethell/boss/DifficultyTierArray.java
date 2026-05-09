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

    // ---------------------------------------------------------------- isValid

    public static boolean isValid(int[] a)     { return a != null && a.length >= COUNT; }
    public static boolean isValid(float[] a)   { return a != null && a.length >= COUNT; }
    public static boolean isValid(long[] a)    { return a != null && a.length >= COUNT; }
    public static boolean isValid(boolean[] a) { return a != null && a.length >= COUNT; }
    public static boolean isValid(String[] a)  { return a != null && a.length >= COUNT; }

    // ---------------------------------------------------------------- normalize (pad-with-last to COUNT)

    public static int[] normalizeInt(int[] a) {
        if (a == null || a.length == 0) return null;
        if (a.length >= COUNT) return java.util.Arrays.copyOf(a, COUNT);
        int[] out = new int[COUNT];
        int last = a[a.length - 1];
        for (int i = 0; i < COUNT; i++) out[i] = i < a.length ? a[i] : last;
        return out;
    }

    public static float[] normalizeFloat(float[] a) {
        if (a == null || a.length == 0) return null;
        if (a.length >= COUNT) return java.util.Arrays.copyOf(a, COUNT);
        float[] out = new float[COUNT];
        float last = a[a.length - 1];
        for (int i = 0; i < COUNT; i++) out[i] = i < a.length ? a[i] : last;
        return out;
    }

    public static long[] normalizeLong(long[] a) {
        if (a == null || a.length == 0) return null;
        if (a.length >= COUNT) return java.util.Arrays.copyOf(a, COUNT);
        long[] out = new long[COUNT];
        long last = a[a.length - 1];
        for (int i = 0; i < COUNT; i++) out[i] = i < a.length ? a[i] : last;
        return out;
    }

    public static String[] normalizeString(String[] a) {
        if (a == null || a.length == 0) return null;
        if (a.length >= COUNT) return java.util.Arrays.copyOf(a, COUNT);
        String[] out = new String[COUNT];
        String last = a[a.length - 1];
        for (int i = 0; i < COUNT; i++) out[i] = i < a.length ? a[i] : last;
        return out;
    }

    // ---------------------------------------------------------------- pick (array → value at difficulty)

    public static int pickInt(int[] a, int difficultyOrdinal, int fallback) {
        if (!isValid(a)) return fallback;
        return a[clampOrdinal(difficultyOrdinal)];
    }

    public static float pickFloat(float[] a, int difficultyOrdinal, float fallback) {
        if (!isValid(a)) return fallback;
        return a[clampOrdinal(difficultyOrdinal)];
    }

    public static long pickLong(long[] a, int difficultyOrdinal, long fallback) {
        if (!isValid(a)) return fallback;
        return a[clampOrdinal(difficultyOrdinal)];
    }

    static int clampOrdinal(int difficultyOrdinal) {
        if (difficultyOrdinal < 0) return 0;
        if (difficultyOrdinal > COUNT - 1) return COUNT - 1;
        return difficultyOrdinal;
    }

    // ---------------------------------------------------------------- normalizeBossDefinition

    /** Normalize all tier arrays on a boss definition (classpath + dev load). */
    public static void normalizeBossDefinition(BossDefinition def) {
        if (def == null || def.phases == null) return;
        for (PhaseDefinition ph : def.phases) {
            if (ph == null) continue;
            TierJson.normalize(ph.byDifficulty);
            normalizePatternSteps(ph.attacks);
            if (ph.emitters != null) {
                for (BossEmitterDefinition em : ph.emitters) {
                    if (em != null) normalizePatternSteps(em.attacks);
                }
            }
        }
    }

    private static void normalizePatternSteps(List<PatternStep> steps) {
        if (steps == null) return;
        for (PatternStep s : steps) {
            if (s == null) continue;
            TierJson.normalize(s.byDifficulty);
        }
    }
}
