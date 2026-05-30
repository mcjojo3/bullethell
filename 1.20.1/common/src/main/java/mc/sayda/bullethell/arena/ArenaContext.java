package mc.sayda.bullethell.arena;

import mc.sayda.bullethell.boss.BossDefinition;
import mc.sayda.bullethell.boss.BossEmitterDefinition;
import mc.sayda.bullethell.boss.BossLoader;
import mc.sayda.bullethell.boss.CharacterDefinition;
import mc.sayda.bullethell.boss.CharacterLoader;
import mc.sayda.bullethell.debug.BHDebugMode;
import mc.sayda.bullethell.entity.BHAttributes;
import mc.sayda.bullethell.boss.FairyRushDefinition;
import mc.sayda.bullethell.boss.FairyWaveCatalog;
import mc.sayda.bullethell.boss.FairyWaveCatalogEntry;
import mc.sayda.bullethell.boss.FairyWaveCatalogLoader;
import mc.sayda.bullethell.boss.FairyWaveLoader;
import mc.sayda.bullethell.boss.PatternStep;
import mc.sayda.bullethell.boss.PhaseDefinition;
import mc.sayda.bullethell.boss.TierJson;
import mc.sayda.bullethell.boss.RulesetConfig;
import mc.sayda.bullethell.boss.StageDefinition;
import mc.sayda.bullethell.boss.StageLoader;
import mc.sayda.bullethell.boss.WaveDefinition;
import mc.sayda.bullethell.boss.WaveEnemy;
import mc.sayda.bullethell.config.BullethellConfig;
import mc.sayda.bullethell.pattern.BulletLineHit;
import mc.sayda.bullethell.pattern.BulletType;
import mc.sayda.bullethell.pattern.BulletTypeLoader;
import mc.sayda.bullethell.pattern.PatternEngine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * All state for one bullet-hell session.
 *
 * Stage flow:
 * WAVES - pre-boss fairy waves spawn and attack; player can collect power items
 * BOSS - boss fight driven by BossDefinition JSON
 *
 * Design rules:
 * - Every system takes ArenaContext - no static globals.
 * - Multiple ArenaContexts = splitscreen / Phantasmagoria mode.
 * - Stage structure, boss behaviour, and gameplay rules are fully JSON-driven.
 * - Spellcard-accurate HP bar: each boss phase owns its own HP pool.
 */
public class ArenaContext {

    // ---------------------------------------------------------------- identity

    private static final AtomicInteger ID_GEN = new AtomicInteger();

    public final UUID playerUuid;
    public final int arenaId;
    public final DifficultyConfig difficulty;
    public final long seed;
    private final java.util.Random random;

    // ---------------------------------------------------------------- definitions
    // (JSON-loaded)

    public final StageDefinition stage;
    public final BossDefinition boss;
    /**
     * Boss phases filtered by this arena's difficulty (falls back to all phases).
     */
    private final List<PhaseDefinition> activeBossPhases;
    public final RulesetConfig rules;

    // ---------------------------------------------------------------- subsystems

    public final BulletPool bullets; // enemy + fairy bullets
    public final BulletPool playerBullets;
    public final ItemPool items;
    public final EnemyPool enemies;
    public final PlayerState2D player;
    /**
     * Per-participant score (co-op: each player has their own chain / extends
     * track).
     */
    private final java.util.LinkedHashMap<UUID, ScoreSystem> scoreByPlayer = new java.util.LinkedHashMap<>();
    public final SpellcardTimer spellcard;
    public final LaserPool lasers = new LaserPool();

    // ---------------------------------------------------------------- stage state
    // machine

    public enum ArenaPhase {
        WAVES, DIALOG_INTRO, BOSS
    }

    public ArenaPhase arenaPhase = ArenaPhase.WAVES;

    // ---------------------------------------------------------------- scheduled
    // enemy list
    // (pre-expanded at init from all waves + waveRef templates, sorted by
    // spawnTick)

    /** Flat, time-sorted list of every enemy to spawn during the wave phase. */
    private final List<ScheduledEnemy> scheduledEnemies = new ArrayList<>();
    /** Index of the next entry in scheduledEnemies that hasn't spawned yet. */
    private int nextScheduledIdx = 0;
    /**
     * Stage waves that pass {@link #waveAppliesToDifficulty(WaveDefinition)} (for
     * HUD progress).
     */
    private int applicableWaveDefinitionCount = 0;
    /**
     * Per-slot attack pattern. Set when an enemy spawns; used by tickEnemyAI()
     * to dispatch the correct firing logic. Sized to EnemyPool.CAPACITY.
     */
    private final EnemyPattern[] enemyPatternIds = new EnemyPattern[EnemyPool.CAPACITY];

    /** Absolute tick counter from arena start (drives wave spawning). */
    private int stageTick = 0;
    /**
     * Small-fairy kills only when large fairies use the classic always-drop rule.
     */
    private int smallEnemyKillCounter = 0;
    /** Shared kill counter when large enemies also follow every-Nth drops. */
    private int combinedDropKillCounter = 0;
    /**
     * Countdown ticks between last wave clearing and boss intro / BOSS phase.
     * -1 = delay not yet triggered (waves not yet clear).
     */
    private int waveEndDelayLeft = -1;
    /** Cyclic index into the normal (small-enemy) drop sequence. */
    private int dropCycleIdx = 0;
    /** Cyclic index into the large-enemy drop sequence. */
    private int largeDropCycleIdx = 0;
    /** Parsed drop cycle for small/normal enemies (POWER + POINT only). */
    private final int[] dropCycle;
    /** Parsed drop cycle for large enemies (may include FULL_POWER). */
    private final int[] largeDropCycle;

    // ---------------------------------------------------------------- boss state

    public int bossPhase = 0;
    public int bossHp;
    public int bossMaxHp;
    public float bossX;
    public float bossY;
    /**
     * True during DIALOG_INTRO so the boss sprite renders before the fight starts.
     */
    public boolean bossIntroVisible = false;

    /**
     * Set by {@code /bullethell test}: enables the test-mode HUD overlay and god
     * mode.
     */
    public boolean testMode = false;
    /**
     * Set when starting from the NPC practice button. Skips progression/XP and
     * starts at boss.
     */
    public boolean practiceMode = false;

    private static final float BOSS_HIT_RADIUS = 24f;

    private float spiralAngle = 0f;
    private final Map<PatternStep, Float> sprinklerAngles = new HashMap<>();
    /**
     * Per-step rotation angle for LASER_ROTATING, enabling independent
     * counter-rotation.
     */
    private final Map<PatternStep, Float> laserAngles = new HashMap<>();
    /**
     * Next arm index for {@link PatternStep#sprinklerSequentialRing} SPRINKLER
     * steps.
     */
    private final Map<PatternStep, Integer> sprinklerSeqArm = new HashMap<>();
    /**
     * Next layer index for {@link PatternStep#divineWindLayers} custom DIVINE_WIND
     * steps.
     */
    private final Map<PatternStep, Integer> divineWindLayer = new HashMap<>();
    /**
     * Current sweep angle (radians) for SWEEP steps; initialized lazily to
     * sweepStartLeft ? π : 0.
     */
    private final Map<PatternStep, Float> sweepAngles = new HashMap<>();
    /** Current sweep direction (+1 or -1) for SWEEP steps. */
    private final Map<PatternStep, Integer> sweepDirs = new HashMap<>();
    /** Tick counter within the current shotgun cycle for each SHOTGUN step. */
    private final Map<PatternStep, Integer> shotgunTick = new HashMap<>();
    private int patternCooldown = 0;
    private int bossTick = 0;
    /** -1 left, 0 idle, +1 right. Synced to client for Cirno travel frames. */
    private int bossMoveDir = 0;
    /**
     * bossTick value at the start of the current phase - keeps movement continuous.
     */
    private int phaseStartTick = 0;
    private int attackIndex = 0;
    /**
     * Wall-clock ticks left for {@link PatternStep#segmentDurationTicks}; 0 = not
     * in a segment.
     */
    private int bossSegmentTicksRemaining = 0;
    /**
     * Ticks until next volley during a timed segment (0 = fire this tick after
     * checks).
     */
    private int bossSegmentVolleyCooldown = 0;
    /**
     * When &gt; 0, {@link #bossBurstStep} still has volleys left in the current
     * burst.
     */
    private int bossBurstVolleysRemaining = 0;
    /** Step being repeated for burst fire; null when not in a burst chain. */
    private PatternStep bossBurstStep = null;
    /** Tracks independent cooldowns for everyTickWhilePhase attacks. */
    private final java.util.Map<PatternStep, Integer> secondaryCooldowns = new java.util.HashMap<>();
    /**
     * Tracks remaining duration for everyTickWhilePhase attacks that have
     * activeTicks set.
     */
    private final java.util.Map<PatternStep, Integer> secondaryLifetimes = new java.util.HashMap<>();

    private int resolveReposShootTicks() {
        PhaseDefinition phase = currentBossPhase();
        return phase != null ? phase.resolveReposShootTicks(difficulty.ordinal()) : 160;
    }

    // --- REPOS_TOP movement state ---
    /** 0=shooting, 1=dashing to new pos, 2=breathing-room cooldown. */
    private int reposDashState = 0;
    /** Countdown ticks remaining in the current repos sub-phase. */
    private int reposPhaseTimer = 0;
    private float reposStartX = 0f;
    private float reposTargetX = 0f;

    // --- DASH_TOP movement state ---
    /** 0=waiting between dashes, 1=dashing. */
    private int dashTopState  = 0;
    private int dashTopTimer  = 0;
    private float dashTopStartX = 0f, dashTopStartY = 0f;
    private float dashTopTargetX = 0f, dashTopTargetY = 0f;

    // --- Per-attack overrides (refreshed every tick from current step) ---
    /** Null = use phase movement; non-null overrides phase movement for the current attack step. */
    private String activeMovementOverride = null;
    /** Current boss texture id override; empty = use default boss id texture. */
    private String activeBossTexture = "";

    /** Returns the active boss texture override for sync to clients. */
    public String getActiveBossTexture() { return activeBossTexture; }
    /**
     * When true the main boss attack loop is suppressed (used during reposition
     * dash).
     */
    private boolean bossFireFrozen = false;

    // --- hidden rank (TH-style dynamic difficulty) ---
    /**
     * 0 = easiest, 32 = hardest, default 16 (neutral). Increases during boss
     * fights, decreases on death/bomb.
     */
    private int rank = 16;

    /**
     * Music track chosen at phase start (random pick from musicPool is done once
     * here, not per-packet).
     */
    private String currentBossPhaseMusicId = "";

    // --- dialog intro / fight-entry Y animation ---
    /**
     * Countdown ticks for the smooth Y entry from dialog landing into the fight.
     */
    private int bossEntryTimer = 0;
    /** Boss Y at the moment the fight begins (dialog landing position). */
    private float bossEntryFromY = 0f;
    /** Target boss Y for the fight-entry lerp (phase-0 natural start Y). */
    private float bossEntryToY = 100f;

    /**
     * {@code PENTAGRAM_RITUAL} (MoF-style non-spell choreography):
     * <ol>
     * <li><b>Charge</b> - 10 pentagram outlines (one full star each) drawn at the
     * boss.</li>
     * <li><b>Radial expansion</b> - stars ease from the boss centre to 10 ring
     * positions.</li>
     * <li><b>Dissolution</b> - single-colour: per star,
     * {@link PatternEngine#firePentagramStarEdgeStreams} spawns
     * outward-normal comb streams. Dual overlapped: existing outline bullets (inner
     * + outer) receive
     * outward velocity along stored edge normals
     * ({@link PentagramFormationRuntime#launchDetachedOutward}).</li>
     * <li><b>Follow-up</b> - brief rapid {@link PatternEngine#fireOrbCRowToward}
     * volleys from random
     * points near the boss aimed at the player, then the cycle restarts.</li>
     * </ol>
     * {@code -1} = inactive.
     */
    private int pentagramRitualTick = -1;
    /**
     * Ritual tick index when the last stacked-outline bullet finished spawning; -1
     * until then.
     */
    private int ritualStackCompleteAt = -1;
    /**
     * Pentagram intro + one edge-comb wave done; only boss orb volleys after this.
     */
    private boolean prPentagramDisassembled;
    private PatternStep pentagramRitualCfg = null;
    private float pentagramRitualSpin = 0f;
    private final PentagramFormationRuntime pentagramFormation = new PentagramFormationRuntime();
    private final WormCircleRuntime   wormCircleRuntime   = new WormCircleRuntime();
    private final RingSpawnerRuntime  ringSpawnerRuntime  = new RingSpawnerRuntime();
    /**
     * Boss-AI tick when {@link PentagramFormationRuntime#beginNewWave()} last ran;
     * {@code 0} at ritual start.
     */
    private int pentagramLastNewWaveTick;
    private final float[] prRingInnerScratch = new float[PentagramFormationRuntime.MAX_WAVES];
    private final float[] prRingOuterScratch = new float[PentagramFormationRuntime.MAX_WAVES];
    /**
     * After {@link PatternStep#skipPentagramRitualFollowup} hands off, ritual tick
     * path is disabled for this phase
     * and {@link #mainRotationPatternSteps} runs (orb rows etc. from JSON).
     */
    private boolean pentagramRitualFollowupHandedOff = false;

    /**
     * 20 ticks = 1s @ 20 tps: wait at centre after stack finishes before ring-out
     * starts.
     */
    private static final int PR_STACKED_HOLD_TICKS = 10;
    /** Ring-out travel duration (ease to ring positions). */
    private static final int PR_RING_SPREAD_TICKS = 40;
    /** 1s @ full ring before edge-comb wave. */
    private static final int PR_RING_SETTLE_TICKS = 0;
    /**
     * Ticks after edge combs before boss orb volleys (avoid same-frame overlap).
     */
    private static final int PR_BOSS_PHASE_START_DELAY = 2;
    private static final float PR_RING_RADIUS = 200f;
    private static final float PR_STAR_RADIUS = 100f;
    /**
     * Outline bullets are kinematically moved every tick; must not expire
     * mid-spell.
     */
    private static final int PR_FORMATION_OUTLINE_LIFE_TICKS = 60_000;
    /**
     * Outline spawn rate - keep stack draw short so "before travel" is not 2窶・s of
     * drawing.
     */
    private static final int PR_STACK_BULLETS_PER_TICK = 28;
    /**
     * Post-ritual C-rows: random spawn on this annulus around the boss (2D, full
     * circle).
     */
    private static final float PR_BOSS_ORB_ROW_MIN_R = 18f;
    private static final float PR_BOSS_ORB_ROW_MAX_R = 98f;
    /** Extra XY jitter so bursts do not stack on the same halo sample. */
    private static final float PR_BOSS_ORB_SPAWN_JITTER = 26f;
    /**
     * Flight directions (radians) for legacy {@code PENTAGRAM_RITUAL} follow-up:
     * wide downward fan;
     * occasional fully random heading.
     */
    private static final float PR_BOSS_ORB_FLIGHT_MIN = (float) (Math.PI / 6.0);
    private static final float PR_BOSS_ORB_FLIGHT_MAX = (float) (Math.PI * 5.0 / 6.0);
    private static final float PR_BOSS_ORB_FLIGHT_JITTER_RAD = 0.22f;
    /**
     * Tighter spacing along the row (comet tail) vs {@link PatternEngine} default.
     */
    private static final float PR_BOSS_ORB_ROW_TIGHT = 0.58f;

    // ---------------------------------------------------------------- phase
    // emitters (Flandre clones/traps)
    private static final class EmitterState {
        BossEmitterDefinition def;
        int attackIndex;
        int cooldown;
        int burstVolleysRemaining;
        PatternStep burstStep;
    }

    private final ArrayList<EmitterState> activeEmitters = new ArrayList<>();

    /**
     * Default difficulty weights per {@code scalingProfile}. Pressure fields
     * default to off
     * ({@code pressureSoftCap}=1, {@code pressureArmDrop}=0,
     * {@code pressureCooldownBoost}=0);
     * set per-step {@link PatternStep#pressureSoftCap} etc. to enable adaptive
     * throttle.
     */
    private record AttackScalingProfile(
            float armsWeight,
            float speedWeight,
            float cooldownWeight,
            float pressureSoftCap,
            float pressureArmDrop,
            int pressureCooldownBoost) {
    }

    private static final AttackScalingProfile SCALE_GEOMETRY = new AttackScalingProfile(
            0.70f, 0.95f, 0.90f, 1f, 0f, 0);
    private static final AttackScalingProfile SCALE_PRECISION = new AttackScalingProfile(
            0.82f, 1.00f, 0.88f, 1f, 0f, 0);
    private static final AttackScalingProfile SCALE_BURST = new AttackScalingProfile(
            0.72f, 0.92f, 0.78f, 1f, 0f, 0);
    private static final AttackScalingProfile SCALE_SPAM = new AttackScalingProfile(
            0.56f, 0.84f, 0.64f, 1f, 0f, 0);

    /**
     * Scarlet Meister scripted cycle: 0 shotgun (fast wide fan), 1 spin CW, 2 spin
     * CCW,
     * 3 short pause, 4 mirrored shotgun, 5 spin CCW, 6 spin CW, 7 rest.
     */
    private int meisterSubPhase = 0;
    private int meisterTimer = 0;
    private float meisterStreamAngle = 0f;

    /**
     * {@code SEA_SPLIT}: scripted 窶彝ed Sea窶・curtain - each volley spawns up to
     * **two** horizontal
     * line-hit bullets at the top (left wall segment and right wall segment) with
     * **pure downward**
     * velocity; the safe gap oscillates with {@code seaSplitAngle} (capped sweep).
     * Visual length
     * per side is driven by {@code bulletType}窶冱 {@code lineVisualHalfLength} in
     * {@code bullet_types.json}
     * (see {@code tickSeaSplit}). {@code -1} = inactive. Other phase steps fire as
     * secondaries via
     * {@code seaSplitSecCd/Idx}.
     */
    private int seaSplitTick = -1;
    private PatternStep seaSplitCfg = null;
    private float seaSplitAngle = 0f;
    private int seaSplitFireCd = 0;
    private int seaSplitSecCd = 0;
    private int seaSplitSecIdx = 0;

    /** Remaining reflections for each enemy-bullet slot (0 = normal bullet). */
    private final int[] bounceRemaining = new int[BulletPool.ENEMY_CAPACITY];
    private final float[] bounceDamping = new float[BulletPool.ENEMY_CAPACITY];
    /** Bitmask of excluded walls: LEFT=1, RIGHT=2, TOP=4, BOTTOM=8. Bullets die on contact with excluded walls. */
    private final int[] bounceExcludeMask = new int[BulletPool.ENEMY_CAPACITY];
    /** Scratch array for pre-spawn active snapshot used by generic bounce wiring. */
    private final boolean[] activeScratch = new boolean[BulletPool.ENEMY_CAPACITY];
    /**
     * Per-slot remaining ticks before DIVINE_WIND bullets straighten and fly away.
     */
    private final int[] divineWindCurveRemaining = new int[BulletPool.ENEMY_CAPACITY];

    /**
     * Ticks remaining in the inter-phase pause (boss drifts to centre, no attacks).
     */
    public int phaseTransitionTimer = 0;
    public int pendingNextPhase = -1;

    // TH19 Character Ability State
    public int timeStopTicks = 0;
    public UUID timeStopOwner = null;

    /**
     * Global master spark beam for simplicity (renderer handles multiple if needed)
     */
    public int masterSparkTicks = 0;
    public UUID masterSparkOwner = null;
    public float masterSparkX = 0f;
    public float masterSparkY = 0f;
    /** 1窶・: PoFV Illusion Laser scaling ({@link #tickMasterSpark}). */
    private int masterSparkLevel = 0;

    // ---------------------------------------------------------------- init
    // dialog state
    private List<mc.sayda.bullethell.boss.DialogLine> activeDialog = null;
    /** Per-player dialog script (character-specific, with intro fallback). */
    private final java.util.LinkedHashMap<UUID, List<mc.sayda.bullethell.boss.DialogLine>> dialogScriptByPlayer = new java.util.LinkedHashMap<>();

    /** Per-player current dialog line index within {@code activeDialog}. */
    private final java.util.LinkedHashMap<UUID, Integer> dialogIndexByPlayer = new java.util.LinkedHashMap<>();
    /** Per-player ticks until their current line auto-advances. */
    private final java.util.LinkedHashMap<UUID, Integer> dialogTicksLeftByPlayer = new java.util.LinkedHashMap<>();
    /** Per-player readiness (true when finished reading or skip-all pressed). */
    private final java.util.LinkedHashMap<UUID, Boolean> dialogReadyByPlayer = new java.util.LinkedHashMap<>();

    // ---------------------------------------------------------------- enemy
    // constants

    /** Laser hits are slightly more forgiving (0.8x hitbox). */
    private static final float LASER_HITBOX_SCALE = 0.8f;

    // ---------------------------------------------------------------- event bus

    public final Queue<GameEvent> pendingEvents = new ConcurrentLinkedQueue<>();
    /**
     * SFX ids (as in {@link mc.sayda.bullethell.boss.PatternStep#activationSound})
     * queued when an attack step fires.
     */
    public final Queue<String> pendingAttackActivationSounds = new ConcurrentLinkedQueue<>();

    /**
     * Runnables queued from C2S packet handlers (MC main thread) to be drained by the
     * arena thread at the start of each tick. Avoids mutating arena state off-thread.
     */
    public final ConcurrentLinkedQueue<Runnable> pendingInputs = new ConcurrentLinkedQueue<>();

    /**
     * Runnables queued by the arena thread for execution on the MC main thread
     * (e.g. win/loss handling, advancement grants, arena removal).
     */
    public final ConcurrentLinkedQueue<Runnable> mainCallbacks = new ConcurrentLinkedQueue<>();

    private volatile boolean over = false;
    /**
     * True when the boss's last phase was cleared (player won). False on game-over.
     */
    private volatile boolean won = false;
    /** Number of spell cards the player successfully captured (no bomb/death). */
    private int spellsCaptured = 0;
    /** Total spell card phases triggered so far. */
    private int spellsAttempted = 0;
    /** ID of the active character (from CharacterDefinition JSON). */
    public String characterId = "reimu";
    /**
     * Host's selected shot type (index into
     * {@link mc.sayda.bullethell.boss.CharacterDefinition#shotOptions} or legacy
     * {@code shotTypes}).
     */
    public int hostShotTypeOrdinal = 0;

    // ---------------------------------------------------------------- co-op

    /**
     * Additional players sharing this arena (not the host).
     * Each has their own PlayerState2D and BulletPool; enemies/boss/items are
     * shared.
     */
    private final java.util.LinkedHashMap<UUID, PlayerState2D> coopPlayers = new java.util.LinkedHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<UUID, BulletPool> coopBullets = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.LinkedHashMap<UUID, String> coopCharIds = new java.util.LinkedHashMap<>();
    private final java.util.LinkedHashMap<UUID, Integer> coopShotTypeOrdinal = new java.util.LinkedHashMap<>();
    /** Participants currently holding the pause menu open. */
    private final java.util.LinkedHashSet<UUID> pausedParticipants = new java.util.LinkedHashSet<>();
    /** Cached participant set – rebuilt only when coopPlayers membership changes. */
    private java.util.Set<UUID> cachedParticipants = null;
    /** Cached player-state list – rebuilt only when coopPlayers membership changes. */
    private java.util.List<PlayerState2D> cachedPlayerStates = null;

    private void invalidateParticipantCaches() {
        cachedParticipants = null;
        cachedPlayerStates = null;
    }
    /** Resolved each server tick from gamerule + paused participants. */
    private boolean globallyPaused = false;

    /** Add a co-op participant. Called when another player joins the match. */
    public void addCoopPlayer(UUID uuid, mc.sayda.bullethell.boss.CharacterDefinition charDef,
            net.minecraft.world.entity.LivingEntity participantAttributes, int shotTypeOrdinal) {
        // Entity-attribute reads must happen on the calling (MC main) thread.
        int startLives = resolveStartingLives(charDef, participantAttributes);
        int startBombs = resolveStartingBombs(charDef, participantAttributes);
        PlayerState2D ps = new PlayerState2D(charDef.hitRadius, charDef.grazeRadius,
                charDef.pickupRadius, charDef.speedNormal, charDef.speedFocused,
                charDef.chargeRateShooting, charDef.chargeRateIdle, charDef.chargeRateCharging,
                charDef.chargeSpeedFrames, charDef.chargeDelayAfterSkill,
                startLives, startBombs);
        // Queue all map mutations to run on the arena thread, eliminating the cross-thread race.
        pendingInputs.offer(() -> {
            coopPlayers.put(uuid, ps);
            invalidateParticipantCaches();
            coopBullets.put(uuid, new BulletPool(BulletPool.PLAYER_CAPACITY));
            coopCharIds.put(uuid, charDef.id);
            coopShotTypeOrdinal.put(uuid, Math.max(0, shotTypeOrdinal));
            if (this.practiceMode) ps.power = PlayerState2D.MAX_POWER;
            ScoreSystem ss = new ScoreSystem();
            ss.configureExtendsEvery(rules.scoreExtendEvery);
            scoreByPlayer.put(uuid, ss);
            if (arenaPhase == ArenaPhase.DIALOG_INTRO) initDialogStateForPlayer(uuid);
        });
    }

    public void removeCoopPlayer(UUID uuid) {
        pendingInputs.offer(() -> {
            coopPlayers.remove(uuid);
            invalidateParticipantCaches();
            coopBullets.remove(uuid);
            coopCharIds.remove(uuid);
            coopShotTypeOrdinal.remove(uuid);
            scoreByPlayer.remove(uuid);
            pausedParticipants.remove(uuid);
            dialogScriptByPlayer.remove(uuid);
            dialogIndexByPlayer.remove(uuid);
            dialogTicksLeftByPlayer.remove(uuid);
            dialogReadyByPlayer.remove(uuid);
        });
    }

    /** Called from C2S pause packet when a participant opens/closes pause menu. */
    public void setParticipantPaused(UUID uuid, boolean paused) {
        if (uuid == null || !allParticipants().contains(uuid))
            return;
        if (paused)
            pausedParticipants.add(uuid);
        else
            pausedParticipants.remove(uuid);
    }

    public boolean hasPausedParticipants() {
        return !pausedParticipants.isEmpty();
    }

    public void setGloballyPaused(boolean paused) {
        this.globallyPaused = paused;
    }

    /**
     * Character ID for any participant. Returns host's characterId for non-coop
     * lookups.
     */
    public String getCharacterId(UUID uuid) {
        if (uuid.equals(playerUuid))
            return characterId;
        String id = coopCharIds.get(uuid);
        return id != null ? id : "reimu";
    }

    public int getShotTypeOrdinal(UUID uuid) {
        if (uuid.equals(playerUuid))
            return hostShotTypeOrdinal;
        Integer v = coopShotTypeOrdinal.get(uuid);
        return v != null ? v : 0;
    }

    /** All UUIDs participating in this arena (host + coop). */
    public java.util.Set<UUID> allParticipants() {
        if (cachedParticipants == null) {
            java.util.LinkedHashSet<UUID> set = new java.util.LinkedHashSet<>();
            set.add(playerUuid);
            set.addAll(coopPlayers.keySet());
            cachedParticipants = java.util.Collections.unmodifiableSet(set);
        }
        return cachedParticipants;
    }

    public PlayerState2D getPlayerState(UUID uuid) {
        return uuid.equals(playerUuid) ? player : coopPlayers.get(uuid);
    }

    public BulletPool getBulletPool(UUID uuid) {
        return uuid.equals(playerUuid) ? playerBullets : coopBullets.get(uuid);
    }

    public java.util.Map<UUID, PlayerState2D> getCoopPlayers() {
        return coopPlayers;
    }

    /**
     * Resolves which participant owns this {@link PlayerState2D} reference (host or
     * co-op).
     */
    public UUID uuidForPlayerState(PlayerState2D ps) {
        if (ps == this.player) {
            return playerUuid;
        }
        for (var e : coopPlayers.entrySet()) {
            if (e.getValue() == ps) {
                return e.getKey();
            }
        }
        return playerUuid;
    }

    /**
     * Score accumulated by this participant only.
     */
    public long getScore(UUID participantUuid) {
        ScoreSystem ss = scoreByPlayer.get(participantUuid);
        return ss != null ? ss.getScore() : 0L;
    }

    /** Sum of every participant's score (team total). */
    public long getCombinedScore() {
        long sum = 0L;
        for (ScoreSystem ss : scoreByPlayer.values()) {
            sum += ss.getScore();
        }
        return sum;
    }

    /**
     * Boss-rush carry: set this participant's score baseline and extend milestones.
     */
    public void importCarriedScoreFor(UUID participantUuid, long carriedScore, long extendEvery) {
        ScoreSystem ss = scoreByPlayer.get(participantUuid);
        if (ss != null) {
            ss.importCarriedScore(carriedScore, extendEvery);
        }
    }

