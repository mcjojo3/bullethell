package mc.sayda.bullethell.boss;

/**
 * One attack step in a phase's attack sequence.
 * Loaded from boss JSON via Gson - all fields are public for direct population.
 *
 * JSON example:
 * <pre>
 * { "pattern": "SPIRAL", "cooldown": 8, "bulletType": "ORB", "arms": 6, "speed": 2.5 }
 * { "pattern": "AIMED",  "cooldown": 12, "bulletType": "STAR", "arms": 5, "speed": 3.0, "spread": 0.20 }
 * { "pattern": "AIMED_RING", "cooldown": 20, "bulletType": "GOLD", "arms": 5, "spread": 0.22, "speed": 3.0,
 *   "ringArms": 10, "ringSpeed": 1.6, "ringBulletType": "RICE" }
 * Burst (same step fires multiple times, then long {@code cooldown} before next step):
 * { "pattern": "BOUNCE", "burstCount": 2, "burstInterval": 8, "cooldown": 45, "bulletType": "KUNAI", ... }
 * Optional ECL-style tuning (applied when the boss runs this step; see {@link mc.sayda.bullethell.arena.ArenaContext}):
 * { "pattern": "RING", "cooldown": 30, "arms": 12, "speed": 2.2,
 *   "bulletLifetimeTicks": 200, "spawnOffsetX": 4, "spawnOffsetY": 0,
 *   "ringStartAngleRad": 0, "bulletAngularVelocity": 0.002 }
 * </pre>
 */
public class PatternStep {

    /**
     * Pattern type to fire.
     * Valid values: SPIRAL, AIMED, AIMED_RING, RING, RING_OFFSET, SPREAD, DENSE_RING, LASER_BEAM,
     * LASER, LASER_ROTATING, RAIN, BOUNCE, MEISTER_CYCLE (Remilia Scarlet Meister - scripted in {@code ArenaContext})
     */
    public String pattern = "RING";

    /** Ticks to wait after this step fires before the next step is executed. */
    public int cooldown = 20;

    /**
     * How many times this step fires in a row (same volley) before advancing the
     * attack rotation. {@code 0} or {@code 1} = single shot (default). {@code 2+}
     * = Touhou-style bursts: shots spaced by {@link #burstInterval} ticks, then
     * {@link #cooldown} applies before the next pattern in the phase list.
     */
    public int burstCount = 0;

    /**
     * Ticks between shots inside one burst (after the first shot). Ignored when
     * {@link #burstCount} &lt;= 1. If 0 but burstCount &gt; 1, the engine uses a small
     * default (see {@code ArenaContext}).
     */
    public int burstInterval = 0;

    /**
     * Bullet visual type name (matches {@code BulletType} enum, case-insensitive).
     * Valid values: ORB, STAR, RICE, LASER_HEAD, BUBBLE, GOLD, SPARK, HOMING_ORB, KUNAI, KNIFE,
     * NEEDLE, SCARLET, SCARLET_LARGE, SCARLET_MENTOS, ICE
     */
    public String bulletType = "ORB";

    /**
     * Number of arms (SPIRAL), bullets in ring (RING / DENSE_RING),
     * or fan count (AIMED / SPREAD).
     */
    public int arms = 8;

    /** Bullet speed in arena units per tick, before difficulty scaling. */
    public float speed = 2.5f;

    /**
     * Visual radius multiplier vs {@link mc.sayda.bullethell.pattern.BulletType#radius} (1 = default).
     * 0 or negative = 1. Used for EoSD-style large orbs that read huge but stay fair via {@link #hitboxScale}.
     */
    public float bulletScale = 0f;

    /**
     * Hit radius multiplier vs type base radius (TH large bullets: often ~0.35–0.5).
     * 0 or negative = auto: if {@link #bulletScale} &gt; 1.25 use forgiving default, else 1.
     */
    public float hitboxScale = 0f;

    /**
     * Extra multiplier on boss density for this step only (stacked after global boss density).
     * 0 or negative = ignored.
     */
    public float densityScale = 0f;

    /**
     * Hard cap on {@code arms} after difficulty scaling (0 = no cap). Keeps sparse-orb cards readable on Lunatic.
     */
    public int maxScaledArms = 0;

    /** Fan spread in radians between adjacent shots. Only used by AIMED. */
    public float spread = 0.20f;

    // ---- Optional DanmakU-style range overrides (sampled each fire) ----

    /**
     * Optional adaptive scaling profile.
     * Valid values: AUTO (default), GEOMETRY, PRECISION, BURST, SPAM.
     */
    public String scalingProfile = "";

