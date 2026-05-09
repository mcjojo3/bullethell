package mc.sayda.bullethell.boss;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Builds and caches boss-unlock lookup tables from data already in each
 * {@link BossDefinition} (the {@code prerequisites} and {@code requirementSummary} fields).
 *
 * No separate JSON file is needed — calling {@link BossLoader#load} for every known boss
 * ID is sufficient.  The cache is invalidated whenever {@link BossLoader#invalidateAll()}
 * is called (or explicitly via {@link #invalidate()}).
 */
public final class BossProgressionLoader {

    private static final AtomicReference<Data> CACHE = new AtomicReference<>(null);

    private BossProgressionLoader() {}

    public static void invalidate() {
        CACHE.set(null);
    }

    public static List<String> prerequisites(String bossId) {
        List<String> result = data().prereqs.get(bossId);
        return result != null ? result : Collections.emptyList();
    }

    public static String requirementSummary(String bossId) {
        String s = data().summaries.get(bossId);
        return s != null ? s : "";
    }

    // ---------------------------------------------------------------- internal

    private static Data data() {
        Data d = CACHE.get();
        if (d == null) {
            d = build();
            CACHE.set(d);
        }
        return d;
    }

    private static Data build() {
        Data d = new Data();
        for (String id : BossLoader.allBossIds()) {
            try {
                BossDefinition def = BossLoader.load(id);
                if (def.prerequisites != null && !def.prerequisites.isEmpty())
                    d.prereqs.put(id, Collections.unmodifiableList(def.prerequisites));
                if (def.requirementSummary != null && !def.requirementSummary.isBlank())
                    d.summaries.put(id, def.requirementSummary);
            } catch (Exception ignored) {}
        }
        return d;
    }

    private static final class Data {
        final Map<String, List<String>> prereqs   = new HashMap<>();
        final Map<String, String>       summaries = new HashMap<>();
    }
}
