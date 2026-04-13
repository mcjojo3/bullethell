package mc.sayda.bullethell.arena;

import mc.sayda.bullethell.boss.BossDefinition;
import mc.sayda.bullethell.boss.BossLoader;
import mc.sayda.bullethell.boss.PatternStep;
import mc.sayda.bullethell.boss.PhaseDefinition;
import mc.sayda.bullethell.boss.RulesetConfig;
import mc.sayda.bullethell.boss.StageDefinition;
import mc.sayda.bullethell.boss.StageLoader;
import mc.sayda.bullethell.boss.WaveDefinition;
import mc.sayda.bullethell.boss.WaveEnemy;
import mc.sayda.bullethell.pattern.BulletType;
import mc.sayda.bullethell.pattern.PatternEngine;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * All state for one bullet-hell session.
 *
 * Stage flow:
 *   WAVES  - pre-boss fairy waves spawn and attack; player can collect power items
 *   BOSS   - boss fight driven by BossDefinition JSON
 *
 * Design rules:
 *   - Every system takes ArenaContext - no static globals.
 *   - Multiple ArenaContexts = splitscreen / Phantasmagoria mode.
 *   - Stage structure, boss behaviour, and gameplay rules are fully JSON-driven.
 *   - Spellcard-accurate HP bar: each boss phase owns its own HP pool.
 */
public class ArenaContext {

    // ---------------------------------------------------------------- identity

    private static final AtomicInteger ID_GEN = new AtomicInteger();

    public final UUID             playerUuid;
    public final int              arenaId;
    public final DifficultyConfig difficulty;
    public final long             seed;
    private final java.util.Random random;

    // ---------------------------------------------------------------- definitions (JSON-loaded)

    public final StageDefinition stage;
    public final BossDefinition  boss;
    public final RulesetConfig   rules;

    // ---------------------------------------------------------------- subsystems

    public final BulletPool      bullets;        // enemy + fairy bullets
    public final BulletPool      playerBullets;
    public final ItemPool        items;
    public final EnemyPool       enemies;
    public final PlayerState2D   player;
    public final ScoreSystem     score;
    public final SpellcardTimer  spellcard;
    public final LaserPool       lasers = new LaserPool();

    // ---------------------------------------------------------------- stage state machine

    public enum ArenaPhase { WAVES, DIALOG_INTRO, BOSS }
    public ArenaPhase arenaPhase = ArenaPhase.WAVES;

    /** Absolute tick counter from arena start (drives wave spawning). */
    private int stageTick    = 0;
    /** Index into stage.waves - next wave to check. */
    private int waveIndex    = 0;
    /** Kill counter for drop-every-Nth-kill rule. */
    private int killCounter      = 0;
    /**
     * Countdown ticks between last wave clearing and boss intro / BOSS phase.
     * -1 = delay not yet triggered (waves not yet clear).
     */
    private int waveEndDelayLeft = -1;
    /** Cyclic index into the normal (small-enemy) drop sequence. */
    private int dropCycleIdx     = 0;
    /** Cyclic index into the large-enemy drop sequence. */
    private int largeDropCycleIdx = 0;
    /** Parsed drop cycle for small/normal enemies (POWER + POINT only). */
    private final int[] dropCycle;
    /** Parsed drop cycle for large enemies (may include FULL_POWER). */
    private final int[] largeDropCycle;

    // ---------------------------------------------------------------- boss state

    public int   bossPhase = 0;
    public int   bossHp;
    public int   bossMaxHp;
    public float bossX;
    public float bossY;

    private static final float BOSS_HIT_RADIUS = 24f;

    private float spiralAngle     = 0f;
    private int   patternCooldown = 0;
    private int   bossTick        = 0;
    /** bossTick value at the start of the current phase - keeps movement continuous. */
    private int   phaseStartTick  = 0;
    private int   attackIndex     = 0;

    /** Ticks remaining in the inter-phase pause (boss drifts to centre, no attacks). */
    private int phaseTransitionTimer = 0;
    /** Next phase index to start once phaseTransitionTimer reaches 0; -1 = none pending. */
    private int pendingNextPhase     = -1;

    // ---------------------------------------------------------------- dialog state

    /** Current dialog line index within {@code boss.introDialog}. */
    private int dialogIndex     = 0;
    /** Ticks until this line auto-advances. */
    private int dialogTicksLeft = 0;

    // ---------------------------------------------------------------- enemy constants

    private static final float ENEMY_HIT_RADIUS = 20f;
    private static final float POC_Y_FRAC       = 0.20f; // overridden by rules.pocFraction

    // ---------------------------------------------------------------- event bus

    public final Queue<GameEvent> pendingEvents = new ConcurrentLinkedQueue<>();