    private ScoreSystem scoreSystemFor(UUID participantUuid) {
        ScoreSystem ss = scoreByPlayer.get(participantUuid);
        return ss != null ? ss : scoreByPlayer.get(playerUuid);
    }

    private boolean allPlayersEliminated() {
        if (player.lives >= 0)
            return false;
        for (PlayerState2D ps : coopPlayers.values()) {
            if (ps.lives >= 0)
                return false;
        }
        return true;
    }

    // ---------------------------------------------------------------- constructors

    /** Start the default stage at NORMAL difficulty with Reimu. */
    public ArenaContext(UUID playerUuid, DifficultyConfig difficulty) {
        this(playerUuid, difficulty, "marisa_stage", "reimu", null);
    }

    /** Start a specific stage with the default character. */
    public ArenaContext(UUID playerUuid, DifficultyConfig difficulty, String stageId) {
        this(playerUuid, difficulty, stageId, "reimu", null);
    }

    /**
     * Start a specific stage with a specific character (no attribute bonuses).
     */
    public ArenaContext(UUID playerUuid, DifficultyConfig difficulty, String stageId, String characterId) {
        this(playerUuid, difficulty, stageId, characterId, null);
    }

    /**
     * Start a specific stage with a specific character.
     *
     * @param stageId        file name (without .json) under
     *                       {@code data/bullethell/stages/}
     * @param characterId    file name (without .json) under
     *                       {@code data/bullethell/characters/}
     * @param hostAttributes host player for {@link BHAttributes} bonuses, or null
     */
    public ArenaContext(UUID playerUuid, DifficultyConfig difficulty, String stageId, String characterId,
            net.minecraft.world.entity.LivingEntity hostAttributes) {
        this(playerUuid, difficulty, StageLoader.load(stageId), characterId, hostAttributes);
    }

    /**
     * Same as
     * {@link #ArenaContext(UUID, DifficultyConfig, String, String, net.minecraft.world.entity.LivingEntity)}
     * but with a pre-built {@link StageDefinition} (e.g. synthetic boss-only stage
     * from commands).
     */
    public ArenaContext(UUID playerUuid, DifficultyConfig difficulty, StageDefinition stageDef, String characterId,
            net.minecraft.world.entity.LivingEntity hostAttributes) {
        this.playerUuid = playerUuid;
        this.arenaId = ID_GEN.getAndIncrement();
        this.difficulty = difficulty;
        this.characterId = (characterId != null) ? characterId : "reimu";
        this.seed = System.nanoTime();
        this.random = new java.util.Random(seed);
        this.bullets = new BulletPool(BulletPool.ENEMY_CAPACITY);
        this.bullets.setOnBeforeWriteSlot(this::clearEnemyBulletSlotMeta);
        this.playerBullets = new BulletPool(BulletPool.PLAYER_CAPACITY);
        this.items = new ItemPool();
        this.enemies = new EnemyPool();
        this.spellcard = new SpellcardTimer();

        // Load stage/rules first so startingLives/Bombs overrides are available
        this.stage = stageDef;
        // Always reload boss JSON: dev-path file (if TEST_DEV_PATH +
        // bosses/&lt;id&gt;.json) wins over cache;
        // otherwise classpath (rebuild to pick up edits under src/main/resources).
        BossLoader.invalidate(stage.bossId);
        this.boss = BossLoader.loadWithDevPath(stage.bossId);
        this.activeBossPhases = buildActiveBossPhases();
        this.rules = stage.rules;
        this.dropCycle = parseDropCycle(rules.dropCyclePattern);
        String largePattern = (rules.largeEnemyDropCyclePattern != null
                && !rules.largeEnemyDropCyclePattern.isEmpty())
                        ? rules.largeEnemyDropCyclePattern
                        : rules.dropCyclePattern;
        this.largeDropCycle = parseDropCycle(largePattern);
        this.scoreByPlayer.put(playerUuid, new ScoreSystem());
        this.scoreByPlayer.get(playerUuid).configureExtendsEvery(rules.scoreExtendEvery);

        // Apply character-specific stats; stage rules can override lives/bombs
        mc.sayda.bullethell.boss.CharacterDefinition charDef = mc.sayda.bullethell.boss.CharacterLoader
                .load(this.characterId);
        int startLives = resolveStartingLives(charDef, hostAttributes);
        int startBombs = resolveStartingBombs(charDef, hostAttributes);
        player = new PlayerState2D(charDef.hitRadius, charDef.grazeRadius, charDef.pickupRadius,
                charDef.speedNormal, charDef.speedFocused,
                charDef.chargeRateShooting, charDef.chargeRateIdle, charDef.chargeRateCharging,
                charDef.chargeSpeedFrames, charDef.chargeDelayAfterSkill,
                startLives, startBombs);

        // Boss position is set when BOSS phase begins
        this.bossX = BulletPool.ARENA_W / 2f;
        this.bossY = 100f;
        this.bossHp = 0;
        this.bossMaxHp = 0;

        // Pre-expand all waves (including waveRef templates) into a flat sorted list
        buildScheduledList();

        // If no waves defined, go straight to dialog/boss
        if (scheduledEnemies.isEmpty()) {
            transitionToDialogOrBoss();
        }
    }

    /**
     * Skip waves and intro dialog and jump straight to a boss phase (0-based
     * index).
     * Used by {@code /bullethell start &lt;target&gt; &lt;phase&gt;} (phase is
     * 1-based in the command).
     */
    public void debugSkipToBossPhase(int phaseIndex0Based) {
        int n = activeBossPhases.size();
        if (n == 0)
            return;
        int idx = Math.max(0, Math.min(phaseIndex0Based, n - 1));
        scheduledEnemies.clear();
        nextScheduledIdx = 0;
        waveEndDelayLeft = -1;
        arenaPhase = ArenaPhase.BOSS;
        bossIntroVisible = false;
        bullets.clearAll();
        lasers.clearAll();
        enemies.clearAll();
        bossTick = 0;
        bossX = BulletPool.ARENA_W / 2f;
        bossY = 100f;
        resetAbilityStates();
        dialogScriptByPlayer.clear();
        dialogIndexByPlayer.clear();
        dialogTicksLeftByPlayer.clear();
        dialogReadyByPlayer.clear();
        activeDialog = null;
        startBossPhase(idx);
        pendingEvents.add(GameEvent.PHASE_CHANGE);
    }

    /**
     * Stage rules override the character's
     * {@link CharacterDefinition#startingLives};
     * then {@link BHAttributes#EXTRA_LIVES} on {@code player} adds on top.
     */
    private int resolveStartingLives(CharacterDefinition charDef,
            net.minecraft.world.entity.LivingEntity player) {
        int base = (rules.startingLives >= 0) ? rules.startingLives : charDef.startingLives;
        return base + BHAttributes.extraLivesBonus(player);
    }

    /**
     * Same as {@link #resolveStartingLives} for bombs; total is capped at 9.
     */
    private int resolveStartingBombs(CharacterDefinition charDef,
            net.minecraft.world.entity.LivingEntity player) {
        int base = (rules.startingBombs >= 0) ? rules.startingBombs : charDef.startingBombs;
        return Math.min(9, base + BHAttributes.extraBombsBonus(player));
    }

    private void addArenaScore(long pts, UUID earnerUuid) {
        ScoreSystem ss = scoreSystemFor(earnerUuid);
        applyScoreExtends(ss.addScore(pts), earnerUuid);
    }

    /**
     * TH-style score extends: +1 life per milestone (sound via
     * {@link GameEvent#SCORE_EXTEND}).
     */
    private void applyScoreExtends(int extendsGranted, UUID milestoneEarnerUuid) {
        if (extendsGranted <= 0) {
            return;
        }
        if (rules.scoreExtendAwardAllCoopPlayers) {
            for (PlayerState2D ps : getAllPlayerStates()) {
                if (ps.lives < 0) {
                    continue;
                }
                ps.lives += extendsGranted;
                for (int i = 0; i < extendsGranted; i++) {
                    ps.personalEvents.add(GameEvent.SCORE_EXTEND);
                }
            }
        } else {
            PlayerState2D ps = getPlayerState(milestoneEarnerUuid);
            if (ps != null && ps.lives >= 0) {
                ps.lives += extendsGranted;
                for (int i = 0; i < extendsGranted; i++) {
                    ps.personalEvents.add(GameEvent.SCORE_EXTEND);
                }
            }
        }
    }

    // ---------------------------------------------------------------- inner types

    /** One pre-computed enemy spawn entry from the flat wave schedule. */
    private static final class ScheduledEnemy {
        final int spawnTick;
        final WaveEnemy we;

        ScheduledEnemy(int spawnTick, WaveEnemy we) {
            this.spawnTick = spawnTick;
            this.we = we;
        }
    }

    // ---------------------------------------------------------------- tick

    public void tick() {
        if (over)
            return;
        if (globallyPaused)
            return;
        stageTick++;

        boolean frozen = timeStopTicks > 0;
        if (frozen) {
            timeStopTicks--;
            if (timeStopTicks == 0) {
                resumeFrozenBullets();
            }
        }

        // 1. World ticking (Frozen if Time Stop active)
        if (!frozen) {
            bullets.tick();
            tickDivineWindCurves();
            tickBouncingEnemyBullets();
            enemies.tick();
            items.tick();
            for (ScoreSystem ss : scoreByPlayer.values()) {
                ss.tick();
            }

            if (masterSparkTicks > 0) {
                masterSparkTicks--;
                tickMasterSpark();
            }
        } else {
            // During time stop, Sakuya can still attract items and collect them
            tickItemAttraction();
        }
        tickAttractingItems();

        // 2. Player Bullets + Homing (Always tick)
        playerBullets.tick();
        tickSpecialBullets(playerBullets, frozen);
        tickHomingBullets(playerBullets);
        for (BulletPool pb : coopBullets.values()) {
            pb.tick();
            tickSpecialBullets(pb, frozen);
            tickHomingBullets(pb);
        }

        // 3. Phase handling
        if (arenaPhase == ArenaPhase.WAVES) {
            if (!frozen)
                tickStage();
        } else if (arenaPhase == ArenaPhase.DIALOG_INTRO) {
            tickDialogIntro();
        } else {
            if (!frozen) {
                spellcard.tick();
                if (spellcard.consumeSurvivalPhaseEnd())
                    checkBossPhaseTransition(true);
                bossTick++;
                // Rank creeps up slowly during the boss fight (1 point per 2s); capped at 32.
                if (bossTick % 40 == 0)
                    rank = Math.min(32, rank + 1);

                if (phaseTransitionTimer > 0) {
                    phaseTransitionTimer--;
                    float targetX = transitionTargetBossX();
                    float targetY = transitionTargetBossY();
                    bossX += (targetX - bossX) * 0.06f;
                    bossY += (targetY - bossY) * 0.06f;
                    if (phaseTransitionTimer == 0 && pendingNextPhase >= 0) {
                        startBossPhase(pendingNextPhase);
                        pendingNextPhase = -1;
                    }
                } else {
                    tickBossAI();
                }
            }
        }

        // 4. Bullet & Laser Collisions (Unified for all phases/states)
        if (arenaPhase != ArenaPhase.DIALOG_INTRO) {
            // Player Bullets vs Enemies (Fairies/Minions)
            checkPlayerBulletsVsEnemies(playerBullets, player);
            for (var entry : coopPlayers.entrySet()) {
                BulletPool pb = coopBullets.get(entry.getKey());
                if (pb != null)
                    checkPlayerBulletsVsEnemies(pb, entry.getValue());
            }

            // Player Bullets vs Boss
            if (arenaPhase == ArenaPhase.BOSS && !currentBossPhase().resolveSurvival(difficulty.ordinal())) {
                checkPlayerBulletsVsBoss(playerUuid, playerBullets, player);
                for (var entry : coopPlayers.entrySet()) {
                    BulletPool pb = coopBullets.get(entry.getKey());
                    if (pb != null)
                        checkPlayerBulletsVsBoss(entry.getKey(), pb, entry.getValue());
                }
            }
        }

        // 5. Player Actions (Shots, Gauge, Skill Follow-ups)
        tickPlayerShots(playerUuid, player, playerBullets);
        if (!frozen)
            tickSkillGauge(playerUuid, player);

        for (var e : coopPlayers.entrySet()) {
            UUID cUuid = e.getKey();
            PlayerState2D cPs = e.getValue();
            BulletPool cPb = coopBullets.get(cUuid);
            if (!frozen)
                tickSkillGauge(cUuid, cPs);
            if (cPb != null && cPs.lives >= 0) {
                tickPlayerShots(cUuid, cPs, cPb);
            }
        }

        // 6. Enemy Bullets & Items vs Players
        checkEnemyBulletsVsPlayer(playerUuid, player);
        for (var e : coopPlayers.entrySet()) {
            if (e.getValue().lives >= 0)
                checkEnemyBulletsVsPlayer(e.getKey(), e.getValue());
        }

        checkLasersVsPlayer(playerUuid, player);
        for (var e : coopPlayers.entrySet()) {
            if (e.getValue().lives >= 0)
                checkLasersVsPlayer(e.getKey(), e.getValue());
        }

        // Refactored Item Pickup to remove host bias
        checkAllItemPickups();

        // Death countdown + invuln tick for host
        if (player.deathPendingTicks > 0) {
            player.deathPendingTicks--;
            if (player.deathPendingTicks == 0)
                applyDeath(playerUuid);
        }
        if (player.invulnTicks > 0)
            player.invulnTicks--;
        tickGrazeChain(player);
        // Death countdown + invuln tick for coop players
        for (var e : coopPlayers.entrySet()) {
            PlayerState2D ps = e.getValue();
            if (ps.deathPendingTicks > 0) {
                ps.deathPendingTicks--;
                if (ps.deathPendingTicks == 0)
                    applyDeath(e.getKey());
            }
            if (ps.invulnTicks > 0)
                ps.invulnTicks--;
            tickGrazeChain(ps);
        }

        refreshDebugGodMode();
    }

    /**
     * Operator debug: infinite lives/bombs and long invulnerability for the
     * toggled participant(s).
     */
    private void refreshDebugGodMode() {
        if (BHDebugMode.isGodMode(playerUuid))
            applyDebugGod(player);
        for (var e : coopPlayers.entrySet()) {
            if (BHDebugMode.isGodMode(e.getKey()))
                applyDebugGod(e.getValue());
        }
    }

    private static void applyDebugGod(PlayerState2D ps) {
        ps.lives = Math.max(ps.lives, 9);
        ps.bombs = 9;
        ps.invulnTicks = Math.max(ps.invulnTicks, 600);
        ps.deathPendingTicks = 0;
    }

    private static void tickGrazeChain(PlayerState2D ps) {
        if (ps.grazeChainCooldown > 0) {
            ps.grazeChainCooldown--;
            if (ps.grazeChainCooldown == 0)
                ps.grazeChain = 0;
        }
    }

    /** Arena tick counter (waves + boss); for debug HUD only. */
    public int getDebugArenaTick() {
        return stageTick;
    }

    /** Current dynamic rank (0 = easiest, 32 = hardest, default 16). */
    public int getRank() {
        return rank;
    }

    /** Boss pattern cooldown remaining; for debug HUD (0 during waves / dialog). */
    public int getDebugBossPatternCooldown() {
        return patternCooldown;
    }

    /** Boss horizontal movement direction used for sprite-side travel frames. */
    public int getBossMoveDir() {
        return bossMoveDir;
    }

    /**
     * Progress for {@code PENTAGRAM_RITUAL} (-1 = not active). Synced for client
     * backdrop drawing.
     */
    public int getPentagramRitualTick() {
        return pentagramRitualTick;
    }

    /**
     * Ritual tick when stacked outline finished spawning; -1 until then. Synced for
     * client backdrop.
     */
    public int getPentagramStackCompleteTick() {
        return ritualStackCompleteAt;
    }

    private void tickStage() {
        tickEnemyAI();
        tickWaves();
        checkWavesComplete();
    }

    private void tickSkillGauge(UUID uuid, PlayerState2D ps) {
        if (ps.lives < 0)
            return;

        // Disable skills entirely if the stage forces Classic (TH6) rules
        if ("classic".equals(rules.forceControlScheme)) {
            ps.storedChargeProgress = 0;
            ps.holdChargeProgress = 0;
            ps.syncChargePacketFields();
            return;
        }

        // Also disable if the player individually chose Classic layout
        if (mc.sayda.bullethell.BHControlSettings
                .serverGetPreference(uuid) == mc.sayda.bullethell.BHControlScheme.CLASSIC) {
            ps.storedChargeProgress = 0;
            ps.holdChargeProgress = 0;
            ps.syncChargePacketFields();
            return;
        }

        // Sakuya's Time Stop pauses gauge build for everyone involved
        if (timeStopTicks > 0)
            return;

        if (ps.chargeLockoutTicks > 0) {
            ps.chargeLockoutTicks--;
            ps.syncChargePacketFields();
            return;
        }

        // Gray stock: passive only while X is not held (TH19: Z still shoots).
        if (!ps.isCharging) {
            float mult = ps.shooting ? ps.chargeRateShooting : ps.chargeRateIdle;
            double passive = mult * (3.0 / 2000.0) * PlayerState2D.CHARGE_GLOBAL_SPEED_MULT;
            ps.storedChargeProgress = Math.min(PlayerState2D.CHARGE_LEVEL_MAX,
                    ps.storedChargeProgress + passive);
        } else {
            ps.chargeConsecutiveHoldTicks++;
            // New press: restart colored hold meter (PoFV).
            if (ps.chargeConsecutiveHoldTicks == 1)
                ps.holdChargeProgress = 0.0;
            // Touhou 9: first 9 frames of holding charge, the bar does not move.
            if (ps.chargeConsecutiveHoldTicks > PlayerState2D.POFV_CHARGE_STARTUP_FRAMES
                    && ps.holdChargeProgress < PlayerState2D.CHARGE_LEVEL_MAX) {
                double per = (1.0 / ps.chargeSpeedFrames) * PlayerState2D.CHARGE_GLOBAL_SPEED_MULT;
                ps.holdChargeProgress = Math.min(PlayerState2D.CHARGE_LEVEL_MAX,
                        ps.holdChargeProgress + per);
            }
            // Hold cannot exceed stored stock (same bar, colored sits on gray).
            ps.holdChargeProgress = Math.min(ps.holdChargeProgress, ps.storedChargeProgress);
        }

        if (!ps.isCharging)
            ps.chargeConsecutiveHoldTicks = 0;

        ps.syncChargePacketFields();
    }

    // ================================================================ WAVE PHASE

    /**
     * Pre-expand all stage waves (including waveRef templates) into a flat list
     * sorted by absolute spawn tick. Called once in the constructor.
     * Difficulty timing compression is baked in here so tickWaves() is trivial.
     */
    private void buildScheduledList() {
        float mult = BullethellConfig.waveTimingMult(difficulty);
        List<ScheduledEnemy> list = new ArrayList<>();
        applicableWaveDefinitionCount = 0;
        boolean procedural = stage.fairyRush != null;

        for (WaveDefinition wave : stage.waves) {
            if (!waveAppliesToDifficulty(wave))
                continue;
            if (procedural && wave.waveRef != null && !wave.waveRef.isEmpty()) {
                System.err.println("[BulletHell] Stage " + stage.id
                        + ": ignoring waveRef while fairyRush is active: " + wave.waveRef);
                continue;
            }
            applicableWaveDefinitionCount++;
            List<WaveEnemy> waveEnemies;
            if (wave.waveRef != null && !wave.waveRef.isEmpty()) {
                waveEnemies = FairyWaveLoader.load(wave.waveRef).enemies;
            } else {
                waveEnemies = wave.enemies;
            }
            int baseSpawnTick = (int) (wave.spawnTick / mult);
            for (WaveEnemy we : waveEnemies) {
                list.add(new ScheduledEnemy(baseSpawnTick + we.delayTicks, we));
            }
        }

        if (procedural) {
            applicableWaveDefinitionCount += Math.max(0, stage.fairyRush.slotCount);
            appendProceduralFairyRush(list, mult, stage.fairyRush);
        }

        list.sort(Comparator.comparingInt(e -> e.spawnTick));
        scheduledEnemies.addAll(list);
    }

    private static int scaleDesignerTicks(int ticks, float scale, int floor) {
        int v = Math.round(ticks * scale);
        return Math.max(floor, v);
    }

    private void appendProceduralFairyRush(List<ScheduledEnemy> list, float mult, FairyRushDefinition rush) {
        FairyWaveCatalog cat = FairyWaveCatalogLoader.load();
        List<FairyWaveCatalogEntry> pool = cat.entriesForSet(rush.catalogId);
        if (pool.isEmpty()) {
            System.err.println("[BulletHell] fairyRush: empty catalog set '" + rush.catalogId + "' for stage "
                    + stage.id);
            return;
        }

        java.util.Random pickRandom = rush.shuffleSeed != null
                ? new java.util.Random(seed ^ rush.shuffleSeed.longValue())
                : random;

        int nSlots = Math.max(0, rush.slotCount);
        int cursor = (int) (rush.startTick / mult);
        Deque<String> recent = new ArrayDeque<>();

        for (int slot = 0; slot < nSlots; slot++) {
            float progress = nSlots <= 1 ? 1f : (slot / (float) (nSlots - 1));

            if (rush.breatherEvery > 0 && slot > 0 && slot % rush.breatherEvery == 0) {
                List<WaveEnemy> breath = breatherEnemies(rush);
                int waveStart = cursor;
                for (WaveEnemy we : breath) {
                    list.add(new ScheduledEnemy(waveStart + we.delayTicks, we));
                }
                int maxDelay = maxEnemyDelayTicks(breath);
                int hintDesigner = maxDelay + 12 + Math.max(0, rush.breatherExtraTicks);
                hintDesigner = scaleDesignerTicks(hintDesigner, BullethellConfig.fairyRushDurationHintScale(difficulty),
                        18);
                int gapDesigner = pickGapDesigner(rush, progress, pickRandom);
                gapDesigner = scaleDesignerTicks(gapDesigner, BullethellConfig.fairyRushGapBreathingScale(difficulty),
                        4);
                cursor = waveStart + (int) (hintDesigner / mult) + (int) (gapDesigner / mult);
                continue;
            }

            int iLo = lerpInt(rush.intensityStartLo, rush.intensityEndLo, progress, rush.gapEasing);
            int iHi = lerpInt(rush.intensityStartHi, rush.intensityEndHi, progress, rush.gapEasing);
            if (iLo > iHi) {
                int t = iLo;
                iLo = iHi;
                iHi = t;
            }
            // Shift catalog intensity window by difficulty (wave timing alone does not
            // change which patterns spawn).
            int ib = BullethellConfig.fairyRushIntensityBias(difficulty);
            iLo = Math.max(0, Math.min(10, iLo + ib));
            iHi = Math.max(0, Math.min(10, iHi + ib));
            if (iLo > iHi) {
                int t = iLo;
                iLo = iHi;
                iHi = t;
            }

            FairyWaveCatalogEntry entry = pickCatalogEntry(pool, iLo, iHi, pickRandom, recent, rush.noRepeatLast);
            if (entry == null)
                continue;

            List<WaveEnemy> enemies = FairyWaveLoader.load(entry.id).enemies;
            if (enemies == null || enemies.isEmpty())
                continue;

            int waveStart = cursor;
            for (WaveEnemy we : enemies) {
                list.add(new ScheduledEnemy(waveStart + we.delayTicks, we));
            }

            pushRecent(recent, entry.id, rush.noRepeatLast);

            int hintDesigner = entry.durationHintTicks > 0
                    ? entry.durationHintTicks
                    : defaultDurationHintTicks(enemies);
            hintDesigner = scaleDesignerTicks(hintDesigner, BullethellConfig.fairyRushDurationHintScale(difficulty),
                    20);
            int gapDesigner = pickGapDesigner(rush, progress, pickRandom);
            gapDesigner = scaleDesignerTicks(gapDesigner, BullethellConfig.fairyRushGapBreathingScale(difficulty), 4);
            cursor = waveStart + (int) (hintDesigner / mult) + (int) (gapDesigner / mult);
        }
    }

    private static List<WaveEnemy> breatherEnemies(FairyRushDefinition rush) {
        if (rush.breatherWaveId != null && !rush.breatherWaveId.isBlank()) {
            List<WaveEnemy> from = FairyWaveLoader.load(rush.breatherWaveId).enemies;
            if (from != null && !from.isEmpty()) {
                return from;
            }
        }
        WaveEnemy w = new WaveEnemy();
        w.x = 240f;
        w.y = -20f;
        w.vx = 0f;
        w.vy = 3.2f;
        w.type = "YELLOW_FAIRY";
        return List.of(w);
    }

    private static int maxEnemyDelayTicks(List<WaveEnemy> enemies) {
        int m = 0;
        for (WaveEnemy we : enemies) {
            m = Math.max(m, we.delayTicks);
        }
        return m;
    }

    private static int defaultDurationHintTicks(List<WaveEnemy> enemies) {
        return maxEnemyDelayTicks(enemies) + 22 + enemies.size() * 6;
    }

    private static float rushEase(float p, String easingRaw) {
        float pClamped = Math.max(0f, Math.min(1f, p));
        if (easingRaw != null && easingRaw.equalsIgnoreCase("LINEAR")) {
            return pClamped;
        }
        return pClamped * pClamped * (3f - 2f * pClamped);
    }

    private static int lerpInt(int a, int b, float progress, String easingRaw) {
        float t = rushEase(progress, easingRaw);
        return Math.round(a + (b - a) * t);
    }

    private static int pickGapDesigner(FairyRushDefinition rush, float progress, java.util.Random pr) {
        float t = rushEase(progress, rush.gapEasing);
        float gmin = rush.gapTicksStartMin + (rush.gapTicksEndMin - rush.gapTicksStartMin) * t;
        float gmax = rush.gapTicksStartMax + (rush.gapTicksEndMax - rush.gapTicksStartMax) * t;
        int lo = Math.round(Math.min(gmin, gmax));
        int hi = Math.round(Math.max(gmin, gmax));
        if (hi < lo) {
            int x = lo;
            lo = hi;
            hi = x;
        }
        int jitter = Math.max(0, rush.jitterTicks);
        int base = lo + (jitter > 0 ? pr.nextInt(hi - lo + 1 + 2 * jitter) - jitter : pr.nextInt(hi - lo + 1));
        return Math.max(4, base);
    }

    private boolean catalogEntryApplies(FairyWaveCatalogEntry e) {
        return difficultyMatchesBounds(e.minDifficulty, e.maxDifficulty);
    }

    /**
     * Effective pick weight (catalog row ﾃ・mild bias toward intense patterns on
     * Hard+).
     */
    private float catalogPickWeight(FairyWaveCatalogEntry e) {
        float w = Math.max(0.001f, e.weight);
        w *= BullethellConfig.fairyCatalogIntensityWeightMultiplier(difficulty, e.intensity);
        return w;
    }

    private FairyWaveCatalogEntry pickCatalogEntry(List<FairyWaveCatalogEntry> pool, int iLo, int iHi,
            java.util.Random pr, Deque<String> recent, int noRepeatLast) {
        int widen = 0;
        while (widen <= 12) {
            int lo = iLo - widen;
            int hi = iHi + widen;
            List<FairyWaveCatalogEntry> candidates = new ArrayList<>();
            float weightSum = 0f;
            for (FairyWaveCatalogEntry e : pool) {
                if (!catalogEntryApplies(e))
                    continue;
                if (e.intensity < lo || e.intensity > hi)
                    continue;
                if (noRepeatLast > 0 && recent.contains(e.id))
                    continue;
                candidates.add(e);
                weightSum += catalogPickWeight(e);
            }
            if (!candidates.isEmpty() && weightSum > 0f) {
                float r = pr.nextFloat() * weightSum;
                for (FairyWaveCatalogEntry e : candidates) {
                    r -= catalogPickWeight(e);
                    if (r <= 0f)
                        return e;
                }
                return candidates.get(candidates.size() - 1);
            }
            widen++;
        }
        // Fallback: ignore no-repeat only
        List<FairyWaveCatalogEntry> candidates = new ArrayList<>();
        float weightSum = 0f;
        for (FairyWaveCatalogEntry e : pool) {
            if (!catalogEntryApplies(e))
                continue;
            candidates.add(e);
            weightSum += catalogPickWeight(e);
        }
        if (candidates.isEmpty() || weightSum <= 0f)
            return null;
        float r = pr.nextFloat() * weightSum;
        for (FairyWaveCatalogEntry e : candidates) {
            r -= catalogPickWeight(e);
            if (r <= 0f)
                return e;
        }
        return candidates.get(candidates.size() - 1);
    }

    private static void pushRecent(Deque<String> recent, String id, int noRepeatLast) {
        if (noRepeatLast <= 0)
            return;
        recent.addLast(id);
        while (recent.size() > noRepeatLast) {
            recent.removeFirst();
        }
    }

