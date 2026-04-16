package mc.sayda.bullethell.arena;

import mc.sayda.bullethell.boss.BossDefinition;
import mc.sayda.bullethell.boss.BossEmitterDefinition;
import mc.sayda.bullethell.boss.BossLoader;
import mc.sayda.bullethell.boss.CharacterDefinition;
import mc.sayda.bullethell.debug.BHDebugMode;
import mc.sayda.bullethell.entity.BHAttributes;
import mc.sayda.bullethell.boss.FairyWaveLoader;
import mc.sayda.bullethell.boss.PatternStep;
import mc.sayda.bullethell.boss.PhaseDefinition;
import mc.sayda.bullethell.boss.RulesetConfig;
import mc.sayda.bullethell.boss.StageDefinition;
import mc.sayda.bullethell.boss.StageLoader;
import mc.sayda.bullethell.boss.WaveDefinition;
import mc.sayda.bullethell.boss.WaveEnemy;
import mc.sayda.bullethell.config.BullethellConfig;
import mc.sayda.bullethell.pattern.BulletType;
import mc.sayda.bullethell.pattern.PatternEngine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
    /** Boss phases filtered by this arena's difficulty (falls back to all phases). */
    private final List<PhaseDefinition> activeBossPhases;
    public final RulesetConfig rules;

    // ---------------------------------------------------------------- subsystems

    public final BulletPool bullets; // enemy + fairy bullets
    public final BulletPool playerBullets;
    public final ItemPool items;
    public final EnemyPool enemies;
    public final PlayerState2D player;
    public final ScoreSystem score;
    public final SpellcardTimer spellcard;
    public final LaserPool lasers = new LaserPool();

    // ---------------------------------------------------------------- stage state
    // machine

    public enum ArenaPhase {
        WAVES, DIALOG_INTRO, BOSS
    }

    public ArenaPhase arenaPhase = ArenaPhase.WAVES;

    // ---------------------------------------------------------------- scheduled enemy list
    // (pre-expanded at init from all waves + waveRef templates, sorted by spawnTick)

    /** Flat, time-sorted list of every enemy to spawn during the wave phase. */
    private final List<ScheduledEnemy> scheduledEnemies = new ArrayList<>();
    /** Index of the next entry in scheduledEnemies that hasn't spawned yet. */
    private int nextScheduledIdx = 0;
    /** Stage waves that pass {@link #waveAppliesToDifficulty(WaveDefinition)} (for HUD progress). */
    private int applicableWaveDefinitionCount = 0;
    /**
     * Per-slot attack pattern. Set when an enemy spawns; used by tickEnemyAI()
     * to dispatch the correct firing logic. Sized to EnemyPool.CAPACITY.
     */
    private final EnemyPattern[] enemyPatternIds = new EnemyPattern[EnemyPool.CAPACITY];

    /** Absolute tick counter from arena start (drives wave spawning). */
    private int stageTick = 0;
    /** Kill counter for drop-every-Nth-kill rule. */
    private int killCounter = 0;
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

    private static final float BOSS_HIT_RADIUS = 24f;

    private float spiralAngle = 0f;
    private int patternCooldown = 0;
    private int bossTick = 0;
    /** -1 left, 0 idle, +1 right. Synced to client for Cirno travel frames. */
    private int bossMoveDir = 0;
    /**
     * bossTick value at the start of the current phase - keeps movement continuous.
     */
    private int phaseStartTick = 0;
    private int attackIndex = 0;
    /** When &gt; 0, {@link #bossBurstStep} still has volleys left in the current burst. */
    private int bossBurstVolleysRemaining = 0;
    /** Step being repeated for burst fire; null when not in a burst chain. */
    private PatternStep bossBurstStep = null;

    // ---------------------------------------------------------------- phase emitters (Flandre clones/traps)
    private static final class EmitterState {
        BossEmitterDefinition def;
        int attackIndex;
        int cooldown;
        int burstVolleysRemaining;
        PatternStep burstStep;
    }

    private final ArrayList<EmitterState> activeEmitters = new ArrayList<>();

    private record AttackScalingProfile(
            float armsWeight,
            float speedWeight,
            float cooldownWeight,
            int minCooldown,
            float pressureSoftCap,
            float pressureArmDrop,
            int pressureCooldownBoost) {
    }

    private static final AttackScalingProfile SCALE_GEOMETRY = new AttackScalingProfile(
            0.70f, 0.95f, 0.90f, 2, 0.78f, 0.22f, 2);
    private static final AttackScalingProfile SCALE_PRECISION = new AttackScalingProfile(
            0.82f, 1.00f, 0.88f, 3, 0.72f, 0.24f, 3);
    private static final AttackScalingProfile SCALE_BURST = new AttackScalingProfile(
            0.72f, 0.92f, 0.78f, 3, 0.67f, 0.33f, 4);
    private static final AttackScalingProfile SCALE_SPAM = new AttackScalingProfile(
            0.56f, 0.84f, 0.64f, 5, 0.58f, 0.50f, 7);

    /**
     * Scarlet Meister scripted cycle: 0 shotgun (fast wide fan), 1 spin CW, 2 spin CCW,
     * 3 short pause, 4 mirrored shotgun, 5 spin CCW, 6 spin CW, 7 rest.
     */
    private int meisterSubPhase = 0;
    private int meisterTimer = 0;
    private float meisterStreamAngle = 0f;

    /** Remaining reflections for each enemy-bullet slot (0 = normal bullet). */
    private final int[] bounceRemaining = new int[BulletPool.ENEMY_CAPACITY];
    /** Per-slot bounce damping multiplier for BOUNCE bullets. */
    private final float[] bounceDamping = new float[BulletPool.ENEMY_CAPACITY];

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
    /** 1–3: PoFV Illusion Laser scaling ({@link #tickMasterSpark}). */
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

    private boolean over = false;
    /**
     * True when the boss's last phase was cleared (player won). False on game-over.
     */
    private boolean won = false;
    /** Number of spell cards the player successfully captured (no bomb/death). */
    private int spellsCaptured = 0;
    /** Total spell card phases triggered so far. */
    private int spellsAttempted = 0;
    /** ID of the active character (from CharacterDefinition JSON). */
    public String characterId = "reimu";

    // ---------------------------------------------------------------- co-op

    /**
     * Additional players sharing this arena (not the host).
     * Each has their own PlayerState2D and BulletPool; enemies/boss/items are
     * shared.
     */
    private final java.util.LinkedHashMap<UUID, PlayerState2D> coopPlayers = new java.util.LinkedHashMap<>();
    private final java.util.LinkedHashMap<UUID, BulletPool> coopBullets = new java.util.LinkedHashMap<>();
    private final java.util.LinkedHashMap<UUID, String> coopCharIds = new java.util.LinkedHashMap<>();
    /** Participants currently holding the pause menu open. */
    private final java.util.LinkedHashSet<UUID> pausedParticipants = new java.util.LinkedHashSet<>();
    /** Resolved each server tick from gamerule + paused participants. */
    private boolean globallyPaused = false;

    /** Add a co-op participant. Called when another player joins the match. */
    public void addCoopPlayer(UUID uuid, mc.sayda.bullethell.boss.CharacterDefinition charDef,
            net.minecraft.world.entity.LivingEntity participantAttributes) {
        int startLives = resolveStartingLives(charDef, participantAttributes);
        int startBombs = resolveStartingBombs(charDef, participantAttributes);
        PlayerState2D ps = new PlayerState2D(charDef.hitRadius, charDef.grazeRadius,
                charDef.pickupRadius, charDef.speedNormal, charDef.speedFocused,
                charDef.chargeRateShooting, charDef.chargeRateIdle, charDef.chargeRateCharging,
                charDef.chargeSpeedFrames, charDef.chargeDelayAfterSkill,
                startLives, startBombs);
        coopPlayers.put(uuid, ps);
        coopBullets.put(uuid, new BulletPool(BulletPool.PLAYER_CAPACITY));
        coopCharIds.put(uuid, charDef.id);
        if (arenaPhase == ArenaPhase.DIALOG_INTRO) {
            initDialogStateForPlayer(uuid);
        }
    }

    public void removeCoopPlayer(UUID uuid) {
        coopPlayers.remove(uuid);
        coopBullets.remove(uuid);
        coopCharIds.remove(uuid);
        pausedParticipants.remove(uuid);
        dialogScriptByPlayer.remove(uuid);
        dialogIndexByPlayer.remove(uuid);
        dialogTicksLeftByPlayer.remove(uuid);
        dialogReadyByPlayer.remove(uuid);
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

    /** All UUIDs participating in this arena (host + coop). */
    public java.util.Set<UUID> allParticipants() {
        java.util.LinkedHashSet<UUID> set = new java.util.LinkedHashSet<>();
        set.add(playerUuid);
        set.addAll(coopPlayers.keySet());
        return set;
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
     * @param stageId     file name (without .json) under
     *                    {@code data/bullethell/stages/}
     * @param characterId file name (without .json) under
     *                    {@code data/bullethell/characters/}
     * @param hostAttributes host player for {@link BHAttributes} bonuses, or null
     */
    public ArenaContext(UUID playerUuid, DifficultyConfig difficulty, String stageId, String characterId,
            net.minecraft.world.entity.LivingEntity hostAttributes) {
        this(playerUuid, difficulty, StageLoader.load(stageId), characterId, hostAttributes);
    }

    /**
     * Same as {@link #ArenaContext(UUID, DifficultyConfig, String, String, net.minecraft.world.entity.LivingEntity)}
     * but with a pre-built {@link StageDefinition} (e.g. synthetic boss-only stage from commands).
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
        this.playerBullets = new BulletPool(BulletPool.PLAYER_CAPACITY);
        this.items = new ItemPool();
        this.enemies = new EnemyPool();
        this.score = new ScoreSystem();
        this.spellcard = new SpellcardTimer();

        // Load stage/rules first so startingLives/Bombs overrides are available
        this.stage = stageDef;
        this.boss = BossLoader.load(stage.bossId);
        this.activeBossPhases = buildActiveBossPhases();
        this.rules = stage.rules;
        this.dropCycle = parseDropCycle(rules.dropCyclePattern);
        String largePattern = (rules.largeEnemyDropCyclePattern != null
                && !rules.largeEnemyDropCyclePattern.isEmpty())
                        ? rules.largeEnemyDropCyclePattern
                        : rules.dropCyclePattern;
        this.largeDropCycle = parseDropCycle(largePattern);

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
     * Skip waves and intro dialog and jump straight to a boss phase (0-based index).
     * Used by {@code /bullethell start &lt;target&gt; &lt;phase&gt;} (phase is 1-based in the command).
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
     * Stage rules override the character's {@link CharacterDefinition#startingLives};
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
            tickBouncingEnemyBullets();
            enemies.tick();
            items.tick();
            score.tick();

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
                bossTick++;

                if (phaseTransitionTimer > 0) {
                    phaseTransitionTimer--;
                    bossX += (BulletPool.ARENA_W / 2f - bossX) * 0.06f;
                    bossY += (80f - bossY) * 0.06f;
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
            if (arenaPhase == ArenaPhase.BOSS) {
                checkPlayerBulletsVsBoss(playerBullets, player);
                for (var entry : coopPlayers.entrySet()) {
                    BulletPool pb = coopBullets.get(entry.getKey());
                    if (pb != null)
                        checkPlayerBulletsVsBoss(pb, entry.getValue());
                }
            }
        }

        // 5. Player Actions (Shots, Gauge, Skill Follow-ups)
        tickPlayerShots(player, playerBullets);
        if (!frozen)
            tickSkillGauge(player);

        for (var e : coopPlayers.entrySet()) {
            UUID cUuid = e.getKey();
            PlayerState2D cPs = e.getValue();
            BulletPool cPb = coopBullets.get(cUuid);
            if (!frozen)
                tickSkillGauge(cPs);
            if (cPb != null && cPs.lives >= 0) {
                tickPlayerShots(cPs, cPb);
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
        ps.power = PlayerState2D.MAX_POWER;
        ps.invulnTicks = Math.max(ps.invulnTicks, 600);
        ps.deathPendingTicks = 0;
    }

    /** Arena tick counter (waves + boss); for debug HUD only. */
    public int getDebugArenaTick() {
        return stageTick;
    }

    /** Boss pattern cooldown remaining; for debug HUD (0 during waves / dialog). */
    public int getDebugBossPatternCooldown() {
        return patternCooldown;
    }

    /** Boss horizontal movement direction used for sprite-side travel frames. */
    public int getBossMoveDir() {
        return bossMoveDir;
    }

    private void tickStage() {
        tickEnemyAI();
        tickWaves();
        checkWavesComplete();
    }

    private void tickSkillGauge(PlayerState2D ps) {
        if (ps.lives < 0)
            return;

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
        for (WaveDefinition wave : stage.waves) {
            if (!waveAppliesToDifficulty(wave))
                continue;
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
        list.sort(Comparator.comparingInt(e -> e.spawnTick));
        scheduledEnemies.addAll(list);
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
            if (ey > BulletPool.ARENA_H + 30
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

                int scaledCount = Math.max(1, Math.round(type.bulletCount * difficulty.densityMult));
                int scaledInterval = Math.max(10, (int) (type.atkInterval / difficulty.densityMult));

                EnemyPattern pat = enemyPatternIds[i];
                if (pat == null) pat = type.defaultPattern;

                switch (pat) {
                    case AIMED:
                        // Aimed fan toward player (TH-standard small fairy)
                        PatternEngine.fireAimed(bullets, ex, ey,
                                player.x, player.y,
                                scaledCount, type.bulletSpread,
                                type.bulletSpeed, difficulty, BulletType.RICE);
                        enemies.setAtkCooldown(i, scaledInterval);
                        break;
                    case RING: {
                        // Uniform ring, random start angle each burst (TH6 barrier style)
                        float ringStart = random.nextFloat() * (float) (Math.PI * 2);
                        PatternEngine.fireRingOffset(bullets, ex, ey,
                                scaledCount, type.bulletSpeed,
                                difficulty, BulletType.BUBBLE, ringStart);
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
                                difficulty, BulletType.STAR, BulletType.BUBBLE, ringStart);
                        enemies.setAtkCooldown(i, scaledInterval);
                        break;
                    }
                    case SPREAD:
                        // Fixed downward fan, not aimed (TH8 curtain style)
                        PatternEngine.fireSpread(bullets, ex, ey,
                                scaledCount, type.bulletSpeed,
                                difficulty, BulletType.STAR);
                        enemies.setAtkCooldown(i, scaledInterval);
                        break;
                    case STREAM:
                        // Rapid single bullet - danger from rate, not spread
                        PatternEngine.fireAimed(bullets, ex, ey,
                                player.x, player.y,
                                1, 0f, type.bulletSpeed, difficulty, BulletType.RICE);
                        enemies.setAtkCooldown(i, Math.max(5, scaledInterval / 3));
                        break;
                    default:
                        break;
                }
            }
        }
    }

    /** Check player bullets (from any participant) hitting enemies. */
    private void checkPlayerBulletsVsEnemies(BulletPool pb, PlayerState2D ps) {
        for (int i = 0; i < pb.getCapacity(); i++) {
            if (!pb.isActive(i))
                continue;
            float bx = pb.getX(i);
            float by = pb.getY(i);
            for (int j = 0; j < EnemyPool.CAPACITY; j++) {
                if (!enemies.isActive(j))
                    continue;
                float ex = enemies.getX(j);
                float ey = enemies.getY(j);
                EnemyType type = EnemyType.fromId(enemies.getType(j));
                float r2 = (type.hitRadius + 3f) * (type.hitRadius + 3f);
                float dx = bx - ex;
                float dy = by - ey;
                if (dx * dx + dy * dy <= r2) {
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

        score.addScore(type.scoreValue);
        killCounter++;

        ps.addStoredChargeProgress(30 * 3.0 / 2000.0);

        // On-kill death burst (Lunatic-style)
        if (rules.onKillDeathBurstCount > 0) {
            PatternEngine.fireRing(bullets, ex, ey,
                    rules.onKillDeathBurstCount, rules.onKillDeathBurstSpeed,
                    difficulty, BulletType.RICE);
        }

        // Item drop - every Nth kill per ruleset
        // Large enemies use their own drop table (may include FULL_POWER).
        // Small fairies use the regular cycle (POWER + POINT only, TH-accurate).
        if (rules.itemDropEveryNthKill <= 1 || (killCounter % rules.itemDropEveryNthKill == 0)) {
            if (rules.bombDropChance > 0 && random.nextFloat() < rules.bombDropChance) {
                return items.spawn(ex, ey, ItemPool.TYPE_BOMB);
            } else if (type.large) {
                int dropType = largeDropCycle[largeDropCycleIdx % largeDropCycle.length];
                largeDropCycleIdx++;
                return items.spawn(ex, ey, dropType);
            } else {
                int dropType = dropCycle[dropCycleIdx % dropCycle.length];
                dropCycleIdx++;
                return items.spawn(ex, ey, dropType);
            }
        }
        return -1;
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
        bossTick = 0;
        bossX = BulletPool.ARENA_W / 2f;
        bossY = 100f;
        startBossPhase(0);
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
     * @param skipAll true = jump straight to the boss fight; false = advance one
     *                line
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

    /**
     * Effective bullet density for boss patterns: base difficulty plus a gentle rise
     * through phase index, with an extra bump on Lunatic so top difficulty stays
     * clearly above Hard.
     */
    private float bossDensityMult() {
        float cap = BullethellConfig.BOSS_PHASE_DENSITY_CAP.get();
        float per = BullethellConfig.BOSS_PHASE_DENSITY_PER_PHASE.get();
        float phaseCreep = 1f + Math.min(cap, bossPhase * per);
        float lunaticExtra = (difficulty == DifficultyConfig.LUNATIC)
                ? BullethellConfig.BOSS_LUNATIC_DENSITY_EXTRA.get()
                : 1f;
        return difficulty.densityMult * phaseCreep * lunaticExtra;
    }

    /** Effective bullet speed multiplier for boss patterns (see {@link #bossDensityMult()}). */
    private float bossSpeedMult() {
        float cap = BullethellConfig.BOSS_PHASE_SPEED_CAP.get();
        float per = BullethellConfig.BOSS_PHASE_SPEED_PER_PHASE.get();
        float phaseCreep = 1f + Math.min(cap, bossPhase * per);
        float lunaticExtra = (difficulty == DifficultyConfig.LUNATIC)
                ? BullethellConfig.BOSS_LUNATIC_SPEED_EXTRA.get()
                : 1f;
        return difficulty.speedMult * phaseCreep * lunaticExtra;
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
     * Living player closest to the boss - boss aimed patterns and lasers target this
     * player so co-op feels like shared pressure instead of always targeting the host.
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

        // Use ticks relative to this phase's start so movement begins at centre
        // every phase and never jumps when formulas change.
        int lt = bossTick - phaseStartTick;
        float oldX = bossX;
        switch (phase.movement == null ? "SINE_WAVE" : phase.movement) {
            case "CIRCLE" -> {
                // Starts at top-centre (sin=0) and orbits - no position jump on entry.
                bossX = BulletPool.ARENA_W / 2f + (float) Math.sin(lt * 0.018) * phase.moveSpeed;
                bossY = 80f + (float) (1 - Math.cos(lt * 0.018)) * phase.moveSpeed * 0.35f;
            }
            case "STATIC" -> {
                /* fixed */ }
            default -> // SINE_WAVE: starts at centre (sin 0 = 0)
                bossX = BulletPool.ARENA_W / 2f + (float) Math.sin(lt * 0.018) * phase.moveSpeed;
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

        patternCooldown--;
        if (patternCooldown > 0)
            return;

        if (phase.attacks.isEmpty()) {
            patternCooldown = 20;
            return;
        }

        // Continue a multi-shot burst (same PatternStep, same rotation slot).
        if (bossBurstVolleysRemaining > 0 && bossBurstStep != null) {
            PatternStep bStep = bossBurstStep;
            executeAttackAt(bStep, bossX + bStep.spawnOffsetX, bossY + bStep.spawnOffsetY);
            String bPat = bStep.pattern == null ? "" : bStep.pattern.toUpperCase();
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

        PatternStep step = phase.attacks.get(attackIndex % phase.attacks.size());
        int volleys = burstVolleyCount(step);
        executeAttackAt(step, bossX + step.spawnOffsetX, bossY + step.spawnOffsetY);
        String pat = step.pattern == null ? "" : step.pattern.toUpperCase();
        if (volleys > 1) {
            bossBurstStep = step;
            bossBurstVolleysRemaining = volleys - 1;
            patternCooldown = burstSpacingTicks(step);
        } else {
            attackIndex++;
            patternCooldown = computeAttackCooldown(step, pat);
        }
    }

    /** Effective shots per burst for this step (min 1). */
    private static int burstVolleyCount(PatternStep step) {
        int n = step.burstCount;
        return n <= 1 ? 1 : n;
    }

    /** Ticks between shots inside one burst (after the first). */
    private static int burstSpacingTicks(PatternStep step) {
        if (step.burstInterval > 0)
            return Math.max(1, step.burstInterval);
        return 5;
    }

    private void tickPhaseEmitters() {
        if (activeEmitters.isEmpty())
            return;
        PlayerState2D aimTarget = getBossAimTarget();
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
                executeAttackAt(bStep, es.def.x + bStep.spawnOffsetX, es.def.y + bStep.spawnOffsetY);
                String pat = bStep.pattern == null ? "" : bStep.pattern.toUpperCase();
                es.burstVolleysRemaining--;
                if (es.burstVolleysRemaining > 0) {
                    es.cooldown = burstSpacingTicks(bStep);
                } else {
                    es.burstStep = null;
                    es.attackIndex++;
                    es.cooldown = computeAttackCooldown(bStep, pat);
                }
                continue;
            }

            PatternStep step = es.def.attacks.get(es.attackIndex % es.def.attacks.size());
            int volleys = burstVolleyCount(step);
            executeAttackAt(step, es.def.x + step.spawnOffsetX, es.def.y + step.spawnOffsetY);
            String pat = step.pattern == null ? "" : step.pattern.toUpperCase();
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

    private void executeAttackAt(PatternStep step, float originX, float originY) {
        String patUpper = step.pattern == null ? "RING" : step.pattern.toUpperCase();
        if ("MEISTER_CYCLE".equals(patUpper))
            return;

        BulletType type = bulletTypeByName(step.bulletType);
        PlayerState2D aimTarget = getBossAimTarget();
        float dens = bossDensityMult();
        if (step.densityScale > 0.01f)
            dens *= step.densityScale;
        AttackScalingProfile profile = resolveScalingProfile(step, patUpper);
        float pressure = bulletPressure();
        float densArms = weightedDifficultyMult(dens, resolveArmsWeight(step, profile));
        float spdRatio = bossSpeedMult() / difficulty.speedMult;
        float effSpdRatio = weightedDifficultyMult(spdRatio, resolveSpeedWeight(step, profile));
        int sampledArms = sampleArms(step);
        float sampledSpread = sampleSpread(step);
        float sampledSpeed = sampleSpeed(step);
        int scaledArms = Math.max(1, Math.round(sampledArms * densArms));
        scaledArms = applyPressureArms(scaledArms, pressure, step, profile);
        if (step.maxScaledArms > 0)
            scaledArms = Math.min(step.maxScaledArms, scaledArms);
        float effSpeed = sampledSpeed * effSpdRatio;
        float vis = bulletVis(step);
        float hit = bulletHit(step);
        float bx = originX;
        float by = originY;
        float angV = step.bulletAngularVelocity;
        switch (patUpper) {
            case "SPIRAL" -> {
                PatternEngine.fireSpiral(bullets, bx, by, spiralAngle,
                        scaledArms, effSpeed, difficulty, type, vis, hit,
                        step.bulletLifetimeTicks, angV);
                spiralAngle += (float) (Math.PI * 2.0 / scaledArms) * 0.15f;
            }
            case "AIMED" -> PatternEngine.fireAimed(bullets, bx, by,
                    aimTarget.x, aimTarget.y, scaledArms, sampledSpread, effSpeed, difficulty, type, vis, hit,
                    step.bulletLifetimeTicks, angV);
            case "BOUNCE" -> fireBouncingAimed(step, type, aimTarget, scaledArms, sampledSpread, effSpeed, vis, hit, bx, by);
            case "AIMED_RING" -> {
                int scaledAimArms = scaledArms;
                int ringCap = BullethellConfig.BOSS_RING_ARMS_MAX.get();
                float ringDensCap = BullethellConfig.BOSS_RING_DENSITY_CAP.get();
                int scaledRingArms = Math.max(6,
                        Math.min(ringCap, Math.round(step.ringArms * Math.min(densArms, ringDensCap))));
                scaledRingArms = applyPressureArms(scaledRingArms, pressure, step, profile);
                float ringSp = step.ringSpeed > 0.01f ? step.ringSpeed * spdRatio : effSpeed * 0.52f;
                BulletType ringType = (step.ringBulletType != null && !step.ringBulletType.isEmpty())
                        ? bulletTypeByName(step.ringBulletType)
                        : BulletType.ORB;
                float ringStart = step.ringStartAngleRad >= 0f
                        ? step.ringStartAngleRad
                        : random.nextFloat() * (float) (Math.PI * 2.0);
                PatternEngine.fireAimedWithRing(bullets, bx, by,
                        aimTarget.x, aimTarget.y,
                        scaledAimArms, sampledSpread, effSpeed,
                        scaledRingArms, ringSp, difficulty, type, ringType, ringStart,
                        vis, hit, step.bulletLifetimeTicks, step.bulletLifetimeTicks, angV);
            }
            case "RING" -> {
                float ringStart = step.ringStartAngleRad >= 0f ? step.ringStartAngleRad : 0f;
                PatternEngine.fireRing(bullets, bx, by,
                        scaledArms, effSpeed, difficulty, type, vis, hit,
                        step.bulletLifetimeTicks, angV, ringStart);
            }
            case "RING_OFFSET" -> {
                float start = step.ringStartAngleRad >= 0f
                        ? step.ringStartAngleRad
                        : random.nextFloat() * (float) (Math.PI * 2.0);
                PatternEngine.fireRingOffset(bullets, bx, by,
                        scaledArms, effSpeed, difficulty, type, start, vis, hit,
                        step.bulletLifetimeTicks, angV);
            }
            case "SPREAD" -> PatternEngine.fireSpread(bullets, bx, by,
                    scaledArms, effSpeed, difficulty, type, vis, hit,
                    step.bulletLifetimeTicks, angV);
            case "RAIN" -> {
                float span = (step.rainWidth > 0f) ? Math.min(step.rainWidth, BulletPool.ARENA_W) : BulletPool.ARENA_W;
                float xStart = (BulletPool.ARENA_W - span) * 0.5f;
                float baseY = step.rainTop;
                int rainLife = resolveBulletLifetime(step, PatternEngine.DEFAULT_LIFE_RAIN);
                for (int i = 0; i < scaledArms; i++) {
                    float spawnX = xStart + random.nextFloat() * span + step.spawnOffsetX;
                    float jitter = (random.nextFloat() * 2f - 1f) * sampledSpread;
                    float ang = (float) (Math.PI * 0.5f) + jitter;
                    float sp = effSpeed * (0.88f + random.nextFloat() * 0.28f)
                            * BullethellConfig.GLOBAL_ENEMY_BULLET_SPEED_MULT.get();
                    float vx = (float) Math.cos(ang) * sp;
                    float vy = (float) Math.sin(ang) * sp;
                    bullets.spawn(spawnX, baseY + step.spawnOffsetY, vx, vy, type.getId(), rainLife, vis, hit, angV);
                }
            }
            case "DENSE_RING" -> {
                float drStart = step.ringStartAngleRad >= 0f ? step.ringStartAngleRad : 0f;
                PatternEngine.fireDenseRing(bullets, bx, by,
                        scaledArms, effSpeed, difficulty, type, vis, hit,
                        step.bulletLifetimeTicks, angV, drStart);
            }
            case "LASER_BEAM" -> PatternEngine.fireLaserBeam(bullets, bx, by,
                    aimTarget.x, aimTarget.y, scaledArms, effSpeed, difficulty, type, vis, hit,
                    step.bulletLifetimeTicks, angV,
                    step.laserBeamSpread);
            case "LASER" -> {
                float angle = (float) Math.atan2(aimTarget.y - by, aimTarget.x - bx);
                float densWarn = weightedDifficultyMult(dens, resolveCooldownWeight(step, profile));
                int scaledWarn = Math.max(10, (int) (step.warnTicks / densWarn));
                lasers.spawn(bx, by, angle, step.laserHalfWidth,
                        scaledWarn, step.activeTicks, type.getId(), false);
            }
            case "LASER_ROTATING" -> {
                float densWarn = weightedDifficultyMult(dens, resolveCooldownWeight(step, profile));
                int scaledWarn = Math.max(6, (int) (step.warnTicks / densWarn));
                float angleStep = (float) (Math.PI * 2.0 / scaledArms);
                for (int i = 0; i < scaledArms; i++) {
                    lasers.spawn(bx, by, spiralAngle + angleStep * i,
                            step.laserHalfWidth, scaledWarn, step.activeTicks, type.getId(), true);
                }
                spiralAngle += 0.45f; // ~26° per fire cycle - visible rotation
            }
            default -> {
                float ringStart = step.ringStartAngleRad >= 0f ? step.ringStartAngleRad : 0f;
                PatternEngine.fireRing(bullets, bx, by,
                        scaledArms, effSpeed, difficulty, type, vis, hit,
                        step.bulletLifetimeTicks, angV, ringStart);
            }
        }
    }

    private static int resolveBulletLifetime(PatternStep step, int def) {
        if (step == null || step.bulletLifetimeTicks <= 0)
            return def;
        return step.bulletLifetimeTicks;
    }

    private static float bulletVis(PatternStep step) {
        return step.bulletScale > 0.01f ? step.bulletScale : 1f;
    }

    private static float bulletHit(PatternStep step) {
        if (step.hitboxScale > 0.01f)
            return step.hitboxScale;
        return bulletVis(step) > 1.25f ? 0.42f : 1f;
    }

    private float sampleSpeed(PatternStep step) {
        return sampleFloatRange(step.speed, step.speedMin, step.speedMax, 0.01f);
    }

    private float sampleSpread(PatternStep step) {
        return sampleFloatRange(step.spread, step.spreadMin, step.spreadMax, 0f);
    }

    private int sampleArms(PatternStep step) {
        if (step.armsMin > 0 && step.armsMax > 0) {
            int lo = Math.min(step.armsMin, step.armsMax);
            int hi = Math.max(step.armsMin, step.armsMax);
            return lo + random.nextInt(hi - lo + 1);
        }
        return Math.max(1, step.arms);
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

    private int computeAttackCooldown(PatternStep step, String patUpper) {
        AttackScalingProfile profile = resolveScalingProfile(step, patUpper);
        float dens = bossDensityMult();
        if (step.densityScale > 0.01f)
            dens *= step.densityScale;
        float effectiveDens = weightedDifficultyMult(dens, resolveCooldownWeight(step, profile));
        int cd = Math.max(1, (int) (step.cooldown / effectiveDens));
        int minCd = Math.max(profile.minCooldown(), step.minCooldown);
        // LASER_BEAM is a rapid faux-laser burst; keep stream readability.
        if ("LASER_BEAM".equals(patUpper))
            minCd = Math.max(minCd, BullethellConfig.BOSS_LASER_BEAM_MIN_COOLDOWN.get());
        cd = Math.max(minCd, cd);
        float pressure = bulletPressure();
        float soft = resolvePressureSoftCap(step, profile);
        int maxBoost = resolvePressureCooldownBoost(step, profile);
        if (maxBoost > 0 && pressure > soft) {
            float t = (pressure - soft) / Math.max(0.01f, (1f - soft));
            cd += Math.round(Math.min(1f, t) * maxBoost);
        }
        return cd;
    }

    private AttackScalingProfile resolveScalingProfile(PatternStep step, String patUpper) {
        String profileName = step.scalingProfile == null ? "" : step.scalingProfile.trim().toUpperCase();
        if (profileName.isEmpty() || "AUTO".equals(profileName)) {
            return switch (patUpper) {
                case "SPIRAL", "RING_OFFSET", "LASER_ROTATING" -> SCALE_GEOMETRY;
                case "AIMED", "BOUNCE", "LASER" -> SCALE_PRECISION;
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
        float clamped = Math.max(0.10f, Math.min(2.0f, weight));
        return 1f + (mult - 1f) * clamped;
    }

    private float bulletPressure() {
        return Math.min(1f, bullets.getActiveCount() / (float) BulletPool.ENEMY_CAPACITY);
    }

    private float resolveArmsWeight(PatternStep step, AttackScalingProfile profile) {
        return step.armsDifficultyWeight > 0.01f ? step.armsDifficultyWeight : profile.armsWeight();
    }

    private float resolveSpeedWeight(PatternStep step, AttackScalingProfile profile) {
        return step.speedDifficultyWeight > 0.01f ? step.speedDifficultyWeight : profile.speedWeight();
    }

    private float resolveCooldownWeight(PatternStep step, AttackScalingProfile profile) {
        return step.cooldownDifficultyWeight > 0.01f ? step.cooldownDifficultyWeight : profile.cooldownWeight();
    }

    private float resolvePressureSoftCap(PatternStep step, AttackScalingProfile profile) {
        float s = step.pressureSoftCap > 0f ? step.pressureSoftCap : profile.pressureSoftCap();
        return Math.max(0.35f, Math.min(0.95f, s));
    }

    private float resolvePressureArmDrop(PatternStep step, AttackScalingProfile profile) {
        float d = step.pressureArmDrop > 0f ? step.pressureArmDrop : profile.pressureArmDrop();
        return Math.max(0f, Math.min(0.90f, d));
    }

    private int resolvePressureCooldownBoost(PatternStep step, AttackScalingProfile profile) {
        return step.pressureCooldownBoost > 0 ? step.pressureCooldownBoost : profile.pressureCooldownBoost();
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

    /** First attack step of Scarlet Meister phase (JSON {@code bulletType} / scales apply to scripted shots). */
    private PatternStep meisterPatternStep() {
        PhaseDefinition p = currentBossPhase();
        if (p == null || p.attacks.isEmpty())
            return null;
        return p.attacks.get(0);
    }

    /**
     * Scarlet Meister: shotgun opener (fast wide fan), quick CW/CCW spin bursts, brief pause,
     * mirrored shotgun + reversed spin order, then rest. Boss movement from phase JSON ({@code CIRCLE}).
     */
    private void tickScarletMeister() {
        PlayerState2D aim = getBossAimTarget();
        float dens = bossDensityMult();
        float spdRatio = bossSpeedMult() / difficulty.speedMult;
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

    /** Wide fast fan toward aim; {@code mirrorFan} flips left/right spread for the second pass. */
    private void fireMeisterShotgun(PlayerState2D aim, float baseSpeed, float dens, boolean mirrorFan) {
        PatternStep step = meisterPatternStep();
        BulletType mainType = step != null ? bulletTypeByName(step.bulletType) : BulletType.SCARLET_LARGE;
        if (mainType == BulletType.ORB)
            mainType = BulletType.SCARLET_LARGE;
        float vis = step != null ? bulletVis(step) : 1.42f;
        float hit = step != null ? bulletHit(step) : 0.48f;
        int lifeMain = resolveBulletLifetime(step, 175);
        float baseAngle = (float) Math.atan2(aim.y - bossY, aim.x - bossX);
        int arms = Math.min(11, Math.max(7, (int) (7 + dens * 1.1f)));
        float spread = 0.52f + 0.06f * Math.min(2.2f, dens);
        float gm = BullethellConfig.GLOBAL_ENEMY_BULLET_SPEED_MULT.get();
        float spd = baseSpeed * 1.12f * difficulty.speedMult * gm;
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
        float mspd = baseSpeed * 0.95f * difficulty.speedMult * gm;
        float mspread = 0.11f;
        float mmid = (mentos - 1) / 2f;
        for (int m = 0; m < mentos; m++) {
            float ang = baseAngle + dir * (m - mmid) * mspread;
            float vx = (float) Math.cos(ang) * mspd;
            float vy = (float) Math.sin(ang) * mspd;
            bullets.spawn(bossX, bossY, vx, vy, BulletType.SCARLET_MENTOS.getId(), lifeM, 1f, 0.88f, 0f);
        }
    }

    private void fireMeisterSpinBurst(float baseAngle, float baseSpeed, float dens) {
        PatternStep step = meisterPatternStep();
        BulletType mainType = step != null ? bulletTypeByName(step.bulletType) : BulletType.SCARLET_LARGE;
        if (mainType == BulletType.ORB)
            mainType = BulletType.SCARLET_LARGE;
        float vis = step != null ? bulletVis(step) : 1.38f;
        float hit = step != null ? bulletHit(step) : 0.46f;
        int life = resolveBulletLifetime(step, 200);
        int arms = Math.min(7, Math.max(3, (int) (3 + dens * 0.9f)));
        float spread = 0.32f;
        float gm = BullethellConfig.GLOBAL_ENEMY_BULLET_SPEED_MULT.get();
        for (int i = 0; i < arms; i++) {
            float ang = baseAngle + (i - (arms - 1) / 2f) * spread;
            float vx = (float) Math.cos(ang) * baseSpeed * difficulty.speedMult * gm;
            float vy = (float) Math.sin(ang) * baseSpeed * difficulty.speedMult * gm;
            bullets.spawn(bossX, bossY, vx, vy, mainType.getId(), life, vis, hit, 0f);
        }
    }

    private void fireBouncingAimed(PatternStep step, BulletType type, PlayerState2D target,
            int scaledArms, float spread, float effSpeed, float visScale, float hitScale,
            float bx, float by) {
        int allowedBounces = Math.max(0, step.bounceCount);
        float damping = Math.max(0.05f, Math.min(1.0f, step.bounceDamping));
        float baseAngle = (float) Math.atan2(target.y - by, target.x - bx);
        float halfSpread = spread * (scaledArms - 1) / 2f;
        float gm = BullethellConfig.GLOBAL_ENEMY_BULLET_SPEED_MULT.get();
        int life = resolveBulletLifetime(step, 250);
        float angV = step.bulletAngularVelocity;
        for (int i = 0; i < scaledArms; i++) {
            float angle = baseAngle - halfSpread + spread * i;
            float vx = (float) Math.cos(angle) * effSpeed * difficulty.speedMult * gm;
            float vy = (float) Math.sin(angle) * effSpeed * difficulty.speedMult * gm;
            int slot = bullets.spawn(bx, by, vx, vy, type.getId(), life, visScale, hitScale, angV);
            if (slot >= 0) {
                bounceRemaining[slot] = allowedBounces;
                bounceDamping[slot] = damping;
            }
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
            boolean hit = false;

            if (x <= minX && vx < 0f) {
                x = minX;
                vx = -vx * bounceDamping[i];
                hit = true;
            } else if (x >= maxX && vx > 0f) {
                x = maxX;
                vx = -vx * bounceDamping[i];
                hit = true;
            }
            if (y <= minY && vy < 0f) {
                y = minY;
                vy = -vy * bounceDamping[i];
                hit = true;
            } else if (y >= maxY && vy > 0f) {
                y = maxY;
                vy = -vy * bounceDamping[i];
                hit = true;
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

    private void checkPlayerBulletsVsBoss(BulletPool pb, PlayerState2D ps) {
        if (bossHp <= 0)
            return;
        int damage = bossBulletDamage(ps);

        for (int i = 0; i < pb.getCapacity(); i++) {
            if (!pb.isActive(i))
                continue;
            float dx = pb.getX(i) - bossX;
            float dy = pb.getY(i) - bossY;
            if (dx * dx + dy * dy <= BOSS_HIT_RADIUS * BOSS_HIT_RADIUS) {
                pb.deactivate(i);
                bossHp = Math.max(0, bossHp - damage);
                score.addScore(damage * 8L);
                checkBossPhaseTransition();
            }
        }
    }

    /**
     * Per-bullet boss damage keyed to power tier.
     * Lower tiers fire fewer bullets so each bullet hits harder, keeping DPS
     * relatively flat.
     * Approximate unfocused DPS: ~40 (tier 0) → ~70 (tier 4). Focused: ~40 → ~93.
     */
    private int bossBulletDamage(PlayerState2D ps) {
        int lv = ps.powerLevel();
        if (ps.focused) {
            return switch (lv) {
                case 0 -> 12;
                case 1 -> 7;
                case 2 -> 5;
                case 3 -> 4;
                default -> 4; // tier 4
            };
        } else {
            return switch (lv) {
                case 0 -> 8;
                case 1 -> 5;
                case 2 -> 3;
                case 3 -> 2;
                default -> 2; // tier 4
            };
        }
    }

    /**
     * Per-hit damage vs {@link EnemyPool} fairies from a single player shot bullet.
     * Mirrors {@link #bossBulletDamage}: low tiers fire few shots so each hits harder;
     * high tiers fire many spread shots so each stays light, keeping fairy clear rate
     * sane on dense TH-style waves without relying on a tiny bullet pool.
     * <p>
     * Rough unfocused DPS to one target (volley every 3 ticks): tier0 ~1.3, tier1 ~2.0,
     * tier2–4 ~1.7–2.3. Focused (every 5 ticks): ~1.2–1.4.
     */
    private int fairyBulletDamage(PlayerState2D ps) {
        int lv = ps.powerLevel();
        if (ps.focused) {
            return switch (lv) {
                case 0 -> 6;
                case 1 -> 2;
                case 2 -> 1;
                case 3 -> 1;
                default -> 1;
            };
        }
        return switch (lv) {
            case 0 -> 4;
            case 1 -> 2;
            case 2 -> 1;
            case 3 -> 1;
            default -> 1;
        };
    }

    private void checkBossPhaseTransition() {
        if (phaseTransitionTimer > 0 || pendingNextPhase >= 0)
            return; // already transitioning
        // Trigger at 0 HP *or* when the threshold fraction is crossed (e.g. 20%
        // remaining).
        float threshold = currentBossPhase().hpThresholdFraction;
        boolean belowThreshold = threshold > 0 && bossHp <= (int) (bossMaxHp * threshold);
        if (bossHp > 0 && !belowThreshold)
            return;

        GameEvent spellResult = spellcard.onPhaseCleared();
        boolean wasSpellCard = currentBossPhase().isSpellCard;
        boolean captured = spellResult == GameEvent.SPELL_CAPTURED;
        if (wasSpellCard) {
            spellsAttempted++;
            if (captured)
                spellsCaptured++;
        }
        if (captured)
            score.onSpellCapture(spellcard.getBonusValue());
        pendingEvents.add(spellResult);
        pendingEvents.add(GameEvent.PHASE_CHANGE);
        dropBossPhaseItems(wasSpellCard, captured);
        bullets.clearAll();
        lasers.clearAll();

        int nextPhase = bossPhase + 1;
        if (nextPhase >= activeBossPhases.size()) {
            won = true;
            over = true;
            return;
        }

        // Queue next phase - boss will drift to centre over 50 ticks first.
        pendingNextPhase = nextPhase;
        phaseTransitionTimer = 50;
    }

    /**
     * Scatter point items from the boss position when a phase is cleared.
     * Mirrors TH7/TH8: cancelled bullets become star/point items.
     * - NonSpell cleared → 4 point items scattered around boss
     * - Spell captured → 8 point items (bigger reward, like bullet cancellation)
     * - Spell failed → nothing (no reward for failing the card)
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
        phaseStartTick = bossTick; // movement formula resets from centre each phase
        meisterSubPhase = 0;
        meisterTimer = 0;
        meisterStreamAngle = 0f;
        patternCooldown = 0;
        bossBurstVolleysRemaining = 0;
        bossBurstStep = null;
        bullets.clearAll();

        PhaseDefinition phase = activeBossPhases.get(phaseIndex);
        bossHp = phase.hp;
        bossMaxHp = phase.hp;

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

        if (phase.isSpellCard) {
            int diffIdx = Math.min(difficulty.ordinal(), phase.spellDurationTicks.length - 1);
            int duration = phase.spellDurationTicks[diffIdx];
            if (duration > 0)
                spellcard.start(duration, phase.spellBonus);
        }
    }

    // ================================================================ SHARED
    // SYSTEMS

    private void tickPlayerShots(PlayerState2D ps, BulletPool pb) {
        if (!ps.shooting) {
            ps.shotCooldown = 0;
            return;
        }
        if (ps.shotCooldown > 0) {
            ps.shotCooldown--;
            return;
        }

        ps.shotCooldown = ps.focused
                ? PlayerState2D.SHOT_COOLDOWN_FOCUSED
                : PlayerState2D.SHOT_COOLDOWN_NORMAL;

        int t = BulletType.PLAYER_SHOT.getId();
        float px = ps.x;
        float py = ps.y - 4;
        int lv = ps.powerLevel();

        if (ps.focused) {
            fireFocusedShot(px, py, t, lv, pb);
        } else {
            fireNormalShot(px, py, t, lv, pb);
        }
    }

    // Normal spread shot - spawns into the caller's bullet pool
    private void fireNormalShot(float px, float py, int t, int lv, BulletPool pb) {
        switch (lv) {
            case 0 ->
                pb.spawn(px, py, 0f, -16f, t, 55);
            case 1 -> {
                pb.spawn(px, py, 0f, -16f, t, 55);
                pb.spawn(px - 8, py, -1.6f, -16f, t, 55);
                pb.spawn(px + 8, py, 1.6f, -16f, t, 55);
            }
            case 2 -> {
                pb.spawn(px, py, 0f, -16f, t, 55);
                pb.spawn(px - 10, py, -2.0f, -16f, t, 55);
                pb.spawn(px + 10, py, 2.0f, -16f, t, 55);
                pb.spawn(px - 22, py, -4.4f, -14f, t, 55);
                pb.spawn(px + 22, py, 4.4f, -14f, t, 55);
            }
            case 3 -> {
                pb.spawn(px, py, 0f, -16f, t, 55);
                pb.spawn(px - 10, py, -2.0f, -16f, t, 55);
                pb.spawn(px + 10, py, 2.0f, -16f, t, 55);
                pb.spawn(px - 22, py, -4.4f, -14f, t, 55);
                pb.spawn(px + 22, py, 4.4f, -14f, t, 55);
                pb.spawn(px - 34, py, -7.6f, -10f, t, 55);
                pb.spawn(px + 34, py, 7.6f, -10f, t, 55);
            }
            default -> {
                pb.spawn(px, py, 0f, -16f, t, 55);
                pb.spawn(px - 10, py, -2.0f, -16f, t, 55);
                pb.spawn(px + 10, py, 2.0f, -16f, t, 55);
                pb.spawn(px - 22, py, -4.4f, -14f, t, 55);
                pb.spawn(px + 22, py, 4.4f, -14f, t, 55);
                pb.spawn(px - 34, py, -8.0f, -12f, t, 55);
                pb.spawn(px + 34, py, 8.0f, -12f, t, 55);
            }
        }
    }

    // Focused shot - spawns into the caller's bullet pool
    private void fireFocusedShot(float px, float py, int t, int lv, BulletPool pb) {
        switch (lv) {
            case 0 ->
                pb.spawn(px, py, 0f, -20f, t, 45);
            case 1 -> {
                pb.spawn(px, py, 0f, -20f, t, 45);
                pb.spawn(px - 5, py, 0f, -18f, t, 45);
                pb.spawn(px + 5, py, 0f, -18f, t, 45);
            }
            case 2 -> {
                pb.spawn(px, py, 0f, -20f, t, 45);
                pb.spawn(px - 5, py, 0f, -20f, t, 45);
                pb.spawn(px + 5, py, 0f, -20f, t, 45);
                pb.spawn(px - 13, py, 0f, -18f, t, 45);
                pb.spawn(px + 13, py, 0f, -18f, t, 45);
            }
            case 3 -> {
                pb.spawn(px, py, 0f, -20f, t, 45);
                pb.spawn(px - 5, py, 0f, -20f, t, 45);
                pb.spawn(px + 5, py, 0f, -20f, t, 45);
                pb.spawn(px - 13, py, 0f, -18f, t, 45);
                pb.spawn(px + 13, py, 0f, -18f, t, 45);
                pb.spawn(px - 22, py, 0f, -18f, t, 45);
                pb.spawn(px + 22, py, 0f, -18f, t, 45);
            }
            default -> {
                pb.spawn(px, py, 0f, -20f, t, 45);
                pb.spawn(px - 5, py, 0f, -20f, t, 45);
                pb.spawn(px + 5, py, 0f, -20f, t, 45);
                pb.spawn(px - 13, py, 0f, -18f, t, 45);
                pb.spawn(px + 13, py, 0f, -18f, t, 45);
                pb.spawn(px - 22, py, 0f, -20f, t, 45);
                pb.spawn(px + 22, py, 0f, -20f, t, 45);
            }
        }
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
            float dx = bullets.getX(i) - ps.x;
            float dy = bullets.getY(i) - ps.y;
            float distSq = dx * dx + dy * dy;
            BulletType bt = BulletType.fromId(bullets.getType(i));
            float bulletR = bt.radius * bullets.getHitScale(i) * bt.hitboxCollisionMul;
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
                ps.addStoredChargeProgress(20 * 3.0 / 2000.0);
                score.onGraze();
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
                pendingEvents.add(GameEvent.HIT);
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
        for (int i = 0; i < ItemPool.CAPACITY; i++) {
            if (!items.isActive(i) || !items.isAttracting(i))
                continue;

            float ix = items.getX(i);
            float iy = items.getY(i);

            // Find nearest player among host + coop fairly
            PlayerState2D nearest = null;
            float bestD2 = Float.MAX_VALUE;
            
            for (PlayerState2D cp : getAllPlayerStates()) {
                if (cp.lives < 0) continue;
                float d2 = (cp.x - ix) * (cp.x - ix) + (cp.y - iy) * (cp.y - iy);
                if (d2 < bestD2) {
                    bestD2 = d2;
                    nearest = cp;
                }
            }

            if (nearest != null && bestD2 < (ItemPool.ATTRACT_SPEED * ItemPool.ATTRACT_SPEED)) {
                collectItem(i, nearest);
            } else if (nearest != null) {
                float dx = nearest.x - ix, dy = nearest.y - iy;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                items.setX(i, ix + dx / dist * ItemPool.ATTRACT_SPEED);
                items.setY(i, iy + dy / dist * ItemPool.ATTRACT_SPEED);
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

        switch (type) {
            case ItemPool.TYPE_POINT -> score.addScore(pointItemScoreAtHeight(itemY));
            case ItemPool.TYPE_POWER -> {
                if (ps.power >= PlayerState2D.MAX_POWER) {
                    // Item stays TYPE_POWER in the world for co-op; max-power
                    // collector gets point value instead of a wasted pickup.
                    score.addScore(pointItemScoreAtHeight(itemY));
                } else {
                    score.onPowerItemPickup();
                    ps.power = Math.min(PlayerState2D.MAX_POWER, ps.power + 4);
                }
            }
            case ItemPool.TYPE_FULL_POWER -> {
                if (ps.power >= PlayerState2D.MAX_POWER) {
                    score.addScore(pointItemScoreAtHeight(itemY));
                } else {
                    score.onPowerItemPickup();
                    ps.power = PlayerState2D.MAX_POWER;
                }
            }
            case ItemPool.TYPE_ONE_UP -> ps.lives++;
            case ItemPool.TYPE_BOMB -> ps.bombs = Math.min(ps.bombs + 1, 9);
        }
        pendingEvents.add(GameEvent.ITEM_PICKUP);
    }

    /** Same height→score mapping as a {@link ItemPool#TYPE_POINT} pickup. */
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
         * PoFV: colored hold bar picks release level; gray stock pays (level − 1)
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
     * Sanae miracles. {@code level} is 1–3 from the hold meter + stock rules.
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
                    case 2 -> 14; // deliberately below half of L3 so 2×L2 < 1×L3 for same stock
                    default -> 26;
                };
            }
            case "sakuya" -> {
                // PoFV: L1 Jack the Ripper (knife stream); L2 denser volley + radial burst;
                // L3 Private Square–style time stop + knife ring for resume wave.
                if (level >= 3) {
                    timeStopTicks = 60;
                    timeStopOwner = uuid;
                    fireSakuyaKnifeRing(uuid, ps, 20);
                } else if (level >= 2) {
                    fireSakuyaJackRipper(uuid, ps, 18, 0.12f);
                    PatternEngine.fireRing(getBulletPool(uuid), ps.x, ps.y, 14, 3.4f, difficulty, BulletType.KNIFE);
                } else {
                    fireSakuyaJackRipper(uuid, ps, 10, 0.08f);
                }
            }
            case "reimu" -> {
                // PoFV: L1 Hakurei Amulet; L2 Yin-Yang Sign (dual rings + amulets); L3
                // Dream Seal (denser rings + more homing amulets).
                fireReimuChargeAttack(uuid, ps, level);
            }
            case "sanae" -> {
                // TH19-style: miracle bullet erase + wind/blessing burst (wiki page sparse).
                fireSanaeMiracle(uuid, ps, level);
            }
            default -> {
            }
        }
    }

    /** PoFV Hakurei Amulet / Yin-Yang Sign / Dream Seal approximations. */
    private void fireReimuChargeAttack(UUID uuid, PlayerState2D ps, int level) {
        BulletPool pb = getBulletPool(uuid);
        float px = ps.x;
        float py = ps.y - 8f;
        if (level <= 1) {
            fireHomingOrbs(ps, pb, 4);
            return;
        }
        if (level == 2) {
            PatternEngine.fireRing(pb, px, py, 12, 3.6f, difficulty, BulletType.RICE);
            PatternEngine.fireRing(pb, px, py, 10, 2.3f, difficulty, BulletType.STAR);
            fireHomingOrbs(ps, pb, 5);
            return;
        }
        PatternEngine.fireRing(pb, px, py, 18, 4.0f, difficulty, BulletType.RICE);
        PatternEngine.fireRing(pb, px, py, 16, 2.6f, difficulty, BulletType.ORB);
        fireHomingOrbs(ps, pb, 10);
    }

    private void fireHomingOrbs(PlayerState2D ps, BulletPool pb, int count) {
        for (int i = 0; i < count; i++) {
            float angle = (float) (i * Math.PI * 2 / Math.max(1, count));
            float vx = (float) Math.cos(angle) * 4f;
            float vy = (float) Math.sin(angle) * 4f;
            pb.spawn(ps.x, ps.y, vx, vy, BulletType.HOMING_ORB.getId(), 200);
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
            float sp = 11.5f * difficulty.speedMult;
            pb.spawn(ps.x, ps.y, (float) Math.cos(ang) * sp, (float) Math.sin(ang) * sp,
                    BulletType.KNIFE.getId(), 140);
        }
    }

    /** Ring of knives used with Sakuya L3 time stop (launches when time resumes). */
    private void fireSakuyaKnifeRing(UUID uuid, PlayerState2D ps, int count) {
        BulletPool pb = getBulletPool(uuid);
        float step = (float) (Math.PI * 2.0 / count);
        for (int i = 0; i < count; i++) {
            float ang = step * i;
            float dist = 22f + random.nextFloat() * 8f;
            float kx = ps.x + (float) Math.cos(ang) * dist;
            float ky = ps.y + (float) Math.sin(ang) * dist;
            pb.spawn(kx, ky, 0f, -12f, BulletType.KNIFE.getId(), 120);
        }
    }

    /** TH19-inspired: bullet miracle + outward wind (stars / bubbles). */
    private void fireSanaeMiracle(UUID uuid, PlayerState2D ps, int level) {
        float cx = ps.x;
        float cy = ps.y;
        if (level <= 1) {
            clearBulletsInRadius(cx, cy, 72f, ps);
            PatternEngine.fireRing(getBulletPool(uuid), cx, cy - 6f, 10, 3.2f, difficulty, BulletType.BUBBLE);
            return;
        }
        if (level == 2) {
            clearBulletsInRadius(cx, cy, 115f, ps);
            PatternEngine.fireSpiral(getBulletPool(uuid), cx, cy - 6f, 0f, 10, 3.6f, difficulty, BulletType.STAR);
            PatternEngine.fireRing(getBulletPool(uuid), cx, cy - 6f, 12, 2.8f, difficulty, BulletType.BUBBLE);
            return;
        }
        clearBulletsInRadius(cx, cy, 200f, ps);
        PatternEngine.fireRing(getBulletPool(uuid), cx, cy - 6f, 22, 4.2f, difficulty, BulletType.STAR);
        PatternEngine.fireRing(getBulletPool(uuid), cx, cy - 6f, 16, 3.0f, difficulty, BulletType.BUBBLE);
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

        if (bossMaxHp > 0) {
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
            if (!pb.isActive(i) || pb.getType(i) != BulletType.HOMING_ORB.getId())
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
        java.util.List<PlayerState2D> all = new java.util.ArrayList<>();
        all.add(player);
        all.addAll(coopPlayers.values());
        return all;
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

        // Drop power items scattered around the death position
        int powerBefore = ps.power;
        ps.power = Math.max(0, ps.power - rules.deathPowerLoss);
        int lost = powerBefore - ps.power;
        int dropCount = lost / 8;
        for (int d = 0; d < dropCount; d++) {
            float ox = (random.nextFloat() - 0.5f) * 80f;
            float oy2 = (random.nextFloat() - 0.5f) * 60f;
            items.spawn(ps.x + ox, ps.y + oy2, ItemPool.TYPE_POWER);
        }

        ps.personalEvents.add(GameEvent.DEATH);

        if (ps.lives > 0) {
            ps.lives--;
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
     * @param gaugeRecipient if non-null, PoFV-style charge is awarded for each bullet cleared (Sanae skill).
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

    /** Activate a bomb for the specified participant. */
    public void activateBomb(UUID uuid) {
        PlayerState2D ps = getPlayerState(uuid);
        if (ps == null)
            return;
        boolean dbg = BHDebugMode.isGodMode(uuid);
        if (!dbg && ps.bombs <= 0)
            return;
        if (!dbg)
            ps.bombs--;
        else
            ps.bombs = 9;
        bullets.clearAll();
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
        if (arenaPhase == ArenaPhase.BOSS)
            spellcard.fail();
        if (ps.deathPendingTicks > 0)
            ps.deathPendingTicks = 0;

        // Collect all items currently on screen via attraction (not instant teleport)
        for (int i = 0; i < ItemPool.CAPACITY; i++) {
            if (items.isActive(i)) {
                items.setAttracting(i, true);
            }
        }

        pendingEvents.add(GameEvent.BOMB_USED);
    }

    // ---------------------------------------------------------------- helpers

    private PhaseDefinition currentBossPhase() {
        return activeBossPhases.get(Math.min(bossPhase, activeBossPhases.size() - 1));
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
            return next.isSpellCard ? next.spellName : "";
        }
        PhaseDefinition cur = currentBossPhase();
        return cur.isSpellCard ? cur.spellName : "";
    }

    /**
     * True when the currently active phase IS a spell card (not during
     * declaration).
     */
    public boolean isActiveSpellCard() {
        return arenaPhase == ArenaPhase.BOSS && !isDeclaring()
                && currentBossPhase().isSpellCard;
    }

    /**
     * Returns the music track ID for the current stage/boss phase.
     * Used by ArenaStatePacket.
     */
    public String getCurrentMusicTrackId() {
        if (arenaPhase == ArenaPhase.WAVES) {
            return (stage.stageMusic != null) ? stage.stageMusic : "";
        }
        PhaseDefinition phase = currentBossPhase();
        return (phase.music != null) ? phase.music : "";
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
                // Freeze the knife in place
                pb.setVx(i, 0);
                pb.setVy(i, 0);
            }
        }
    }

    /** Restores velocity to knives when time resumes. */
    private void resumeFrozenBullets() {
        // Restore Sakuya's knives to their launch speed
        for (int i = 0; i < playerBullets.getCapacity(); i++) {
            if (playerBullets.isActive(i) && isSakuyaBladeBullet(playerBullets.getType(i))) {
                playerBullets.setVy(i, -12); // launch upwards
            }
        }
        for (BulletPool pb : coopBullets.values()) {
            for (int i = 0; i < pb.getCapacity(); i++) {
                if (pb.isActive(i) && isSakuyaBladeBullet(pb.getType(i))) {
                    pb.setVy(i, -12);
                }
            }
        }
    }

    /** Kunai + knife share the same hit profile; both participate in Sakuya time stop. */
    private static boolean isSakuyaBladeBullet(int typeId) {
        return typeId == BulletType.KUNAI.getId() || typeId == BulletType.KNIFE.getId();
    }

    private static BulletType bulletTypeByName(String name) {
        if (name == null)
            return BulletType.ORB;
        try {
            return BulletType.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return BulletType.ORB;
        }
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
        return "sakuya".equals(getCharacterId(uuid));
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