    private boolean over = false;
    /** True when the boss's last phase was cleared (player won). False on game-over. */
    private boolean won  = false;
    /** Number of spell cards the player successfully captured (no bomb/death). */
    private int spellsCaptured = 0;
    /** Total spell card phases triggered so far. */
    private int spellsAttempted = 0;
    /** ID of the active character (from CharacterDefinition JSON). */
    public String characterId = "reimu";

    // ---------------------------------------------------------------- co-op

    /**
     * Additional players sharing this arena (not the host).
     * Each has their own PlayerState2D and BulletPool; enemies/boss/items are shared.
     */
    private final java.util.LinkedHashMap<UUID, PlayerState2D> coopPlayers    = new java.util.LinkedHashMap<>();
    private final java.util.LinkedHashMap<UUID, BulletPool>    coopBullets    = new java.util.LinkedHashMap<>();
    private final java.util.LinkedHashMap<UUID, String>        coopCharIds    = new java.util.LinkedHashMap<>();

    /** Add a co-op participant. Called when another player joins the match. */
    public void addCoopPlayer(UUID uuid, mc.sayda.bullethell.boss.CharacterDefinition charDef) {
        int startLives = (rules.startingLives >= 0) ? rules.startingLives : charDef.startingLives;
        int startBombs = (rules.startingBombs >= 0) ? rules.startingBombs : charDef.startingBombs;
        PlayerState2D ps = new PlayerState2D(charDef.hitRadius, charDef.grazeRadius,
                charDef.pickupRadius, charDef.speedNormal, charDef.speedFocused,
                startLives, startBombs);
        coopPlayers.put(uuid, ps);
        coopBullets.put(uuid, new BulletPool(BulletPool.PLAYER_CAPACITY));
        coopCharIds.put(uuid, charDef.id);
    }

    public void removeCoopPlayer(UUID uuid) {
        coopPlayers.remove(uuid);
        coopBullets.remove(uuid);
        coopCharIds.remove(uuid);
    }

