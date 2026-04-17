package mc.sayda.bullethell.boss;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Root JSON for {@code data/bullethell/fairy_waves/catalog.json}.
 */
public class FairyWaveCatalog {

    /**
     * Named lists of catalog entries (e.g. {@code "default"}). Stages pick a set
     * via {@link FairyRushDefinition#catalogId}.
     */
    public Map<String, List<FairyWaveCatalogEntry>> sets = new HashMap<>();

    public List<FairyWaveCatalogEntry> entriesForSet(String catalogId) {
        if (catalogId == null || catalogId.isBlank()) {
            return sets.getOrDefault("default", List.of());
        }
        List<FairyWaveCatalogEntry> list = sets.get(catalogId);
        if (list != null && !list.isEmpty()) {
            return list;
        }
        return sets.getOrDefault("default", List.of());
    }

    /** Deep-copy entry lists so callers cannot mutate the cached catalog. */
    public FairyWaveCatalog copy() {
        FairyWaveCatalog c = new FairyWaveCatalog();
        for (Map.Entry<String, List<FairyWaveCatalogEntry>> e : sets.entrySet()) {
            if (e.getValue() == null) {
                continue;
            }
            List<FairyWaveCatalogEntry> copyList = new ArrayList<>();
            for (FairyWaveCatalogEntry src : e.getValue()) {
                FairyWaveCatalogEntry d = new FairyWaveCatalogEntry();
                d.id = src.id;
                d.intensity = src.intensity;
                d.minDifficulty = src.minDifficulty;
                d.maxDifficulty = src.maxDifficulty;
                d.weight = src.weight;
                d.durationHintTicks = src.durationHintTicks;
                copyList.add(d);
            }
            c.sets.put(e.getKey(), copyList);
        }
        return c;
    }
}
