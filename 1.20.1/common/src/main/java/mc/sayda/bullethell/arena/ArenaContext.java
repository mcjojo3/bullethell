package mc.sayda.bullethell.arena;

import mc.sayda.bullethell.boss.BossDefinition;
import mc.sayda.bullethell.boss.BossLoader;
import mc.sayda.bullethell.boss.FairyWaveLoader;
import mc.sayda.bullethell.boss.PatternStep;
import mc.sayda.bullethell.boss.PhaseDefinition;
import mc.sayda.bullethell.boss.RulesetConfig;
import mc.sayda.bullethell.boss.StageDefinition;
import mc.sayda.bullethell.boss.StageLoader;
import mc.sayda.bullethell.boss.WaveDefinition;
import mc.sayda.bullethell.boss.WaveEnemy;
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
    /**
     * bossTick value at the start of the current phase - keeps movement continuous.
     */
    private int phaseStartTick = 0;
    private int attackIndex = 0;

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

    /** Add a co-op participant. Called when another player joins the match. */
    public void addCoopPlayer(UUID uuid, mc.sayda.bullethell.boss.CharacterDefinition charDef) {
        int startLives = (rules.startingLives >= 0) ? rules.startingLives : charDef.startingLives;
        int startBombs = (rules.startingBombs >= 0) ? rules.startingBombs : charDef.startingBombs;
        PlayerState2D ps = new PlayerState2D(charDef.hitRadius, charDef.grazeRadius,
                charDef.pickupRadius, charDef.speedNormal, charDef.speedFocused,
                charDef.chargeRateShooting, charDef.chargeRateIdle, charDef.chargeRateCharging,
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
        dialogScriptByPlayer.remove(uuid);
        dialogIndexByPlayer.remove(uuid);
        dialogTicksLeftByPlayer.remove(uuid);
        dialogReadyByPlayer.remove(uuid);
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

    /** Start the default stage (stage_1) at NORMAL difficulty with Reimu. */
    public ArenaContext(UUID playerUuid, DifficultyConfig difficulty) {
        this(playerUuid, difficulty, "stage_1", "reimu");
    }

    /** Start a specific stage with the default character. */
    public ArenaContext(UUID playerUuid, DifficultyConfig difficulty, String stageId) {
        this(playerUuid, difficulty, stageId, "reimu");
    }

    /**
     * Start a specific stage with a specific character.
     *
     * @param stageId     file name (without .json) under
     *                    {@code data/bullethell/stages/}
     * @param characterId file name (without .json) under
     *                    {@code data/bullethell/characters/}
     */
    public ArenaContext(UUID playerUuid, DifficultyConfig difficulty, String stageId, String characterId) {
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
        this.stage = StageLoader.load(stageId);
        this.boss = BossLoader.load(stage.bossId);
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
        int startLives = (rules.startingLives >= 0) ? rules.startingLives : charDef.startingLives;
        int startBombs = (rules.startingBombs >= 0) ? rules.startingBombs : charDef.startingBombs;
        player = new PlayerState2D(charDef.hitRadius, charDef.grazeRadius, charDef.pickupRadius,
                charDef.speedNormal, charDef.speedFocused,
                charDef.chargeRateShooting, charDef.chargeRateIdle, charDef.chargeRateCharging,
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
    }

    private void tickStage() {
        tickEnemyAI();
        tickWaves();
        checkWavesComplete();
    }

    private void tickSkillGauge(PlayerState2D ps) {
        if (ps.lives < 0)
            return;

        // 1. Passive Regeneration
        // In TH19, gauge fills faster when NOT shooting
        float gain = ps.shooting ? ps.chargeRateShooting : ps.chargeRateIdle;

        // Sakuya's Time Stop pauses other players' gauges if freezer is active
        if (timeStopTicks > 0)
            return;

        // 2. Charging Logic (Holding X)
        if (ps.isCharging) {
            gain += ps.chargeRateCharging;
        }

        ps.skillGauge = Math.min(PlayerState2D.MAX_GAUGE, ps.skillGauge + (int) gain);

        // 3. Update Discrete Levels
        if (ps.skillGauge >= 2000)
            ps.chargeLevel = 4;
        else if (ps.skillGauge >= 1000)
            ps.chargeLevel = 3;
        else if (ps.skillGauge >= 500)
            ps.chargeLevel = 2;
        else if (ps.skillGauge >= 200)
            ps.chargeLevel = 1;
        else
            ps.chargeLevel = 0;
    }

    // ================================================================ WAVE PHASE

    /**
     * Compression factor: higher difficulties shrink the gap between waves.
     * EASY=0.80, NORMAL=1.00, HARD=1.25, LUNATIC=1.55 (boss patterns use separate creep)
     */
    private float waveTimingMult() {
        return switch (difficulty) {
            case EASY -> 0.80f;
            case NORMAL -> 1.00f;
            case HARD -> 1.25f;
            case LUNATIC -> 1.55f;
        };
    }

    /**
     * Pre-expand all stage waves (including waveRef templates) into a flat list
     * sorted by absolute spawn tick. Called once in the constructor.
     * Difficulty timing compression is baked in here so tickWaves() is trivial.
     */
    private void buildScheduledList() {
        float mult = waveTimingMult();
        List<ScheduledEnemy> list = new ArrayList<>();
        for (WaveDefinition wave : stage.waves) {
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
                    if (enemies.damage(j, 1))
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

        // Award Skill Gauge on kill
        ps.skillGauge = Math.min(PlayerState2D.MAX_GAUGE, ps.skillGauge + 30);

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
        float phaseCreep = 1f + Math.min(0.30f, bossPhase * 0.034f);
        float lunaticExtra = (difficulty == DifficultyConfig.LUNATIC) ? 1.12f : 1f;
        return difficulty.densityMult * phaseCreep * lunaticExtra;
    }

    /** Effective bullet speed multiplier for boss patterns (see {@link #bossDensityMult()}). */
    private float bossSpeedMult() {
        float phaseCreep = 1f + Math.min(0.22f, bossPhase * 0.026f);
        float lunaticExtra = (difficulty == DifficultyConfig.LUNATIC) ? 1.10f : 1f;
        return difficulty.speedMult * phaseCreep * lunaticExtra;
    }

    private void tickBossAI() {
        lasers.tick();
        PhaseDefinition phase = currentBossPhase();

        // Use ticks relative to this phase's start so movement begins at centre
        // every phase and never jumps when formulas change.
        int lt = bossTick - phaseStartTick;
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

        patternCooldown--;
        if (patternCooldown > 0)
            return;

        if (phase.attacks.isEmpty()) {
            patternCooldown = 20;
            return;
        }

        PatternStep step = phase.attacks.get(attackIndex % phase.attacks.size());
        attackIndex++;
        executeAttack(step);
        patternCooldown = Math.max(1, (int) (step.cooldown / bossDensityMult()));
    }

    private void executeAttack(PatternStep step) {
        BulletType type = bulletTypeByName(step.bulletType);
        float dens = bossDensityMult();
        float spdRatio = bossSpeedMult() / difficulty.speedMult;
        int scaledArms = Math.max(1, Math.round(step.arms * dens));
        float effSpeed = step.speed * spdRatio;
        switch (step.pattern == null ? "RING" : step.pattern.toUpperCase()) {
            case "SPIRAL" -> {
                PatternEngine.fireSpiral(bullets, bossX, bossY, spiralAngle,
                        scaledArms, effSpeed, difficulty, type);
                spiralAngle += (float) (Math.PI * 2.0 / scaledArms) * 0.15f;
            }
            case "AIMED" -> PatternEngine.fireAimed(bullets, bossX, bossY,
                    player.x, player.y, scaledArms, step.spread, effSpeed, difficulty, type);
            case "RING" -> PatternEngine.fireRing(bullets, bossX, bossY,
                    scaledArms, effSpeed, difficulty, type);
            case "SPREAD" -> PatternEngine.fireSpread(bullets, bossX, bossY,
                    scaledArms, effSpeed, difficulty, type);
            case "DENSE_RING" -> PatternEngine.fireDenseRing(bullets, bossX, bossY,
                    scaledArms, effSpeed, difficulty, type);
            case "LASER_BEAM" -> PatternEngine.fireLaserBeam(bullets, bossX, bossY,
                    player.x, player.y, scaledArms, effSpeed, difficulty, type);
            case "LASER" -> {
                // Single directional laser aimed at the player's current position (Master
                // Spark).
                float angle = (float) Math.atan2(player.y - bossY, player.x - bossX);
                int scaledWarn = Math.max(10, (int) (step.warnTicks / dens));
                lasers.spawn(bossX, bossY, angle, step.laserHalfWidth,
                        scaledWarn, step.activeTicks, type.getId(), false);
            }
            case "LASER_ROTATING" -> {
                // Bidirectional beams radiating in all directions (Non-Directional Laser).
                // Difficulty scaling: more beams + shorter warning on harder difficulties.
                // Normal: arms=8, warn=14 | Lunatic: arms=16, warn=7
                int scaledWarn = Math.max(6, (int) (step.warnTicks / dens));
                float angleStep = (float) (Math.PI * 2.0 / scaledArms);
                for (int i = 0; i < scaledArms; i++) {
                    lasers.spawn(bossX, bossY, spiralAngle + angleStep * i,
                            step.laserHalfWidth, scaledWarn, step.activeTicks, type.getId(), true);
                }
                spiralAngle += 0.45f; // ~26° per fire cycle - visible rotation
            }
            default -> PatternEngine.fireRing(bullets, bossX, bossY,
                    scaledArms, effSpeed, difficulty, type);
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
        if (nextPhase >= boss.phases.size()) {
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
        bullets.clearAll();

        PhaseDefinition phase = boss.phases.get(phaseIndex);
        bossHp = phase.hp;
        bossMaxHp = phase.hp;

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
        if (ps.deathPendingTicks > 0 || ps.invulnTicks > 0)
            return;

        float hr2 = ps.hitRadius * ps.hitRadius;
        float gr2 = ps.grazeRadius * ps.grazeRadius;

        for (int i = 0; i < bullets.getCapacity(); i++) {
            if (!bullets.isActive(i))
                continue;
            float dx = bullets.getX(i) - ps.x;
            float dy = bullets.getY(i) - ps.y;
            float distSq = dx * dx + dy * dy;

            if (distSq <= hr2) {
                bullets.deactivate(i);
                ps.deathPendingTicks = PlayerState2D.DEATH_BOMB_GRACE;
                ps.personalEvents.add(GameEvent.HIT);
                return;
            } else if (distSq <= gr2 && rules.grazeScoringEnabled) {
                ps.graze++;
                ps.skillGauge = Math.min(PlayerState2D.MAX_GAUGE, ps.skillGauge + 20); // Award Skill Gauge on Graze
                score.onGraze();
                ps.personalEvents.add(GameEvent.GRAZE);
                if (ps.graze % 50 == 0)
                    ps.personalEvents.add(GameEvent.GRAZE_CHAIN);
            }
        }
    }

    private void checkLasersVsPlayer(UUID uuid, PlayerState2D ps) {
        if (ps.deathPendingTicks > 0 || ps.invulnTicks > 0)
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
                ps.deathPendingTicks = PlayerState2D.DEATH_BOMB_GRACE;
                pendingEvents.add(GameEvent.HIT);
                return;
            }

            // 2. Graze Check (build gauge)
            if (dist - ps.grazeRadius < hw) {
                // Award small amount per tick while in beam vicinity
                ps.skillGauge = Math.min(PlayerState2D.MAX_GAUGE, ps.skillGauge + 2);
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

        float heightFrac = 1.0f - itemY / BulletPool.ARENA_H;
        switch (type) {
            case ItemPool.TYPE_POINT -> {
                int val = (int) (rules.pointItemMinValue +
                        (rules.pointItemMaxValue - rules.pointItemMinValue) * heightFrac);
                score.addScore(val);
            }
            case ItemPool.TYPE_POWER -> {
                score.onPowerItemPickup();
                ps.power = Math.min(PlayerState2D.MAX_POWER, ps.power + 4);
            }
            case ItemPool.TYPE_FULL_POWER -> {
                score.onPowerItemPickup();
                ps.power = PlayerState2D.MAX_POWER;
            }
            case ItemPool.TYPE_ONE_UP -> ps.lives++;
            case ItemPool.TYPE_BOMB -> ps.bombs = Math.min(ps.bombs + 1, 9);
        }
        pendingEvents.add(GameEvent.ITEM_PICKUP);
    }

    // ---------------------------------------------------------------- TH19
    // Abilities

    public void activateSkill(UUID uuid) {
        PlayerState2D ps = getPlayerState(uuid);
        if (ps == null || ps.lives < 0)
            return;

        int level = ps.chargeLevel;
        if (level < 1)
            return; // Need at least Lvl 1

        // Consume gauge based on level reached
        // Lvl 1: consume 200, Lvl 2: consume 500, Lvl 3: consume 1000
        int cost = level >= 3 ? 1000 : (level >= 2 ? 500 : 200);
        ps.skillGauge = Math.max(0, ps.skillGauge - cost);

        // Reset charge level after use
        ps.chargeLevel = 0;

        triggerCharacterSkill(uuid, ps, level);
        pendingEvents.add(GameEvent.SKILL_USED); // Use skill event for the distinct visual effect
    }

    private void triggerCharacterSkill(UUID uuid, PlayerState2D ps, int level) {
        String cid = getCharacterId(uuid);

        switch (cid) {
            case "marisa" -> {
                if (level >= 3) {
                    masterSparkTicks = 20; // 1 second stationary beam
                    masterSparkOwner = uuid;
                    masterSparkX = ps.x;
                    masterSparkY = Math.max(0f, ps.y - 32f);
                } else if (level >= 2) {
                    masterSparkTicks = 20; // 1 second burst
                    masterSparkOwner = uuid;
                    masterSparkX = ps.x;
                    masterSparkY = Math.max(0f, ps.y - 32f);
                }
            }
            case "sakuya" -> {
                if (level >= 3) {
                    timeStopTicks = 60; // 3 seconds freeze
                    timeStopOwner = uuid;
                } else if (level >= 2) {
                    // Knife freeze toss
                    fireTimeStopKnives(ps);
                }
            }
            case "reimu" -> {
                int count = level >= 3 ? 12 : 4;
                fireHomingOrbs(ps, count);
            }
            case "sanae" -> {
                float radius = level >= 3 ? 180f : 80f;
                clearBulletsInRadius(ps.x, ps.y, radius);
            }
        }
    }

    private void fireHomingOrbs(PlayerState2D ps, int count) {
        for (int i = 0; i < count; i++) {
            float angle = (float) (i * Math.PI * 2 / count);
            float vx = (float) Math.cos(angle) * 4f;
            float vy = (float) Math.sin(angle) * 4f;
            // PatternEngine needs a PLAYER bullet pool.
            playerBullets.spawn(ps.x, ps.y, vx, vy, BulletType.HOMING_ORB.getId(), 200);
        }
    }


    private void fireTimeStopKnives(PlayerState2D ps) {
        // Spawns knives that have 0 velocity for a while, then launch
        for (int i = 0; i < 16; i++) {
            float ang = (float) (random.nextFloat() * Math.PI * 2);
            float dist = 20f + random.nextFloat() * 20f;
            float kx = ps.x + (float) Math.cos(ang) * dist;
            float ky = ps.y + (float) Math.sin(ang) * dist;
            playerBullets.spawn(kx, ky, 0, -12, BulletType.KUNAI.getId(), 100);
        }
    }

    private void tickMasterSpark() {
        // Stationary vertical beam spawned in front of Marisa.
        float hw = 32f; // beam half-width (arena units)
        float x = masterSparkX;
        float y = masterSparkY;

        // Find owner to award gauge
        PlayerState2D ownerPs = getPlayerState(masterSparkOwner);

        // Kill enemies in beam
        for (int i = 0; i < EnemyPool.CAPACITY; i++) {
            if (!enemies.isActive(i))
                continue;
            float dist = getLaserDistance(enemies.getX(i), enemies.getY(i), x, y, -1.570796f, false);
            if (dist >= 0 && dist < hw) {
                if (enemies.damage(i, 5))
                    killEnemy(i, ownerPs != null ? ownerPs : player);
            }
        }

        // Damage boss in beam
        if (bossMaxHp > 0) {
            float dist = getLaserDistance(bossX, bossY, x, y, -1.570796f, false);
            if (dist >= 0 && dist < hw) {
                bossHp = Math.max(0, bossHp - 10);
                if (bossHp == 0)
                    checkBossPhaseTransition();
            }
        }

        // Clear bullets in beam
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
        masterSparkY = 0f;
    }

    private void applyDeath(UUID uuid) {
        PlayerState2D ps = getPlayerState(uuid);
        if (ps == null)
            return;

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

            clearBulletsInRadius(ps.x, ps.y, DEATH_CLEAR_RADIUS);
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
    private void clearBulletsInRadius(float cx, float cy, float r) {
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
        // Sanae's Miracle: award gauge for bullets cleared
        if (count > 0) {
            // Find Sanae player (simplified: award to host for now)
            player.skillGauge = Math.min(PlayerState2D.MAX_GAUGE, player.skillGauge + count * 2);
        }
    }

    /** Activate a bomb for the specified participant. */
    public void activateBomb(UUID uuid) {
        PlayerState2D ps = getPlayerState(uuid);
        if (ps == null || ps.bombs <= 0)
            return;
        ps.bombs--;
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
        return boss.phases.get(Math.min(bossPhase, boss.phases.size() - 1));
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
        if (pendingNextPhase >= 0 && pendingNextPhase < boss.phases.size()) {
            PhaseDefinition next = boss.phases.get(pendingNextPhase);
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
            if (pb.getType(i) == BulletType.KUNAI.getId()) {
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
            if (playerBullets.isActive(i) && playerBullets.getType(i) == BulletType.KUNAI.getId()) {
                playerBullets.setVy(i, -12); // launch upwards
            }
        }
        for (BulletPool pb : coopBullets.values()) {
            for (int i = 0; i < pb.getCapacity(); i++) {
                if (pb.isActive(i) && pb.getType(i) == BulletType.KUNAI.getId()) {
                    pb.setVy(i, -12);
                }
            }
        }
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
        int totalWaves = stage.waves.size();
        int totalPhases = boss.phases.size();
        int totalSteps = totalWaves + totalPhases;
        if (totalSteps == 0)
            return 100.0f;

        float stepsDone = 0;
        if (arenaPhase == ArenaPhase.WAVES) {
            stepsDone = scheduledEnemies.isEmpty() ? 0
                    : (float) nextScheduledIdx / scheduledEnemies.size() * stage.waves.size();
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