    /** Character ID for any participant. Returns host's characterId for non-coop lookups. */
    public String getCharacterId(UUID uuid) {
        if (uuid.equals(playerUuid)) return characterId;
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

    public java.util.Map<UUID, PlayerState2D> getCoopPlayers() { return coopPlayers; }

    private boolean allPlayersEliminated() {
        if (player.lives >= 0) return false;
        for (PlayerState2D ps : coopPlayers.values()) {
            if (ps.lives >= 0) return false;
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
     * @param stageId      file name (without .json) under {@code data/bullethell/stages/}
     * @param characterId  file name (without .json) under {@code data/bullethell/characters/}
     */
    public ArenaContext(UUID playerUuid, DifficultyConfig difficulty, String stageId, String characterId) {
        this.playerUuid    = playerUuid;
        this.arenaId       = ID_GEN.getAndIncrement();
        this.difficulty    = difficulty;
        this.characterId   = (characterId != null) ? characterId : "reimu";
        this.seed          = System.nanoTime();
        this.random        = new java.util.Random(seed);
        this.bullets       = new BulletPool(BulletPool.ENEMY_CAPACITY);
        this.playerBullets = new BulletPool(BulletPool.PLAYER_CAPACITY);
        this.items         = new ItemPool();
        this.enemies       = new EnemyPool();
        this.score         = new ScoreSystem();
        this.spellcard     = new SpellcardTimer();

        // Load stage/rules first so startingLives/Bombs overrides are available
        this.stage         = StageLoader.load(stageId);
        this.boss          = BossLoader.load(stage.bossId);
        this.rules         = stage.rules;
        this.dropCycle      = parseDropCycle(rules.dropCyclePattern);
        String largePattern = (rules.largeEnemyDropCyclePattern != null
                               && !rules.largeEnemyDropCyclePattern.isEmpty())
                              ? rules.largeEnemyDropCyclePattern
                              : rules.dropCyclePattern;
        this.largeDropCycle = parseDropCycle(largePattern);

        // Apply character-specific stats; stage rules can override lives/bombs
        mc.sayda.bullethell.boss.CharacterDefinition charDef =
                mc.sayda.bullethell.boss.CharacterLoader.load(this.characterId);
        int startLives = (rules.startingLives  >= 0) ? rules.startingLives  : charDef.startingLives;
        int startBombs = (rules.startingBombs  >= 0) ? rules.startingBombs  : charDef.startingBombs;
        this.player = new PlayerState2D(
                charDef.hitRadius, charDef.grazeRadius, charDef.pickupRadius,
                charDef.speedNormal, charDef.speedFocused,
                startLives, startBombs);

        // Boss position is set when BOSS phase begins
        this.bossX    = BulletPool.ARENA_W / 2f;
        this.bossY    = 100f;
        this.bossHp   = 0;
        this.bossMaxHp= 0;

        // If no waves defined, go straight to dialog/boss
        if (stage.waves.isEmpty()) {
            transitionToDialogOrBoss();
        }
    }

    // ---------------------------------------------------------------- tick

    public void tick() {
        if (over) return;
        stageTick++;

        bullets.tick();
        playerBullets.tick();
        for (BulletPool pb : coopBullets.values()) pb.tick();
        items.tick();
        score.tick();

        if (arenaPhase == ArenaPhase.WAVES) {
            enemies.tick();
            tickWaves();
            tickEnemyAI();
            checkPlayerBulletsVsEnemies(playerBullets);
            for (BulletPool pb : coopBullets.values()) checkPlayerBulletsVsEnemies(pb);
            checkWavesComplete();
        } else if (arenaPhase == ArenaPhase.DIALOG_INTRO) {
            // Auto-advance dialog lines; transition to boss when all lines exhausted
            if (dialogTicksLeft > 0) {
                dialogTicksLeft--;
            } else {
                dialogIndex++;
                if (boss.introDialog == null || dialogIndex >= boss.introDialog.size()) {
                    transitionToBoss();
                } else {
                    dialogTicksLeft = boss.introDialog.get(dialogIndex).delayTicks;
                }
            }
        } else {
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

            checkPlayerBulletsVsBoss(playerBullets, player);
            for (var e : coopPlayers.entrySet()) {
                BulletPool pb = coopBullets.get(e.getKey());
                if (pb != null) checkPlayerBulletsVsBoss(pb, e.getValue());
            }
        }

        // Tick shots + collisions for every participant
        tickPlayerShots(player, playerBullets);
        for (var e : coopPlayers.entrySet()) {
            UUID cUuid = e.getKey(); PlayerState2D cPs = e.getValue();
            BulletPool cPb = coopBullets.get(cUuid);
            if (cPb != null && cPs.lives >= 0) tickPlayerShots(cPs, cPb);
        }

        checkEnemyBulletsVsPlayer(playerUuid, player);
        for (var e : coopPlayers.entrySet()) {
            if (e.getValue().lives >= 0) checkEnemyBulletsVsPlayer(e.getKey(), e.getValue());
        }

        checkLasersVsPlayer(playerUuid, player);
        for (var e : coopPlayers.entrySet()) {
            if (e.getValue().lives >= 0) checkLasersVsPlayer(e.getKey(), e.getValue());
        }

        checkItemPickup(player);
        for (PlayerState2D ps : coopPlayers.values()) {
            if (ps.lives >= 0) checkItemPickup(ps);
        }

        // Death countdown for host
        if (player.deathPendingTicks > 0) {
            player.deathPendingTicks--;
            if (player.deathPendingTicks == 0) applyDeath(playerUuid);
        }
        // Death countdown for coop players
        for (var e : coopPlayers.entrySet()) {
            PlayerState2D ps = e.getValue();
            if (ps.deathPendingTicks > 0) {
                ps.deathPendingTicks--;
                if (ps.deathPendingTicks == 0) applyDeath(e.getKey());
            }
        }
    }

    // ================================================================ WAVE PHASE

    /**
     * Compression factor: higher difficulties shrink the gap between waves.
     * EASY=0.80, NORMAL=1.00, HARD=1.25, LUNATIC=1.55
     */
    private float waveTimingMult() {
        return switch (difficulty) {
            case EASY    -> 0.80f;
            case NORMAL  -> 1.00f;
            case HARD    -> 1.25f;
            case LUNATIC -> 1.55f;
        };
    }

    private void tickWaves() {
        float mult = waveTimingMult();
        while (waveIndex < stage.waves.size()) {
            WaveDefinition wave = stage.waves.get(waveIndex);
            // Scale spawnTick down on harder difficulties so waves arrive sooner.
            if (stageTick < (int)(wave.spawnTick / mult)) break;
            for (WaveEnemy we : wave.enemies) {
                EnemyType type = enemyTypeByName(we.type);
                enemies.spawn(we.x, we.y, we.vx, we.vy, we.angVel, we.arcTicks, type);
            }
            waveIndex++;
        }
    }

    private void tickEnemyAI() {
        float pocY = BulletPool.ARENA_H * (float) rules.pocFraction;

        for (int i = 0; i < EnemyPool.CAPACITY; i++) {
            if (!enemies.isActive(i)) continue;

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

            // Attack AI - fire when cooldown hits 0
            if (enemies.getAtkCd(i) == 0) {
                EnemyType type = EnemyType.fromId(enemies.getType(i));

                int scaledCount = Math.max(1, Math.round(type.bulletCount * difficulty.densityMult));
                int scaledInterval = Math.max(10, (int)(type.atkInterval / difficulty.densityMult));

                PatternEngine.fireAimed(
                        bullets, ex, ey,
                        player.x, player.y,
                        scaledCount, type.bulletSpread,
                        type.bulletSpeed, difficulty, BulletType.RICE);
                enemies.setAtkCooldown(i, scaledInterval);
            }
        }
    }

    /** Check player bullets (from any participant) hitting enemies. */
    private void checkPlayerBulletsVsEnemies(BulletPool pb) {
        int damage = 15; // flat enemy damage — not scaled by power
        float r2   = ENEMY_HIT_RADIUS * ENEMY_HIT_RADIUS;

        for (int bi = 0; bi < pb.getCapacity(); bi++) {
            if (!pb.isActive(bi)) continue;
            float bx = pb.getX(bi);
            float by = pb.getY(bi);

            for (int ei = 0; ei < EnemyPool.CAPACITY; ei++) {
                if (!enemies.isActive(ei)) continue;
                float dx = bx - enemies.getX(ei);
                float dy = by - enemies.getY(ei);
                if (dx * dx + dy * dy <= r2) {
                    pb.deactivate(bi);
                    if (enemies.damage(ei, damage)) killEnemy(ei);
                    break;
                }
            }
        }
    }

    /** Kill an enemy: deactivate, award score, apply drop cycle. */
    private void killEnemy(int slot) {
        float ex = enemies.getX(slot);
        float ey = enemies.getY(slot);
        EnemyType type = EnemyType.fromId(enemies.getType(slot));
        enemies.deactivate(slot);

        score.addScore(type.scoreValue);
        killCounter++;

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
                items.spawn(ex, ey, ItemPool.TYPE_BOMB);
            } else if (type.large) {
                int dropType = largeDropCycle[largeDropCycleIdx % largeDropCycle.length];
                largeDropCycleIdx++;
                items.spawn(ex, ey, dropType);
            } else {
                int dropType = dropCycle[dropCycleIdx % dropCycle.length];
                dropCycleIdx++;
                items.spawn(ex, ey, dropType);
            }
        }
    }

    /** Transition to BOSS phase once all waves have spawned and cleared. */
    private void checkWavesComplete() {
        if (waveIndex < stage.waves.size()) return; // still waves pending
        if (enemies.getActiveCount() > 0) return;   // enemies still on screen

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
        if (boss.introDialog != null && !boss.introDialog.isEmpty()) {
            arenaPhase      = ArenaPhase.DIALOG_INTRO;
            dialogIndex     = 0;
            dialogTicksLeft = boss.introDialog.get(0).delayTicks;
        } else {
            transitionToBoss();
        }
    }

    private void transitionToBoss() {
        arenaPhase = ArenaPhase.BOSS;
        bullets.clearAll();
        lasers.clearAll();
        enemies.clearAll();
        bossTick   = 0;
        bossX = BulletPool.ARENA_W / 2f;
        bossY = 100f;
        startBossPhase(0);
        pendingEvents.add(GameEvent.PHASE_CHANGE);
    }

    // ---------------------------------------------------------------- dialog accessors (for ArenaStatePacket)

    /** Speaker of the current dialog line; empty string when no dialog is active. */
    public String getDialogSpeaker() {
        if (arenaPhase != ArenaPhase.DIALOG_INTRO
                || boss.introDialog == null || boss.introDialog.isEmpty()
                || dialogIndex >= boss.introDialog.size()) return "";
        return boss.introDialog.get(dialogIndex).speaker;
    }

    /** Text of the current dialog line; empty string when no dialog is active. */
    public String getDialogText() {
        if (arenaPhase != ArenaPhase.DIALOG_INTRO
                || boss.introDialog == null || boss.introDialog.isEmpty()
                || dialogIndex >= boss.introDialog.size()) return "";
        return boss.introDialog.get(dialogIndex).text;
    }

    /** Increments with each new line; lets the client reset slide-in animation. */
    public int getDialogLineIndex() { return dialogIndex; }

    // ================================================================ BOSS PHASE

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
                bossY = 80f + (float)(1 - Math.cos(lt * 0.018)) * phase.moveSpeed * 0.35f;
            }
            case "STATIC" -> { /* fixed */ }
            default ->  // SINE_WAVE: starts at centre (sin 0 = 0)
                bossX = BulletPool.ARENA_W / 2f + (float) Math.sin(lt * 0.018) * phase.moveSpeed;
        }

        patternCooldown--;
        if (patternCooldown > 0) return;

        if (phase.attacks.isEmpty()) { patternCooldown = 20; return; }

        PatternStep step = phase.attacks.get(attackIndex % phase.attacks.size());
        attackIndex++;
        executeAttack(step);
        // Scale cooldown inversely with densityMult: Lunatic fires ~2× as often as Normal
        patternCooldown = Math.max(1, (int)(step.cooldown / difficulty.densityMult));
    }

    private void executeAttack(PatternStep step) {
        BulletType type = bulletTypeByName(step.bulletType);
        int scaledArms = Math.max(1, Math.round(step.arms * difficulty.densityMult));
        switch (step.pattern == null ? "RING" : step.pattern.toUpperCase()) {
            case "SPIRAL" -> {
                PatternEngine.fireSpiral(bullets, bossX, bossY, spiralAngle,
                        scaledArms, step.speed, difficulty, type);
                spiralAngle += (float)(Math.PI * 2.0 / scaledArms) * 0.15f;
            }
            case "AIMED" -> PatternEngine.fireAimed(bullets, bossX, bossY,
                    player.x, player.y, scaledArms, step.spread, step.speed, difficulty, type);
            case "RING"  -> PatternEngine.fireRing(bullets, bossX, bossY,
                    scaledArms, step.speed, difficulty, type);
            case "SPREAD" -> PatternEngine.fireSpread(bullets, bossX, bossY,
                    scaledArms, step.speed, difficulty, type);
            case "DENSE_RING" -> PatternEngine.fireDenseRing(bullets, bossX, bossY,
                    scaledArms, step.speed, difficulty, type);
            case "LASER_BEAM" -> PatternEngine.fireLaserBeam(bullets, bossX, bossY,
                    player.x, player.y, scaledArms, step.speed, difficulty, type);
            case "LASER" -> {
                // Single directional laser aimed at the player's current position (Master Spark).
                float angle       = (float) Math.atan2(player.y - bossY, player.x - bossX);
                int   scaledWarn  = Math.max(10, (int)(step.warnTicks  / difficulty.densityMult));
                lasers.spawn(bossX, bossY, angle, step.laserHalfWidth,
                        scaledWarn, step.activeTicks, type.getId(), false);
            }
            case "LASER_ROTATING" -> {
                // Bidirectional beams radiating in all directions (Non-Directional Laser).
                // Difficulty scaling: more beams + shorter warning on harder difficulties.
                //   Normal: arms=8, warn=14  |  Lunatic: arms=16, warn=7
                int   scaledWarn  = Math.max(6, (int)(step.warnTicks  / difficulty.densityMult));
                float angleStep   = (float)(Math.PI * 2.0 / scaledArms);
                for (int i = 0; i < scaledArms; i++) {
                    lasers.spawn(bossX, bossY, spiralAngle + angleStep * i,
                            step.laserHalfWidth, scaledWarn, step.activeTicks, type.getId(), true);
                }
                spiralAngle += 0.45f; // ~26° per fire cycle — visible rotation
            }
            default -> PatternEngine.fireRing(bullets, bossX, bossY,
                    scaledArms, step.speed, difficulty, type);
        }
    }

    private void checkPlayerBulletsVsBoss(BulletPool pb, PlayerState2D ps) {
        if (bossHp <= 0) return;
        int damage = bossBulletDamage(ps);

        for (int i = 0; i < pb.getCapacity(); i++) {
            if (!pb.isActive(i)) continue;
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
     * Lower tiers fire fewer bullets so each bullet hits harder, keeping DPS relatively flat.
     * Approximate unfocused DPS: ~40 (tier 0) → ~70 (tier 4). Focused: ~40 → ~93.
     */
    private int bossBulletDamage(PlayerState2D ps) {
        int lv = ps.powerLevel();
        if (ps.focused) {
            return switch (lv) {
                case 0  -> 12;
                case 1  ->  7;
                case 2  ->  5;
                case 3  ->  4;
                default ->  4; // tier 4
            };
        } else {
            return switch (lv) {
                case 0  ->  8;
                case 1  ->  5;
                case 2  ->  3;
                case 3  ->  2;
                default ->  2; // tier 4
            };
        }
    }

    private void checkBossPhaseTransition() {
        if (phaseTransitionTimer > 0 || pendingNextPhase >= 0) return; // already transitioning
        // Trigger at 0 HP *or* when the threshold fraction is crossed (e.g. 20% remaining).
        float threshold = currentBossPhase().hpThresholdFraction;
        boolean belowThreshold = threshold > 0 && bossHp <= (int)(bossMaxHp * threshold);
        if (bossHp > 0 && !belowThreshold) return;

        GameEvent spellResult = spellcard.onPhaseCleared();
        boolean wasSpellCard = currentBossPhase().isSpellCard;
        boolean captured     = spellResult == GameEvent.SPELL_CAPTURED;
        if (wasSpellCard) {
            spellsAttempted++;
            if (captured) spellsCaptured++;
        }
        if (captured) score.onSpellCapture(spellcard.getBonusValue());
        pendingEvents.add(spellResult);
        pendingEvents.add(GameEvent.PHASE_CHANGE);
        dropBossPhaseItems(wasSpellCard, captured);
        bullets.clearAll();
        lasers.clearAll();

        int nextPhase = bossPhase + 1;
        if (nextPhase >= boss.phases.size()) {
            won  = true;
            over = true;
            return;
        }

        // Queue next phase - boss will drift to centre over 50 ticks first.
        pendingNextPhase      = nextPhase;
        phaseTransitionTimer  = 50;
    }

    /**
     * Scatter point items from the boss position when a phase is cleared.
     * Mirrors TH7/TH8: cancelled bullets become star/point items.
     *   - NonSpell cleared    → 4 point items scattered around boss
     *   - Spell captured      → 8 point items (bigger reward, like bullet cancellation)
     *   - Spell failed        → nothing (no reward for failing the card)
     *
     * Bosses NEVER drop power items, bombs, or 1-ups — those are fairy-only drops.
     */
    private void dropBossPhaseItems(boolean isSpellCard, boolean captured) {
        if (isSpellCard && !captured) return;
        int count = isSpellCard ? 8 : 4;
        for (int i = 0; i < count; i++) {
            float ox = (random.nextFloat() - 0.5f) * 80f;
            float oy = (random.nextFloat() - 0.5f) * 40f;
            items.spawn(bossX + ox, bossY + oy, ItemPool.TYPE_POINT);
        }
    }

    private void startBossPhase(int phaseIndex) {
        bossPhase      = phaseIndex;
        attackIndex    = 0;
        phaseStartTick = bossTick; // movement formula resets from centre each phase
        bullets.clearAll();

        PhaseDefinition phase = boss.phases.get(phaseIndex);
        bossHp    = phase.hp;
        bossMaxHp = phase.hp;

        if (phase.isSpellCard) {
            int diffIdx  = Math.min(difficulty.ordinal(), phase.spellDurationTicks.length - 1);
            int duration = phase.spellDurationTicks[diffIdx];
            if (duration > 0) spellcard.start(duration, phase.spellBonus);
        }
    }

    // ================================================================ SHARED SYSTEMS

    private void tickPlayerShots(PlayerState2D ps, BulletPool pb) {
        if (!ps.shooting) { ps.shotCooldown = 0; return; }
        if (ps.shotCooldown > 0) { ps.shotCooldown--; return; }

        ps.shotCooldown = ps.focused
                ? PlayerState2D.SHOT_COOLDOWN_FOCUSED
                : PlayerState2D.SHOT_COOLDOWN_NORMAL;

        int   t  = BulletType.PLAYER_SHOT.getId();
        float px = ps.x;
        float py = ps.y - 4;
        int   lv = ps.powerLevel();

        if (ps.focused) {
            fireFocusedShot(px, py, t, lv, pb);
        } else {
            fireNormalShot(px, py, t, lv, pb);
        }
    }

    // Normal spread shot — spawns into the caller's bullet pool
    private void fireNormalShot(float px, float py, int t, int lv, BulletPool pb) {
        switch (lv) {
            case 0 ->
                pb.spawn(px, py, 0f, -16f, t, 55);
            case 1 -> {
                pb.spawn(px,      py,  0f,   -16f, t, 55);
                pb.spawn(px -  8, py, -1.6f, -16f, t, 55);
                pb.spawn(px +  8, py,  1.6f, -16f, t, 55);
            }
            case 2 -> {
                pb.spawn(px,      py,  0f,    -16f, t, 55);
                pb.spawn(px - 10, py, -2.0f,  -16f, t, 55);
                pb.spawn(px + 10, py,  2.0f,  -16f, t, 55);
                pb.spawn(px - 22, py, -4.4f,  -14f, t, 55);
                pb.spawn(px + 22, py,  4.4f,  -14f, t, 55);
            }
            case 3 -> {
                pb.spawn(px,      py,  0f,    -16f, t, 55);
                pb.spawn(px - 10, py, -2.0f,  -16f, t, 55);
                pb.spawn(px + 10, py,  2.0f,  -16f, t, 55);
                pb.spawn(px - 22, py, -4.4f,  -14f, t, 55);
                pb.spawn(px + 22, py,  4.4f,  -14f, t, 55);
                pb.spawn(px - 34, py, -7.6f,  -10f, t, 55);
                pb.spawn(px + 34, py,  7.6f,  -10f, t, 55);
            }
            default -> {
                pb.spawn(px,      py,  0f,    -16f, t, 55);
                pb.spawn(px - 10, py, -2.0f,  -16f, t, 55);
                pb.spawn(px + 10, py,  2.0f,  -16f, t, 55);
                pb.spawn(px - 22, py, -4.4f,  -14f, t, 55);
                pb.spawn(px + 22, py,  4.4f,  -14f, t, 55);
                pb.spawn(px - 34, py, -8.0f,  -12f, t, 55);
                pb.spawn(px + 34, py,  8.0f,  -12f, t, 55);
            }
        }
    }

    // Focused shot — spawns into the caller's bullet pool
    private void fireFocusedShot(float px, float py, int t, int lv, BulletPool pb) {
        switch (lv) {
            case 0 ->
                pb.spawn(px, py, 0f, -20f, t, 45);
            case 1 -> {
                pb.spawn(px,     py, 0f, -20f, t, 45);
                pb.spawn(px - 5, py, 0f, -18f, t, 45);
                pb.spawn(px + 5, py, 0f, -18f, t, 45);
            }
            case 2 -> {
                pb.spawn(px,      py, 0f, -20f, t, 45);
                pb.spawn(px -  5, py, 0f, -20f, t, 45);
                pb.spawn(px +  5, py, 0f, -20f, t, 45);
                pb.spawn(px - 13, py, 0f, -18f, t, 45);
                pb.spawn(px + 13, py, 0f, -18f, t, 45);
            }
            case 3 -> {
                pb.spawn(px,      py, 0f, -20f, t, 45);
                pb.spawn(px -  5, py, 0f, -20f, t, 45);
                pb.spawn(px +  5, py, 0f, -20f, t, 45);
                pb.spawn(px - 13, py, 0f, -18f, t, 45);
                pb.spawn(px + 13, py, 0f, -18f, t, 45);
                pb.spawn(px - 22, py, 0f, -18f, t, 45);
                pb.spawn(px + 22, py, 0f, -18f, t, 45);
            }
            default -> {
                pb.spawn(px,      py, 0f, -20f, t, 45);
                pb.spawn(px -  5, py, 0f, -20f, t, 45);
                pb.spawn(px +  5, py, 0f, -20f, t, 45);
                pb.spawn(px - 13, py, 0f, -18f, t, 45);
                pb.spawn(px + 13, py, 0f, -18f, t, 45);
                pb.spawn(px - 22, py, 0f, -20f, t, 45);
                pb.spawn(px + 22, py, 0f, -20f, t, 45);
            }
        }
    }

    private void checkEnemyBulletsVsPlayer(UUID uuid, PlayerState2D ps) {
        if (ps.deathPendingTicks > 0) return;

        float hr2 = ps.hitRadius  * ps.hitRadius;
        float gr2 = ps.grazeRadius * ps.grazeRadius;

        for (int i = 0; i < bullets.getCapacity(); i++) {
            if (!bullets.isActive(i)) continue;
            float dx     = bullets.getX(i) - ps.x;
            float dy     = bullets.getY(i) - ps.y;
            float distSq = dx * dx + dy * dy;

            if (distSq <= hr2) {
                bullets.deactivate(i);
                ps.deathPendingTicks = PlayerState2D.DEATH_BOMB_GRACE;
                pendingEvents.add(GameEvent.HIT);
                return;
            } else if (distSq <= gr2 && rules.grazeScoringEnabled) {
                ps.graze++;
                score.onGraze();
                if (ps.graze % 50 == 0) pendingEvents.add(GameEvent.GRAZE_CHAIN);
            }
        }
    }

    private void checkLasersVsPlayer(UUID uuid, PlayerState2D ps) {
        if (ps.deathPendingTicks > 0) return;
        for (int i = 0; i < LaserPool.CAPACITY; i++) {
            if (!lasers.isFiring(i)) continue;
            float lx    = lasers.getX(i);
            float ly    = lasers.getY(i);
            float angle = lasers.getAngle(i);
            float hw    = lasers.getHalfWidth(i) + ps.hitRadius;
            float cosA  = (float) Math.cos(angle);
            float sinA  = (float) Math.sin(angle);
            float dx    = ps.x - lx;
            float dy    = ps.y - ly;
            float along = dx * cosA + dy * sinA;
            float perp  = Math.abs(-dx * sinA + dy * cosA);
            // Bidirectional (NDL): beam passes through origin in both directions — only perp matters.
            // Directional (Master Spark): only forward half can hit (along > 0).
            boolean hit = lasers.isBidir(i) ? perp < hw : (along > 0 && perp < hw);
            if (hit) {
                ps.deathPendingTicks = PlayerState2D.DEATH_BOMB_GRACE;
                pendingEvents.add(GameEvent.HIT);
                return;
            }
        }
    }

    private void checkItemPickup(PlayerState2D ps) {
        float pocY       = BulletPool.ARENA_H * (float) rules.pocFraction;
        boolean abovePoc = ps.y < pocY;
        float pickupR2   = ps.pickupRadius * ps.pickupRadius;

        for (int i = 0; i < ItemPool.CAPACITY; i++) {
            if (!items.isActive(i)) continue;
            float dx = items.getX(i) - ps.x;
            float dy = items.getY(i) - ps.y;
            boolean inRange = (rules.pocAutoCollect && abovePoc)
                    || (dx * dx + dy * dy <= pickupR2);

            if (inRange) {
                int type = items.getType(i);
                items.deactivate(i);
                float heightFrac = 1.0f - items.getY(i) / BulletPool.ARENA_H;
                switch (type) {
                    case ItemPool.TYPE_POINT -> {
                        int val = (int)(rules.pointItemMinValue +
                                (rules.pointItemMaxValue - rules.pointItemMinValue) * heightFrac);
                        score.addScore(val);
                    }
                    case ItemPool.TYPE_POWER      -> { score.onPowerItemPickup(); ps.power = Math.min(PlayerState2D.MAX_POWER, ps.power + 4); }
                    case ItemPool.TYPE_FULL_POWER -> { score.onPowerItemPickup(); ps.power = PlayerState2D.MAX_POWER; }
                    case ItemPool.TYPE_ONE_UP     -> ps.lives++;
                    case ItemPool.TYPE_BOMB       -> ps.bombs = Math.min(ps.bombs + 1, 9);
                }
                pendingEvents.add(GameEvent.ITEM_PICKUP);
            }
        }
    }

    // ---------------------------------------------------------------- death + bomb

    private void applyDeath(UUID uuid) {
        PlayerState2D ps = getPlayerState(uuid);
        if (ps == null) return;

        spellcard.fail();
        int powerBefore = ps.power;
        ps.power = Math.max(0, ps.power - rules.deathPowerLoss);
        int lost = powerBefore - ps.power;
        int dropCount = lost / 8;
        for (int d = 0; d < dropCount; d++) {
            float ox  = (random.nextFloat() - 0.5f) * 80f;
            float oy2 = (random.nextFloat() - 0.5f) * 60f;
            items.spawn(ps.x + ox, ps.y + oy2, ItemPool.TYPE_POWER);
        }
        pendingEvents.add(GameEvent.DEATH);
        if (ps.lives > 0) {
            ps.lives--;
            ps.respawn();
            bullets.clearAll();
        } else {
            ps.lives = -1; // eliminated
            if (allPlayersEliminated()) over = true;
        }
    }

    /** Activate a bomb for the specified participant. */
    public void activateBomb(UUID uuid) {
        PlayerState2D ps = getPlayerState(uuid);
        if (ps == null || ps.bombs <= 0) return;
        ps.bombs--;
        bullets.clearAll();
        if (arenaPhase == ArenaPhase.BOSS) spellcard.fail();
        if (ps.deathPendingTicks > 0) ps.deathPendingTicks = 0;
        pendingEvents.add(GameEvent.BOMB_USED);
    }

    // ---------------------------------------------------------------- helpers

    private PhaseDefinition currentBossPhase() {
        return boss.phases.get(Math.min(bossPhase, boss.phases.size() - 1));
    }

    // ---------------------------------------------------------------- boss declaration helpers (used by ArenaStatePacket)

    /**
     * True while the inter-phase pause is counting down.
     * The client uses this to drive the spell-card declaration animation.
     */
    public boolean isDeclaring() { return phaseTransitionTimer > 0; }

    /**
     * The spell name to display:
     *   - During declaration: the INCOMING phase's name (so "Fantasy Seal" shows before the card starts).
     *   - During active phase: the current phase's name if it is a spell card.
     *   - Otherwise: empty string.
     */
    public String getDisplaySpellName() {
        if (pendingNextPhase >= 0 && pendingNextPhase < boss.phases.size()) {
            PhaseDefinition next = boss.phases.get(pendingNextPhase);
            return next.isSpellCard ? next.spellName : "";
        }
        PhaseDefinition cur = currentBossPhase();
        return cur.isSpellCard ? cur.spellName : "";
    }

    /** True when the currently active phase IS a spell card (not during declaration). */
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

    /** Parse the drop cycle string from rules into an int[] of ItemPool type constants. */
    private static int[] parseDropCycle(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return new int[]{ ItemPool.TYPE_POWER, ItemPool.TYPE_POINT };
        }
        String[] parts = pattern.split(",");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = switch (parts[i].trim().toUpperCase()) {
                case "POWER"      -> ItemPool.TYPE_POWER;
                case "POINT"      -> ItemPool.TYPE_POINT;
                case "FULL_POWER" -> ItemPool.TYPE_FULL_POWER;
                case "ONE_UP"     -> ItemPool.TYPE_ONE_UP;
                default           -> ItemPool.TYPE_POINT;
            };
        }
        return result;
    }

    private static EnemyType enemyTypeByName(String name) {
        if (name == null) return EnemyType.BLUE_FAIRY;
        try { return EnemyType.valueOf(name.toUpperCase()); }
        catch (IllegalArgumentException e) { return EnemyType.BLUE_FAIRY; }
    }

    private static BulletType bulletTypeByName(String name) {
        if (name == null) return BulletType.ORB;
        try { return BulletType.valueOf(name.toUpperCase()); }
        catch (IllegalArgumentException e) { return BulletType.ORB; }
    }

    // ---------------------------------------------------------------- queries

    public boolean isOver()           { return over; }
    public boolean isWon()            { return won; }
    public int     getSpellsCaptured(){ return spellsCaptured; }
    public int     getSpellsAttempted(){ return spellsAttempted; }
}