    private void tickWaves() {
        while (nextScheduledIdx < scheduledEnemies.size()) {
            ScheduledEnemy se = scheduledEnemies.get(nextScheduledIdx);
            if (stageTick < se.spawnTick)
                break;
            spawnScheduledEnemy(se.we);
            nextScheduledIdx++;
        }
    }

    private void spawnScheduledEnemy(WaveEnemy we) {
        EnemyType type = enemyTypeByName(we.type);
        int slot = enemies.spawn(we.x, we.y, we.vx, we.vy, we.angVel, we.arcTicks, type);
        if (slot >= 0) {
            EnemyPattern pattern;
            if (we.pattern != null && !we.pattern.isEmpty()) {
                pattern = EnemyPattern.fromName(we.pattern);
            } else {
                pattern = type.defaultPattern;
            }
            enemyPatternIds[slot] = pattern;
        }
    }

    private void tickEnemyAI() {
        for (int i = 0; i < EnemyPool.CAPACITY; i++) {
            if (!enemies.isActive(i))
                continue;

            float ex = enemies.getX(i);
            float ey = enemies.getY(i);

            // Despawn if off-screen
            if (ey > BulletPool.ARENA_H + 100
                    || ey < -100
                    || ex < -100
                    || ex > BulletPool.ARENA_W + 100) {
                enemies.deactivate(i);
                continue;
            }

            // Don't fire while outside arena bounds - prevents invisible fairies from
            // shooting
            if (ex < 0 || ex > BulletPool.ARENA_W || ey < 0 || ey > BulletPool.ARENA_H)
                continue;

            // Attack AI - fire when cooldown hits 0
            if (enemies.getAtkCd(i) == 0) {
                EnemyType type = EnemyType.fromId(enemies.getType(i));

                float effDens = BullethellConfig.effectiveDensityMult(difficulty);
                int scaledCount = Math.max(1,
                        Math.round(type.bulletCount * effDens * BullethellConfig.FAIRY_BULLET_COUNT_MULT.get()));
                int scaledInterval = (int) (type.atkInterval / effDens);
                scaledInterval = Math.round(scaledInterval * BullethellConfig.FAIRY_ATTACK_INTERVAL_MULT.get());
                scaledInterval = Math.max(BullethellConfig.FAIRY_MIN_ATTACK_INTERVAL_TICKS.get(), scaledInterval);

                EnemyPattern pat = enemyPatternIds[i];
                if (pat == null)
                    pat = type.defaultPattern;

                switch (pat) {
                    case AIMED: {
                        // Cap small-fairy aimed fans; Lunatic allows one extra way over Hard.
                        int aimedCap = BullethellConfig.fairyAimedBurstCap(difficulty);
                        int aimed = Math.min(scaledCount, aimedCap);
                        PatternEngine.fireAimed(bullets, ex, ey,
                                player.x, player.y,
                                aimed, type.bulletSpread,
                                type.bulletSpeed, difficulty, BulletType.fromName("RICE"));
                        enemies.setAtkCooldown(i, scaledInterval);
                        break;
                    }
                    case RING: {
                        // Uniform ring, random start angle each burst (TH6 barrier style)
                        float ringStart = random.nextFloat() * (float) (Math.PI * 2);
                        PatternEngine.fireRingOffset(bullets, ex, ey,
                                scaledCount, type.bulletSpeed,
                                difficulty, BulletType.fromName("BUBBLE"), ringStart);
                        enemies.setAtkCooldown(i, scaledInterval);
                        break;
                    }
                    case AIMED_RING: {
                        // Aimed fan + slower outer ring (large fairy dual-threat)
                        float ringStart = random.nextFloat() * (float) (Math.PI * 2);
                        PatternEngine.fireAimedWithRing(bullets, ex, ey,
                                player.x, player.y,
                                scaledCount, type.bulletSpread, type.bulletSpeed,
                                8, type.bulletSpeed * 0.6f,
                                difficulty, BulletType.fromName("STAR"), BulletType.fromName("BUBBLE"), ringStart);
                        enemies.setAtkCooldown(i, scaledInterval);
                        break;
                    }
                    case SPREAD: {
                        int spreadCap = BullethellConfig.fairySpreadBurstCap(difficulty);
                        int spread = Math.min(scaledCount, spreadCap);
                        PatternEngine.fireSpread(bullets, ex, ey,
                                spread, type.bulletSpeed,
                                difficulty, BulletType.fromName("STAR"));
                        enemies.setAtkCooldown(i, scaledInterval);
                        break;
                    }
                    case STREAM:
                        // Rapid single bullet - danger from rate, not spread
                        PatternEngine.fireAimed(bullets, ex, ey,
                                player.x, player.y,
                                1, 0f, type.bulletSpeed, difficulty, BulletType.fromName("RICE"));
                        enemies.setAtkCooldown(i, Math.max(BullethellConfig.FAIRY_STREAM_COOLDOWN_MIN_TICKS.get(),
                                scaledInterval / Math.max(1, BullethellConfig.FAIRY_STREAM_COOLDOWN_DIVISOR.get())));
                        break;
                    default:
                        break;
                }
            }
        }
    }

    /** Check player bullets (from any participant) hitting enemies. */
    private void checkPlayerBulletsVsEnemies(BulletPool pb, PlayerState2D ps) {
        if (enemies.getActiveCount() == 0) return;
        for (int i = 0; i < pb.getCapacity(); i++) {
            if (!pb.isActive(i))
                continue;
            float bx = pb.getX(i);
            float by = pb.getY(i);
            BulletType bt = BulletType.fromId(pb.getType(i));
            float bulletR = bt.getRadius() * pb.getHitScale(i) * bt.getHitboxMul();
            // Iterate only live enemies via compact list - O(activeCount) instead of O(CAPACITY).
            for (int jj = 0; jj < enemies.getActiveCount(); jj++) {
                int j = enemies.getActiveSlot(jj);
                float ex = enemies.getX(j);
                float ey = enemies.getY(j);
                EnemyType type = EnemyType.fromId(enemies.getType(j));
                float enemyR = type.hitRadius + 3f;
                float combined = enemyR + bulletR;
                float dx = bx - ex;
                float dy = by - ey;
                if (dx * dx + dy * dy <= combined * combined) {
                    pb.deactivate(i);
                    if (enemies.damage(j, fairyBulletDamage(ps)))
                        killEnemy(j, ps);
                    break;
                }
            }
        }
    }

    /**
     * Kill an enemy: deactivate, award score, apply drop cycle.
     * 
     * @return the item pool slot that was spawned, or -1 if no item was dropped
     */
    private int killEnemy(int slot, PlayerState2D ps) {
        float ex = enemies.getX(slot);
        float ey = enemies.getY(slot);
        EnemyType type = EnemyType.fromId(enemies.getType(slot));
        enemies.deactivate(slot);

        addArenaScore(type.scoreValue, uuidForPlayerState(ps));

        pendingEvents.add(GameEvent.ENEMY_KILL);

        ps.addStoredChargeProgress(30 * 3.0 / 2000.0);

        // On-kill death burst (Lunatic-style)
        if (rules.onKillDeathBurstCount > 0) {
            PatternEngine.fireRing(bullets, ex, ey,
                    rules.onKillDeathBurstCount, rules.onKillDeathBurstSpeed,
                    difficulty, BulletType.fromName("RICE"));
        }

        // Item drops: TH6-style every-Nth small kill; large anchors always pay out
        // by default so rhythm is not stolen by mid-wave heavies.
        int n = rules.itemDropEveryNthKill < 1 ? 1 : rules.itemDropEveryNthKill;
        boolean dropThisKill;
        if (type.large && rules.largeEnemyAlwaysDrops) {
            dropThisKill = true;
        } else if (rules.largeEnemyAlwaysDrops) {
            smallEnemyKillCounter++;
            dropThisKill = (smallEnemyKillCounter % n == 0);
        } else {
            combinedDropKillCounter++;
            dropThisKill = (combinedDropKillCounter % n == 0);
        }

        if (!dropThisKill) {
            return -1;
        }

        // Rare bomb substitutes for the scheduled drop; does not advance P/Point
        // cycles.
        if (rules.bombDropChance > 0f && random.nextFloat() < rules.bombDropChance) {
            return items.spawn(ex, ey, ItemPool.TYPE_BOMB);
        }
        if (type.large) {
            int dropType = largeDropCycle[largeDropCycleIdx % largeDropCycle.length];
            largeDropCycleIdx++;
            return items.spawn(ex, ey, dropType);
        }
        int dropType = dropCycle[dropCycleIdx % dropCycle.length];
        dropCycleIdx++;
        return items.spawn(ex, ey, dropType);
    }

    /** Transition to BOSS phase once all waves have spawned and cleared. */
    private void checkWavesComplete() {
        if (nextScheduledIdx < scheduledEnemies.size())
            return; // still enemies pending
        if (enemies.getActiveCount() > 0)
            return; // enemies still on screen

        // First tick after clearing: wipe screen and start countdown
        if (waveEndDelayLeft < 0) {
            bullets.clearAll();
            lasers.clearAll();
            waveEndDelayLeft = Math.max(0, stage.bossIntroDelayTicks);
        }
        if (waveEndDelayLeft > 0) {
            waveEndDelayLeft--;
            return;
        }
        transitionToDialogOrBoss();
    }

    private void transitionToDialogOrBoss() {
        dialogScriptByPlayer.clear();
        boolean hasAnyDialog = false;
        for (UUID participant : allParticipants()) {
            String participantCharId = getCharacterId(participant);
            List<mc.sayda.bullethell.boss.DialogLine> script = boss.characterDialogs.get(participantCharId);
            if (script == null || script.isEmpty()) {
                script = boss.introDialog;
            }
            dialogScriptByPlayer.put(participant, script);
            if (script != null && !script.isEmpty()) {
                hasAnyDialog = true;
            }
        }

        // Retain a non-null generic reference for legacy helpers/debugging.
        activeDialog = boss.introDialog;

        if (hasAnyDialog) {
            arenaPhase = ArenaPhase.DIALOG_INTRO;
            resetDialogProgressForAllPlayers();
            // Boss sprite glides in from above during dialog
            bossIntroVisible = true;
            bossX = BulletPool.ARENA_W / 2f;
            bossY = -80f;
        } else {
            transitionToBoss();
        }
    }

    private void transitionToBoss() {
        boolean hadIntro = bossIntroVisible;
        resetAbilityStates();
        arenaPhase = ArenaPhase.BOSS;
        dialogScriptByPlayer.clear();
        dialogIndexByPlayer.clear();
        dialogTicksLeftByPlayer.clear();
        dialogReadyByPlayer.clear();
        bossIntroVisible = false;
        bullets.clearAll();
        lasers.clearAll();
        enemies.clearAll();
        playerBullets.clearAll();
        for (BulletPool pb : coopBullets.values()) pb.clearAll();
        bossTick = 0;
        bossX = BulletPool.ARENA_W / 2f;
        // Smooth Y from dialog landing into the fight; snap when there was no dialog
        if (hadIntro) {
            bossEntryFromY = bossY;
            bossEntryTimer = boss.fightEntryTicks;
            String phase0mvmt = activeBossPhases.isEmpty() ? "SINE_WAVE"
                    : activeBossPhases.get(0).resolveMovement(difficulty.ordinal());
            bossEntryToY = switch (phase0mvmt) {
                case "REPOS_TOP" -> activeBossPhases.isEmpty() ? 80f : activeBossPhases.get(0).reposBossY;
                case "CIRCLE" -> 80f;
                default -> 100f;
            };
        } else {
            bossY = 100f;
            bossEntryTimer = 0;
        }
        startBossPhase(0);
        if (bossEntryTimer > 0)
            bossFireFrozen = true;
        pendingEvents.add(GameEvent.PHASE_CHANGE);
    }

    // ---------------------------------------------------------------- dialog
    // control

    private void resetDialogProgressForAllPlayers() {
        dialogIndexByPlayer.clear();
        dialogTicksLeftByPlayer.clear();
        dialogReadyByPlayer.clear();
        for (UUID participant : allParticipants()) {
            initDialogStateForPlayer(participant);
        }
    }

    private void initDialogStateForPlayer(UUID participant) {
        List<mc.sayda.bullethell.boss.DialogLine> script = dialogScriptByPlayer.get(participant);
        if (script == null || script.isEmpty()) {
            dialogIndexByPlayer.put(participant, 0);
            dialogTicksLeftByPlayer.put(participant, 0);
            dialogReadyByPlayer.put(participant, true);
            return;
        }
        dialogIndexByPlayer.put(participant, 0);
        dialogTicksLeftByPlayer.put(participant, Math.max(0, script.get(0).delayTicks));
        dialogReadyByPlayer.put(participant, false);
    }

    private void tickDialogIntro() {
        // Slide boss in from off-screen top toward dialog landing position each tick
        if (bossIntroVisible) {
            float landY = boss.introLandY;
            if (bossY < landY) {
                bossY += (landY - bossY) * boss.introSlideSpeed;
                if (bossY >= landY - 0.5f)
                    bossY = landY;
            }
        }

        if (dialogScriptByPlayer.isEmpty()) {
            transitionToBoss();
            return;
        }
        for (UUID participant : allParticipants()) {
            if (!dialogReadyByPlayer.containsKey(participant)) {
                initDialogStateForPlayer(participant);
            }
            if (Boolean.TRUE.equals(dialogReadyByPlayer.get(participant))) {
                continue;
            }
            int ticksLeft = Math.max(0, dialogTicksLeftByPlayer.getOrDefault(participant, 0));
            if (ticksLeft > 0) {
                dialogTicksLeftByPlayer.put(participant, ticksLeft - 1);
            } else {
                advanceDialogOneLine(participant);
            }
        }
        if (isDialogReadyForAllActivePlayers()) {
            transitionToBoss();
        }
    }

    private void advanceDialogOneLine(UUID participant) {
        List<mc.sayda.bullethell.boss.DialogLine> script = dialogScriptByPlayer.get(participant);
        if (script == null || script.isEmpty()) {
            dialogReadyByPlayer.put(participant, true);
            dialogTicksLeftByPlayer.put(participant, 0);
            dialogIndexByPlayer.put(participant, 0);
            return;
        }
        int nextIndex = dialogIndexByPlayer.getOrDefault(participant, 0) + 1;
        if (nextIndex >= script.size()) {
            dialogReadyByPlayer.put(participant, true);
            dialogTicksLeftByPlayer.put(participant, 0);
            dialogIndexByPlayer.put(participant, Math.max(0, script.size() - 1));
            return;
        }
        dialogIndexByPlayer.put(participant, nextIndex);
        dialogTicksLeftByPlayer.put(participant, Math.max(0, script.get(nextIndex).delayTicks));
    }

