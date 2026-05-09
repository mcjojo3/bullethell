package mc.sayda.bullethell.boss;

import com.google.gson.JsonObject;

/**
 * One attack step in a phase's attack sequence.
 * Loaded from boss JSON via Gson - all fields are public for direct population.
 *
 * JSON example:
 * 
 * <pre>
 * { "pattern": "SPIRAL", "cooldown": 8, "bulletType": "DOT", "arms": 6, "speed": 2.5 }
 * { "pattern": "AIMED",  "cooldown": 12, "bulletType": "STAR", "arms": 5, "speed": 3.0, "spread": 0.20 }
 * { "pattern": "AIMED_RING", "cooldown": 20, "bulletType": "GOLD", "arms": 5, "spread": 0.22, "speed": 3.0,
 *   "ringArms": 10, "ringSpeed": 1.6, "ringBulletType": "RICE" }
 * Burst (same step fires multiple times, then long {@code
 * cooldown
 * } before next step):
 * { "pattern": "BOUNCE", "burstCount": 2, "burstInterval": 8, "cooldown": 45, "bulletType": "KUNAI", ... }
 * Optional ECL-style tuning (applied when the boss runs this step; see {@link mc.sayda.bullethell.arena.ArenaContext}):
 * { "pattern": "RING", "cooldown": 30, "arms": 12, "speed": 2.2,
 *   "bulletLifetimeTicks": 200, "spawnOffsetX": 4, "spawnOffsetY": 0,
 *   "ringStartAngleRad": 0, "bulletAngularVelocity": 0.002 }
 * Per-difficulty values use the same order as {@code
 * spellDurationTicks
 * }: {@code [EASY, NORMAL, HARD, LUNATIC]}.
 * You may either (1) put that array <strong>on the scalar key</strong> (e.g. {@code "speed": [2.2, 2.4, 2.6, 2.9]} - load
 * copies it to the internal tier field and sets the scalar to the first entry), (2) use a {@code
 * byDifficulty
 * }
 * object whose keys match scalar names, or (3) legacy separate {@code
 * ByDifficulty
 * } keys. When the same field
 * is tiered both ways, the array on the scalar key wins during boss load.
 * </pre>
 */
public class PatternStep {

    /**
     * Pattern type to fire.
     * Valid values: SPIRAL, AIMED, AIMED_RING, RING, RING_OFFSET, SPREAD,
     * DENSE_RING, LASER_BEAM,
     * LASER, LASER_ROTATING, PENTAGRAM (two offset N-gon rings; TH MoF-style star
     * lattice),
     * PENTAGRAM_RITUAL (stacked pentagrams → ring → true-star edge comb; optional
     * scripted follow-up or JSON {@code ORB_C_ROW}),
     * ORB_C_ROW (curved orb row in one direction; pentagram-ritual phase-4 style,
     * JSON-driven),
     * STACK_FAN_VOLLEY (random annulus origin around boss: 3 stacked-ray arms in a
     * downward fan of {@code bulletType}
     * + 1 stacked ray of {@code ringBulletType} aimed at player; legacy alias
     * {@code DAGGER_HALO_VOLLEY}),
     * SEA_SPLIT (scripted curtain + secondaries in {@code ArenaContext}),
     * DIVINE_WIND (Yasaka custom spiral placement + C-turn release; scripted in
     * {@code ArenaContext}),
     * RAIN, BOUNCE, MEISTER_CYCLE (Remilia Scarlet Meister - scripted in
     * {@code ArenaContext})
     */
    public String pattern = "RING";

    /** Cached trimmed-uppercase pattern name. Computed once on first call; safe for per-tick comparisons. */
    private transient String patternUpper;

    /** Returns {@link #pattern} trimmed and upper-cased, cached. Never null. */
    public String getPatternUpper() {
        if (patternUpper == null)
            patternUpper = pattern == null ? "" : pattern.trim().toUpperCase();
        return patternUpper;
    }