    /** Optional weight for how strongly difficulty scales arms (0 = profile default). */
    public float armsDifficultyWeight = 0f;
    /** Optional weight for how strongly difficulty scales speed (0 = profile default). */
    public float speedDifficultyWeight = 0f;
    /** Optional weight for how strongly difficulty scales cooldown compression (0 = profile default). */
    public float cooldownDifficultyWeight = 0f;

    /**
     * Optional active-bullet pressure soft cap (0..1). Above this, adaptive throttle engages.
     * 0 = profile default.
     */
    public float pressureSoftCap = 0f;
    /** Optional max fractional arm reduction under full pressure (0..0.9). 0 = profile default. */
    public float pressureArmDrop = 0f;
    /** Optional max extra cooldown ticks under full pressure. 0 = profile default. */
    public int pressureCooldownBoost = 0;
    /** Optional minimum cooldown floor for this step (0 = profile/default floor only). */
    public int minCooldown = 0;

    /** Optional speed range low bound; <= 0 means disabled. */
    public float speedMin = 0f;
    /** Optional speed range high bound; <= 0 means disabled. */
    public float speedMax = 0f;

    /** Optional spread range low bound; < 0 means disabled. */
    public float spreadMin = -1f;
    /** Optional spread range high bound; < 0 means disabled. */
    public float spreadMax = -1f;

    /** Optional arm count range low bound; <= 0 means disabled. */
    public int armsMin = 0;
    /** Optional arm count range high bound; <= 0 means disabled. */
    public int armsMax = 0;

    // ---- BOUNCE (AIMED fan with wall-reflecting bullets) ----

    /**
     * Number of arena wall reflections allowed for {@code BOUNCE}.
     * 0 means no bouncing (equivalent to AIMED behavior).
     */
    public int bounceCount = 1;

    /**
     * Velocity retained after each bounce for {@code BOUNCE}.
     * 1.0 = perfect reflection, 0.9 = slight damping.
     */
    public float bounceDamping = 0.96f;

    // ---- RAIN (random top-lane downward shower) ----

    /**
     * Y position rain bullets spawn at for {@code RAIN}.
     * Defaults to -16 (slightly above arena top).
     */
    public float rainTop = -16f;

    /**
     * Width of the rain spawn band, centered in the arena.
     * <= 0 means full arena width.
     */
    public float rainWidth = 0f;

    // ---- AIMED_RING (aimed fan + omnidirectional ring) ----

    /**
     * Ring bullet count for {@code AIMED_RING}. Capped when scaling on Lunatic to avoid
     * runaway density.
     */
    public int ringArms = 10;

    /**
     * Ring bullet speed; if zero, defaults to ~0.52× the aim {@link #speed} after
     * difficulty scaling in {@code ArenaContext}.
     */
    public float ringSpeed = 0f;

    /** Ring bullet type name; empty = {@code ORB}. */
    public String ringBulletType = "";

    // ---- Laser-specific fields ----

    /**
     * Fan spread in radians between adjacent bullets for {@code LASER_BEAM} only (rapid aimed burst,
     * not {@code LASER} / {@code LASER_ROTATING} LaserPool beams). If {@code < 0}, engine uses default (~0.04 rad).
     * TH06 needle stakes (e.g. Sub33) use ~π/100 (~0.031) class spreads in ECL.
     */
    public float laserBeamSpread = -1f;

    /** Half-width of the laser beam in arena units. Master Spark ≈ 30, thin laser ≈ 4. */
    public float laserHalfWidth = 5f;

    /** Ticks the warning indicator is shown before the beam fires. Default 40 (~2 s at 20 tps). */
    public int warnTicks = 40;

    /** Ticks the beam is active (dealing damage). Default 60 (~3 s at 20 tps). */
    public int activeTicks = 60;

    /**
     * Bullet lifetime in mod ticks. 0 = pattern default ({@code AIMED}≈220, {@code RING}≈200,
     * {@code BOUNCE}≈250, {@code RAIN}≈230, etc.).
     */
    public int bulletLifetimeTicks = 0;

    /** Fire origin offset from boss position (arena units). */
    public float spawnOffsetX = 0f;
    public float spawnOffsetY = 0f;

    /**
     * Ring base angle in radians for {@code RING}, {@code RING_OFFSET}, {@code DENSE_RING}.
     * Negative = randomize start angle (same as legacy {@code RING_OFFSET} behavior).
     */
    public float ringStartAngleRad = -1f;

    /**
     * Rotate bullet velocity by this many radians per tick before movement (TH curved shots).
     * 0 = straight flight.
     */
    public float bulletAngularVelocity = 0f;
}
