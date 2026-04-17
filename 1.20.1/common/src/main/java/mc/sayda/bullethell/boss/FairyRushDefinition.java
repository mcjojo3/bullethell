package mc.sayda.bullethell.boss;

/**
 * Procedural pre-boss fairy section: picks random patterns from a catalog with
 * intensity ramping and widened inter-wave gaps. When non-null on
 * {@link StageDefinition}, {@link mc.sayda.bullethell.arena.ArenaContext} expands
 * {@link #slotCount} waves after inline preamble rows (waves must not use
 * {@code waveRef} for the procedural segment — keep only hand-authored preamble
 * entries in {@link StageDefinition#waves}).
 */
public class FairyRushDefinition {

    /** Key into {@code catalog.json} {@code sets} map; empty uses {@code "default"}. */
    public String catalogId = "default";

    /** How many catalog-driven waves to schedule after {@link #startTick}. */
    public int slotCount = 40;

    /**
     * Arena tick (after {@link mc.sayda.bullethell.config.BullethellConfig#waveTimingMult})
     * when the first procedural wave may begin — same convention as
     * {@link WaveDefinition#spawnTick}.
     */
    public int startTick = 120;

    // --- gap between wave starts (designer ticks; divided by waveTimingMult like spawnTick)

    public int gapTicksStartMin = 72;
    public int gapTicksStartMax = 118;
    public int gapTicksEndMin = 22;
    public int gapTicksEndMax = 42;

    /** {@code LINEAR} or {@code SMOOTHSTEP} (default). */
    public String gapEasing = "SMOOTHSTEP";

    /** Extra ± random ticks added to each gap (designer space). */
    public int jitterTicks = 16;

    // --- intensity target band evolves with slot progress (0..1)

    public int intensityStartLo = 0;
    public int intensityStartHi = 2;
    public int intensityEndLo = 5;
    public int intensityEndHi = 10;

    public int breatherEvery = 8;
    public int breatherExtraTicks = 72;

    /**
     * Optional wave template id for a breather slot (single light enemy). Empty =
     * spawn one {@code YELLOW_FAIRY} at centre top.
     */
    public String breatherWaveId = "";

    /** If set, procedural picks use this seed (XOR arena seed) for reproducibility. */
    public Long shuffleSeed = null;

    /** Avoid re-picking the same catalog id for this many prior slots (0 = off). */
    public int noRepeatLast = 5;
}