    /**
     * Ticks after this step fires before the boss attack rotation advances
     * (firerate for that slot).
     * Not bullet travel speed - that is {@link #speed}. Effective gap is at least
     * {@link #minCooldown}
     * after difficulty scaling (see {@code ArenaContext.computeAttackCooldown});
     * {@code 0} = as fast as the tick loop allows.
     */
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
     * {@link #burstCount} &lt;= 1. If 0 but burstCount &gt; 1, the engine uses a
     * small
     * default (see {@code ArenaContext}).
     */
    public int burstInterval = 0;

    /**
     * Optional sound played on the client when this step fires (each call to
     * {@code executeAttackAt}, including burst follow-ups). Use a registered id:
     * short form {@code attack_charge} → {@code bullethell:attack_charge}, or full
     * {@code namespace:path}. Empty = silent. Avoid on {@code everyTickWhilePhase}
     * steps unless you want a sound every tick. Not used for {@code MEISTER_CYCLE}.
     */
    public String activationSound = "";

    /**
     * Bullet visual type name (matches {@code BulletType} enum, case-insensitive).
     */
    public String bulletType = "DOT";

    /**
     * Number of arms (SPIRAL), bullets in ring (RING / DENSE_RING),
     * or fan count (AIMED / SPREAD).
     */
    public int arms = 8;

    /**
     * Bullet <em>travel</em> speed (arena units per tick, before difficulty
     * scaling): velocity magnitude
     * passed into {@link mc.sayda.bullethell.pattern.PatternEngine} spawns. It does
     * <strong>not</strong> change
     * how often the boss fires this step - that is {@link #cooldown} and
     * {@link #minCooldown} (server
     * volley gap each cycle). For {@code SPRINKLER}, emitter rotation per shot is
     * {@link #sprinklerAdvanceRad} per shot; to spray faster, lower
     * {@link #cooldown} / {@link #minCooldown}, not {@code speed}.
     */
    public float speed = 2.5f;

    /**
     * Visual radius multiplier vs
     * {@link mc.sayda.bullethell.pattern.BulletType#radius} (1 = default).
     * 0 or negative = 1. Used for EoSD-style large orbs that read huge but stay
     * fair via {@link #hitboxScale}.
     */
    public float bulletScale = 0f;

    /**
     * Hit radius multiplier vs type base radius (TH large bullets: often
     * ~0.35-0.5).
     * 0 or negative = auto: if {@link #bulletScale} &gt; 1.25 use forgiving
     * default, else 1.
     */
    public float hitboxScale = 0f;

    /**
     * Extra multiplier on boss density for this step only (stacked after global
     * boss density).
     * 0 or negative = ignored.
     */
    public float densityScale = 0f;

    /**
     * Hard cap on {@code arms} after difficulty scaling (0 = no cap). Keeps
     * sparse-orb cards readable on Lunatic.
     */
    public int maxScaledArms = 0;

    /** Fan spread in radians between adjacent shots. Only used by AIMED. */
    public float spread = 0.20f;

    /**
     * Per-difficulty overrides: keys match scalar JSON field names; each value is
     * {@code [EASY, NORMAL, HARD, LUNATIC]}. Shorter arrays pad with the last
     * entry.
     * Any scalar field that holds a {@code [E,N,H,L]} array in JSON is
     * automatically
     * promoted here at load time — no separate {@code *ByDifficulty} keys needed.
     */
    public JsonObject byDifficulty = null;

    // ---- Optional DanmakU-style range overrides (sampled each fire) ----

    /**
     * Optional adaptive scaling profile.
     * Valid values: AUTO (default), GEOMETRY, PRECISION, BURST, SPAM.
     */
    public String scalingProfile = "";

    /**
     * Per-step tempo override. Divides this step's computed cooldown, identical to
     * {@link mc.sayda.bullethell.boss.PhaseDefinition#patternTempo} but scoped to
     * one step.
     * When &gt; 0 it replaces the phase-level tempo for this step only; 0 = use
     * phase default.
     */
    public float patternTempo = 0f;

    /**
     * When true (default), difficulty multipliers from {@link DifficultyConfig} are
     * applied to
     * speed, arms, and cooldown. When false, the pattern uses base values
     * regardless of difficulty.
     */
    public boolean dynamicDifficulty = true;

    /**
     * Random jitter added to each bullet's firing angle (radians).
     * If 0 (default), bullets are fired at perfectly precise angles.
     */
    public float angleJitterRad = 0f;

    /**
     * Optional weight for how strongly difficulty scales arms (0 = profile
     * default).
     */
    public float armsDifficultyWeight = 0f;
    /**
     * Optional weight for how strongly difficulty scales speed (0 = profile
     * default).
     */
    public float speedDifficultyWeight = 0f;
    /**
     * Optional weight for how strongly difficulty scales cooldown compression (0 =
     * profile default).
     */
    public float cooldownDifficultyWeight = 0f;

    /**
     * Optional active-bullet pressure soft cap (0..1). Above this, adaptive
     * throttle engages.
     * 0 = profile default (profiles default to {@code 1} = off unless you set
     * this).
     */
    public float pressureSoftCap = 0f;
    /**
     * Optional max fractional arm reduction under full pressure (0..1). 0 = profile
     * default.
     */
    public float pressureArmDrop = 0f;
    /**
     * Optional max extra cooldown ticks under full pressure. 0 = profile default.
     */
    public int pressureCooldownBoost = 0;
    /**
     * Minimum volley gap (ticks) after difficulty scaling. {@code 0} = no extra
     * floor beyond scaled {@link #cooldown}.
     * {@code speed} is bullet travel only.
     */
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

    // ---- SWEEP (180° downward arc, one direction at a time) ----

    /**
     * {@code SWEEP} only: ticks to sweep across the full 180° downward arc.
     * Advance per event = π / sweepTicksPerHalf. Default 30 (fast sweep).
     */
    public int sweepTicksPerHalf = 30;

    /**
     * {@code SWEEP} only: when true, reverses direction at each boundary so the
     * sweep
     * bounces back and forth. When false, snaps back to the start angle after each
     * pass.
     */
    public boolean sweepAlternate = true;

    /**
     * {@code SWEEP} only: when true, the first sweep starts from the left edge
     * (angle π,
     * pointing left) and advances toward the right edge (angle 0). When false,
     * starts
     * from the right edge.
     */
    public boolean sweepStartLeft = true;

    /**
     * {@code SWEEP} only: when true, the sweep slows while the player's angle from
     * the
     * boss is within {@link #sweepSlowZoneRad} of the current sweep direction,
     * making it
     * harder to dodge by repositioning laterally.
     */
    public boolean sweepTargeted = false;

    /**
     * {@code SWEEP} targeted mode: half-angle of the slow zone in radians.
     * Default π/5 (36°). The sweep slows from full speed at the zone edge down to
     * {@link #sweepSlowAdvanceMul} × full speed at the zone center.
     */
    public float sweepSlowZoneRad = (float) (Math.PI / 5);

    /**
     * {@code SWEEP} targeted mode: minimum advance multiplier when the sweep is
     * aimed
     * directly at the player (center of the slow zone). 0.15 = 15% of normal speed.
     * Range [0, 1]; values close to 0 create a very sticky sweep.
     */
    public float sweepSlowAdvanceMul = 0.15f;

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

    /**
     * {@code RAIN} per-bullet speed multiplier lower bound (relative to
     * {@link #speed} after scaling).
     * Each bullet's speed is
     * {@code effSpeed * uniform(rainSpeedVarMin, rainSpeedVarMax)}.
     * Default 0.88 matches historical behavior.
     */
    public float rainSpeedVarMin = 0.88f;

    /**
     * {@code RAIN} per-bullet speed multiplier upper bound. Default 1.16.
     */
    public float rainSpeedVarMax = 1.16f;

    // ---- AIMED_RING (aimed fan + omnidirectional ring) ----

    /**
     * Ring bullet count for {@code AIMED_RING}. Capped when scaling on Lunatic to
     * avoid
     * runaway density.
     */
    public int ringArms = 10;

    /**
     * Ring bullet speed; if zero, defaults to ~0.52× the aim {@link #speed} after
     * difficulty scaling in {@code ArenaContext}.
     */
    public float ringSpeed = 0f;

    /** Ring bullet type name; empty = {@code DOT}. */
    public String ringBulletType = "";

    // ---- Laser-specific fields ----

    /**
     * Fan spread in radians between adjacent bullets for {@code LASER_BEAM} only
     * (rapid aimed burst,
     * not {@code LASER} / {@code LASER_ROTATING} LaserPool beams). If {@code < 0},
     * engine uses default (~0.04 rad).
     * TH06 needle stakes (e.g. Sub33) use ~π/100 (~0.031) class spreads in ECL.
     */
    public float laserBeamSpread = -1f;

    /**
     * Half-width of the laser beam in arena units. Master Spark ≈ 30, thin laser ≈
     * 4.
     */
    public float laserHalfWidth = 5f;

    /**
     * Ticks the warning indicator is shown before the beam fires. Default 40 (~2 s
     * at 20 tps).
     */
    public int warnTicks = 40;

    /**
     * Ticks the beam is active (dealing damage). Default 60 (~3 s at 20 tps). -1 =
     * use engine default.
     */
    public int activeTicks = -1;

    /**
     * Radians to advance the per-step laser angle after each {@code LASER_ROTATING}
     * volley.
     * 0 = engine default (~0.45 rad). Negative values rotate counter-clockwise,
     * enabling
     * two LASER_ROTATING steps in the same phase to spin in opposite directions
     * independently.
     */
    public float laserRotateAdvanceRad = 0f;

    /**
     * Vertices for {@code PENTAGRAM} (e.g. 5 for pentagram lattice). 0 or &lt;3 =
     * 5.
     * Inner ring uses {@link #ringBulletType} when set, else duplicates outer type.
     */
    public int pentagramPoints = 0;

    /**
     * {@code PENTAGRAM_RITUAL} only: degrees per tick the formation rotates while
     * active. Positive = clockwise, negative = counter-clockwise. {@code 0} = no
     * spin (default).
     */
    public float pentagramRitualSpinSpeedDeg = 0f;

    /**
     * {@code PENTAGRAM_RITUAL} only: if &gt; 0, phase-4 follow-up lasts this many
     * ticks (aimed C-rows),
     * then optionally {@link #pentagramLoopRitual} restarts the ritual. 0 = legacy
     * follow-up until
     * the phase ends (wider random-heading rows).
     */
    public int pentagramFollowupDurationTicks = 0;

    /**
     * {@code PENTAGRAM_RITUAL} only: after phase-4 follow-up duration (when used),
     * restart from phase 1.
     * If {@link #skipPentagramRitualFollowup} is true, restarts immediately after
     * edge combs instead of orb volleys.
     */
    public boolean pentagramLoopRitual = false;

    /**
     * {@code PENTAGRAM_RITUAL} only: when true, scripted phase 4 (orb C-rows) is
     * <strong>disabled</strong>.
     * After the edge-comb wave, either normal {@code attacks} rotation runs (other
     * steps in the phase), or
     * if {@link #pentagramLoopRitual} is also true the pentagram cycle restarts
     * with no orb volleys.
     */
    public boolean skipPentagramRitualFollowup = false;

    /**
     * {@code PENTAGRAM_RITUAL} only: draw paired outline bullets per sample -
     * {@link #bulletType} (inner ring)
     * and {@link #ringBulletType} (outer ring). Both start stacked on the boss;
     * outer ring eases to a larger
     * radius than inner (see {@link #pentagramInnerRingScale} /
     * {@link #pentagramOuterRingScale}).
     */
    public boolean pentagramDualOverlapped = false;

    /**
     * {@code PENTAGRAM_RITUAL} dual mode: multiplier on the eased ring-out radius
     * for {@link #bulletType}
     * (inner ring). Ignored when {@link #pentagramDualOverlapped} is false.
     */
    public float pentagramInnerRingScale = 1f;

    /**
     * {@code PENTAGRAM_RITUAL} dual mode: max ring radius multiplier for
     * {@link #ringBulletType}.
     */
    public float pentagramOuterRingScale = 1f;

    /**
     * {@code PENTAGRAM_RITUAL} only: minimum boss-AI ticks after the last
     * {@code beginNewWave} before another
     * stack may start, once the current spawn wave has finished stacking. {@code 0}
     * = single stack. Requires
     * {@link #pentagramSkipEdgeComb} if edge combs must not clear older outline
     * waves.
     */
    public int pentagramRepeatStackTicks = 0;

    /**
     * {@code PENTAGRAM_RITUAL} only: skip per-star edge-comb dissolution (keeps
     * outline bullets kinematic until
     * lifetime). Set true for dual looping spells.
     */
    public boolean pentagramSkipEdgeComb = false;

    /**
     * {@code ORB_C_ROW} only: curvature scale for
     * {@link mc.sayda.bullethell.pattern.PatternEngine#fireOrbCRowInDirection}
     * ({@code <= 0} = same tiered default as pentagram ritual phase 4).
     */
    public float orbCRowCurvatureScale = 0f;

    /**
     * {@code ORB_C_ROW} only: row spacing tightness ({@code ~0.58} = ritual
     * default); {@code <= 0} = use ritual default.
     */
    public float orbCRowSpacingScale = 0f;

    /**
     * {@code ORB_C_ROW} only: when true, each volley picks a random flight angle
     * instead of aiming at
     * the nearest player.
     */
    public boolean orbCRowRandomDirection = false;

    /**
     * {@code ORB_C_ROW} only: per-index speed slope across the row (same concept as
     * {@code SPRINKLER} comb slope). Multiplier for bullet {@code i} is
     * {@code 1 + (i - center) * orbCRowSpeedSlope}; {@code 0} = uniform row speed.
     */
    public float orbCRowSpeedSlope = 0f;

    /**
     * {@code STACK_FAN_VOLLEY} only: bullets per ray, laid out along flight
     * direction from the spawn
     * (first bullet at the halo sample, rest farther along velocity); {@code <= 0}
     * = 10.
     */
    public int rayStackDepth = 0;

    /**
     * {@code STACK_FAN_VOLLEY} only: spacing between consecutive bullets along the
     * ray (arena units);
     * {@code <= 0} = 3.
     */
    public float rayStackSpacing = 0f;

    /**
     * Explicit bullet lifetime in mod ticks. 0 (default) = no timer — bullets are
     * culled by the
     * arena kill wall when they travel off screen. Set a positive value only when
     * you need bullets
     * to expire before reaching the kill wall (e.g. BOUNCE patterns that stay
     * in-arena,
     * kinematic/formation bullets with a precise release window).
     */
    public int bulletLifetimeTicks = 0;

    /** Fire origin offset from boss position (arena units). */
    public float spawnOffsetX = 0f;
    public float spawnOffsetY = 0f;

    /**
     * When true, {@code SEA_SPLIT} secondary volleys sample each burst origin on a
     * random annulus around
     * the boss (same geometry as {@code PENTAGRAM_RITUAL} phase-4 halo). Ignored
     * for primaries and
     * non-{@code SEA_SPLIT} contexts.
     */
    public boolean fireFromRandomBossHalo = false;

    /**
     * Inner radius for {@link #fireFromRandomBossHalo}; {@code <= 0} uses ritual
     * default ({@code 18}).
     */
    public float randomHaloMinR = 0f;

    /** Outer radius; {@code <=} inner uses ritual default ({@code 98}). */
    public float randomHaloMaxR = 0f;

    /**
     * Extra ± jitter on X/Y after polar sample; {@code <= 0} uses ritual default
     * ({@code 26}).
     */
    public float randomHaloJitter = 0f;

    /**
     * Ring base angle in radians for {@code RING}, {@code RING_OFFSET},
     * {@code DENSE_RING}. Use {@code -1} (the default) to let the engine pick the
     * angle (random or pattern-controlled). Any other negative value is treated
     * identically to {@code -1} — only {@code 0} or greater sets a fixed angle.
     */
    public float ringStartAngleRad = -1f;

    /**
     * Rotate bullet velocity by this many radians per tick before movement (TH
     * curved shots).
     * 0 = straight flight.
     */
    public float bulletAngularVelocity = 0f;

    /**
     * When true, this step is <strong>not</strong> part of the main attack
     * rotation. It runs once
     * every boss AI tick while its phase is active (same timing as movement),
     * independent of
     * {@link #cooldown} on other steps. Use for continuous {@code SPRINKLER} /
     * streams while slower
     * patterns (e.g. {@code AIMED}) advance the normal {@code attacks} cycle.
     */
    public boolean everyTickWhilePhase = false;

    /**
     * When &gt; 0, this step holds the attack rotation for exactly this many boss
     * AI ticks (wall-clock).
     * Volleys during that window are spaced by {@link #segmentVolleyIntervalTicks}.
     * When time runs out,
     * the rotation advances to the next attack. {@code 0} = normal rotation / burst
     * behaviour.
     * Do not combine with {@link #everyTickWhilePhase} on the same step (every-tick
     * path skips when this &gt; 0).
     */
    public int segmentDurationTicks = 0;

    /**
     * Minimum ticks between volleys while a {@link #segmentDurationTicks} segment
     * is active.
     * {@code 1} = fire every tick (subject to segment duration). {@code 0} or
     * omitted = treat as {@code 1}.
     */
    public int segmentVolleyIntervalTicks = 1;

    /**
     * Optional segment sync group id. When non-empty on a segmented step,
     * contiguous steps in the
     * same phase attack list with the same id fire together on each segment volley
     * and advance together
     * when the segment ends. Empty = normal one-step segment behavior.
     */
    public String segmentSyncGroup = "";

    /**
     * {@code SPRINKLER} only: radians the emitter rotates between consecutive shots
     * (scaled by the
     * phase's {@link mc.sayda.bullethell.boss.PhaseDefinition#patternTempo}).
     * Positive = clockwise,
     * negative = counter-clockwise. Good starting values: ±0.18-0.30 per shot with
     * {@link #everyTickWhilePhase} or cooldown 1-2.
     */
    public float sprinklerAdvanceRad = 0.22f;

    /**
     * {@code SPRINKLER} comb mode: when &gt; 0, each nozzle (see {@link #arms})
     * fires a fan of
     * {@link #combCount} bullets instead of a single bullet. Value = radians
     * between adjacent
     * bullets in the fan; total fan width = (combCount−1) × sprinklerSpread.
     * 0 (default) = ring mode, one bullet per nozzle.
     */
    public float sprinklerSpread = 0f;

    /**
     * {@code SPRINKLER} ring mode only: spawn one arm per volley in order around
     * the ring. Each bullet stays
     * frozen (no movement, no lifetime decay) until every arm in the ring has
     * fired; freeze length for arm
     * {@code i} is {@code (arms - 1 - i) × sprinklerSpawnStaggerTicks}. Use with
     * {@link #segmentVolleyIntervalTicks}
     * to space spawns. After release, bullets use normal outward velocity and
     * {@link #bulletAngularVelocity}.
     */
    public boolean sprinklerSequentialRing = false;

    /**
     * Extra freeze stagger per arm index for {@link #sprinklerSequentialRing}
     * (ticks). {@code 0} = no group hold
     * (still one bullet per volley).
     */
    public int sprinklerSpawnStaggerTicks = 1;

    /**
     * For {@link #sprinklerSequentialRing}: max radial offset from the boss along
     * each arm while placing the
     * spiral (0 = all bullets spawn at the boss origin).
     */
    public float sprinklerSpawnRadiusMax = 0f;

    /**
     * {@code DIVINE_WIND} only: number of ring layers to place before layer index
     * wraps.
     * Typical usage is 21 so each arm receives 21 placed bullets per cycle.
     */
    public int divineWindLayers = 21;

    /**
     * {@code DIVINE_WIND} only: radial spacing (arena units) between consecutive
     * placed layers.
     */
    public float divineWindLayerSpacing = 8.0f;

    /**
     * {@code DIVINE_WIND} only: freeze stagger per layer in ticks while building
     * the pre-release stack.
     * Freeze for layer {@code i} is
     * {@code (layers - 1 - i) × divineWindFreezeStaggerTicks}.
     */
    public int divineWindFreezeStaggerTicks = 1;

    /**
     * {@code DIVINE_WIND} only: tangential velocity factor relative to
     * {@link #speed} after release.
     */
    public float divineWindTangentialFactor = 0.90f;

    /**
     * {@code DIVINE_WIND} only: inward radial velocity factor relative to
     * {@link #speed} after release.
     * Positive values push slightly inward first (a C-like fold) before the angular
     * turn swings outward.
     */
    public float divineWindInwardFactor = 0.22f;

    /**
     * {@code DIVINE_WIND} only: additional angular velocity (radians/tick) applied
     * to released bullets.
     * Sign should usually oppose {@link #sprinklerAdvanceRad} to produce the
     * characteristic C-shaped turn.
     */
    public float divineWindCurveAngularVelocity = 0.03f;

    /**
     * {@code DIVINE_WIND} only: moving ticks to keep the curved C-turn active after
     * release, then bullets
     * are forced to continue straight away from the boss.
     */
    public int divineWindCurveTicks = 22;

    /**
     * {@code PENTAGRAM_RITUAL} dual release only: angular velocity for inner set
     * ({@link #bulletType})
     * after split/disassembly. Use non-zero for curving red while blue remains
     * straight.
     */
    public float pentagramDualInnerReleaseAngularVelocity = 0f;

    /**
     * {@code PENTAGRAM_RITUAL} dual release only: angular velocity for outer set
     * ({@link #ringBulletType}).
     */
    public float pentagramDualOuterReleaseAngularVelocity = 0f;

    /**
     * {@code PENTAGRAM_RITUAL} dual release only: total bullets per inner outline
     * bullet after disassembly.
     * {@code 1} = no split, {@code 2} = one extra clone, etc. Clamped in runtime.
     */
    public int pentagramDualInnerSplitCount = 1;

    /**
     * {@code PENTAGRAM_RITUAL} dual release only: radians between adjacent inner
     * split lanes.
     * Used when {@link #pentagramDualInnerSplitCount} &gt; 1.
     */
    public float pentagramDualInnerSplitSpreadRad = 0f;

    /**
     * {@code PENTAGRAM_RITUAL} dual release only: speed multiplier for cloned inner
     * split bullets.
     */
    public float pentagramDualInnerSplitSpeedMul = 1f;

    /**
     * {@code SPRINKLER} comb mode: bullets per nozzle when {@link #sprinklerSpread}
     * &gt; 0.
     * {@link #arms} controls how many evenly-spaced nozzles fire simultaneously;
     * this controls
     * how many bullets each nozzle fires in a fan. Default 1 (single bullet per
     * nozzle).
     */
    public int combCount = 1;

    /**
     * {@code SPRINKLER} comb mode only: per-index speed slope across the comb fan.
     * Speed multiplier for bullet {@code i} is
     * {@code 1 + (i - center) * sprinklerSpeedSlope}.
     * Example: {@code 0.1} makes one side of the comb travel farther/faster,
     * creating a slanted lane front.
     */
    public float sprinklerSpeedSlope = 0f;
    /**
     * Absolute X coordinate override for fire origin. If null, boss position +
     * offset is used.
     */
    public Float x = null;
    /**
     * Absolute Y coordinate override for fire origin. If null, boss position +
     * offset is used.
     */
    public Float y = null;

    // ---- WORM_CIRCLE ----

    /**
     * {@code WORM_CIRCLE} only: ticks the rings spin around the boss before all
     * knives fire radially outward. Runs as a background formation — the attack
     * rotation continues normally while spinning.
     */
    public int spinTicks = 100;

    /**
     * {@code WORM_CIRCLE} only: ring definitions. Each entry spawns one orbiting
     * ring of knives; up to {@link mc.sayda.bullethell.arena.WormCircleRuntime#MAX_RINGS}.
     * Uses {@link #speed} (after difficulty scaling) as the radial fire speed.
     */
    public WormCircleDef[] wormCircles = null;
}