    private boolean isDialogReadyForAllActivePlayers() {
        for (UUID participant : allParticipants()) {
            if (!Boolean.TRUE.equals(dialogReadyByPlayer.get(participant))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Called when a player presses Z (advance one line) or Ctrl (skip all).
     * Safe to call from any participant - each player has an independent dialog
     * cursor.
     *
     * @param participant UUID of the player issuing the dialog action
     * @param skipAll     true = jump straight to the boss fight; false = advance
     *                    one
     *                    line
     */
    public void skipDialog(UUID participant, boolean skipAll) {
        if (arenaPhase != ArenaPhase.DIALOG_INTRO)
            return;
        if (!allParticipants().contains(participant))
            return;
        if (skipAll) {
            dialogReadyByPlayer.put(participant, true);
            dialogTicksLeftByPlayer.put(participant, 0);
        } else {
            // Advance this player's line immediately
            advanceDialogOneLine(participant);
        }
        if (isDialogReadyForAllActivePlayers()) {
            transitionToBoss();
        }
    }

    // ---------------------------------------------------------------- dialog
    // accessors (for ArenaStatePacket)

    /**
     * Speaker of the current dialog line; empty string when no dialog is active.
     */
    public String getDialogSpeaker(UUID participant) {
        if (Boolean.TRUE.equals(dialogReadyByPlayer.get(participant)))
            return "";
        int idx = dialogIndexByPlayer.getOrDefault(participant, 0);
        List<mc.sayda.bullethell.boss.DialogLine> script = dialogScriptByPlayer.get(participant);
        if (arenaPhase != ArenaPhase.DIALOG_INTRO
                || script == null || script.isEmpty()
                || idx >= script.size())
            return "";
        return script.get(idx).speaker;
    }

    /** Text of the current dialog line; empty string when no dialog is active. */
    public String getDialogText(UUID participant) {
        if (Boolean.TRUE.equals(dialogReadyByPlayer.get(participant)))
            return "";
        int idx = dialogIndexByPlayer.getOrDefault(participant, 0);
        List<mc.sayda.bullethell.boss.DialogLine> script = dialogScriptByPlayer.get(participant);
        if (arenaPhase != ArenaPhase.DIALOG_INTRO
                || script == null || script.isEmpty()
                || idx >= script.size())
            return "";
        return script.get(idx).text;
    }

    /** Increments with each new line; lets the client reset slide-in animation. */
    public int getDialogLineIndex(UUID participant) {
        return dialogIndexByPlayer.getOrDefault(participant, 0);
    }

    public int getDialogReadyCount() {
        int ready = 0;
        for (UUID participant : allParticipants()) {
            if (Boolean.TRUE.equals(dialogReadyByPlayer.get(participant))) {
                ready++;
            }
        }
        return ready;
    }

    public int getDialogParticipantCount() {
        return allParticipants().size();
    }

    // ================================================================ BOSS PHASE

    private boolean isDynamicDifficultyEnabled(PatternStep step) {
        if (step != null && !step.dynamicDifficulty)
            return false;
        PhaseDefinition cur = currentBossPhase();
        if (cur != null && !cur.dynamicDifficulty)
            return false;
        return true;
    }

    /**
     * Effective bullet density for boss patterns. LUNATIC = raw JSON (exactly 1.0,
     * no phase creep - JSON is the ceiling). Lower difficulties scale down and
     * creep upward toward the JSON ceiling as phases progress.
     */
    private float bossDensityMult(PatternStep step) {
        if (!isDynamicDifficultyEnabled(step))
            return 1f;
        if (difficulty == DifficultyConfig.LUNATIC)
            return 1f;
        float phaseCreep = 1f + Math.min(
                BullethellConfig.BOSS_PHASE_DENSITY_CAP.get(),
                bossPhase * BullethellConfig.BOSS_PHASE_DENSITY_PER_PHASE.get());
        return Math.min(1f, BullethellConfig.effectiveDensityMult(difficulty) * phaseCreep);
    }

    /**
     * Effective bullet speed multiplier for boss patterns. LUNATIC = raw JSON
     * (no phase creep). Lower difficulties scale down and creep upward, capped
     * at the Lunatic ceiling (1.0).
     */
    private float bossSpeedMult(PatternStep step) {
        if (!isDynamicDifficultyEnabled(step))
            return 1f;
        if (difficulty == DifficultyConfig.LUNATIC)
            return BullethellConfig.effectiveSpeedMult(difficulty);
        float phaseCreep = 1f + Math.min(
                BullethellConfig.BOSS_PHASE_SPEED_CAP.get(),
                bossPhase * BullethellConfig.BOSS_PHASE_SPEED_PER_PHASE.get());
        return Math.min(BullethellConfig.effectiveSpeedMult(DifficultyConfig.LUNATIC),
                BullethellConfig.effectiveSpeedMult(difficulty) * phaseCreep);
    }

    private List<PhaseDefinition> buildActiveBossPhases() {
        if (boss.phases == null || boss.phases.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<PhaseDefinition> filtered = new ArrayList<>();
        for (PhaseDefinition phase : boss.phases) {
            if (phaseAppliesToDifficulty(phase)) {
                filtered.add(phase);
            }
        }
        // Safety fallback: keep fights playable even if all gates are misconfigured.
        return filtered.isEmpty() ? boss.phases : filtered;
    }

    private boolean phaseAppliesToDifficulty(PhaseDefinition phase) {
        return difficultyMatchesBounds(phase.minDifficulty, phase.maxDifficulty);
    }

    private boolean waveAppliesToDifficulty(WaveDefinition wave) {
        return difficultyMatchesBounds(wave.minDifficulty, wave.maxDifficulty);
    }

    private boolean difficultyMatchesBounds(String minRaw, String maxRaw) {
        DifficultyConfig min = parseDifficultyBound(minRaw);
        DifficultyConfig max = parseDifficultyBound(maxRaw);
        int cur = difficulty.ordinal();
        if (min != null && cur < min.ordinal())
            return false;
        if (max != null && cur > max.ordinal())
            return false;
        return true;
    }

    private static DifficultyConfig parseDifficultyBound(String raw) {
        if (raw == null || raw.isBlank())
            return null;
        try {
            return DifficultyConfig.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * Living player closest to the boss - boss aimed patterns and lasers target
     * this
     * player so co-op feels like shared pressure instead of always targeting the
     * host.
     */
    private PlayerState2D getBossAimTarget() {
        PlayerState2D closest = null;
        float bestD2 = Float.MAX_VALUE;
        if (player.lives >= 0) {
            float dx = player.x - bossX;
            float dy = player.y - bossY;
            bestD2 = dx * dx + dy * dy;
            closest = player;
        }
        for (var e : coopPlayers.entrySet()) {
            PlayerState2D ps = e.getValue();
            if (ps.lives < 0)
                continue;
            float dx = ps.x - bossX;
            float dy = ps.y - bossY;
            float d2 = dx * dx + dy * dy;
            if (d2 < bestD2) {
                bestD2 = d2;
                closest = ps;
            }
        }
        return closest != null ? closest : player;
    }

    private void tickBossAI() {
        lasers.tick();
        PhaseDefinition phase = currentBossPhase();
        if (!pentagramRitualForCurrentPhase(phase) && pentagramRitualTick >= 0) {
            pentagramFormation.clear(bullets);
            pentagramRitualTick = -1;
            ritualStackCompleteAt = -1;
            prPentagramDisassembled = false;
            pentagramRitualCfg = null;
            pentagramLastNewWaveTick = 0;
        }

        // Refresh per-attack movement and texture overrides from the current step.
        if (phase.attacks != null && !phase.attacks.isEmpty()) {
            mc.sayda.bullethell.boss.PatternStep curStep =
                    phase.attacks.get(attackIndex % phase.attacks.size());
            activeMovementOverride = (curStep.movementOverride != null && !curStep.movementOverride.isEmpty())
                    ? curStep.movementOverride : null;
            activeBossTexture = curStep.bossTexture != null ? curStep.bossTexture : "";
        }

        // Use ticks relative to this phase's start so movement begins at centre
        // every phase and never jumps when formulas change.
        int lt = bossTick - phaseStartTick;
        float oldX = bossX;
        float moveAmp = phase.resolveMoveRange(difficulty.ordinal());
        String resolvedMovement = activeMovementOverride != null
                ? activeMovementOverride : phase.resolveMovement(difficulty.ordinal());
        switch (resolvedMovement) {
            case "CIRCLE" -> {
                bossX = BulletPool.ARENA_W / 2f + (float) Math.sin(lt * phase.orbitSpeed) * moveAmp;
                bossY = 80f + (float) (1 - Math.cos(lt * phase.orbitSpeed)) * moveAmp * phase.orbitHeight;
            }
            case "STATIC" -> {
                /* fixed */ }
            case "REPOS_TOP" -> {
                bossY = phase.reposBossY;
                if (reposPhaseTimer <= 0) {
                    switch (reposDashState) {
                        case 0 -> {
                            reposStartX = bossX;
                            float minX = phase.reposMinX < 0 ? phase.reposXMargin : phase.reposMinX;
                            float maxX = phase.reposMaxX < 0 ? BulletPool.ARENA_W - phase.reposXMargin
                                    : phase.reposMaxX;
                            reposTargetX = minX + random.nextFloat() * Math.max(1f, maxX - minX);
                            reposDashState = 1;
                            reposPhaseTimer = phase.reposDashTicks;
                            bossFireFrozen = true;
                        }
                        case 1 -> {
                            bossX = reposTargetX;
                            reposDashState = 2;
                            reposPhaseTimer = phase.reposBreathTicks;
                        }
                        case 2 -> {
                            reposDashState = 0;
                            reposPhaseTimer = resolveReposShootTicks();
                            bossFireFrozen = false;
                            if (!phase.keepAttackIndexOnRepos) attackIndex = 0;
                            patternCooldown = 0;
                            secondaryCooldowns.clear();
                            resetSecondaryLifetimes();
                        }
                        default -> reposPhaseTimer = resolveReposShootTicks();
                    }
                }
                if (reposDashState == 1 && reposPhaseTimer > 0) {
                    // Cubic ease-in-out for smooth acceleration + deceleration
                    float frac = 1f - reposPhaseTimer / (float) phase.reposDashTicks;
                    float t = frac * frac * (3f - 2f * frac);
                    bossX = reposStartX + (reposTargetX - reposStartX) * t;
                }
                if (reposPhaseTimer > 0)
                    reposPhaseTimer--;
            }
            case "DASH_TOP" -> {
                if (dashTopState == 0) {
                    // Waiting between dashes
                    if (dashTopTimer <= 0) {
                        // Start next dash
                        dashTopStartX  = bossX;
                        dashTopStartY  = bossY;
                        dashTopTargetX = phase.dashTopMinX + random.nextFloat() * (phase.dashTopMaxX - phase.dashTopMinX);
                        dashTopTargetY = phase.dashTopMinY + random.nextFloat() * (phase.dashTopMaxY - phase.dashTopMinY);
                        dashTopState   = 1;
                        dashTopTimer   = phase.dashTopDashTicks;
                    } else {
                        dashTopTimer--;
                    }
                } else {
                    // Dashing - cubic ease-in-out
                    if (dashTopTimer > 0) {
                        float frac = 1f - dashTopTimer / (float) phase.dashTopDashTicks;
                        float t = frac * frac * (3f - 2f * frac);
                        bossX = dashTopStartX + (dashTopTargetX - dashTopStartX) * t;
                        bossY = dashTopStartY + (dashTopTargetY - dashTopStartY) * t;
                        dashTopTimer--;
                    } else {
                        bossX = dashTopTargetX;
                        bossY = dashTopTargetY;
                        dashTopState = 0;
                        dashTopTimer = phase.dashTopIntervalTicks;
                    }
                }
            }
            default -> // SINE_WAVE: starts at centre (sin 0 = 0)
                bossX = BulletPool.ARENA_W / 2f + (float) Math.sin(lt * phase.swingSpeed) * moveAmp;
        }

        // Smoothstep Y from dialog landing position into the fight start position
        if (bossEntryTimer > 0) {
            float frac = 1f - bossEntryTimer / (float) boss.fightEntryTicks;
            float t = frac * frac * (3f - 2f * frac);
            bossY = bossEntryFromY + (bossEntryToY - bossEntryFromY) * t;
            bossEntryTimer--;
            if (bossEntryTimer == 0)
                bossFireFrozen = false;
        }

        float dx = bossX - oldX;
        if (Math.abs(dx) > 0.025f) {
            bossMoveDir = dx > 0f ? 1 : -1;
        } else {
            bossMoveDir = 0;
        }

        if (isScarletMeisterPhase()) {
            tickScarletMeister();
            return;
        }

        tickPhaseEmitters();

        if (pentagramRitualForCurrentPhase(phase)) {
            if (pentagramRitualTick < 0) {
                pentagramRitualCfg = findPentagramRitualStep(phase);
                if (pentagramRitualCfg != null) {
                    pentagramFormation.clear(bullets);
                    pentagramRitualTick = 0;
                    ritualStackCompleteAt = -1;
                    prPentagramDisassembled = false;
                    pentagramLastNewWaveTick = 0;
                }
            }
            if (pentagramRitualTick >= 0 && pentagramRitualCfg != null)
                tickPentagramRitual();
            return;
        }

        if (!seaSplitForCurrentPhase(phase) && seaSplitTick >= 0) {
            seaSplitTick = -1;
            seaSplitCfg = null;
        }
        if (seaSplitForCurrentPhase(phase)) {
            if (seaSplitTick < 0) {
                seaSplitCfg = findSeaSplitStep(phase);
                seaSplitAngle = 0f;
                seaSplitFireCd = 0;
                seaSplitSecCd = 0;
                seaSplitSecIdx = 0;
                seaSplitTick = 0;
            }
            if (seaSplitCfg != null)
                tickSeaSplit();
            return;
        }

        tickWormCircle();
        tickRingSpawner();
        fireEveryTickWhilePhaseAttacks(phase);

        if (phase.attacks == null || phase.attacks.isEmpty()) {
            patternCooldown = 20;
            return;
        }

        List<PatternStep> mainRotation = phase.getMainRotation();
        if (mainRotation.isEmpty())
            return;

        if (bossSegmentTicksRemaining > 0) {
            tickBossAttackSegment(phase, mainRotation);
            return;
        }

        if (bossFireFrozen)
            return;

        patternCooldown--;
        if (patternCooldown > 0)
            return;

        // Continue a multi-shot burst (same PatternStep, same rotation slot).
        if (bossBurstVolleysRemaining > 0 && bossBurstStep != null) {
            PatternStep bStep = bossBurstStep;
            executeAttackAt(bStep, bossX + stepSpawnOX(bStep), bossY + stepSpawnOY(bStep));
            String bPat = bStep.getPatternUpper();
            bossBurstVolleysRemaining--;
            if (bossBurstVolleysRemaining > 0) {
                patternCooldown = burstSpacingTicks(bStep);
            } else {
                bossBurstStep = null;
                attackIndex++;
                patternCooldown = computeAttackCooldown(bStep, bPat);
            }
            return;
        }

        PatternStep step = mainRotation.get(attackIndex % mainRotation.size());
        int segDurTicks = effectiveSegmentDurationTicks(step);
        if (segDurTicks > 0) {
            bossSegmentTicksRemaining = Math.max(1, applyPatternTempoToCooldownTicks(segDurTicks, step, phase));
            bossSegmentVolleyCooldown = 0;
            tickBossAttackSegment(phase, mainRotation);
            return;
        }
        int volleys = burstVolleyCount(step);
        executeAttackAt(step, bossX + stepSpawnOX(step), bossY + stepSpawnOY(step));
        String pat = step.getPatternUpper();
        if (volleys > 1) {
            bossBurstStep = step;
            bossBurstVolleysRemaining = volleys - 1;
            patternCooldown = burstSpacingTicks(step);
        } else {
            attackIndex++;
            patternCooldown = computeAttackCooldown(step, pat);
        }
    }

    /**
     * Wall-clock segment length for {@link PatternStep#segmentDurationTicks} (tier
     * array or scalar).
     */
    private int effectiveSegmentDurationTicks(PatternStep step) {
        int v = stepTI(step, "segmentDurationTicks", step.segmentDurationTicks);
        return Math.max(0, v);
    }

    private int effectiveSegmentVolleyIntervalTicks(PatternStep step) {
        int iv = stepTI(step, "segmentVolleyIntervalTicks", step.segmentVolleyIntervalTicks);
        return iv > 0 ? iv : 1;
    }

    private String segmentSyncGroup(PatternStep step) {
        if (step == null)
            return "";
        String g = stepTS(step, "segmentSyncGroup", step.segmentSyncGroup);
        return g == null ? "" : g.trim();
    }

    private int contiguousSegmentSyncCount(List<PatternStep> mainRotation, int startIdx) {
        if (mainRotation == null || mainRotation.isEmpty())
            return 1;
        PatternStep first = mainRotation.get(startIdx % mainRotation.size());
        String group = segmentSyncGroup(first);
        if (group.isEmpty())
            return 1;
        int n = 1;
        while (startIdx + n < mainRotation.size()) {
            PatternStep s = mainRotation.get(startIdx + n);
            if (!group.equals(segmentSyncGroup(s)))
                break;
            n++;
        }
        return Math.max(1, n);
    }

    /**
     * One boss tick of a timed segment: wall-clock countdown, volleys spaced by
     * {@link PatternStep#segmentVolleyIntervalTicks}.
     */
    private void tickBossAttackSegment(PhaseDefinition phase, List<PatternStep> mainRotation) {
        if (mainRotation.isEmpty() || bossSegmentTicksRemaining <= 0)
            return;
        int slot = attackIndex % mainRotation.size();
        PatternStep step = mainRotation.get(slot);
        int syncCount = contiguousSegmentSyncCount(mainRotation, slot);
        bossSegmentTicksRemaining--;
        if (bossSegmentVolleyCooldown > 0) {
            bossSegmentVolleyCooldown--;
        } else {
            for (int i = 0; i < syncCount && (slot + i) < mainRotation.size(); i++) {
                PatternStep fire = mainRotation.get(slot + i);
                executeAttackAt(fire, bossX + stepSpawnOX(fire), bossY + stepSpawnOY(fire));
            }
            int iv = effectiveSegmentVolleyIntervalTicks(step);
            int scaledIv = Math.max(1, applyPatternTempoToCooldownTicks(iv, step, phase));
            bossSegmentVolleyCooldown = Math.max(0, scaledIv - 1);
        }
        if (bossSegmentTicksRemaining <= 0) {
            attackIndex += syncCount;
            patternCooldown = computeAttackCooldown(step, step.getPatternUpper());
        }
    }

    private void fireEveryTickWhilePhaseAttacks(PhaseDefinition phase) {
        if (phase.attacks == null || phase.attacks.isEmpty())
            return;

        for (PatternStep s : phase.attacks) {
            if (s != null && s.everyTickWhilePhase) {
                // Check if this attack has a lifetime limit and if it has expired
                if (secondaryLifetimes.containsKey(s)) {
                    int life = secondaryLifetimes.get(s);
                    if (life <= 0)
                        continue;
                    secondaryLifetimes.put(s, life - 1);
                }

                int cd = secondaryCooldowns.getOrDefault(s, 0);
                if (cd > 0) {
                    secondaryCooldowns.put(s, cd - 1);
                } else {
                    executeAttackAt(s, bossX + stepSpawnOX(s), bossY + stepSpawnOY(s));
                    int nextCd = computeAttackCooldown(s, s.getPatternUpper());
                    if (nextCd > 0) {
                        secondaryCooldowns.put(s, nextCd - 1);
                    }
                }
            }
        }
    }

    /** Effective shots per burst for this step (min 1). */
    private int burstVolleyCount(PatternStep step) {
        int n = stepTI(step, "burstCount", step.burstCount);
        return n <= 1 ? 1 : n;
    }

    /**
     * Ticks between shots inside one burst (after the first), scaled by
     * {@link PhaseDefinition#patternTempo}.
     */
    private int burstSpacingTicks(PatternStep step) {
        int bi = stepTI(step, "burstInterval", step.burstInterval);
        int raw = bi > 0 ? Math.max(0, bi) : 5;
        return applyPatternTempoToCooldownTicks(raw, step, currentBossPhase());
    }

    private void tickPhaseEmitters() {
        if (activeEmitters.isEmpty())
            return;
        for (int i = 0; i < activeEmitters.size(); i++) {
            EmitterState es = activeEmitters.get(i);
            if (es == null || es.def == null || es.def.attacks == null || es.def.attacks.isEmpty())
                continue;
            if (es.cooldown > 0) {
                es.cooldown--;
                continue;
            }

            // Continue burst for this emitter.
            if (es.burstVolleysRemaining > 0 && es.burstStep != null) {
                PatternStep bStep = es.burstStep;
                executeAttackAt(bStep, es.def.x + stepSpawnOX(bStep), es.def.y + stepSpawnOY(bStep));
                String bPat = bStep.getPatternUpper();
                es.burstVolleysRemaining--;
                if (es.burstVolleysRemaining > 0) {
                    es.cooldown = burstSpacingTicks(bStep);
                } else {
                    es.burstStep = null;
                    es.attackIndex++;
                    es.cooldown = computeAttackCooldown(bStep, bPat);
                }
                continue;
            }

            PatternStep step = es.def.attacks.get(es.attackIndex % es.def.attacks.size());
            int volleys = burstVolleyCount(step);
            executeAttackAt(step, es.def.x + stepSpawnOX(step), es.def.y + stepSpawnOY(step));
            String pat = step.getPatternUpper();
            if (volleys > 1) {
                es.burstStep = step;
                es.burstVolleysRemaining = volleys - 1;
                es.cooldown = burstSpacingTicks(step);
            } else {
                es.attackIndex++;
                es.cooldown = computeAttackCooldown(step, pat);
            }
        }
    }

    private static final int MAX_ACTIVATION_SFX_ID_LEN = 192;

    private void queueAttackActivationSound(PatternStep step) {
        if (step == null)
            return;
        String s = stepTS(step, "activationSound", step.activationSound);
        if (s == null)
            return;
        String t = s.trim();
        if (t.isEmpty())
            return;
        if (t.length() > MAX_ACTIVATION_SFX_ID_LEN)
            t = t.substring(0, MAX_ACTIVATION_SFX_ID_LEN);
        pendingAttackActivationSounds.add(t);
    }

    private void executeAttackAt(PatternStep step, float originX, float originY) {
        String patRaw = stepTS(step, "pattern", step.pattern == null ? "RING" : step.pattern);
        String patUpper = patRaw == null ? "RING" : patRaw.toUpperCase();
        if ("MEISTER_CYCLE".equals(patUpper))
            return;

        bullets.setPendingSpawnGravity(step.bulletGravity);
        queueAttackActivationSound(step);

        BulletType type = bulletTypeByName(stepTS(step, "bulletType", step.bulletType));
        PlayerState2D aimTarget = getBossAimTarget();
        float dens = bossDensityMult(step);
        float densScale = stepTF(step, "densityScale", step.densityScale);
        if (densScale > 0.01f)
            dens *= densScale;
        AttackScalingProfile profile = resolveScalingProfile(step, patUpper);
        float pressure = bulletPressure();
        float densArms = weightedDifficultyMult(dens, resolveArmsWeight(step, profile));
        float spdRatio = mc.sayda.bullethell.boss.TierJson.hasTierArray(step.byDifficulty, "speed") ? 1f
                : bossSpeedMult(step) / BullethellConfig.effectiveSpeedMult(DifficultyConfig.LUNATIC);
        float effSpdRatio = weightedDifficultyMult(spdRatio, resolveSpeedWeight(step, profile));
        float patTempo = stepPatternTempo(step, currentBossPhase());
        int sampledArms = sampleArms(step);
        float sampledSpread = sampleSpread(step);
        float sampledSpeed = sampleSpeed(step);
        float angleJitter = stepTF(step, "angleJitterRad", step.angleJitterRad);
        int scaledArms = Math.max(1, Math.round(sampledArms * densArms));
        scaledArms = applyPressureArms(scaledArms, pressure, step, profile);
        scaledArms = applyMaxScaledArmsCap(step, scaledArms);
        float effSpeed = sampledSpeed * effSpdRatio;
        float vis = bulletVis(step);
        float hit = bulletHit(step);
        float bx = stepTF(step, "x", step.x == null ? -9999f : step.x);
        if (bx < -9000f)
            bx = originX;
        float by = stepTF(step, "y", step.y == null ? -9999f : step.y);
        if (by < -9000f)
            by = originY;
        float angV = stepTF(step, "bulletAngularVelocity", step.bulletAngularVelocity);
        int lifeRing = resolveBulletLifetime(step, BullethellConfig.PATTERN_DEFAULT_LIFE_RING.get());
        int lifeAimed = resolveBulletLifetime(step, BullethellConfig.PATTERN_DEFAULT_LIFE_AIMED.get());
        int bounceN = stepTI(step, "bounceCount", step.bounceCount);
        float bounceDampVal = 0f;
        int bounceMaskVal = 0;
        if (bounceN > 0) {
            bounceDampVal = Math.max(0.05f, Math.min(1f, stepTF(step, "bounceDamping", step.bounceDamping)));
            bounceMaskVal = parseBounceExcludeMask(step);
            for (int _bi = 0; _bi < BulletPool.ENEMY_CAPACITY; _bi++) activeScratch[_bi] = bullets.isActive(_bi);
        }
        switch (patUpper) {
            case "SPIRAL" -> {
                float rsaS = stepTF(step, "startRad", step.startRad);
                float saS = rsaS >= 0f ? rsaS : spiralAngle;
                PatternEngine.fireSpiral(bullets, bx, by, saS,
                        scaledArms, effSpeed, difficulty, type, vis, hit,
                        lifeRing, angV, angleJitter);
                spiralAngle += (float) (Math.PI * 2.0 / scaledArms) * 0.15f * patTempo;
            }
            case "SPRINKLER" -> {
                float _rsaInit = stepTF(step, "startRad", step.startRad);
                float sa = sprinklerAngles.getOrDefault(step, _rsaInit >= 0f ? _rsaInit : 0f);
                float sprAdv = stepTF(step, "advanceRad", step.advanceRad);
                int comb = stepTI(step, "combCount", step.combCount);
                if (comb > 1) {
                    // Comb mode: scaledArms nozzles evenly spaced 360°, each fires comb bullets in a fan.
                    int perNozzle = comb;
                    float speedSlope = stepTF(step, "sprinklerSpeedSlope", step.sprinklerSpeedSlope);
                    float es = PatternEngine.enemySpeedScale(difficulty);
                    float nozzleStep = scaledArms > 1 ? (float) (Math.PI * 2.0 / scaledArms) : 0f;
                    java.util.concurrent.ThreadLocalRandom combRng = java.util.concurrent.ThreadLocalRandom.current();
                    for (int n = 0; n < scaledArms; n++) {
                        float nozzleAngle = sa + nozzleStep * n;
                        float tx = bx + (float) Math.cos(nozzleAngle) * 100f;
                        float ty = by + (float) Math.sin(nozzleAngle) * 100f;
                        float baseAngle = (float) Math.atan2(ty - by, tx - bx);
                        float halfSpread = sampledSpread * (perNozzle - 1) / 2f;
                        float center = (perNozzle - 1) * 0.5f;
                        for (int i = 0; i < perNozzle; i++) {
                            float angle = baseAngle - halfSpread + sampledSpread * i;
                            float speedMul;
                            if (Math.abs(speedSlope) < 1e-6f) {
                                if (angleJitter > 1e-4f) angle += (combRng.nextFloat() - 0.5f) * angleJitter;
                                speedMul = 1f;
                            } else {
                                speedMul = 1f + (i - center) * speedSlope;
                            }
                            float shotSpeed = Math.max(0.05f, effSpeed * speedMul) * es;
                            float vx = (float) Math.cos(angle) * shotSpeed;
                            float vy = (float) Math.sin(angle) * shotSpeed;
                            bullets.spawn(bx, by, vx, vy, type.getId(), lifeRing, vis, hit, angV);
                        }
                    }
                    sprinklerAngles.put(step, sa + sprAdv * patTempo);
                } else if (step.sprinklerSequentialRing) {
                    // One arm per volley: bullets stay frozen until the full ring has been spawned,
                    // then release together.
                    int idx = sprinklerSeqArm.getOrDefault(step, 0);
                    float stepAng = (float) (Math.PI * 2.0 / scaledArms);
                    float angle = sa + stepAng * idx;
                    float radMax = Math.max(0f, stepTF(step, "sprinklerSpawnRadiusMax", step.sprinklerSpawnRadiusMax));
                    float lead = scaledArms > 1 ? radMax * (idx / (float) (scaledArms - 1)) : 0f;
                    float sx = bx + (float) Math.cos(angle) * lead;
                    float sy = by + (float) Math.sin(angle) * lead;
                    float escale = BullethellConfig.enemyBulletSpeedFactor(difficulty);
                    float pvx = (float) Math.cos(angle) * effSpeed * escale;
                    float pvy = (float) Math.sin(angle) * effSpeed * escale;
                    int st = Math.max(0, stepTI(step, "sprinklerSpawnStaggerTicks", step.sprinklerSpawnStaggerTicks));
                    int freezeTicks = (scaledArms - 1 - idx) * st;
                    bullets.spawn(sx, sy, pvx, pvy, type.getId(), lifeRing, vis, hit, angV, freezeTicks);
                    idx++;
                    float saNext = sa;
                    if (idx >= scaledArms) {
                        idx = 0;
                        saNext = sa + sprAdv * patTempo;
                    }
                    sprinklerSeqArm.put(step, idx);
                    sprinklerAngles.put(step, saNext);
                } else {
                    // Ring mode: scaledArms bullets evenly distributed around 360°, one per nozzle.
                    PatternEngine.fireSpiral(bullets, bx, by, sa,
                            scaledArms, effSpeed, difficulty, type, vis, hit,
                            lifeRing, angV, angleJitter);
                    sprinklerAngles.put(step, sa + sprAdv * patTempo);
                }
            }
            case "DIVINE_WIND" -> {
                float sa = sprinklerAngles.getOrDefault(step, 0f);
                float sprAdv = stepTF(step, "advanceRad", step.advanceRad);
                int layerIdx = divineWindLayer.getOrDefault(step, 0);
                int layersRaw = stepTI(step, "divineWindLayers", step.divineWindLayers);
                int layers = Math.max(1, layersRaw);
                float layerSpacing = Math.max(0f, stepTF(step, "divineWindLayerSpacing", step.divineWindLayerSpacing));
                int freezeStagger = Math.max(0,
                        stepTI(step, "divineWindFreezeStaggerTicks", step.divineWindFreezeStaggerTicks));
                int curveTicks = Math.max(0, stepTI(step, "divineWindCurveTicks", step.divineWindCurveTicks));
                float tFactor = Math.max(0f,
                        stepTF(step, "divineWindTangentialFactor", step.divineWindTangentialFactor));
                float inwardFactor = Math.max(0f, stepTF(step, "divineWindInwardFactor", step.divineWindInwardFactor));
                float curveAv = stepTF(step, "divineWindCurveAngularVelocity", step.divineWindCurveAngularVelocity);
                float advSign = sprAdv >= 0f ? 1f : -1f;
                float curveSign = curveAv >= 0f ? 1f : -1f;
                if (Math.abs(curveAv) < 1e-5f)
                    curveSign = -advSign;
                float curve = Math.abs(curveAv) > 1e-5f ? curveAv : curveSign * 0.03f;
                float es = BullethellConfig.enemyBulletSpeedFactor(difficulty);
                float stepAng = (float) (Math.PI * 2.0 / scaledArms);
                float ringR = layerIdx * layerSpacing;
                int freezeTicks = (layers - 1 - layerIdx) * freezeStagger;
                for (int i = 0; i < scaledArms; i++) {
                    float ang = sa + stepAng * i;
                    float ux = (float) Math.cos(ang);
                    float uy = (float) Math.sin(ang);
                    float tx = -uy * advSign;
                    float ty = ux * advSign;
                    float vx = (tx * (effSpeed * tFactor) - ux * (effSpeed * inwardFactor)) * es;
                    float vy = (ty * (effSpeed * tFactor) - uy * (effSpeed * inwardFactor)) * es;
                    float sx = bx + ux * ringR;
                    float sy = by + uy * ringR;
                    int slot = bullets.spawn(sx, sy, vx, vy, type.getId(), lifeRing, vis, hit, curve, freezeTicks);
                    if (slot >= 0 && slot < divineWindCurveRemaining.length)
                        divineWindCurveRemaining[slot] = freezeTicks + curveTicks;
                }
                layerIdx++;
                float saNext = sa + sprAdv * patTempo;
                if (layerIdx >= layers) {
                    layerIdx = 0;
                    // For segment-based DIVINE_WIND legs, stop exactly on full stack completion
                    // so color handoff does not overrun by extra layers.
                    if (effectiveSegmentDurationTicks(step) > 0)
                        bossSegmentTicksRemaining = 0;
                }
                divineWindLayer.put(step, layerIdx);
                sprinklerAngles.put(step, saNext);
            }
            case "AIMED" -> {
                float rsaA = stepTF(step, "startRad", step.startRad);
                if (rsaA >= 0f) {
                    PatternEngine.fireAimedAtAngle(bullets, bx, by, rsaA,
                            scaledArms, sampledSpread, effSpeed, difficulty, type, vis, hit, lifeAimed, angV,
                            angleJitter);
                } else {
                    PatternEngine.fireAimed(bullets, bx, by,
                            aimTarget.x, aimTarget.y, scaledArms, sampledSpread, effSpeed, difficulty, type, vis, hit,
                            lifeAimed, angV, angleJitter);
                }
            }
            case "SWEEP" -> {
                int ticksPerHalf = stepTI(step, "sweepTicksPerHalf",
                        step.sweepTicksPerHalf <= 0 ? 30 : step.sweepTicksPerHalf);
                boolean alternate = step.sweepAlternate;
                boolean targeted = step.sweepTargeted;

                float startAngle = step.sweepStartLeft ? (float) Math.PI : 0f;
                float curAngle = sweepAngles.getOrDefault(step, startAngle);
                // -1 = angle decreasing (π→0 when sweepStartLeft), +1 = angle increasing (0→π)
                int curDir = sweepDirs.getOrDefault(step, step.sweepStartLeft ? -1 : 1);

                float baseAdvance = (float) Math.PI / ticksPerHalf;
                float advance = baseAdvance;

                if (targeted) {
                    float dx = aimTarget.x - bx;
                    float dy = aimTarget.y - by;
                    float playerAngle = (float) Math.atan2(dy, dx);
                    // Only slow when player is in the downward half (sweep's domain [0, π])
                    if (playerAngle >= 0f) {
                        float diff = Math.abs(playerAngle - curAngle);
                        float slowZone = stepTF(step, "sweepSlowZoneRad", step.sweepSlowZoneRad);
                        if (slowZone < 1e-4f)
                            slowZone = (float) (Math.PI / 5);
                        if (diff < slowZone) {
                            float t = 1f - (diff / slowZone); // 1 = directly aimed
                            float minMul = stepTF(step, "sweepSlowAdvanceMul", step.sweepSlowAdvanceMul);
                            if (minMul < 0f)
                                minMul = 0f;
                            advance = baseAdvance * (minMul + (1f - minMul) * (1f - t));
                        }
                    }
                }

                for (int i = 0; i < scaledArms; i++) {
                    float jitter = angleJitter > 0f ? (random.nextFloat() * 2f - 1f) * angleJitter : 0f;
                    float fa = curAngle + jitter;
                    float fvx = (float) Math.cos(fa) * effSpeed;
                    float fvy = (float) Math.sin(fa) * effSpeed;
                    bullets.spawn(bx, by, fvx, fvy, type.getId(), lifeAimed, vis, hit, angV);
                }

                float newAngle = curAngle + curDir * advance;
                int newDir = curDir;
                if (newAngle <= 0f) {
                    newAngle = 0f;
                    newDir = alternate ? 1 : -1;
                    if (!alternate)
                        newAngle = startAngle;
                } else if (newAngle >= (float) Math.PI) {
                    newAngle = (float) Math.PI;
                    newDir = alternate ? -1 : 1;
                    if (!alternate)
                        newAngle = startAngle;
                }
                sweepAngles.put(step, newAngle);
                sweepDirs.put(step, newDir);
            }
            case "SHOTGUN" -> {
                int windup = stepTI(step, "shotgunWindupTicks", step.shotgunWindupTicks);
                int cycle  = stepTI(step, "shotgunCycleTicks",  step.shotgunCycleTicks);
                if (cycle <= 0) cycle = windup + 40;
                int t = shotgunTick.getOrDefault(step, 0);
                if (t >= windup) {
                    float baseAngle = (float)(Math.PI / 2.0); // default: straight down
                    float dx = aimTarget.x - bx;
                    float dy = aimTarget.y - by;
                    float playerAngle = (float) Math.atan2(dy, dx);
                    if (playerAngle >= 0f && playerAngle <= (float) Math.PI)
                        baseAngle = playerAngle;
                    float halfCone = sampledSpread;
                    if (halfCone <= 0f) halfCone = (float)(Math.PI / 3.0);
                    float es = PatternEngine.enemySpeedScale(difficulty);
                    java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();
                    for (int i = 0; i < scaledArms; i++) {
                        float angle    = baseAngle + (rng.nextFloat() * 2f - 1f) * halfCone;
                        float shotSpd  = effSpeed * (0.75f + rng.nextFloat() * 0.5f) * es;
                        float vx       = (float) Math.cos(angle) * shotSpd;
                        float vy       = (float) Math.sin(angle) * shotSpd;
                        bullets.spawn(bx, by, vx, vy, type.getId(), lifeAimed, vis, hit, angV);
                    }
                }
                t++;
                if (t >= cycle) t = 0;
                shotgunTick.put(step, t);
            }
            case "AIMED_RING" -> {
                int scaledAimArms = scaledArms;
                int ringArmsBase = stepTI(step, "ringArms", step.ringArms);
                int scaledRingArms = Math.max(1, Math.round(ringArmsBase * densArms));
                scaledRingArms = applyPressureArms(scaledRingArms, pressure, step, profile);
                float ringSpBase = stepTF(step, "ringSpeed", step.ringSpeed);
                float ringSp = ringSpBase > 0.01f ? ringSpBase * spdRatio : effSpeed * 0.52f;
                String ringBt = stepTS(step, "ringBulletType", step.ringBulletType != null ? step.ringBulletType : "");
                BulletType ringType = (ringBt != null && !ringBt.isEmpty())
                        ? bulletTypeByName(ringBt)
                        : BulletType.fromName("DOT");
                float ringStartRad = stepTF(step, "startRad", step.startRad);
                float ringStart = ringStartRad >= 0f
                        ? ringStartRad
                        : random.nextFloat() * (float) (Math.PI * 2.0);
                PatternEngine.fireAimedWithRing(bullets, bx, by,
                        aimTarget.x, aimTarget.y,
                        scaledAimArms, sampledSpread, effSpeed,
                        scaledRingArms, ringSp, difficulty, type, ringType, ringStart,
                        vis, hit, lifeAimed, lifeAimed, angV);
            }
            case "RING" -> {
                float rsa = stepTF(step, "startRad", step.startRad);
                float ringStart = rsa >= 0f ? rsa : 0f;
                PatternEngine.fireRing(bullets, bx, by,
                        scaledArms, effSpeed, difficulty, type, vis, hit,
                        lifeRing, angV, ringStart);
            }
            case "RING_OFFSET" -> {
                float rsa2 = stepTF(step, "startRad", step.startRad);
                float start = rsa2 >= 0f
                        ? rsa2
                        : random.nextFloat() * (float) (Math.PI * 2.0);
                PatternEngine.fireRingOffset(bullets, bx, by,
                        scaledArms, effSpeed, difficulty, type, start, vis, hit,
                        lifeRing, angV);
            }
            case "SPREAD" -> {
                float sprd = sampledSpread > 0.001f ? sampledSpread : 0.28f;
                PatternEngine.fireAimed(bullets, bx, by,
                        aimTarget.x, aimTarget.y, scaledArms, sprd, effSpeed, difficulty, type, vis, hit,
                        lifeAimed, angV, angleJitter);
            }
            case "RAIN" -> {
                float rainW = stepTF(step, "rainWidth", step.rainWidth);
                float span = (rainW > 0f) ? Math.min(rainW, BulletPool.ARENA_W) : BulletPool.ARENA_W;
                float xStart = (BulletPool.ARENA_W - span) * 0.5f;
                float baseY = stepTF(step, "rainTop", step.rainTop);
                float rainOx = stepTF(step, "spawnOffsetX", step.spawnOffsetX);
                float rainOy = stepTF(step, "spawnOffsetY", step.spawnOffsetY);
                int rainLife = resolveBulletLifetime(step, BullethellConfig.PATTERN_DEFAULT_LIFE_RAIN.get());
                float rainSpdLo = stepTF(step, "rainSpeedVarMin", step.rainSpeedVarMin);
                float rainSpdHi = stepTF(step, "rainSpeedVarMax", step.rainSpeedVarMax);
                if (rainSpdHi <= rainSpdLo)
                    rainSpdHi = rainSpdLo + 0.001f;
                for (int i = 0; i < scaledArms; i++) {
                    float spawnX = xStart + random.nextFloat() * span + rainOx;
                    float jitter = (random.nextFloat() * 2f - 1f) * sampledSpread;
                    float ang = (float) (Math.PI * 0.5f) + jitter;
                    float sp = effSpeed * (rainSpdLo + random.nextFloat() * (rainSpdHi - rainSpdLo))
                            * BullethellConfig.GLOBAL_ENEMY_BULLET_SPEED_MULT.get();
                    float vx = (float) Math.cos(ang) * sp;
                    float vy = (float) Math.sin(ang) * sp;
                    bullets.spawn(spawnX, baseY + rainOy, vx, vy, type.getId(), rainLife, vis, hit, angV);
                }
            }
            case "DENSE_RING" -> {
                float drRsa = stepTF(step, "startRad", step.startRad);
                float drStart = drRsa >= 0f ? drRsa : 0f;
                PatternEngine.fireDenseRing(bullets, bx, by,
                        scaledArms, effSpeed, difficulty, type, vis, hit,
                        lifeRing, angV, drStart);
            }
            case "LASER_BEAM" -> {
                float rsaLB = stepTF(step, "startRad", step.startRad);
                // Pass -1 when spread wasn't explicitly set (default 0.20), so PatternEngine
                // uses its built-in laser-beam default (~0.04 rad).
                boolean lbExplicit = mc.sayda.bullethell.boss.TierJson.hasTierArray(step.byDifficulty, "spread")
                        || Math.abs(step.spread - 0.20f) > 1e-4f;
                float lbSpread = lbExplicit ? sampledSpread : -1f;
                if (rsaLB >= 0f) {
                    PatternEngine.fireLaserBeamAtAngle(bullets, bx, by, rsaLB,
                            scaledArms, effSpeed, difficulty, type, vis, hit,
                            lifeRing, angV, lbSpread);
                } else {
                    PatternEngine.fireLaserBeam(bullets, bx, by,
                            aimTarget.x, aimTarget.y, scaledArms, effSpeed, difficulty, type, vis, hit,
                            lifeRing, angV, lbSpread);
                }
            }
            case "LASER" -> {
                float rsaL = stepTF(step, "startRad", step.startRad);
                float angle = rsaL >= 0f ? rsaL : (float) Math.atan2(aimTarget.y - by, aimTarget.x - bx);
                float densWarn = weightedDifficultyMult(dens, resolveCooldownWeight(step, profile));
                int wTicks = stepTI(step, "warnTicks", step.warnTicks);
                if (!mc.sayda.bullethell.boss.TierJson.hasTierArray(step.byDifficulty, "warnTicks"))
                    wTicks = Math.round(wTicks * difficulty.warnTicksScale);
                int aTicks = stepTI(step, "activeTicks", step.activeTicks);
                if (aTicks < 0)
                    aTicks = 60;
                int activeTicks = Math.max(1, (int) (aTicks / patTempo));
                int scaledWarn = Math.max(1, (int) (wTicks / densWarn / patTempo));
                float lHalf = stepTF(step, "laserHalfWidth", step.laserHalfWidth);
                lasers.spawn(bx, by, angle, lHalf,
                        scaledWarn, activeTicks, type.getId(), false);
            }
            case "LASER_ROTATING" -> {
                float densWarn = weightedDifficultyMult(dens, resolveCooldownWeight(step, profile));
                int wTicksR = stepTI(step, "warnTicks", step.warnTicks);
                if (!mc.sayda.bullethell.boss.TierJson.hasTierArray(step.byDifficulty, "warnTicks"))
                    wTicksR = Math.round(wTicksR * difficulty.warnTicksScale);
                int aTicksR = stepTI(step, "activeTicks", step.activeTicks);
                if (aTicksR < 0)
                    aTicksR = 60;
                int activeTicks = Math.max(1, (int) (aTicksR / patTempo));
                int scaledWarn = Math.max(1, (int) (wTicksR / densWarn / patTempo));
                float lHalfR = stepTF(step, "laserHalfWidth", step.laserHalfWidth);
                float rsaLR = stepTF(step, "startRad", step.startRad);
                float lAngle = laserAngles.getOrDefault(step, rsaLR >= 0f ? rsaLR : spiralAngle);
                float angleStep = (float) (Math.PI * 2.0 / scaledArms);
                for (int i = 0; i < scaledArms; i++) {
                    lasers.spawn(bx, by, lAngle + angleStep * i,
                            lHalfR, scaledWarn, activeTicks, type.getId(), true);
                }
                float advRaw = stepTF(step, "laserRotateAdvanceRad", step.laserRotateAdvanceRad);
                float advance = Math.abs(advRaw) > 1e-4f ? advRaw : 0.45f;
                laserAngles.put(step, lAngle + advance * patTempo);
            }
            case "PENTAGRAM" -> {
                int pp = stepTI(step, "pentagramPoints", step.pentagramPoints);
                int pts = pp >= 3 ? pp : 5;
                String innerBt = stepTS(step, "ringBulletType", step.ringBulletType != null ? step.ringBulletType : "");
                BulletType inner = (innerBt != null && !innerBt.isEmpty())
                        ? bulletTypeByName(innerBt)
                        : type;
                float pRsa = stepTF(step, "startRad", step.startRad);
                float start = pRsa >= 0f
                        ? pRsa
                        : spiralAngle;
                PatternEngine.firePentagramDouble(bullets, bx, by, pts, effSpeed, difficulty,
                        type, inner, vis, hit, lifeRing, angV, start);
                spiralAngle += (float) (Math.PI / pts) * patTempo;
            }
            case "ORB_C_ROW" -> {
                float[] halo = new float[2];
                sampleBossHaloOrigin(step, halo);
                boolean randomDir = stepTB(step, "orbCRowRandomDirection", step.orbCRowRandomDirection);
                float aimRad = randomDir
                        ? random.nextFloat() * (float) (Math.PI * 2.0)
                        : (float) Math.atan2(aimTarget.y - halo[1], aimTarget.x - halo[0]);
                float orbSpd = effSpeed * 1.322f;
                int armsPick = stepTI(step, "arms", step.arms);
                int armsMinPick = stepTI(step, "armsMin", step.armsMin);
                int armsMaxPick = stepTI(step, "armsMax", step.armsMax);
                boolean useArmsFromJson = armsPick > 0 || armsMinPick > 0 || armsMaxPick > 0
                        || TierJson.hasTierArray(step.byDifficulty, "arms")
                        || TierJson.hasTierArray(step.byDifficulty, "armsMin")
                        || TierJson.hasTierArray(step.byDifficulty, "armsMax");
                int rowN = useArmsFromJson ? Math.max(1, scaledArms) : prBossRowBulletCount(difficulty);
                float curvPick = stepTF(step, "orbCRowCurvatureScale", step.orbCRowCurvatureScale);
                float curv = curvPick > 0.01f ? curvPick : prBossCurvScale(difficulty);
                float tightPick = stepTF(step, "orbCRowSpacing", step.orbCRowSpacing);
                float tight = tightPick > 0.01f ? tightPick : PR_BOSS_ORB_ROW_TIGHT;
                float rowSpeedSlope = stepTF(step, "orbCRowSpeedSlope", step.orbCRowSpeedSlope);
                float driftAngVel = stepTF(step, "orbCRowDrift", step.orbCRowDrift);
                int lifeOrb = resolveBulletLifetime(step, BullethellConfig.PATTERN_DEFAULT_LIFE_RING.get());
                PatternEngine.fireOrbCRowInDirection(bullets, halo[0], halo[1], aimRad,
                        orbSpd, difficulty, type, vis, hit, lifeOrb,
                        random, curv, rowN, tight, rowSpeedSlope, driftAngVel);
            }
            case "STACK_FAN_VOLLEY", "DAGGER_HALO_VOLLEY" -> {
                float sx, sy;
                float haloMinRaw = stepTF(step, "randomHaloMinR", step.randomHaloMinR);
                float haloMaxRaw = stepTF(step, "randomHaloMaxR", step.randomHaloMaxR);
                if (haloMinRaw > 0.01f || haloMaxRaw > 0.01f) {
                    float[] halo = new float[2];
                    sampleBossHaloOrigin(step, halo);
                    sx = halo[0];
                    sy = halo[1];
                } else {
                    sx = originX;
                    sy = originY;
                }
                BulletType fanType = type;
                String stackRingBt = stepTS(step, "ringBulletType",
                        step.ringBulletType != null ? step.ringBulletType : "");
                BulletType aimedType = (stackRingBt != null && !stackRingBt.isEmpty())
                        ? bulletTypeByName(stackRingBt)
                        : BulletType.fromName("RED_DAGGER");
                float totalFan = sampledSpread > 1e-4f ? sampledSpread : 0.38f;
                float halfFan = totalFan * 0.5f;
                float midAng = (float) (Math.PI * 0.5);
                float es = PatternEngine.enemySpeedScale(difficulty);
                int lifeD = resolveBulletLifetime(step, BullethellConfig.PATTERN_DEFAULT_LIFE_AIMED.get());
                int depthBase = stepTI(step, "rayStackDepth", step.rayStackDepth);
                int depth = depthBase > 0 ? depthBase : 10;
                float spacingBase = stepTF(step, "rayStackSpacing", step.rayStackSpacing);
                float spacing = spacingBase > 1e-4f ? spacingBase : 3.0f;
                float sp = effSpeed * es;
                for (int i = -1; i <= 1; i++) {
                    float ang = midAng + i * halfFan;
                    spawnStackedRay(sx, sy, ang, sp, fanType, lifeD, vis, hit, depth, spacing);
                }
                float aimAng = (float) Math.atan2(aimTarget.y - sy, aimTarget.x - sx);
                spawnStackedRay(sx, sy, aimAng, sp, aimedType, lifeD, vis, hit, depth, spacing);
            }
            case "PENTAGRAM_RITUAL" -> {
                /* driven by tickPentagramRitual(); not fired here */ }
            case "SEA_SPLIT" -> {
                /* driven by tickSeaSplit(); not fired here */ }
            case "WORM_CIRCLE" -> {
                if (step.wormCircles != null && step.wormCircles.length > 0) {
                    int st = stepTI(step, "orbitTicks", step.orbitTicks);
                    float sp = stepTF(step, "speed", step.speed);
                    wormCircleRuntime.init(step.wormCircles, st, sp, bullets, bossX, bossY);
                }
            }
            case "RING_SPAWNER" -> {
                String rsChildTypeStr = stepTS(step, "ringBulletType",
                        step.ringBulletType != null && !step.ringBulletType.isEmpty()
                                ? step.ringBulletType : "RED_ORB");
                int   rsChildTypeId = bulletTypeByName(rsChildTypeStr).getId();
                float rsChildVis    = stepTF(step, "childBulletScale", step.childBulletScale);
                if (rsChildVis < 0.01f) rsChildVis = 1f;
                float rsChildHit    = stepTF(step, "childHitboxScale", step.childHitboxScale);
                if (rsChildHit <= 0f) rsChildHit = 0.55f;
                float rsConeAngle   = stepTF(step, "coneAngleDeg",   step.coneAngleDeg);
                float rsConeHalf    = stepTF(step, "coneHalfAngleDeg", step.coneHalfAngleDeg);
                int   rsInterval    = stepTI(step, "childSpawnIntervalTicks", step.childSpawnIntervalTicks);
                float rsChildSpd    = stepTF(step, "childSpeed",       step.childSpeed) * spdRatio;
                float rsChildAccel  = stepTF(step, "childAcceleration", step.childAcceleration);
                float rsChildFanRad = (float) Math.toRadians(
                        stepTF(step, "childFanDeg", step.childFanDeg));
                ringSpawnerRuntime.init(scaledArms, rsConeAngle, rsConeHalf,
                        type.getId(), vis, hit, effSpeed,
                        rsChildTypeId, rsChildVis, rsChildHit,
                        rsInterval, rsChildSpd, rsChildAccel, rsChildFanRad,
                        bullets, bx, by);
            }
            default -> {
                float defRsa = stepTF(step, "startRad", step.startRad);
                float ringStart = defRsa >= 0f ? defRsa : 0f;
                PatternEngine.fireRing(bullets, bx, by,
                        scaledArms, effSpeed, difficulty, type, vis, hit,
                        lifeRing, angV, ringStart);
            }
        }
        if (bounceN > 0) {
            for (int _bi = 0; _bi < BulletPool.ENEMY_CAPACITY; _bi++) {
                if (!activeScratch[_bi] && bullets.isActive(_bi)) {
                    bounceRemaining[_bi] = bounceN;
                    bounceDamping[_bi] = bounceDampVal;
                    bounceExcludeMask[_bi] = bounceMaskVal;
                }
            }
        }
    }

    private static boolean seaSplitForCurrentPhase(PhaseDefinition phase) {
        return findSeaSplitStep(phase) != null;
    }

    private static PatternStep findSeaSplitStep(PhaseDefinition phase) {
        if (phase == null || phase.attacks == null)
            return null;
        for (PatternStep s : phase.attacks)
            if (s != null && "SEA_SPLIT".equalsIgnoreCase(s.pattern))
                return s;
        return null;
    }

    private boolean pentagramRitualForCurrentPhase(PhaseDefinition phase) {
        if (pentagramRitualFollowupHandedOff)
            return false;
        return findPentagramRitualStep(phase) != null;
    }

    private static PatternStep findPentagramRitualStep(PhaseDefinition phase) {
        if (phase == null || phase.attacks == null || phase.attacks.isEmpty())
            return null;
        for (PatternStep s : phase.attacks) {
            if (s != null && "PENTAGRAM_RITUAL".equals(s.getPatternUpper()))
                return s;
        }
        return null;
    }

    /**
     * Random point on an annulus around {@link #bossX}/{@link #bossY} (pentagram
     * phase-4 / {@code ORB_C_ROW} / halo secondaries / {@code STACK_FAN_VOLLEY}).
     */
    private void sampleBossHaloOrigin(PatternStep step, float[] outXY) {
        if (outXY == null || outXY.length < 2)
            return;
        float minRRaw = stepTF(step, "randomHaloMinR", step.randomHaloMinR);
        float minR = minRRaw > 0.01f ? minRRaw : PR_BOSS_ORB_ROW_MIN_R;
        float maxRRaw = stepTF(step, "randomHaloMaxR", step.randomHaloMaxR);
        float maxR = maxRRaw > minR + 0.01f ? maxRRaw : PR_BOSS_ORB_ROW_MAX_R;
        float jitRaw = stepTF(step, "randomHaloJitter", step.randomHaloJitter);
        float jitter = jitRaw > 0.01f ? jitRaw : PR_BOSS_ORB_SPAWN_JITTER;
        float haloAng = random.nextFloat() * (float) (Math.PI * 2.0);
        float haloR = minR + random.nextFloat() * (maxR - minR);
        float sx = bossX + (float) Math.cos(haloAng) * haloR;
        float sy = bossY + (float) Math.sin(haloAng) * haloR;
        sx += (random.nextFloat() - 0.5f) * jitter;
        sy += (random.nextFloat() - 0.5f) * jitter;
        outXY[0] = sx;
        outXY[1] = sy;
    }

    /**
     * {@code STACK_FAN_VOLLEY}: bullets spaced along the ray <strong>in the
     * direction of travel</strong>
     * from the spawn (k=0 at the halo origin, k+1 farther along velocity). Avoids
     * backward tails from
     * multiple rays crossing into an X when {@code rayStackDepth} is large.
     */
    private void spawnStackedRay(float sx, float sy, float ang, float speedMag,
            BulletType bType, int life, float vis, float hit, int depth, float spacing) {
        float ux = (float) Math.cos(ang);
        float uy = (float) Math.sin(ang);
        for (int k = 0; k < depth; k++) {
            float px = sx + ux * k * spacing;
            float py = sy + uy * k * spacing;
            bullets.spawn(px, py, ux * speedMag, uy * speedMag, bType.getId(), life, vis, hit, 0f);
        }
    }

    private void tickPentagramRitual() {
        PatternStep st = pentagramRitualCfg;
        if (st == null)
            return;
        int t = pentagramRitualTick;
        pentagramRitualSpin += (float) Math.toRadians(stepTF(st, "pentagramRitualSpinSpeedDeg", st.pentagramRitualSpinSpeedDeg));

        String ringBt = stepTS(st, "ringBulletType", st.ringBulletType != null ? st.ringBulletType : "");
        boolean dual = st.pentagramDualOverlapped && ringBt != null && !ringBt.isEmpty();
        float innerScale = stepTF(st, "pentagramInnerRingScale", st.pentagramInnerRingScale);
        float outerScale = stepTF(st, "pentagramOuterRingScale", st.pentagramOuterRingScale);
        if (!Float.isFinite(innerScale) || innerScale <= 0f)
            innerScale = dual ? 0.58f : 1f;
        if (!Float.isFinite(outerScale) || outerScale <= 0f)
            outerScale = 1f;
        int repeatTicks = stepTI(st, "pentagramRepeatStackTicks", st.pentagramRepeatStackTicks);
        boolean skipComb = st.pentagramSkipEdgeComb;

        BulletType typeInner = bulletTypeByName(stepTS(st, "bulletType", st.bulletType));
        BulletType typeOuter = dual ? bulletTypeByName(ringBt) : typeInner;
        String patUpper = "PENTAGRAM_RITUAL";
        AttackScalingProfile profile = resolveScalingProfile(st, patUpper);
        float dens = bossDensityMult(st);
        float dScalePr = stepTF(st, "densityScale", st.densityScale);
        if (dScalePr > 0.01f)
            dens *= dScalePr;
        float pressure = bulletPressure();
        float densArms = weightedDifficultyMult(dens, resolveArmsWeight(st, profile));
        float spdRatio = mc.sayda.bullethell.boss.TierJson.hasTierArray(st.byDifficulty, "speed") ? 1f
                : bossSpeedMult(st) / BullethellConfig.effectiveSpeedMult(DifficultyConfig.LUNATIC);
        float effSpdRatio = weightedDifficultyMult(spdRatio, resolveSpeedWeight(st, profile));
        int sampledArms = sampleArms(st);
        int scaledArms = Math.max(1, Math.round(sampledArms * densArms));
        scaledArms = applyPressureArms(scaledArms, pressure, st, profile);
        scaledArms = applyMaxScaledArmsCap(st, scaledArms);
        float effSpeed = sampleSpeed(st) * effSpdRatio;
        float vis = bulletVis(st);
        float hit = bulletHit(st);
        float angV = stepTF(st, "bulletAngularVelocity", st.bulletAngularVelocity);
        int samplesPerEdge = Math.max(3, Math.min(12, scaledArms * 6));
        int ringDef = BullethellConfig.PATTERN_DEFAULT_LIFE_RING.get();
        int streamLife = Math.max(ringDef, resolveBulletLifetime(st, ringDef));
        float streamSpeed = effSpeed * 1.15f;
        float curveDeg = stepTF(st, "pentagramArcCurveDeg", st.pentagramArcCurveDeg);
        int curveDelayTicks = (!dual && curveDeg > 1e-6f)
                ? Math.max(0, stepTI(st, "pentagramArcCurveDelayTicks", st.pentagramArcCurveDelayTicks))
                : 0;
        int ringReadyTick = computePentagramRingReadyTick();
        /*
         * Avoid Integer.MAX_VALUE + k overflow (was firing combs during stack intro).
         */
        int combReleaseTick = addTickSafe(ringReadyTick, PR_RING_SETTLE_TICKS);
        int bossSummonBeginTick = addTickSafe(combReleaseTick, PR_BOSS_PHASE_START_DELAY + curveDelayTicks);

        if (t == 0) {
            pentagramFormation.setDualMode(dual);
            pentagramFormation.beginStack(samplesPerEdge);
        }

        if (!pentagramFormation.isStackComplete()) {
            pentagramFormation.spawnNextBatch(bullets, PR_STACK_BULLETS_PER_TICK, PR_STAR_RADIUS,
                    typeInner, typeOuter, vis, hit, PR_FORMATION_OUTLINE_LIFE_TICKS, angV);
        }
        if (pentagramFormation.isStackComplete()
                && pentagramFormation.getWaveStackDoneAt(pentagramFormation.getSpawnWave()) < 0)
            pentagramFormation.markCurrentSpawnWaveComplete(t);

        if (ritualStackCompleteAt < 0 && pentagramFormation.getWaveStackDoneAt(0) >= 0)
            ritualStackCompleteAt = pentagramFormation.getWaveStackDoneAt(0);

        if (repeatTicks > 0 && dual && skipComb && t > 0
                && pentagramFormation.isCurrentSpawnWaveStackComplete()
                && t - pentagramLastNewWaveTick >= repeatTicks
                && pentagramFormation.getSpawnWave() + 1 < PentagramFormationRuntime.MAX_WAVES) {
            pentagramFormation.beginNewWave();
            pentagramLastNewWaveTick = t;
            if (!pentagramFormation.isStackComplete()) {
                pentagramFormation.spawnNextBatch(bullets, PR_STACK_BULLETS_PER_TICK, PR_STAR_RADIUS,
                        typeInner, typeOuter, vis, hit, PR_FORMATION_OUTLINE_LIFE_TICKS, angV);
            }
        }

        float inMul = dual ? innerScale : 1f;
        float outMul = dual ? outerScale : 1f;
        int nWaves = Math.min(pentagramFormation.getWavesStartedCount(), PentagramFormationRuntime.MAX_WAVES);
        for (int w = 0; w < nWaves; w++) {
            int done = pentagramFormation.getWaveStackDoneAt(w);
            float baseR = prWaveRingRadiusAt(t, done);
            prRingInnerScratch[w] = baseR * inMul;
            prRingOuterScratch[w] = dual ? baseR * outMul : baseR * inMul;
        }
        if (pentagramFormation.getCount() > 0)
            pentagramFormation.syncPositions(bullets, bossX, bossY, pentagramRitualSpin,
                    prRingInnerScratch, prRingOuterScratch, nWaves);

        /*
         * Phase 3: stacked orbs 竊・ring-out 竊・settle 竊・per-star edge combs (outward
         * normals).
         */
        if (!skipComb && ritualStackCompleteAt >= 0 && t >= combReleaseTick && !prPentagramDisassembled
                && pentagramFormation.getCount() > 0) {
            float relSpeed = streamSpeed * PatternEngine.enemySpeedScale(difficulty);
            if (dual && pentagramFormation.isDualMode()) {
                /*
                 * Dual: reuse outline bullets (inner + outer); outward along stored edge
                 * normals - no comb spawns.
                 */
                float dualInnerAngVel = stepTF(st, "pentagramDualInnerReleaseAngularVelocity",
                        st.pentagramDualInnerReleaseAngularVelocity);
                float dualOuterAngVel = stepTF(st, "pentagramDualOuterReleaseAngularVelocity",
                        st.pentagramDualOuterReleaseAngularVelocity);
                int dualInnerSplitCount = stepTI(st, "pentagramDualInnerSplitCount", st.pentagramDualInnerSplitCount);
                float dualInnerSplitSpread = stepTF(st, "pentagramDualInnerSplitSpreadRad",
                        st.pentagramDualInnerSplitSpreadRad);
                float dualInnerSplitSpeedMul = stepTF(st, "pentagramDualInnerSplitSpeedMul",
                        st.pentagramDualInnerSplitSpeedMul);
                pentagramFormation.launchDetachedOutward(bullets, pentagramRitualSpin, relSpeed, streamLife,
                        dualInnerAngVel, dualOuterAngVel, dualInnerSplitCount, dualInnerSplitSpread,
                        dualInnerSplitSpeedMul, curveDeg);
            } else {
                if (curveDeg > 1e-6f) {
                    if (curveDelayTicks > 0) {
                        pentagramFormation.prelaunchArcStraight(bullets, pentagramRitualSpin, relSpeed, streamLife);
                    } else {
                        float arcAngVel = stepTF(st, "pentagramArcAngularVelocity", st.pentagramArcAngularVelocity);
                        pentagramFormation.launchArcSingleColor(bullets, pentagramRitualSpin, relSpeed, curveDeg,
                                streamLife, arcAngVel);
                    }
                } else {
                    int combSamples = Math.max(4, Math.min(8, samplesPerEdge));
                    int nStars = PentagramFormationRuntime.STAR_COUNT;
                    float step = (float) (Math.PI * 2.0 / nStars);
                    float ringAtComb = computePentagramRitualRingR(t);
                    for (int si = 0; si < nStars; si++) {
                        float aa = si * step + pentagramRitualSpin * 1.15f;
                        float cx = bossX + (float) Math.cos(aa) * ringAtComb;
                        float cy = bossY + (float) Math.sin(aa) * ringAtComb;
                        float rot = pentagramRitualSpin + si * step;
                        PatternEngine.firePentagramStarEdgeStreams(bullets, cx, cy, PR_STAR_RADIUS, rot, combSamples,
                                relSpeed, difficulty, typeInner, vis, hit, streamLife, 0f);
                    }
                    pentagramFormation.deactivateFormationKeepStackDone(bullets);
                }
            }
            prPentagramDisassembled = true;
        } else if (skipComb && ritualStackCompleteAt >= 0 && t >= combReleaseTick && !prPentagramDisassembled) {
            prPentagramDisassembled = true;
        }

        // Per-tick speed ramp during the pre-arc slow phase; redirect to arc direction at the end.
        if (!dual && curveDelayTicks > 0 && pentagramFormation.isArcPrelaunched()) {
            float relSpeedR = streamSpeed * PatternEngine.enemySpeedScale(difficulty);
            int ticksElapsed = t - combReleaseTick;
            if (ticksElapsed < curveDelayTicks) {
                pentagramFormation.tickArcPrelaunch(bullets, pentagramRitualSpin, relSpeedR, curveDeg,
                        curveDelayTicks, ticksElapsed);
            } else {
                float arcAngVelR = stepTF(st, "pentagramArcAngularVelocity", st.pentagramArcAngularVelocity);
                pentagramFormation.launchArcSingleColor(bullets, pentagramRitualSpin, relSpeedR, curveDeg,
                        streamLife, arcAngVelR);
            }
        }

        /*
         * Phase 4: follow-up volleys - MoF non-spell (timed + aimed + optional loop) or
         * legacy spray.
         */
        if (prPentagramDisassembled && t >= bossSummonBeginTick) {
            if (st.skipPentagramRitualFollowup) {
                if (st.pentagramLoopRitual) {
                    restartPentagramRitualCycle();
                    return;
                }
                pentagramRitualFollowupHandedOff = true;
                pentagramFormation.clear(bullets);
                pentagramRitualTick = -1;
                pentagramRitualCfg = null;
                ritualStackCompleteAt = -1;
                prPentagramDisassembled = false;
                pentagramRitualSpin = 0f;
                pentagramLastNewWaveTick = 0;
                attackIndex = 0;
                patternCooldown = 0;
                return;
            }
            int followElapsed = t - bossSummonBeginTick;
            int followDur = stepTI(st, "pentagramFollowupDurationTicks", st.pentagramFollowupDurationTicks);
            if (followDur > 0) {
                if (followElapsed >= followDur) {
                    if (st.pentagramLoopRitual) {
                        restartPentagramRitualCycle();
                        return;
                    }
                } else {
                    int followEvery = prBossFollowupWallIntervalTicks(difficulty);
                    if (followElapsed % followEvery == 0) {
                        PlayerState2D aim = getBossAimTarget();
                        float spd = streamSpeed * 1.16f;
                        int rowN = prBossRowBulletCount(difficulty);
                        float curv = prBossCurvScale(difficulty);
                        float rowSpeedSlope = stepTF(st, "orbCRowSpeedSlope", st.orbCRowSpeedSlope);
                        float driftAngVel = stepTF(st, "orbCRowDrift", st.orbCRowDrift);
                        float haloAng = random.nextFloat() * (float) (Math.PI * 2.0);
                        float haloR = PR_BOSS_ORB_ROW_MIN_R
                                + random.nextFloat() * (PR_BOSS_ORB_ROW_MAX_R - PR_BOSS_ORB_ROW_MIN_R);
                        float sx = bossX + (float) Math.cos(haloAng) * haloR;
                        float sy = bossY + (float) Math.sin(haloAng) * haloR;
                        sx += (random.nextFloat() - 0.5f) * PR_BOSS_ORB_SPAWN_JITTER;
                        sy += (random.nextFloat() - 0.5f) * PR_BOSS_ORB_SPAWN_JITTER;
                        boolean randomDir = stepTB(st, "orbCRowRandomDirection", st.orbCRowRandomDirection);
                        float aimRad = randomDir
                                ? random.nextFloat() * (float) (Math.PI * 2.0)
                                : (float) Math.atan2(aim.y - sy, aim.x - sx);
                        PatternEngine.fireOrbCRowInDirection(bullets, sx, sy, aimRad,
                                spd, difficulty, typeInner, vis, hit, streamLife,
                                random, curv, rowN, PR_BOSS_ORB_ROW_TIGHT, rowSpeedSlope, driftAngVel);
                    }
                }
            } else {
                int bossWallEvery = prBossWallIntervalTicks(difficulty);
                if (followElapsed % bossWallEvery == 0) {
                    float spd = streamSpeed * 1.16f;
                    int rowN = prBossRowBulletCount(difficulty);
                    float curv = prBossCurvScale(difficulty);
                    float rowSpeedSlope = stepTF(st, "orbCRowSpeedSlope", st.orbCRowSpeedSlope);
                    float driftAngVel = stepTF(st, "orbCRowDrift", st.orbCRowDrift);
                    float haloAng = random.nextFloat() * (float) (Math.PI * 2.0);
                    float haloR = PR_BOSS_ORB_ROW_MIN_R
                            + random.nextFloat() * (PR_BOSS_ORB_ROW_MAX_R - PR_BOSS_ORB_ROW_MIN_R);
                    float sx = bossX + (float) Math.cos(haloAng) * haloR;
                    float sy = bossY + (float) Math.sin(haloAng) * haloR;
                    sx += (random.nextFloat() - 0.5f) * PR_BOSS_ORB_SPAWN_JITTER;
                    sy += (random.nextFloat() - 0.5f) * PR_BOSS_ORB_SPAWN_JITTER;
                    float flight = prBossRandomOrbFlightAngle(random);
                    PatternEngine.fireOrbCRowInDirection(bullets, sx, sy, flight,
                            spd, difficulty, typeInner, vis, hit, streamLife,
                            random, curv, rowN, PR_BOSS_ORB_ROW_TIGHT, rowSpeedSlope, driftAngVel);
                }
            }
        }

        pentagramRitualTick++;
    }

    /**
     * After the follow-up barrage, clear ritual state and restart from phase 1
     * (charge drawing).
     * Does not increment {@link #pentagramRitualTick} - caller must return before
     * the usual tick++.
     */
    private void restartPentagramRitualCycle() {
        pentagramFormation.clear(bullets);
        ritualStackCompleteAt = -1;
        prPentagramDisassembled = false;
        pentagramRitualTick = 0;
        pentagramLastNewWaveTick = 0;
        /*
         * Next tick uses t==0 竊・{@link PentagramFormationRuntime#beginStack} + spawn
         * batch.
         */
    }

    /**
     * Eased ring radius in arena units for one outline wave; {@code stackDoneAt} is
     * ritual tick when that wave finished stacking.
     */
    private float prWaveRingRadiusAt(int ritualTick, int stackDoneAt) {
        if (stackDoneAt < 0)
            return 0f;
        int holdEnd = stackDoneAt + PR_STACKED_HOLD_TICKS;
        int spreadEnd = holdEnd + PR_RING_SPREAD_TICKS;
        if (ritualTick < holdEnd)
            return 0f;
        if (ritualTick < spreadEnd) {
            float u = (ritualTick - holdEnd) / (float) PR_RING_SPREAD_TICKS;
            u = Math.min(1f, u);
            return PR_RING_RADIUS * smoothstep(u);
        }
        return PR_RING_RADIUS;
    }

    private float computePentagramRitualRingR(int t) {
        return prWaveRingRadiusAt(t, ritualStackCompleteAt);
    }

    private int computePentagramRingReadyTick() {
        if (ritualStackCompleteAt < 0)
            return Integer.MAX_VALUE;
        return ritualStackCompleteAt + PR_STACKED_HOLD_TICKS + PR_RING_SPREAD_TICKS;
    }

    /**
     * Sea Opening scripted handler. Each tick advances {@code seaSplitAngle}; on
     * {@code seaSplitFireCd}
     * the curtain places **two** lasers when there is room: one covering
     * {@code [0, corridorCX - laserHalfWidth]},
     * one covering {@code [corridorCX + laserHalfWidth, ARENA_W]}. Spawn Y is fixed
     * above the playfield;
     * velocity is {@code (0, effSpeed ﾃ・enemySpeedScale)}. Each bullet窶冱
     * {@code vis} scale is chosen so
     * {@code lineVisualHalfLength ﾃ・vis} matches half the segment width (type data
     * from
     * {@link mc.sayda.bullethell.pattern.BulletTypeLoader}). Corridor center is
     * {@code ARENA_W/2 + sin(angle) ﾃ・sweepRange} with sweep capped (margin +
     * {@code sweepScale 0.56}).
     * {@code arms} / {@code maxScaledArms} are not used for the curtain row.
     * Secondaries: other
     * {@code phase.attacks} steps, skipping this pattern, on {@code seaSplitSecCd};
     * see
     * {@link PatternStep#fireFromRandomBossHalo}.
     */

    /** Ticks the active {@link WormCircleRuntime} if one is running. */
    private void tickWormCircle() {
        if (wormCircleRuntime.isActive())
            wormCircleRuntime.tick(bullets, bossX, bossY);
    }

    private void tickRingSpawner() {
        if (ringSpawnerRuntime.isActive())
            ringSpawnerRuntime.tick(bullets);
    }

    private void tickSeaSplit() {
        PatternStep st = seaSplitCfg;
        PhaseDefinition phase = currentBossPhase();
        seaSplitTick++;

        float tempo = stepPatternTempo(seaSplitCfg, phase);
        float advPick = stepTF(st, "laserRotateAdvanceRad", st.laserRotateAdvanceRad);
        float advance = advPick > 1e-4f ? advPick : 0.006f;
        seaSplitAngle += advance * tempo;

        float dens = bossDensityMult(st);
        AttackScalingProfile seaProfile = SCALE_GEOMETRY;
        float pressure = bulletPressure();
        float spdRatio = mc.sayda.bullethell.boss.TierJson.hasTierArray(st.byDifficulty, "speed") ? 1f
                : bossSpeedMult(st) / BullethellConfig.effectiveSpeedMult(DifficultyConfig.LUNATIC);
        float effSpeed = sampleSpeed(st)
                * weightedDifficultyMult(spdRatio, resolveSpeedWeight(st, seaProfile))
                * BullethellConfig.GLOBAL_ENEMY_BULLET_SPEED_MULT.get();
        BulletType seaType = bulletTypeByName(stepTS(st, "bulletType", st.bulletType));
        float seaHit = bulletHit(st);
        int seaLife = resolveBulletLifetime(st, BullethellConfig.PATTERN_DEFAULT_LIFE_RAIN.get());
        int scaledArms = applyPressureArms(
                Math.max(4, Math.round(sampleArms(st)
                        * weightedDifficultyMult(dens, resolveArmsWeight(st, seaProfile)))),
                pressure, st, seaProfile);
        scaledArms = applyMaxScaledArmsCap(st, scaledArms);

        // Corridor center oscillates left-right: driven by seaSplitAngle (sweeps once
        // per 2ﾏ/advance).
        // Cap sweep so the safe channel stays a wide central "river" - never parks
        // against arena corners.
        float corridorHalfPick = stepTF(st, "laserHalfWidth", st.laserHalfWidth);
        float corridorHalf = corridorHalfPick > 0.01f ? corridorHalfPick : 50f;
        float arenaMargin = 26f;
        float maxExtent = BulletPool.ARENA_W * 0.5f - corridorHalf - arenaMargin;
        float sweepScale = 0.56f;
        float corridorRange = Math.max(0f, maxExtent * sweepScale);
        float corridorCX = BulletPool.ARENA_W * 0.5f + (float) Math.sin(seaSplitAngle) * corridorRange;

        // === Curtain row ===
        // Two segments per row: one spanning the full left wall, one spanning the full
        // right wall.
        // visScale is sized so lineVisualHalfLength * vis exactly fills each side.
        seaSplitFireCd--;
        if (seaSplitFireCd <= 0) {
            float angV = stepTF(st, "bulletAngularVelocity", st.bulletAngularVelocity);
            float leftBound = corridorCX - corridorHalf;
            float rightBound = corridorCX + corridorHalf;
            float es = PatternEngine.enemySpeedScale(difficulty);
            float vy = effSpeed * es;
            float baseHalfLen = BulletTypeLoader.get(seaType).lineVisualHalfLength;
            if (baseHalfLen < 1f)
                baseHalfLen = 56f;

            if (leftBound > 1f) {
                float halfLen = leftBound * 0.5f;
                bullets.spawn(halfLen, -14f, 0f, vy, seaType.getId(), seaLife,
                        halfLen / baseHalfLen, seaHit, angV);
            }
            if (rightBound < BulletPool.ARENA_W - 1f) {
                float halfLen = (BulletPool.ARENA_W - rightBound) * 0.5f;
                bullets.spawn(rightBound + halfLen, -14f, 0f, vy, seaType.getId(), seaLife,
                        halfLen / baseHalfLen, seaHit, angV);
            }
            float effCdDens = weightedDifficultyMult(dens, resolveCooldownWeight(st, seaProfile));
            int cdPick = stepTI(st, "cooldown", st.cooldown);
            int minPick = stepTI(st, "minCooldown", st.minCooldown);
            int rawCd = Math.max(Math.max(0, minPick), (int) (cdPick / effCdDens));
            seaSplitFireCd = applyPatternTempoToCooldownTicks(rawCd, seaSplitCfg, phase);
        }

        // === Secondary attacks (AIMED KNIFE, RING_OFFSET, etc.) ===
        seaSplitSecCd--;
        if (seaSplitSecCd <= 0 && phase != null && phase.attacks != null) {
            int total = phase.attacks.size();
            int attempts = total;
            while (attempts-- > 0) {
                seaSplitSecIdx = seaSplitSecIdx % total;
                PatternStep sec = phase.attacks.get(seaSplitSecIdx);
                seaSplitSecIdx++;
                if (sec == null || "SEA_SPLIT".equals(sec.getPatternUpper()))
                    continue;
                String secPat = sec.getPatternUpper();
                int bCount = Math.max(1, stepTI(sec, "burstCount", sec.burstCount));
                for (int b = 0; b < bCount; b++) {
                    float ox = bossX + stepSpawnOX(sec);
                    float oy = bossY + stepSpawnOY(sec);
                    if (sec.fireFromRandomBossHalo) {
                        float[] xy = new float[2];
                        sampleBossHaloOrigin(sec, xy);
                        ox = xy[0];
                        oy = xy[1];
                    }
                    executeAttackAt(sec, ox, oy);
                }
                seaSplitSecCd = computeAttackCooldown(sec, secPat);
                break;
            }
        }
    }

    /**
     * Add ritual phase offsets without overflowing {@link Integer#MAX_VALUE} (used
     * as "not yet").
     */
    private static int addTickSafe(int base, int delta) {
        if (delta <= 0)
            return base;
        if (base >= Integer.MAX_VALUE - delta)
            return Integer.MAX_VALUE;
        return base + delta;
    }

    /**
     * Cadence for phase-4 aimed follow-up volleys when
     * {@link PatternStep#pentagramFollowupDurationTicks} &gt; 0.
     */
    private static int prBossFollowupWallIntervalTicks(DifficultyConfig d) {
        return switch (d) {
            case EASY -> 5;
            case NORMAL -> 4;
            case HARD -> 3;
            case LUNATIC -> 2;
        };
    }

    /** Legacy phase-4 cadence (random-heading rows until phase ends). */
    private static int prBossWallIntervalTicks(DifficultyConfig d) {
        return switch (d) {
            case EASY -> 11;
            case NORMAL -> 8;
            case HARD -> 6;
            case LUNATIC -> 4;
        };
    }

    /** Random travel angle for legacy ritual follow-up rows. */
    private static float prBossRandomOrbFlightAngle(java.util.Random rng) {
        if (rng.nextFloat() < 0.14f)
            return rng.nextFloat() * (float) (Math.PI * 2.0);
        float a = PR_BOSS_ORB_FLIGHT_MIN
                + rng.nextFloat() * (PR_BOSS_ORB_FLIGHT_MAX - PR_BOSS_ORB_FLIGHT_MIN);
        a += (rng.nextFloat() - 0.5f) * PR_BOSS_ORB_FLIGHT_JITTER_RAD * 2f;
        return a;
    }

    /** Orbs per row (slight "more" on higher tiers). */
    private static int prBossRowBulletCount(DifficultyConfig d) {
        return switch (d) {
            case EASY, NORMAL -> 7;
            case HARD -> 8;
            case LUNATIC -> 9;
        };
    }

    /** Scales slight C-curve on each orb row (higher on Lunatic). */
    private static float prBossCurvScale(DifficultyConfig d) {
        return switch (d) {
            case EASY -> 0.82f;
            case NORMAL -> 1f;
            case HARD -> 1.22f;
            case LUNATIC -> 1.5f;
        };
    }

    private static float smoothstep(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return x * x * (3f - 2f * x);
    }

    private int stepTI(PatternStep s, String key, int base) {
        return TierJson.pickInt(s == null ? null : s.byDifficulty, key, difficulty.ordinal(), base);
    }

    private float stepTF(PatternStep s, String key, float base) {
        return TierJson.pickFloat(s == null ? null : s.byDifficulty, key, difficulty.ordinal(), base);
    }

    private String stepTS(PatternStep s, String key, String base) {
        return TierJson.pickString(s == null ? null : s.byDifficulty, key, difficulty.ordinal(), base);
    }

    private boolean stepTB(PatternStep s, String key, boolean base) {
        return TierJson.pickBoolean(s == null ? null : s.byDifficulty, key, difficulty.ordinal(), base);
    }

    private float stepSpawnOX(PatternStep s) {
        return stepTF(s, "spawnOffsetX", s.spawnOffsetX);
    }

    private float stepSpawnOY(PatternStep s) {
        return stepTF(s, "spawnOffsetY", s.spawnOffsetY);
    }

    private int applyMaxScaledArmsCap(PatternStep step, int scaledArms) {
        int cap = stepTI(step, "maxScaledArms", step.maxScaledArms);
        if (cap > 0)
            return Math.min(cap, scaledArms);
        return scaledArms;
    }

    private int resolveBulletLifetime(PatternStep step, int def) {
        if (step != null) {
            int lt = stepTI(step, "bulletLifetimeTicks", step.bulletLifetimeTicks);
            if (lt > 0)
                return lt;
        }
        return BulletPool.LIFE_KILL_WALL_ONLY;
    }

    /**
     * Restores {@code secondaryLifetimes} to initial values - called when the boss
     * repositions so burst secondaries replay each loop.
     */
    private void resetSecondaryLifetimes() {
        secondaryLifetimes.clear();
        PhaseDefinition phase = currentBossPhase();
        if (phase == null)
            return;
        if (phase.attacks != null) {
            for (PatternStep s : phase.attacks) {
                if (s.everyTickWhilePhase && s.activeTicks > 0 && !isLaserPattern(s.pattern)) {
                    secondaryLifetimes.put(s, stepTI(s, "activeTicks", s.activeTicks));
                }
            }
        }
        if (phase.emitters != null) {
            for (mc.sayda.bullethell.boss.BossEmitterDefinition em : phase.emitters) {
                if (em.attacks != null) {
                    for (PatternStep s : em.attacks) {
                        if (s.everyTickWhilePhase && s.activeTicks > 0 && !isLaserPattern(s.pattern)) {
                            secondaryLifetimes.put(s, stepTI(s, "activeTicks", s.activeTicks));
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns true for patterns where {@code activeTicks} means beam damage
     * duration, not secondary step lifetime.
     */
    private static boolean isLaserPattern(String pattern) {
        if (pattern == null)
            return false;
        String p = pattern.trim().toUpperCase();
        return p.equals("LASER") || p.equals("LASER_ROTATING") || p.equals("LASER_BEAM");
    }

    private float bulletVis(PatternStep step) {
        float sc = stepTF(step, "bulletScale", step.bulletScale);
        return sc > 0.01f ? sc : 1f;
    }

    private float bulletHit(PatternStep step) {
        float hb = stepTF(step, "hitboxScale", step.hitboxScale);
        if (hb > 0.01f)
            return hb;
        return bulletVis(step) > 1.25f ? 0.42f : 1f;
    }

    private float sampleSpeed(PatternStep step) {
        float base = stepTF(step, "speed", step.speed);
        return sampleFloatRange(base, stepTF(step, "speedMin", step.speedMin), stepTF(step, "speedMax", step.speedMax),
                0.01f);
    }

    private float sampleSpread(PatternStep step) {
        float base = stepTF(step, "spread", step.spread);
        if (!mc.sayda.bullethell.boss.TierJson.hasTierArray(step.byDifficulty, "spread"))
            base *= difficulty.spreadScale;
        return sampleFloatRange(base, stepTF(step, "spreadMin", step.spreadMin),
                stepTF(step, "spreadMax", step.spreadMax),
                0f);
    }

    private int sampleArms(PatternStep step) {
        boolean armsTiered = TierJson.hasTierArray(step.byDifficulty, "arms")
                || TierJson.hasTierArray(step.byDifficulty, "armsMin")
                || TierJson.hasTierArray(step.byDifficulty, "armsMax");
        int baseArms = stepTI(step, "arms", step.arms);
        if (armsTiered)
            return Math.max(1, baseArms);
        int minA = stepTI(step, "armsMin", step.armsMin);
        int maxA = stepTI(step, "armsMax", step.armsMax);
        if (minA > 0 && maxA > 0) {
            int lo = Math.min(minA, maxA);
            int hi = Math.max(minA, maxA);
            return lo + random.nextInt(hi - lo + 1);
        }
        return Math.max(1, baseArms);
    }

    private float sampleFloatRange(float fallback, float min, float max, float validMin) {
        boolean hasRange = min >= validMin && max >= validMin;
        if (!hasRange)
            return fallback;
        float lo = Math.min(min, max);
        float hi = Math.max(min, max);
        if (hi - lo < 1e-6f)
            return lo;
        return lo + random.nextFloat() * (hi - lo);
    }

    private float stepPatternTempo(PatternStep step, PhaseDefinition phase) {
        if (step == null)
            return phasePatternTempo(phase);
        float stepTempo = stepTF(step, "patternTempo", step.patternTempo);
        return stepTempo > 0.001f ? stepTempo : phasePatternTempo(phase);
    }

    /**
     * Phase-wide pattern speed ({@code 1} = JSON as authored). Invalid or tiny
     * values behave as {@code 1}.
     */
    private float phasePatternTempo(PhaseDefinition phase) {
        if (phase == null)
            return 1f;
        float t = phase.resolvePatternTempo(difficulty.ordinal());
        if (!Float.isFinite(t) || t < 1e-3f)
            return 1f;
        return t;
    }

    /**
     * Scales tick waits by phase {@link PhaseDefinition#patternTempo} (e.g. tempo 2
     * halves gaps).
     * Does not affect bullet travel speed.
     */
    private int applyPatternTempoToCooldownTicks(int ticks, PatternStep step, PhaseDefinition phase) {
        float tempo = stepPatternTempo(step, phase);
        if (Math.abs(tempo - 1f) < 0.0005f)
            return ticks;
        return Math.max(0, (int) Math.floor(ticks / tempo));
    }

    private int computeAttackCooldown(PatternStep step, String patUpper) {
        AttackScalingProfile profile = resolveScalingProfile(step, patUpper);
        float dens = bossDensityMult(step);
        float dScale = stepTF(step, "densityScale", step.densityScale);
        if (dScale > 0.01f)
            dens *= dScale;
        float effectiveDens = weightedDifficultyMult(dens, resolveCooldownWeight(step, profile));
        int cdRaw = stepTI(step, "cooldown", step.cooldown);
        int minCdRaw = stepTI(step, "minCooldown", step.minCooldown);
        int cd = Math.max(0, (int) (cdRaw / effectiveDens));
        int minCd = Math.max(0, minCdRaw);
        cd = Math.max(minCd, cd);
        float pressure = bulletPressure();
        float soft = resolvePressureSoftCap(step, profile);
        int maxBoost = resolvePressureCooldownBoost(step, profile);
        if (maxBoost > 0 && pressure > soft) {
            float t = (pressure - soft) / Math.max(0.01f, (1f - soft));
            cd += Math.round(Math.min(1f, t) * maxBoost);
        }
        // Rank modifier: rank 16 = neutral; rank 32 = 24% shorter cooldown (harder);
        // rank 0 = 24% longer (easier).
        if (rank != 16) {
            float rankMult = 1f - (rank - 16) * 0.015f;
            rankMult = Math.max(0.5f, Math.min(1.5f, rankMult));
            cd = Math.max(0, (int) (cd * rankMult));
        }
        float effectiveTempo = stepPatternTempo(step, currentBossPhase());
        if (Math.abs(effectiveTempo - 1f) < 0.0005f)
            return cd;
        return Math.max(0, (int) Math.floor(cd / effectiveTempo));
    }

    private AttackScalingProfile resolveScalingProfile(PatternStep step, String patUpper) {
        String rawProf = stepTS(step, "scalingProfile", step.scalingProfile == null ? "" : step.scalingProfile);
        String profileName = rawProf == null ? "" : rawProf.trim().toUpperCase();
        if (profileName.isEmpty() || "AUTO".equals(profileName)) {
            return switch (patUpper) {
                case "SPIRAL", "SPRINKLER", "DIVINE_WIND", "RING_OFFSET", "LASER_ROTATING", "PENTAGRAM",
                        "PENTAGRAM_RITUAL", "SEA_SPLIT", "ORB_C_ROW" ->
                    SCALE_GEOMETRY;
                case "STACK_FAN_VOLLEY", "DAGGER_HALO_VOLLEY" -> SCALE_PRECISION;
                case "AIMED", "BOUNCE", "LASER", "SWEEP", "SHOTGUN" -> SCALE_PRECISION;
                case "RING" -> SCALE_BURST;
                case "SPREAD", "RAIN", "DENSE_RING", "AIMED_RING", "LASER_BEAM" -> SCALE_SPAM;
                default -> SCALE_BURST;
            };
        }
        return switch (profileName) {
            case "GEOMETRY" -> SCALE_GEOMETRY;
            case "PRECISION" -> SCALE_PRECISION;
            case "SPAM" -> SCALE_SPAM;
            case "BURST" -> SCALE_BURST;
            default -> SCALE_BURST;
        };
    }

    private static float weightedDifficultyMult(float mult, float weight) {
        if (!Float.isFinite(weight) || Math.abs(weight) < 1e-6f)
            return 1f;
        return 1f + (mult - 1f) * weight;
    }

    private float bulletPressure() {
        return Math.min(1f, bullets.getActiveCount() / (float) BulletPool.ENEMY_CAPACITY);
    }

    private float resolveArmsWeight(PatternStep step, AttackScalingProfile profile) {
        float w = stepTF(step, "armsDifficultyWeight", step.armsDifficultyWeight);
        return w > 0.01f ? w : profile.armsWeight();
    }

    private float resolveSpeedWeight(PatternStep step, AttackScalingProfile profile) {
        float w = stepTF(step, "speedDifficultyWeight", step.speedDifficultyWeight);
        return w > 0.01f ? w : profile.speedWeight();
    }

    private float resolveCooldownWeight(PatternStep step, AttackScalingProfile profile) {
        float w = stepTF(step, "cooldownDifficultyWeight", step.cooldownDifficultyWeight);
        return w > 0.01f ? w : profile.cooldownWeight();
    }

    private float resolvePressureSoftCap(PatternStep step, AttackScalingProfile profile) {
        float s = stepTF(step, "pressureSoftCap", step.pressureSoftCap);
        s = s > 0f ? s : profile.pressureSoftCap();
        return Math.max(0f, Math.min(1f, s));
    }

    private float resolvePressureArmDrop(PatternStep step, AttackScalingProfile profile) {
        float d = stepTF(step, "pressureArmDrop", step.pressureArmDrop);
        d = d > 0f ? d : profile.pressureArmDrop();
        return Math.max(0f, Math.min(1f, d));
    }

    private int resolvePressureCooldownBoost(PatternStep step, AttackScalingProfile profile) {
        int b = stepTI(step, "pressureCooldownBoost", step.pressureCooldownBoost);
        return b > 0 ? b : profile.pressureCooldownBoost();
    }

    private int applyPressureArms(int arms, float pressure, PatternStep step, AttackScalingProfile profile) {
        float soft = resolvePressureSoftCap(step, profile);
        float drop = resolvePressureArmDrop(step, profile);
        if (drop <= 0f || pressure <= soft)
            return Math.max(1, arms);
        float t = Math.min(1f, (pressure - soft) / Math.max(0.01f, (1f - soft)));
        float factor = 1f - drop * t;
        return Math.max(1, Math.round(arms * factor));
    }

    private boolean isScarletMeisterPhase() {
        PhaseDefinition p = currentBossPhase();
        if (p.attacks.isEmpty())
            return false;
        String pat = p.attacks.get(0).pattern;
        return pat != null && "MEISTER_CYCLE".equalsIgnoreCase(pat.trim());
    }

    /**
     * First attack step of Scarlet Meister phase (JSON {@code bulletType} / scales
     * apply to scripted shots).
     */
    private PatternStep meisterPatternStep() {
        PhaseDefinition p = currentBossPhase();
        if (p == null || p.attacks.isEmpty())
            return null;
        return p.attacks.get(0);
    }

    /**
     * Scarlet Meister: shotgun opener (fast wide fan), quick CW/CCW spin bursts,
     * brief pause,
     * mirrored shotgun + reversed spin order, then rest. Boss movement from phase
     * JSON ({@code CIRCLE}).
     */
    private void tickScarletMeister() {
        PlayerState2D aim = getBossAimTarget();
        float dens = bossDensityMult(null);
        float spdRatio = bossSpeedMult(null) / BullethellConfig.effectiveSpeedMult(DifficultyConfig.LUNATIC);
        float baseSpeed = 3.15f * spdRatio;
        meisterTimer++;

        float step = 0.102f + 0.045f * (Math.min(2.2f, dens) - 1f);
        int spinLen = (int) (26 + 8 * Math.min(2.5f, dens));
        int shotgunLen = (int) (20 + 6 * Math.min(2.5f, dens));
        int shortPause = (int) (10 + 3 * Math.min(2.5f, dens));
        int rest = (int) (24 + 6 * Math.min(2.5f, dens));

        switch (meisterSubPhase) {
            case 0 -> {
                if (meisterTimer % 2 == 0)
                    fireMeisterShotgun(aim, baseSpeed, dens, false);
                if (meisterTimer >= shotgunLen) {
                    meisterSubPhase = 1;
                    meisterTimer = 0;
                    meisterStreamAngle = (float) Math.atan2(aim.y - bossY, aim.x - bossX);
                }
            }
            case 1 -> {
                meisterStreamAngle -= step;
                fireMeisterSpinBurst(meisterStreamAngle, baseSpeed, dens);
                if (meisterTimer >= spinLen) {
                    meisterSubPhase = 2;
                    meisterTimer = 0;
                }
            }
            case 2 -> {
                meisterStreamAngle += step;
                fireMeisterSpinBurst(meisterStreamAngle, baseSpeed, dens);
                if (meisterTimer >= spinLen) {
                    meisterSubPhase = 3;
                    meisterTimer = 0;
                }
            }
            case 3 -> {
                if (meisterTimer >= shortPause) {
                    meisterSubPhase = 4;
                    meisterTimer = 0;
                }
            }
            case 4 -> {
                if (meisterTimer % 2 == 0)
                    fireMeisterShotgun(aim, baseSpeed, dens, true);
                if (meisterTimer >= shotgunLen) {
                    meisterSubPhase = 5;
                    meisterTimer = 0;
                    meisterStreamAngle = (float) Math.atan2(aim.y - bossY, aim.x - bossX);
                }
            }
            case 5 -> {
                meisterStreamAngle += step;
                fireMeisterSpinBurst(meisterStreamAngle, baseSpeed, dens);
                if (meisterTimer >= spinLen) {
                    meisterSubPhase = 6;
                    meisterTimer = 0;
                }
            }
            case 6 -> {
                meisterStreamAngle -= step;
                fireMeisterSpinBurst(meisterStreamAngle, baseSpeed, dens);
                if (meisterTimer >= spinLen) {
                    meisterSubPhase = 7;
                    meisterTimer = 0;
                }
            }
            case 7 -> {
                if (meisterTimer >= rest) {
                    meisterSubPhase = 0;
                    meisterTimer = 0;
                }
            }
            default -> {
                meisterSubPhase = 0;
                meisterTimer = 0;
            }
        }
    }

    /**
     * Wide fast fan toward aim; {@code mirrorFan} flips left/right spread for the
     * second pass.
     */
    private void fireMeisterShotgun(PlayerState2D aim, float baseSpeed, float dens, boolean mirrorFan) {
        PatternStep step = meisterPatternStep();
        BulletType mainType = step != null ? bulletTypeByName(stepTS(step, "bulletType", step.bulletType))
                : BulletType.fromName("RED_ORB_LARGE");
        if (mainType.name.equals("ORB") || mainType.name.equals("DOT"))
            mainType = BulletType.fromName("RED_ORB_LARGE");
        float vis = step != null ? bulletVis(step) : 1.42f;
        float hit = step != null ? bulletHit(step) : 0.48f;
        int lifeMain = resolveBulletLifetime(step, 175);
        float baseAngle = (float) Math.atan2(aim.y - bossY, aim.x - bossX);
        int arms = Math.min(11, Math.max(7, (int) (7 + dens * 1.1f)));
        float spread = 0.52f + 0.06f * Math.min(2.2f, dens);
        float spd = baseSpeed * 1.12f * BullethellConfig.enemyBulletSpeedFactor(difficulty);
        float dir = mirrorFan ? -1f : 1f;
        float mid = (arms - 1) / 2f;
        for (int i = 0; i < arms; i++) {
            float ang = baseAngle + dir * (i - mid) * spread;
            float vx = (float) Math.cos(ang) * spd;
            float vy = (float) Math.sin(ang) * spd;
            bullets.spawn(bossX, bossY, vx, vy, mainType.getId(), lifeMain, vis, hit, 0f);
        }
        // TH-style mentos trail toward the player (sparse; ECL tail pressure).
        int mentos = Math.min(6, Math.max(3, (int) (3 + dens * 0.55f)));
        int lifeM = resolveBulletLifetime(step, 200);
        float mspd = baseSpeed * 0.95f * BullethellConfig.enemyBulletSpeedFactor(difficulty);
        float mspread = 0.11f;
        float mmid = (mentos - 1) / 2f;
        for (int m = 0; m < mentos; m++) {
            float ang = baseAngle + dir * (m - mmid) * mspread;
            float vx = (float) Math.cos(ang) * mspd;
            float vy = (float) Math.sin(ang) * mspd;
            bullets.spawn(bossX, bossY, vx, vy, BulletType.fromName("SCARLET_MENTOS").getId(), lifeM, 1f, 0.88f, 0f);
        }
    }

    private void fireMeisterSpinBurst(float baseAngle, float baseSpeed, float dens) {
        PatternStep step = meisterPatternStep();
        BulletType mainType = step != null ? bulletTypeByName(stepTS(step, "bulletType", step.bulletType))
                : BulletType.fromName("RED_ORB_LARGE");
        if (mainType.name.equals("ORB") || mainType.name.equals("DOT"))
            mainType = BulletType.fromName("RED_ORB_LARGE");
        float vis = step != null ? bulletVis(step) : 1.38f;
        float hit = step != null ? bulletHit(step) : 0.46f;
        int life = resolveBulletLifetime(step, 200);
        int arms = Math.min(7, Math.max(3, (int) (3 + dens * 0.9f)));
        float spread = 0.32f;
        float es = BullethellConfig.enemyBulletSpeedFactor(difficulty);
        for (int i = 0; i < arms; i++) {
            float ang = baseAngle + (i - (arms - 1) / 2f) * spread;
            float vx = (float) Math.cos(ang) * baseSpeed * es;
            float vy = (float) Math.sin(ang) * baseSpeed * es;
            bullets.spawn(bossX, bossY, vx, vy, mainType.getId(), life, vis, hit, 0f);
        }
    }

    /**
     * Clears wall-bounce metadata before a slot is reused (including pool recycle
     * when at capacity).
     */
    private void clearEnemyBulletSlotMeta(int slot) {
        if (slot >= 0 && slot < bounceRemaining.length) {
            bounceRemaining[slot] = 0;
            bounceDamping[slot] = 0.96f;
            bounceExcludeMask[slot] = 0;
            divineWindCurveRemaining[slot] = 0;
        }
    }

    private static int parseBounceExcludeMask(PatternStep step) {
        if (step.bounceExcludeSides == null) return 0;
        int mask = 0;
        for (String s : step.bounceExcludeSides) {
            if (s == null) continue;
            switch (s.toLowerCase()) {
                case "left"   -> mask |= 1;
                case "right"  -> mask |= 2;
                case "top"    -> mask |= 4;
                case "bottom" -> mask |= 8;
            }
        }
        return mask;
    }

    /**
     * After the C-turn window, DIVINE_WIND bullets continue straight away instead
     * of looping back.
     */
    private void tickDivineWindCurves() {
        for (int i = 0; i < BulletPool.ENEMY_CAPACITY; i++) {
            if (!bullets.isActive(i)) {
                divineWindCurveRemaining[i] = 0;
                continue;
            }
            int rem = divineWindCurveRemaining[i];
            if (rem <= 0)
                continue;
            rem--;
            divineWindCurveRemaining[i] = rem;
            if (rem > 0)
                continue;
            float x = bullets.getX(i);
            float y = bullets.getY(i);
            float dx = x - bossX;
            float dy = y - bossY;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            float ux = len > 1e-4f ? dx / len : 0f;
            float uy = len > 1e-4f ? dy / len : -1f;
            float vx = bullets.getVx(i);
            float vy = bullets.getVy(i);
            float sp = (float) Math.sqrt(vx * vx + vy * vy);
            if (sp < 1e-3f)
                sp = 1.75f * BullethellConfig.enemyBulletSpeedFactor(difficulty);
            bullets.setAngVel(i, 0f);
            bullets.setVx(i, ux * sp);
            bullets.setVy(i, uy * sp);
        }
    }

    private void tickBouncingEnemyBullets() {
        final float minX = 0f;
        final float maxX = BulletPool.ARENA_W;
        final float minY = 0f;
        final float maxY = BulletPool.ARENA_H;
        for (int i = 0; i < BulletPool.ENEMY_CAPACITY; i++) {
            if (!bullets.isActive(i)) {
                bounceRemaining[i] = 0;
                continue;
            }
            int rem = bounceRemaining[i];
            if (rem <= 0) {
                continue;
            }
            float x = bullets.getX(i);
            float y = bullets.getY(i);
            float vx = bullets.getVx(i);
            float vy = bullets.getVy(i);
            int mask = bounceExcludeMask[i];
            boolean hit = false;

            if (x <= minX && vx < 0f) {
                if ((mask & 1) == 0) { x = minX; vx = -vx * bounceDamping[i]; hit = true; }
            } else if (x >= maxX && vx > 0f) {
                if ((mask & 2) == 0) { x = maxX; vx = -vx * bounceDamping[i]; hit = true; }
            }
            if (y <= minY && vy < 0f) {
                if ((mask & 4) == 0) { y = minY; vy = -vy * bounceDamping[i]; hit = true; }
            } else if (y >= maxY && vy > 0f) {
                if ((mask & 8) == 0) { y = maxY; vy = -vy * bounceDamping[i]; hit = true; }
            }

            if (!hit) {
                continue;
            }

            bullets.setVx(i, vx);
            bullets.setVy(i, vy);
            float[] slot = bullets.getSlotData(i);
            slot[BulletPool.F_X] = x;
            slot[BulletPool.F_Y] = y;
            bullets.setSlotData(i, slot, true);
            bounceRemaining[i] = rem - 1;
        }
    }

    private void checkPlayerBulletsVsBoss(UUID shooterUuid, BulletPool pb, PlayerState2D ps) {
        if (bossHp <= 0)
            return;
        int damage = bossBulletDamage(ps);

        for (int i = 0; i < pb.getCapacity(); i++) {
            if (!pb.isActive(i))
                continue;
            float bx = pb.getX(i);
            float by = pb.getY(i);
            BulletType bt = BulletType.fromId(pb.getType(i));
            float bulletR = bt.getRadius() * pb.getHitScale(i) * bt.getHitboxMul();
            float combined = BOSS_HIT_RADIUS + bulletR;
            float dx = bx - bossX;
            float dy = by - bossY;
            if (dx * dx + dy * dy <= combined * combined) {
                pb.deactivate(i);
                if (bossEntryTimer <= 0) {
                    bossHp = Math.max(0, bossHp - damage);
                    addArenaScore(damage * 8L, shooterUuid);
                    checkBossPhaseTransition();
                }
            }
        }
    }

    /**
     * Per-bullet boss damage keyed to power tier.
     * DPS scales monotonically: each tier fires more bullets and the per-bullet
     * value keeps total volley damage rising.
     *
     * Bullet counts per volley (from PlayerShotPatterns):
     * Unfocused: tier 0=1, 1=3, 2=5, 3=6, 4=8
     * Focused: tier 0=1, 1=3, 2=5, 3=6, 4=8
     *
     * Volley DPS at 20TPS (damage ﾃ・bullets / cooldown_ticks ﾃ・20):
     * Unfocused (cooldown=2): 80, 180, 250, 300, 400
     * Focused (cooldown=3): 93, 160, 233, 280, 373
     */
    private int bossBulletDamage(PlayerState2D ps) {
        int lv = ps.powerLevel();
        if (ps.focused) {
            return switch (lv) {
                case 0 -> 14; // 1 ﾃ・14 = 14
                case 1 -> 8; // 3 ﾃ・8 = 24
                case 2 -> 7; // 5 ﾃ・7 = 35
                case 3 -> 7; // 6 ﾃ・7 = 42
                default -> 7; // 8 ﾃ・7 = 56
            };
        } else {
            return switch (lv) {
                case 0 -> 8; // 1 ﾃ・8 = 8
                case 1 -> 6; // 3 ﾃ・6 = 18
                case 2 -> 5; // 5 ﾃ・5 = 25
                case 3 -> 5; // 6 ﾃ・5 = 30
                default -> 5; // 8 ﾃ・5 = 40
            };
        }
    }

    /**
     * Per-hit damage vs {@link EnemyPool} fairies from a single player shot bullet.
     * 1-HP fairies die in one hit regardless - flat 2 keeps the formula simple and
     * ensures medium/large fairies are cleared at a consistent rate across tiers.
     */
    private int fairyBulletDamage(PlayerState2D ps) {
        return 2;
    }

    private void checkBossPhaseTransition() {
        checkBossPhaseTransition(false);
    }

    /**
     * @param ignoreHpGate when true, advance regardless of boss HP (survival spell
     *                     timer,
     *                     or breaking a survival spell with a bomb)
     */
    private void checkBossPhaseTransition(boolean ignoreHpGate) {
        if (phaseTransitionTimer > 0 || pendingNextPhase >= 0)
            return; // already transitioning
        if (!ignoreHpGate) {
            // Trigger at 0 HP *or* when the threshold fraction is crossed (e.g. 20%
            // remaining).
            float threshold = currentBossPhase().resolveHpThresholdFraction(difficulty.ordinal());
            boolean belowThreshold = threshold > 0 && bossHp <= (int) (bossMaxHp * threshold);
            if (bossHp > 0 && !belowThreshold)
                return;
        }

        GameEvent spellResult = spellcard.onPhaseCleared();
        boolean wasSpellCard = currentBossPhase().resolveIsSpellCard(difficulty.ordinal());
        boolean captured = spellResult == GameEvent.SPELL_CAPTURED;
        if (wasSpellCard) {
            spellsAttempted++;
            if (captured)
                spellsCaptured++;
        }
        if (captured) {
            long bonus = spellcard.getBonusValue();
            java.util.List<UUID> alive = new java.util.ArrayList<>();
            for (UUID pid : allParticipants()) {
                PlayerState2D pss = getPlayerState(pid);
                if (pss != null && pss.lives >= 0) {
                    alive.add(pid);
                }
            }
            int n = alive.size();
            if (n > 0) {
                long each = bonus / n;
                long rem = bonus % n;
                for (int i = 0; i < alive.size(); i++) {
                    UUID pid = alive.get(i);
                    long piece = each + (i < rem ? 1L : 0L);
                    if (piece > 0L) {
                        applyScoreExtends(scoreSystemFor(pid).onSpellCapture(piece), pid);
                    }
                }
            }
        }
        pendingEvents.add(spellResult);
        pendingEvents.add(GameEvent.PHASE_CHANGE);
        if (wasSpellCard && captured) {
            // TH-style: bullets become score items at their positions; vacuum like
            // post-bomb.
            cancelBulletsIntoItems(ItemPool.TYPE_POINT_GREEN);
            attractAllCollectibleItems();
        } else if (!wasSpellCard) {
            // Non-spell phase clear: also convert bullets and attract items (standard TH
            // behavior)
            cancelBulletsIntoItems(ItemPool.TYPE_POINT_GREEN);
            attractAllCollectibleItems();
        }
        dropBossPhaseItems(wasSpellCard, captured);
        wormCircleRuntime.clear(bullets);
        ringSpawnerRuntime.clear(bullets);
        pentagramFormation.clear(bullets);
        bullets.clearAll();
        lasers.clearAll();
        playerBullets.clearAll();
        for (BulletPool pb : coopBullets.values()) pb.clearAll();

        int nextPhase = bossPhase + 1;
        if (nextPhase >= activeBossPhases.size()) {
            won = true;
            over = true;
            return;
        }

        // Queue next phase - boss drifts to that phase's anchor (or default intro spot)
        // over 50 ticks first.
        pendingNextPhase = nextPhase;
        phaseTransitionTimer = 50;
    }

    private float transitionTargetBossX() {
        if (pendingNextPhase >= 0 && pendingNextPhase < activeBossPhases.size()) {
            PhaseDefinition next = activeBossPhases.get(pendingNextPhase);
            if (next != null && next.bossPhaseAnchorX != null)
                return next.bossPhaseAnchorX;
        }
        return BulletPool.ARENA_W / 2f;
    }

    private float transitionTargetBossY() {
        if (pendingNextPhase >= 0 && pendingNextPhase < activeBossPhases.size()) {
            PhaseDefinition next = activeBossPhases.get(pendingNextPhase);
            if (next != null && next.bossPhaseAnchorY != null)
                return next.bossPhaseAnchorY;
            if (next != null && "REPOS_TOP".equals(next.resolveMovement(difficulty.ordinal())))
                return next.reposBossY;
        }
        return 80f;
    }

    /**
     * Scatter point items from the boss position when a phase is cleared.
     * Spell capture also runs {@link #cancelBulletsIntoItems()} + item vacuum in
     * {@link #checkBossPhaseTransition(boolean)} (TH-style bonus collect).
     * - NonSpell cleared 竊・4 point items scattered around boss
     * - Spell captured 竊・8 point items at boss (extra to per-bullet spawns from
     * cancel)
     * - Spell failed 竊・nothing (no reward for failing the card)
     *
     * Bosses NEVER drop power items, bombs, or 1-ups - those are fairy-only drops.
     */
    private void dropBossPhaseItems(boolean isSpellCard, boolean captured) {
        if (isSpellCard && !captured)
            return;
        int count = isSpellCard ? 8 : 4;
        for (int i = 0; i < count; i++) {
            float ox = (random.nextFloat() - 0.5f) * 80f;
            float oy = (random.nextFloat() - 0.5f) * 40f;
            items.spawn(bossX + ox, bossY + oy, ItemPool.TYPE_POINT);
        }
    }

    private void startBossPhase(int phaseIndex) {
        bossPhase = phaseIndex;
        attackIndex = 0;
        bossSegmentTicksRemaining = 0;
        bossSegmentVolleyCooldown = 0;
        phaseStartTick = bossTick; // movement formula resets from centre each phase
        meisterSubPhase = 0;
        meisterTimer = 0;
        meisterStreamAngle = 0f;
        patternCooldown = 0;
        bossBurstVolleysRemaining = 0;
        bossBurstStep = null;
        sprinklerAngles.clear();
        laserAngles.clear();
        sprinklerSeqArm.clear();
        divineWindLayer.clear();
        sweepAngles.clear();
        sweepDirs.clear();
        shotgunTick.clear();
        secondaryCooldowns.clear();
        secondaryLifetimes.clear();
        pentagramRitualFollowupHandedOff = false;
        pentagramRitualTick = -1;
        ritualStackCompleteAt = -1;
        prPentagramDisassembled = false;
        pentagramRitualCfg = null;
        pentagramRitualSpin = 0f;
        pentagramLastNewWaveTick = 0;
        wormCircleRuntime.clear(bullets);
        ringSpawnerRuntime.clear(bullets);
        pentagramFormation.clear(bullets);
        bullets.clearAll();

        PhaseDefinition phase = activeBossPhases.get(phaseIndex);
        int phaseHp = phase.resolveHp(difficulty.ordinal());
        if (!mc.sayda.bullethell.boss.TierJson.hasTierArray(phase.byDifficulty, "hp"))
            phaseHp = Math.max(1, Math.round(phaseHp * difficulty.healthScale));
        bossHp = phaseHp;
        bossMaxHp = phaseHp;
        // Resolve music once here so the random pick from a musicPool stays stable for
        // the whole phase.
        currentBossPhaseMusicId = phase.resolveMusic(difficulty.ordinal());

        sprinklerAngles.clear();
        sprinklerSeqArm.clear();
        divineWindLayer.clear();
        laserAngles.clear();
        sweepAngles.clear();
        sweepDirs.clear();
        secondaryCooldowns.clear();
        resetSecondaryLifetimes();
        reposDashState = 0;
        reposPhaseTimer = resolveReposShootTicks();
        reposStartX = 0f;
        reposTargetX = 0f;
        bossFireFrozen = false;
        if (phase.bossPhaseAnchorX != null) {
            bossX = phase.bossPhaseAnchorX;
        }
        if (phase.bossPhaseAnchorY != null) {
            if (bossEntryTimer > 0) {
                bossEntryToY = phase.bossPhaseAnchorY;
            } else {
                bossY = phase.bossPhaseAnchorY;
            }
        }

        // Reset phase emitters (logical spawners used for faithful ECL ports)
        activeEmitters.clear();
        if (phase.emitters != null && !phase.emitters.isEmpty()) {
            for (BossEmitterDefinition ed : phase.emitters) {
                if (ed == null || ed.attacks == null || ed.attacks.isEmpty())
                    continue;
                EmitterState es = new EmitterState();
                es.def = ed;
                es.attackIndex = 0;
                es.cooldown = 0;
                es.burstVolleysRemaining = 0;
                es.burstStep = null;
                activeEmitters.add(es);
            }
        }

        if (phase.resolveIsSpellCard(difficulty.ordinal())) {
            int duration = phase.resolveSpellDurationTicks(difficulty.ordinal());
            if (duration > 0)
                spellcard.start(duration, phase.resolveSpellBonus(difficulty.ordinal()),
                        phase.resolveSurvival(difficulty.ordinal()));
        }

        if (phaseIndex == 0)
            pendingEvents.add(GameEvent.BOSS_INTRO);
    }

    // ================================================================ SHARED
    // SYSTEMS

    private void tickPlayerShots(UUID uuid, PlayerState2D ps, BulletPool pb) {
        if (!ps.shooting) {
            ps.shotCooldown = 0;
            ps.shotTick = 0;
            return;
        }
        ps.shotTick++;

        CharacterDefinition cd = CharacterLoader.load(getCharacterId(uuid));
        int shotTypeIndex = getShotTypeOrdinal(uuid);

        // Periodic secondary shots (fireRateTicks > 0) fire on their own cadence every
        // tick.
        PlayerShotPatterns.firePeriodic(ps, pb, cd, shotTypeIndex);

        if (ps.shotCooldown > 0) {
            ps.shotCooldown--;
            return;
        }

        int cdN = cd.shotCooldownNormal > 0 ? cd.shotCooldownNormal : PlayerState2D.SHOT_COOLDOWN_NORMAL;
        int cdF = cd.shotCooldownFocused > 0 ? cd.shotCooldownFocused : PlayerState2D.SHOT_COOLDOWN_FOCUSED;
        ps.shotCooldown = ps.focused ? cdF : cdN;

        PlayerShotPatterns.fire(ps, pb, cd, shotTypeIndex);
    }

    private void checkEnemyBulletsVsPlayer(UUID uuid, PlayerState2D ps) {
        if (ps.deathPendingTicks > 0)
            return;
        boolean god = BHDebugMode.isGodMode(uuid);
        if (!god && ps.invulnTicks > 0)
            return;

        for (int i = 0; i < bullets.getCapacity(); i++) {
            if (!bullets.isActive(i))
                continue;
            BulletType bt = BulletType.fromId(bullets.getType(i));
            float bx = bullets.getX(i);
            float by = bullets.getY(i);

            if (bt.isShortLaserLineHit()) {
                float halfLen = bt.lineHitCollisionHalfLength(bullets.getVisScale(i));
                float thick = bt.lineHitCollisionHalfWidth(bullets.getHitScale(i));
                float distSq = BulletLineHit.distSqToShortLaser(
                        ps.x, ps.y, bx, by, bullets.getVx(i), bullets.getVy(i), halfLen);
                float hitMax = thick + ps.hitRadius;
                float grazeMax = thick + ps.grazeRadius;
                if (distSq <= hitMax * hitMax) {
                    bullets.deactivate(i);
                    if (god)
                        continue;
                    ps.deathPendingTicks = PlayerState2D.DEATH_BOMB_GRACE;
                    ps.personalEvents.add(GameEvent.HIT);
                    return;
                } else if (distSq <= grazeMax * grazeMax && rules.grazeScoringEnabled) {
                    ps.graze++;
                    ps.addStoredChargeProgress(20 * 3.0 / 2000.0);
                    applyScoreExtends(scoreSystemFor(uuid).onGraze(rules.grazeScoreMultiplier), uuid);
                    ps.personalEvents.add(GameEvent.GRAZE);
                    if (ps.graze % 50 == 0)
                        ps.personalEvents.add(GameEvent.GRAZE_CHAIN);
                }
                continue;
            }

            float dx = bx - ps.x;
            float dy = by - ps.y;
            float distSq = dx * dx + dy * dy;
            float bulletR = bt.getRadius() * bullets.getHitScale(i) * bt.getHitboxMul();
            float hitCombined = ps.hitRadius + bulletR;
            float grazeCombined = ps.grazeRadius + bulletR;

            if (distSq <= hitCombined * hitCombined) {
                bullets.deactivate(i);
                if (god)
                    continue;
                ps.deathPendingTicks = PlayerState2D.DEATH_BOMB_GRACE;
                ps.personalEvents.add(GameEvent.HIT);
                return;
            } else if (distSq <= grazeCombined * grazeCombined && rules.grazeScoringEnabled) {
                ps.graze++;
                ps.grazeChain++;
                ps.grazeChainCooldown = PlayerState2D.GRAZE_CHAIN_TIMEOUT;
                ps.addStoredChargeProgress(20 * 3.0 / 2000.0);
                applyScoreExtends(scoreSystemFor(uuid).onGraze(rules.grazeScoreMultiplier), uuid);
                ps.personalEvents.add(GameEvent.GRAZE);
                if (ps.graze % 50 == 0)
                    ps.personalEvents.add(GameEvent.GRAZE_CHAIN);
            }
        }
    }

    private void checkLasersVsPlayer(UUID uuid, PlayerState2D ps) {
        if (ps.deathPendingTicks > 0)
            return;
        boolean god = BHDebugMode.isGodMode(uuid);
        if (!god && ps.invulnTicks > 0)
            return;
        for (int i = 0; i < LaserPool.CAPACITY; i++) {
            if (!lasers.isFiring(i))
                continue;

            float lx = lasers.getX(i);
            float ly = lasers.getY(i);
            float angle = lasers.getAngle(i);
            float hw = lasers.getHalfWidth(i);
            boolean bidir = lasers.isBidir(i);

            // Returns -1 if directional laser and player is behind origin
            float dist = getLaserDistance(ps.x, ps.y, lx, ly, angle, bidir);
            if (dist < 0)
                continue;

            // 1. Hitbox Check (more forgiving)
            if (dist - (ps.hitRadius * LASER_HITBOX_SCALE) < hw) {
                if (god)
                    continue;
                ps.deathPendingTicks = PlayerState2D.DEATH_BOMB_GRACE;
                ps.personalEvents.add(GameEvent.HIT);
                return;
            }

            // 2. Graze Check (build gauge)
            if (dist - ps.grazeRadius < hw) {
                // Award small amount per tick while in beam vicinity
                ps.addStoredChargeProgress(2 * 3.0 / 2000.0);
                // Continuous laser graze event - throttled by sound engine usually
                if (stageTick % 10 == 0)
                    ps.personalEvents.add(GameEvent.GRAZE);
            }
        }
    }

    /**
     * Helper to get perpendicular distance from point to laser line.
     * Returns -1 if directional and behind start.
     */
    private float getLaserDistance(float px, float py, float lx, float ly, float angle, boolean bidir) {
        float cosA = (float) Math.cos(angle);
        float sinA = (float) Math.sin(angle);
        float dx = px - lx;
        float dy = py - ly;
        float along = dx * cosA + dy * sinA;
        if (!bidir && along < 0)
            return -1;
        return Math.abs(-dx * sinA + dy * cosA);
    }

    /**
     * Moves bomb-attracted items toward the nearest active player each tick.
     * Items collect automatically on contact. Uses a fixed speed (arena
     * units/tick).
     */
    private void tickAttractingItems() {
        float attract = rules.itemAttractionSpeed;
        float attract2 = attract * attract;
        for (int i = 0; i < ItemPool.CAPACITY; i++) {
            if (!items.isActive(i) || !items.isAttracting(i))
                continue;

            float ix = items.getX(i);
            float iy = items.getY(i);

            // Find nearest player among host + coop fairly
            PlayerState2D nearest = null;
            float bestD2 = Float.MAX_VALUE;

            for (PlayerState2D cp : getAllPlayerStates()) {
                if (cp.lives < 0)
                    continue;
                float d2 = (cp.x - ix) * (cp.x - ix) + (cp.y - iy) * (cp.y - iy);
                if (d2 < bestD2) {
                    bestD2 = d2;
                    nearest = cp;
                }
            }

            if (nearest != null && bestD2 < attract2) {
                collectItem(i, nearest);
            } else if (nearest != null) {
                float dx = nearest.x - ix, dy = nearest.y - iy;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                items.setX(i, ix + dx / dist * attract);
                items.setY(i, iy + dy / dist * attract);
            }
        }
    }

    /**
     * Checks all items against all players fairly.
     * Removes host bias by giving the item to the closest player among all in
     * range.
     */
    private void checkAllItemPickups() {
        float pocY = BulletPool.ARENA_H * (float) rules.pocFraction;

        for (int i = 0; i < ItemPool.CAPACITY; i++) {
            if (!items.isActive(i) || items.isAttracting(i))
                continue;

            float ix = items.getX(i);
            float iy = items.getY(i);

            PlayerState2D winner = null;
            float bestDistSq = Float.MAX_VALUE;

            for (PlayerState2D ps : getAllPlayerStates()) {
                if (ps.lives < 0)
                    continue;

                float dx = ix - ps.x;
                float dy = iy - ps.y;
                float d2 = dx * dx + dy * dy;

                boolean inRange = (rules.pocAutoCollect && ps.y < pocY)
                        || (d2 <= ps.pickupRadius * ps.pickupRadius);

                if (inRange) {
                    if (d2 < bestDistSq) {
                        bestDistSq = d2;
                        winner = ps;
                    }
                }
            }

            if (winner != null) {
                if (rules.pocAutoCollect && winner.y < pocY) {
                    items.setAttracting(i, true);
                } else {
                    collectItem(i, winner);
                }
            }
        }
    }

    /**
     * Deactivates an item and applies its effects (score, power, etc.) to the
     * player.
     */
    private void collectItem(int i, PlayerState2D ps) {
        int type = items.getType(i);
        float itemY = items.getY(i);
        items.deactivate(i);
        UUID u = uuidForPlayerState(ps);
        ScoreSystem ss = scoreSystemFor(u);

        switch (type) {
            case ItemPool.TYPE_POINT -> {
                addArenaScore(pointItemScoreAtHeight(itemY), u);
                ps.personalEvents.add(GameEvent.ITEM_PICK_UP);
            }
            case ItemPool.TYPE_POWER -> {
                if (ps.power >= PlayerState2D.MAX_POWER) {
                    // Item stays TYPE_POWER in the world for co-op; max-power
                    // collector gets point value instead of a wasted pickup.
                    addArenaScore(pointItemScoreAtHeight(itemY), u);
                    ps.personalEvents.add(GameEvent.ITEM_PICK_UP);
                } else {
                    applyScoreExtends(ss.onPowerItemPickup(), u);
                    ps.power = Math.min(PlayerState2D.MAX_POWER, ps.power + 1);
                    if (ps.power >= PlayerState2D.MAX_POWER) {
                        if (rules.bulletClearOnMaxPower && !ps.reachedMaxPowerInThisLife) {
                            cancelBulletsIntoItems(ItemPool.TYPE_POINT_GREEN);
                            ps.reachedMaxPowerInThisLife = true;
                        }
                        ps.personalEvents.add(GameEvent.ITEM_POWER_UP);
                    } else {
                        ps.personalEvents.add(GameEvent.ITEM_PICK_UP);
                    }
                }
            }
            case ItemPool.TYPE_FULL_POWER -> {
                if (ps.power >= PlayerState2D.MAX_POWER) {
                    addArenaScore(pointItemScoreAtHeight(itemY), u);
                } else {
                    applyScoreExtends(ss.onPowerItemPickup(), u);
                    ps.power = PlayerState2D.MAX_POWER;
                    if (rules.bulletClearOnMaxPower && !ps.reachedMaxPowerInThisLife) {
                        cancelBulletsIntoItems(ItemPool.TYPE_POINT_GREEN);
                        ps.reachedMaxPowerInThisLife = true;
                    }
                }
                ps.personalEvents.add(GameEvent.ITEM_POWER_UP);
            }
            case ItemPool.TYPE_POWER_LARGE -> {
                if (ps.power >= PlayerState2D.MAX_POWER) {
                    addArenaScore(pointItemScoreAtHeight(itemY), u);
                    ps.personalEvents.add(GameEvent.ITEM_PICK_UP);
                } else {
                    // POWER_LARGE gives 500 pts (vs 200 for small chip) - rarer large-fairy drop
                    applyScoreExtends(ss.addScore(500), u);
                    ps.power = Math.min(PlayerState2D.MAX_POWER, ps.power + 8);
                    if (ps.power >= PlayerState2D.MAX_POWER) {
                        if (rules.bulletClearOnMaxPower && !ps.reachedMaxPowerInThisLife) {
                            cancelBulletsIntoItems(ItemPool.TYPE_POINT_GREEN);
                            ps.reachedMaxPowerInThisLife = true;
                        }
                        ps.personalEvents.add(GameEvent.ITEM_POWER_UP);
                    } else {
                        ps.personalEvents.add(GameEvent.ITEM_PICK_UP);
                    }
                }
            }
            case ItemPool.TYPE_ONE_UP -> {
                ps.lives++;
                ps.personalEvents.add(GameEvent.ITEM_ONE_UP);
            }
            case ItemPool.TYPE_BOMB -> {
                ps.bombs = Math.min(ps.bombs + 1, 9);
                ps.personalEvents.add(GameEvent.ITEM_ONE_UP); // bomb pickup uses life-extend sound/flash
            }
            case ItemPool.TYPE_LIFE_PIECE -> {
                ps.lifePieces++;
                if (ps.lifePieces >= PlayerState2D.PIECES_PER_EXTEND) {
                    ps.lifePieces -= PlayerState2D.PIECES_PER_EXTEND;
                    ps.lives++;
                    ps.personalEvents.add(GameEvent.SCORE_EXTEND);
                } else {
                    ps.personalEvents.add(GameEvent.ITEM_PICK_UP);
                }
            }
            case ItemPool.TYPE_BOMB_PIECE -> {
                ps.bombPieces++;
                if (ps.bombPieces >= PlayerState2D.PIECES_PER_EXTEND) {
                    ps.bombPieces -= PlayerState2D.PIECES_PER_EXTEND;
                    ps.bombs = Math.min(ps.bombs + 1, 9);
                    ps.personalEvents.add(GameEvent.ITEM_ONE_UP);
                } else {
                    ps.personalEvents.add(GameEvent.ITEM_PICK_UP);
                }
            }
            case ItemPool.TYPE_POINT_GREEN -> {
                // White items from bomb cancellations are worth 100 pts (TH-authentic low
                // value)
                applyScoreExtends(ss.addScore(100), u);
                ps.personalEvents.add(GameEvent.ITEM_PICK_UP);
            }
        }
    }

    /** Same height竊痴core mapping as a {@link ItemPool#TYPE_POINT} pickup. */
    private int pointItemScoreAtHeight(float itemY) {
        float heightFrac = 1.0f - itemY / BulletPool.ARENA_H;
        return (int) (rules.pointItemMinValue
                + (rules.pointItemMaxValue - rules.pointItemMinValue) * heightFrac);
    }

    // ---------------------------------------------------------------- TH19
    // Abilities

    public void activateSkill(UUID uuid) {
        PlayerState2D ps = getPlayerState(uuid);
        if (ps == null || ps.lives < 0)
            return;

        /*
         * PoFV: colored hold bar picks release level; gray stock pays (level 竏・1)
         * only for L2+; L1 never drains stock. Cast is capped by 1 + floor(stock).
         */
        int held = (int) Math.floor(ps.holdChargeProgress + 1e-9);
        ps.holdChargeProgress = 0.0;
        ps.chargeConsecutiveHoldTicks = 0;
        if (held < 1) {
            ps.syncChargePacketFields();
            return;
        }

        int stockLevels = Math.min(PlayerState2D.CHARGE_LEVEL_MAX,
                (int) Math.floor(ps.storedChargeProgress + 1e-9));
        int maxCast = Math.min(PlayerState2D.CHARGE_LEVEL_MAX, 1 + stockLevels);
        int castLevel = Math.min(held, maxCast);
        int cost = castLevel - 1;
        ps.storedChargeProgress = Math.max(0.0, ps.storedChargeProgress - cost);
        ps.chargeLockoutTicks = ps.chargeDelayAfterSkill;
        ps.syncChargePacketFields();

        triggerCharacterSkill(uuid, ps, castLevel);
        pendingEvents.add(GameEvent.SKILL_USED); // Use skill event for the distinct visual effect
    }

    /**
     * TH09 PoFV-style charge attacks (Reimu / Marisa / Sakuya) and TH19-inspired
     * Sanae miracles. {@code level} is 1窶・ from the hold meter + stock rules.
     */
    private void triggerCharacterSkill(UUID uuid, PlayerState2D ps, int level) {
        String cid = getCharacterId(uuid);

        switch (cid) {
            case "marisa" -> {
                // PoFV: Illusion Laser - thin forward laser; stronger levels last longer
                // and hit harder (still one shared beam for networking simplicity).
                masterSparkOwner = uuid;
                masterSparkX = ps.x;
                masterSparkY = Math.max(0f, ps.y - 32f);
                masterSparkLevel = Math.min(3, Math.max(1, level));
                masterSparkTicks = switch (masterSparkLevel) {
                    case 1 -> 12;
                    case 2 -> 14; // deliberately below half of L3 so 2ﾃ有2 < 1ﾃ有3 for same stock
                    default -> 26;
                };
            }
            case "sakuya" -> {
                // PoFV: L1 Jack the Ripper (knife stream); L2 denser volley + radial burst;
                // L3 Private Square窶都tyle time stop + knife ring for resume wave.
                if (level >= 3) {
                    timeStopTicks = 60;
                    timeStopOwner = uuid;
                    fireSakuyaKnifeRing(uuid, ps, 20);
                } else if (level >= 2) {
                    fireSakuyaJackRipper(uuid, ps, 18, 0.12f);
                    PatternEngine.fireRing(getBulletPool(uuid), ps.x, ps.y, 14, 3.4f, difficulty,
                            BulletType.fromName("KNIFE"));
                } else {
                    fireSakuyaJackRipper(uuid, ps, 10, 0.08f);
                }
            }
            case "reimu" -> {
                // PoFV: L1 Hakurei Ofuda; L2 Yin-Yang Sign (dual rings + ofudas); L3
                // Dream Seal (denser rings + more homing ofudas).
                fireReimuChargeAttack(uuid, ps, level);
            }
            case "sanae" -> {
                // TH19-style: miracle bullet erase + wind/blessing burst (wiki page sparse).
                fireSanaeMiracle(uuid, ps, level);
            }
            case "yuuka" -> {
                // PoFV: L1 Leaf ring; L2 Denser Leaf + Jade rings; L3 Master Spark + dense rings
                fireYuukaChargeAttack(uuid, ps, level);
            }
            default -> {
            }
        }
    }

    /** PoFV Hakurei Ofuda / Yin-Yang Sign / Dream Seal approximations. */
    private void fireReimuChargeAttack(UUID uuid, PlayerState2D ps, int level) {
        BulletPool pb = getBulletPool(uuid);
        float px = ps.x;
        float py = ps.y - 8f;
        if (level <= 1) {
            fireHomingOrbs(ps, pb, 4);
            return;
        }
        if (level == 2) {
            PatternEngine.fireRing(pb, px, py, 12, 3.6f, difficulty, BulletType.fromName("RICE"));
            PatternEngine.fireRing(pb, px, py, 10, 2.3f, difficulty, BulletType.fromName("STAR"));
            fireHomingOrbs(ps, pb, 5);
            return;
        }
        PatternEngine.fireRing(pb, px, py, 18, 4.0f, difficulty, BulletType.fromName("RICE"));
        PatternEngine.fireRing(pb, px, py, 16, 2.6f, difficulty, BulletType.fromName("DOT"));
        fireHomingOrbs(ps, pb, 10);
    }

    private void fireHomingOrbs(PlayerState2D ps, BulletPool pb, int count) {
        for (int i = 0; i < count; i++) {
            float angle = (float) (i * Math.PI * 2 / Math.max(1, count));
            float vx = (float) Math.cos(angle) * 4f;
            float vy = (float) Math.sin(angle) * 4f;
            // ~Legacy HOMING_ORB draw (6fﾃ・.90) with BLUE_ORB base (4fﾃ・.80): vis 竕・1.5;
            // tighter hit via type mul + hitScale.
            pb.spawn(ps.x, ps.y, vx, vy, BulletType.fromName("BLUE_ORB").getId(), 200, 1.5f, 0.88f, 0f, 0,
                    BulletPool.HOMING_ON);
        }
    }

    /**
     * PoFV Jack the Ripper: knives aimed toward boss (or upward in wave phases).
     */
    private void fireSakuyaJackRipper(UUID uuid, PlayerState2D ps, int count, float spread) {
        BulletPool pb = getBulletPool(uuid);
        float tx = bossMaxHp > 0 ? bossX : ps.x;
        float ty = bossMaxHp > 0 ? bossY : ps.y - 220f;
        float base = (float) Math.atan2(ty - ps.y, tx - ps.x);
        for (int i = 0; i < count; i++) {
            float ang = base + (i - (count - 1) / 2f) * spread;
            float sp = 11.5f * BullethellConfig.effectiveSpeedMult(difficulty);
            pb.spawn(ps.x, ps.y, (float) Math.cos(ang) * sp, (float) Math.sin(ang) * sp,
                    BulletType.fromName("KNIFE").getId(), 140);
        }
    }

    /**
     * Ring of knives used with Sakuya L3 time stop (launches when time resumes).
     */
    private void fireSakuyaKnifeRing(UUID uuid, PlayerState2D ps, int count) {
        BulletPool pb = getBulletPool(uuid);
        float step = (float) (Math.PI * 2.0 / count);
        for (int i = 0; i < count; i++) {
            float ang = step * i;
            float dist = 22f + random.nextFloat() * 8f;
            float kx = ps.x + (float) Math.cos(ang) * dist;
            float ky = ps.y + (float) Math.sin(ang) * dist;
            pb.spawn(kx, ky, 0f, -12f, BulletType.fromName("KNIFE").getId(), 120);
        }
    }

    /** TH19-inspired: bullet miracle + outward wind (stars / bubbles). */
    private void fireSanaeMiracle(UUID uuid, PlayerState2D ps, int level) {
        float cx = ps.x;
        float cy = ps.y;
        if (level <= 1) {
            clearBulletsInRadius(cx, cy, 72f, ps);
            PatternEngine.fireRing(getBulletPool(uuid), cx, cy - 6f, 10, 3.2f, difficulty,
                    BulletType.fromName("BUBBLE"));
            return;
        }
        if (level == 2) {
            clearBulletsInRadius(cx, cy, 115f, ps);
            PatternEngine.fireSpiral(getBulletPool(uuid), cx, cy - 6f, 0f, 10, 3.6f, difficulty,
                    BulletType.fromName("STAR"));
            PatternEngine.fireRing(getBulletPool(uuid), cx, cy - 6f, 12, 2.8f, difficulty,
                    BulletType.fromName("BUBBLE"));
            return;
        }
        clearBulletsInRadius(cx, cy, 200f, ps);
        PatternEngine.fireRing(getBulletPool(uuid), cx, cy - 6f, 22, 4.2f, difficulty, BulletType.fromName("STAR"));
        PatternEngine.fireRing(getBulletPool(uuid), cx, cy - 6f, 16, 3.0f, difficulty, BulletType.fromName("BUBBLE"));
    }

    private void fireYuukaChargeAttack(UUID uuid, PlayerState2D ps, int level) {
        BulletPool pb = getBulletPool(uuid);
        float px = ps.x;
        float py = ps.y - 8f;
        if (level <= 1) {
            PatternEngine.fireRing(pb, px, py, 12, 3.6f, difficulty, BulletType.fromName("LEAF"));
            return;
        }
        if (level == 2) {
            PatternEngine.fireRing(pb, px, py, 16, 4.0f, difficulty, BulletType.fromName("LEAF"));
            PatternEngine.fireRing(pb, px, py, 12, 2.8f, difficulty, BulletType.fromName("JADE"));
            return;
        }
        
        // Level 3: Master Spark (Twin Spark vibe) + dense rings
        masterSparkOwner = uuid;
        masterSparkX = ps.x;
        masterSparkY = Math.max(0f, ps.y - 32f);
        masterSparkLevel = 3;
        masterSparkTicks = 26;
        
        PatternEngine.fireRing(pb, px, py, 20, 4.5f, difficulty, BulletType.fromName("JADE"));
        PatternEngine.fireRing(pb, px, py, 18, 3.2f, difficulty, BulletType.fromName("LEAF"));
    }

    private void tickMasterSpark() {
        // PoFV Illusion Laser: vertical beam; width and damage scale with charge level.
        int lv = masterSparkLevel > 0 ? masterSparkLevel : 3;
        float hw = switch (lv) {
            case 1 -> 15f;
            case 2 -> 19f;
            default -> 34f;
        };
        int enemyDmg = switch (lv) {
            case 1 -> 3;
            case 2 -> 3;
            default -> 6;
        };
        int bossDmg = switch (lv) {
            case 1 -> 6;
            case 2 -> 7;
            default -> 14;
        };
        float x = masterSparkX;
        float y = masterSparkY;

        PlayerState2D ownerPs = getPlayerState(masterSparkOwner);

        for (int i = 0; i < EnemyPool.CAPACITY; i++) {
            if (!enemies.isActive(i))
                continue;
            float dist = getLaserDistance(enemies.getX(i), enemies.getY(i), x, y, -1.570796f, false);
            if (dist >= 0 && dist < hw) {
                if (enemies.damage(i, enemyDmg))
                    killEnemy(i, ownerPs != null ? ownerPs : player);
            }
        }

        if (bossMaxHp > 0 && !currentBossPhase().resolveSurvival(difficulty.ordinal())) {
            float dist = getLaserDistance(bossX, bossY, x, y, -1.570796f, false);
            if (dist >= 0 && dist < hw) {
                bossHp = Math.max(0, bossHp - bossDmg);
                if (bossHp == 0)
                    checkBossPhaseTransition();
            }
        }

        for (int i = 0; i < bullets.getCapacity(); i++) {
            if (!bullets.isActive(i))
                continue;
            float dist = getLaserDistance(bullets.getX(i), bullets.getY(i), x, y, -1.570796f, false);
            if (dist >= 0 && dist < hw) {
                bullets.deactivate(i);
            }
        }
    }

    private void tickHomingBullets(BulletPool pb) {
        for (int i = 0; i < pb.getCapacity(); i++) {
            if (!pb.isActive(i) || pb.getPlayerHoming(i) <= 0f)
                continue;

            float bx = pb.getX(i);
            float by = pb.getY(i);

            // Find target
            float tx = -1, ty = -1;
            if (bossMaxHp > 0) {
                tx = bossX;
                ty = bossY;
            } else {
                // Find nearest enemy
                float bestD2 = 200 * 200; // max detection range
                for (int ei = 0; ei < EnemyPool.CAPACITY; ei++) {
                    if (!enemies.isActive(ei))
                        continue;
                    float dx = enemies.getX(ei) - bx;
                    float dy = enemies.getY(ei) - by;
                    float d2 = dx * dx + dy * dy;
                    if (d2 < bestD2) {
                        bestD2 = d2;
                        tx = enemies.getX(ei);
                        ty = enemies.getY(ei);
                    }
                }
            }

            if (tx != -1) {
                // Gently rotate velocity towards target
                float dx = tx - bx;
                float dy = ty - by;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist > 1) {
                    float vx = pb.getVx(i);
                    float vy = pb.getVy(i);
                    float speed = (float) Math.sqrt(vx * vx + vy * vy);

                    float targetVx = dx / dist * speed;
                    float targetVy = dy / dist * speed;

                    // Convergence factor (0.1 = fairly aggressive homing)
                    pb.setVx(i, vx + (targetVx - vx) * 0.15f);
                    pb.setVy(i, vy + (targetVy - vy) * 0.15f);
                }
            }
        }
    }

    private void tickItemAttraction() {
        // When time is stopped, things don't move, but Sakuya can still attract items
        for (var ps : getAllPlayerStates()) {
            checkItemAttraction(ps);
        }
    }

    private void checkItemAttraction(PlayerState2D ps) {
        float r2 = pickupRadiusMultiplier() * ps.pickupRadius;
        r2 *= r2;
        for (int i = 0; i < ItemPool.CAPACITY; i++) {
            if (items.isActive(i)) {
                float dx = items.getX(i) - ps.x;
                float dy = items.getY(i) - ps.y;
                if (dx * dx + dy * dy <= r2) {
                    items.setAttracting(i, true);
                }
            }
        }
    }

    private float pickupRadiusMultiplier() {
        return 5f; // items are attracted from 5x the pickup hit radius
    }

    private java.util.Collection<PlayerState2D> getAllPlayerStates() {
        if (cachedPlayerStates == null) {
            java.util.List<PlayerState2D> all = new java.util.ArrayList<>();
            all.add(player);
            all.addAll(coopPlayers.values());
            cachedPlayerStates = java.util.Collections.unmodifiableList(all);
        }
        return cachedPlayerStates;
    }

    // ---------------------------------------------------------------- death + bomb

    /** Radius within which all enemy bullets are cleared on player death. */
    private static final float DEATH_CLEAR_RADIUS = 110f;

    private void resetAbilityStates() {
        timeStopTicks = 0;
        timeStopOwner = null;
        masterSparkTicks = 0;
        masterSparkOwner = null;
        masterSparkX = 0f;
        masterSparkY = 0f;
        masterSparkLevel = 0;
    }

    private void applyDeath(UUID uuid) {
        PlayerState2D ps = getPlayerState(uuid);
        if (ps == null)
            return;
        if (BHDebugMode.isGodMode(uuid)) {
            ps.deathPendingTicks = 0;
            return;
        }

        spellcard.fail();
        if (arenaPhase == ArenaPhase.BOSS && currentBossPhase().resolveSurvival(difficulty.ordinal()))
            checkBossPhaseTransition(true);

        // Drop power items scattered around the death position so the player can
        // recover the full lost amount by collecting all drops.
        int powerBefore = ps.power;
        ps.power = Math.max(0, ps.power - rules.deathPowerLoss);
        int lost = powerBefore - ps.power;
        int dropLarge = lost / 8; // TYPE_POWER_LARGE = +8 each
        int dropSmall = lost % 8; // TYPE_POWER = +1 each (remainder)
        for (int d = 0; d < dropLarge; d++) {
            float ox = (random.nextFloat() - 0.5f) * 80f;
            float oy2 = (random.nextFloat() - 0.5f) * 60f;
            items.spawn(ps.x + ox, ps.y + oy2, ItemPool.TYPE_POWER_LARGE);
        }
        for (int d = 0; d < dropSmall; d++) {
            float ox = (random.nextFloat() - 0.5f) * 80f;
            float oy2 = (random.nextFloat() - 0.5f) * 60f;
            items.spawn(ps.x + ox, ps.y + oy2, ItemPool.TYPE_POWER);
        }

        ps.personalEvents.add(GameEvent.DEATH);
        // Death breaks graze chain and lowers rank
        ps.grazeChain = 0;
        ps.grazeChainCooldown = 0;
        rank = Math.max(0, rank - 4);

        if (ps.lives > 0) {
            ps.lives--;
            ps.bombs = ps.baseStartingBombs;
            ps.reachedMaxPowerInThisLife = false;
            // Player stays in place - only clear bullets within a radius around them
            boolean anyoneAlive = player.lives >= 0;
            if (!anyoneAlive) {
                for (var p : coopPlayers.values())
                    if (p.lives >= 0)
                        anyoneAlive = true;
            }
            if (!anyoneAlive) {
                resetAbilityStates();
            }

            clearBulletsInRadius(ps.x, ps.y, DEATH_CLEAR_RADIUS, null);
            ps.deathPendingTicks = 0;
            ps.invulnTicks = PlayerState2D.INVULN_TICKS;
        } else {
            ps.lives = -1; // eliminated
            if (allPlayersEliminated())
                over = true;
        }
    }

    /**
     * Deactivate every enemy bullet whose centre is within {@code radius} of (cx,
     * cy).
     */
    /**
     * @param gaugeRecipient if non-null, PoFV-style charge is awarded for each
     *                       bullet cleared (Sanae skill).
     */
    private void clearBulletsInRadius(float cx, float cy, float r, PlayerState2D gaugeRecipient) {
        float r2 = r * r;
        int count = 0;
        for (int i = 0; i < bullets.getCapacity(); i++) {
            if (!bullets.isActive(i))
                continue;
            float dx = bullets.getX(i) - cx;
            float dy = bullets.getY(i) - cy;
            if (dx * dx + dy * dy <= r2) {
                bullets.deactivate(i);
                count++;
            }
        }
        if (gaugeRecipient != null && count > 0)
            gaugeRecipient.addStoredChargeProgress(count * (2 * 3.0 / 2000.0));
    }

    /**
     * MAX power reached: cancel all enemy bullets into point items (TH6+ parity).
     * Each active bullet is deactivated and a point item is spawned at its
     * location,
     * up to the remaining item pool capacity.
     */
    /**
     * Cancel all enemy bullets into items of the specified type (TH-style).
     * Used for phase clears, max power, and bombs.
     */
    private void cancelBulletsIntoItems(int itemType) {
        for (int i = 0; i < bullets.getCapacity(); i++) {
            if (!bullets.isActive(i))
                continue;
            float bx = bullets.getX(i);
            float by = bullets.getY(i);
            bullets.deactivate(i);
            items.spawn(bx, by, itemType);
        }
        if (rules.bulletClearVacuum) {
            attractAllCollectibleItems();
        }
    }

    /** Activate a bomb for the specified participant. */
    public void activateBomb(UUID uuid) {
        PlayerState2D ps = getPlayerState(uuid);
        if (ps == null)
            return;
        boolean dbg = BHDebugMode.isGodMode(uuid);
        if (!dbg && ps.bombs <= 0)
            return;
        boolean isDeathBomb = ps.deathPendingTicks > 0;
        if (!dbg)
            ps.bombs--;
        else
            ps.bombs = 9;
        // Convert bullets to low-value white items (TH-authentic; bomb clears all
        // threats but rewards little)
        cancelBulletsIntoItems(ItemPool.TYPE_POINT_GREEN);
        lasers.clearAll();
        // Kill all small (non-large) enemies; mark their drops as attracted toward the
        // player
        for (int i = 0; i < EnemyPool.CAPACITY; i++) {
            if (!enemies.isActive(i))
                continue;
            EnemyType eType = EnemyType.fromId(enemies.getType(i));
            if (!eType.large) {
                int itemSlot = killEnemy(i, ps);
                if (itemSlot >= 0)
                    items.setAttracting(itemSlot, true);
            }
        }
        if (arenaPhase == ArenaPhase.BOSS) {
            spellcard.fail();
            if (currentBossPhase().resolveSurvival(difficulty.ordinal()))
                checkBossPhaseTransition(true);
        }
        if (ps.deathPendingTicks > 0)
            ps.deathPendingTicks = 0;

        attractAllCollectibleItems();

        if (isDeathBomb)
            pendingEvents.add(GameEvent.DEATH_BOMB);
        pendingEvents.add(GameEvent.BOMB_USED);

        // Bomb use lowers rank slightly (reward for difficult situation)
        rank = Math.max(0, rank - 2);
    }

    /** Pull every active pickup toward players (same as bomb vacuum). */
    private void attractAllCollectibleItems() {
        for (int i = 0; i < ItemPool.CAPACITY; i++) {
            if (items.isActive(i))
                items.setAttracting(i, true);
        }
    }

    // ---------------------------------------------------------------- helpers

    private PhaseDefinition currentBossPhase() {
        int n = activeBossPhases.size();
        if (n == 0)
            throw new IllegalStateException(
                    "activeBossPhases is empty - boss JSON phases missing or all filtered by difficulty");
        return activeBossPhases.get(Math.min(bossPhase, n - 1));
    }

    // ---------------------------------------------------------------- boss
    // declaration helpers (used by ArenaStatePacket)

    /**
     * True while the inter-phase pause is counting down.
     * The client uses this to drive the spell-card declaration animation.
     */
    public boolean isDeclaring() {
        return phaseTransitionTimer > 0;
    }

    /**
     * The spell name to display:
     * - During declaration: the INCOMING phase's name (so "Fantasy Seal" shows
     * before the card starts).
     * - During active phase: the current phase's name if it is a spell card.
     * - Otherwise: empty string.
     */
    public String getDisplaySpellName() {
        if (pendingNextPhase >= 0 && pendingNextPhase < activeBossPhases.size()) {
            PhaseDefinition next = activeBossPhases.get(pendingNextPhase);
            return next.resolveIsSpellCard(difficulty.ordinal()) ? next.resolveSpellName(difficulty.ordinal()) : "";
        }
        PhaseDefinition cur = currentBossPhase();
        return cur.resolveIsSpellCard(difficulty.ordinal()) ? cur.resolveSpellName(difficulty.ordinal()) : "";
    }

    /**
     * True when the currently active phase IS a spell card (not during
     * declaration).
     */
    public boolean isActiveSpellCard() {
        return arenaPhase == ArenaPhase.BOSS && !isDeclaring()
                && currentBossPhase().resolveIsSpellCard(difficulty.ordinal());
    }

    /**
     * Returns the music track ID for the current stage/boss phase.
     * Used by ArenaStatePacket.
     */
    public String getCurrentMusicTrackId() {
        if (arenaPhase == ArenaPhase.WAVES) {
            return (stage.stageMusic != null) ? stage.stageMusic : "";
        }
        return currentBossPhaseMusicId;
    }

    /**
     * Parse the drop cycle string from rules into an int[] of ItemPool type
     * constants.
     */
    private static int[] parseDropCycle(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return new int[] { ItemPool.TYPE_POWER, ItemPool.TYPE_POINT };
        }
        String[] parts = pattern.split(",");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = switch (parts[i].trim().toUpperCase()) {
                case "POWER" -> ItemPool.TYPE_POWER;
                case "POINT" -> ItemPool.TYPE_POINT;
                case "FULL_POWER" -> ItemPool.TYPE_FULL_POWER;
                case "ONE_UP" -> ItemPool.TYPE_ONE_UP;
                case "BOMB" -> ItemPool.TYPE_BOMB;
                case "POWER_LARGE" -> ItemPool.TYPE_POWER_LARGE;
                default -> ItemPool.TYPE_POINT;
            };
        }
        return result;
    }

    private static EnemyType enemyTypeByName(String name) {
        if (name == null)
            return EnemyType.BLUE_FAIRY;
        try {
            return EnemyType.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return EnemyType.BLUE_FAIRY;
        }
    }

    /** Handles special bullet logic like Sakuya's knives freezing. */
    private void tickSpecialBullets(BulletPool pb, boolean frozen) {
        if (!frozen)
            return;
        for (int i = 0; i < pb.getCapacity(); i++) {
            if (!pb.isActive(i))
                continue;
            if (isSakuyaBladeBullet(pb.getType(i))) {
                float cvx = pb.getVx(i), cvy = pb.getVy(i);
                if (cvx != 0f || cvy != 0f) {
                    pb.setPendingVx(i, cvx);
                    pb.setPendingVy(i, cvy);
                    pb.setVx(i, 0f);
                    pb.setVy(i, 0f);
                }
            }
        }
    }

    /** Restores velocity to knives when time resumes. */
    private void resumeFrozenBullets() {
        resumeFrozenPool(playerBullets);
        for (BulletPool pb : coopBullets.values()) {
            resumeFrozenPool(pb);
        }
    }

    private void resumeFrozenPool(BulletPool pb) {
        for (int i = 0; i < pb.getCapacity(); i++) {
            if (pb.isActive(i) && isSakuyaBladeBullet(pb.getType(i))) {
                float pvx = pb.getPendingVx(i);
                float pvy = pb.getPendingVy(i);
                if (pvx == 0f && pvy == 0f)
                    pvy = -12f;
                pb.setVx(i, pvx);
                pb.setVy(i, pvy);
            }
        }
    }

    /**
     * Kunai + knife share the same hit profile; both participate in Sakuya time
     * stop.
     */
    private static boolean isSakuyaBladeBullet(int typeId) {
        return BulletType.fromId(typeId).isSakuyaBlade();
    }

    private static BulletType bulletTypeByName(String name) {
        if (name == null)
            return BulletType.fromName("DOT");
        return BulletType.fromName(name);
    }

    // ---------------------------------------------------------------- queries

    public boolean isOver() {
        return over;
    }

    public void forceGameOver() {
        this.over = true;
    }

    public boolean isWon() {
        return won;
    }

    /**
     * @return true if time is currently frozen by Sakuya.
     */
    public boolean isTimeStopped() {
        return timeStopTicks > 0;
    }

    public boolean canPlayerMove(UUID uuid) {
        if (timeStopTicks <= 0)
            return true;
        return mc.sayda.bullethell.boss.CharacterLoader.load(getCharacterId(uuid)).immuneToTimeStop;
    }

    public int getSpellsCaptured() {
        return spellsCaptured;
    }

    public int getSpellsAttempted() {
        return spellsAttempted;
    }

    /**
     * Calculates the total completion percentage of the stage.
     * Combines wave progress and boss phase progress into a single 0-100 value.
     */

    public float getCompletionPercentage() {
        int totalWaves = Math.max(0, applicableWaveDefinitionCount);
        int totalPhases = activeBossPhases.size();
        int totalSteps = totalWaves + totalPhases;
        if (totalSteps == 0)
            return 100.0f;

        float stepsDone = 0;
        if (arenaPhase == ArenaPhase.WAVES) {
            stepsDone = scheduledEnemies.isEmpty() ? 0
                    : (float) nextScheduledIdx / scheduledEnemies.size() * totalWaves;
        } else if (arenaPhase == ArenaPhase.DIALOG_INTRO) {
            stepsDone = totalWaves;
        } else if (arenaPhase == ArenaPhase.BOSS) {
            float phaseProgress = 1.0f - (bossMaxHp > 0 ? (float) bossHp / bossMaxHp : 0f);
            stepsDone = totalWaves + bossPhase + phaseProgress;
        } else if (won) {
            stepsDone = totalSteps;
        }

        return Math.min(100.0f, (stepsDone / totalSteps) * 100.0f);
    }
}
