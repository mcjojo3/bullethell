package mc.sayda.bullethell.boss;

/**
 * One row in {@code data/bullethell/fairy_waves/catalog.json} — metadata for a
 * reusable wave template referenced by {@code id} (filename under fairy_waves/).
 */
public class FairyWaveCatalogEntry {

    /** Must match {@link FairyWaveDefinition#id} / template filename stem. */
    public String id = "";

    /** 0 = calm filler; higher = denser / faster / more large enemies. */
    public int intensity = 0;

    public String minDifficulty = "";
    public String maxDifficulty = "";

    /** Relative pick weight when multiple entries fit the intensity window. */
    public float weight = 1.0f;

    /**
     * Designer-time clearance after the last staggered spawn in this pattern
     * before the next wave may begin (ticks). 0 = derive a heuristic at runtime.
     */
    public int durationHintTicks = 0;
}
