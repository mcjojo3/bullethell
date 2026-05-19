package mc.sayda.bullethell.client;

import mc.sayda.bullethell.arena.BulletPool;
import mc.sayda.bullethell.arena.EnemyPool;
import mc.sayda.bullethell.arena.ItemPool;
import mc.sayda.bullethell.arena.LaserPool;
import mc.sayda.bullethell.arena.PlayerState2D;
import mc.sayda.bullethell.network.AllPlayerBulletsSyncPacket;
import mc.sayda.bullethell.network.ArenaStatePacket;
import mc.sayda.bullethell.network.BulletDeltaPacket;
import mc.sayda.bullethell.network.BulletFullSyncPacket;
import mc.sayda.bullethell.network.CoopPlayersSyncPacket;
import mc.sayda.bullethell.network.EnemySyncPacket;
import mc.sayda.bullethell.arena.GameEvent;
import mc.sayda.bullethell.network.AttackActivationSfxPacket;
import mc.sayda.bullethell.network.GameEventPacket;
import mc.sayda.bullethell.network.ItemSyncPacket;
import mc.sayda.bullethell.network.LaserSyncPacket;
import mc.sayda.bullethell.network.PlayerBulletSyncPacket;
import mc.sayda.bullethell.sound.BHSounds;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;

import mc.sayda.bullethell.network.CoopPlayersSyncPacket.Entry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side mirror of the server ArenaContext. Updated by incoming packets.
 * Packet application methods are called from BHClientPackets (common network
 * layer).
 */
@Environment(EnvType.CLIENT)
public class ClientArenaState {

    public static final ClientArenaState INSTANCE = new ClientArenaState();

    // ---------------------------------------------------------------- state

    public boolean active = false;

    public final BulletPool bullets = new BulletPool(BulletPool.ENEMY_CAPACITY);
    public final BulletPool playerBullets = new BulletPool(BulletPool.PLAYER_CAPACITY);
    public final ItemPool items = new ItemPool();
    public final EnemyPool enemies = new EnemyPool();
    public final PlayerState2D player = new PlayerState2D();
    public final LaserPool lasers = new LaserPool();
    /** Last input direction sent to server - used for sub-tick position extrapolation. */
    public float inputDx = 0f, inputDy = 0f;
    public boolean inputFocused = false;
    /** Client-predicted player position - updated every tick with current input, reconciled against server. */
    public float predX = 0f, predY = 0f;

    public float bossX, bossY;
    /** Previous packet's boss position - used for sub-tick velocity extrapolation. */
    public float prevBossX = 0f, prevBossY = 0f;
    public int bossHp, bossMaxHp, bossPhase;

    // PoFV: gray stock (skillGauge) + colored hold (holdChargeGauge); chargeLevel = floor(stock).
    public int skillGauge = 0;
    public int chargeLevel = 0;
    public int holdChargeGauge = 0;

    // Active Ability State
    public int abilityType = 0; // 0=none, 1=timestop, 2=masterspark
    public int abilityTicks = 0;
    public float abilityX = 0f;
    public float abilityY = 0f;
    public java.util.UUID abilityOwner = new java.util.UUID(0, 0);

    public long score;
    /** Sum of all players' scores in co-op (equals {@link #score} when solo). */
    public long combinedScore;
    public int spellTimerTicks, spellTimerTotal;
    public int power;
    public int playerIndex = 1;

    /**
     * Other players sharing this arena (excludes self). Updated each tick.
     */
    public final List<Entry> coopPlayers = new ArrayList<>();

    /**
     * Bullet pools for every participant: playerIndex → BulletPool.
     * Index 1 = local player (mirrors playerBullets). Index 2+ = coop.
     * Updated every tick by AllPlayerBulletsSyncPacket.
     */
    public final Map<Integer, BulletPool> allPlayerBullets = new HashMap<>();

    /** For local-player shoot SFX: detect slots that became active since last sync. */
    private final boolean[] prevLocalPlayerBulletsActive = new boolean[BulletPool.PLAYER_CAPACITY];

    /** True when this player is dead but watching the coop partner's run. */
    public boolean spectating = false;

    /**
     * Set just before ArenaEndScreen opens so that the stopped() packet does not
     * immediately reset state. ArenaEndScreen.removed() clears this and calls reset().
     */
    public boolean pendingEndOverlay = false;

    /** {@link mc.sayda.bullethell.debug.BHDebugMode} god-mode (from server; test mode turns this on). */
    public boolean debugGodMode = false;
    public int debugArenaTick = 0;
    public int debugPatternCooldown = 0;
    public int debugEnemyBulletCount = 0;

    /** Consecutive graze chain (resets on hit or timeout). */
    public int grazeChain = 0;

    // ---- Cached formatted strings (recomputed only when the source value changes) ----
    private long cachedScore = Long.MIN_VALUE;
    private String cachedScoreStr = "0";
    private long cachedCombinedScore = Long.MIN_VALUE;
    private String cachedCombinedScoreStr = "0";

    public String getScoreStr() {
        if (score != cachedScore) {
            cachedScore = score;
            cachedScoreStr = String.format("%,d", score);
        }
        return cachedScoreStr;
    }

    public String getCombinedScoreStr() {
        if (combinedScore != cachedCombinedScore) {
            cachedCombinedScore = combinedScore;
            cachedCombinedScoreStr = String.format("%,d", combinedScore);
        }
        return cachedCombinedScoreStr;
    }

    private long cachedPtsScore = Long.MIN_VALUE;
    private String cachedPtsStr = "PTS 0";

    public String getPtsStr() {
        if (score != cachedPtsScore) {
            cachedPtsScore = score;
            cachedPtsStr = "PTS " + getScoreStr();
        }
        return cachedPtsStr;
    }

    private int cachedPhase = -1;
    private String cachedPhLabel = "PHASE 1";

    public String getPhLabel() {
        if (bossPhase != cachedPhase) {
            cachedPhase = bossPhase;
            cachedPhLabel = "PHASE " + (bossPhase + 1);
        }
        return cachedPhLabel;
    }

    private int cachedPower = -1;
    private String cachedPwrStr = "PWR 0/128";

    public String getPwrStr() {
        if (power != cachedPower) {
            cachedPower = power;
            cachedPwrStr = "PWR " + power + "/128";
        }
        return cachedPwrStr;
    }

    private int cachedGraze = -1;
    private int cachedGrazeChain = -1;
    private String cachedGrazeStr = "GRAZE 0";

    public String getGrazeStr() {
        int graze = player.graze;
        int chain = grazeChain;
        if (graze != cachedGraze || chain != cachedGrazeChain) {
            cachedGraze = graze;
            cachedGrazeChain = chain;
            cachedGrazeStr = chain > 0 ? "GRAZE " + graze + " x" + chain : "GRAZE " + graze;
        }
        return cachedGrazeStr;
    }

    /** Life piece count toward next extend. */
    public int lifePieces = 0;
    /** Bomb piece count toward next extend. */
    public int bombPieces = 0;

    // ---- test mode overlay (/bullethell test) ----
    public int rank = 16;
    public float pocFraction = 0.20f;
    public boolean pocAutoCollect = true;

    public boolean testMode = false;
    public boolean testHitboxVisible = false;
    public int testPage = 0; // 0=BOSS, 1=STAGE, 2=WAVE, 3=CHAR, 4=SHOT
    // per-page ID lists
    public java.util.List<String> testBossIds  = new java.util.ArrayList<>();
    public java.util.List<String> testStageIds = new java.util.ArrayList<>();
    public java.util.List<String> testWaveIds  = new java.util.ArrayList<>();
    public java.util.List<String> testCharIds  = new java.util.ArrayList<>();
    public java.util.List<String> testShotTypeIds = new java.util.ArrayList<>();
    // current selection per page
    public String testCurrentBossId   = "";
    public String testCurrentStageId  = "";
    public String testCurrentWaveId   = "";
    public String testCurrentCharId   = "reimu";
    public int testCurrentShotTypeIdx = 0;
    public int testCurrentDifficulty  = 1; // DifficultyConfig.NORMAL ordinal
    // scroll + selected index per page
    public int testScrollOffset       = 0; // BOSS
    public int testSelectedIdx        = 0;
    public int testStageScrollOffset  = 0;
    public int testStageSelectedIdx   = 0;
    public int testWaveScrollOffset   = 0;
    public int testWaveSelectedIdx    = 0;
    public int testCharScrollOffset   = 0;
    public int testCharSelectedIdx    = 0;
    public int testShotTypeScrollOffset = 0;
    public int testShotTypeSelectedIdx  = 0;

    /**
     * Track ID for the current phase's music (empty = no music).
     */
    public String currentMusicTrackId = "";

    /**
     * Character id of the local player (e.g. "reimu").
     */
    public String characterId = "reimu";

    /**
     * Boss id for the current fight (e.g. "marisa_boss").
     */
    public String bossId = "";
    /** Active boss texture override (empty = use bossId default). */
    public String bossTexture = "";
    /** Synced from server during {@code PENTAGRAM_RITUAL}; -1 = inactive. */
    public int pentagramRitualTick = -1;
    /** When outline stacking finished; -1 until then. */
    public int pentagramStackCompleteTick = -1;
    /**
     * True during the pre-boss dialog intro so the boss sprite renders before the
     * fight starts.
     */
    public boolean bossIntroVisible = false;

    // --- boss sprite animation ---
    public int bossAnimCounter = 0;
    /** Client-only: advances while {@link #active}; drives fairy sprite sheet frames during waves (not gated on boss). */
    public int arenaAnimTick = 0;
    /** -1 left, 0 idle, +1 right (server-authoritative). */
    public int bossMoveDir = 0;

    // --- sprite sheet animation ---
    public int animRow = 0;
    public int animLeanFrame = 0;
    public int animIdleFrame = 0;
    public int animIdleTick = 0;

    // spell card declaration
    public String spellName = "";
    public boolean declaring = false;
    public boolean activeSpellCard = false;
    public int declarationFrame = 0;

    // pre-boss intro dialog
    public String bossName = "";
    public String dialogSpeaker = "";
    public String dialogText = "";
    public int dialogLineIndex = -1;
    public int dialogReadyCount = 0;
    public int dialogTotalCount = 0;
    public int dialogSlideInTick = 0;

    // ---------------------------------------------------------------- packet
    // application (called from BHClientPackets)

    public void applyBulletDelta(int[] changedSlots, float[][] slotData, boolean[] isActive) {
        for (int i = 0; i < changedSlots.length; i++)
            bullets.setSlotData(changedSlots[i], slotData[i], isActive[i]);
    }

    public void applyFullSync(float[][] allSlotData, boolean[] allActive) {
        for (int i = 0; i < BulletPool.ENEMY_CAPACITY; i++)
            bullets.setSlotData(i, allSlotData[i], allActive[i]);
        active = true;
    }

    public void applyArenaState(boolean pktActive, boolean pktSpectating, float playerX, float playerY,
            int lives, int bombs, int graze, int power, int pIdx,
            float bossX, float bossY, int bossHp, int bossMaxHp, int bossPhase, int bossMoveDir,
            int skillGauge, int chargeLevel, int holdChargeGauge, int abilityType, int abilityTicks, float abilityX, float abilityY, java.util.UUID abilityOwner,
            long score, long combinedScore, int spellTimerTicks, int spellTimerTotal,
            String musicTrackId, String spellName, boolean activeSpellCard, boolean declaring,
            String characterId, String bossId, String bossName, boolean bossIntroVisible,
            String dialogSpeaker, String dialogText, int dialogLineIndex, int dialogReadyCount, int dialogTotalCount,
            int pentagramRitualTick, int pentagramStackCompleteTick,
            boolean debugGodMode, int debugArenaTick, int debugPatternCooldown, int debugEnemyBulletCount,
            int grazeChain, int lifePieces, int bombPieces) {

        if (!pktActive) {
            // ArenaEndScreen sets this flag before opening so the renderer keeps drawing
            // the frozen arena behind the overlay. Let removed() handle cleanup.
            if (pendingEndOverlay) return;
            active = false;
            BHScaleManager.restoreOriginalScale();
            reset();
            net.minecraft.client.gui.screens.Screen currentScreen = Minecraft.getInstance().screen;
            if (currentScreen instanceof mc.sayda.bullethell.client.screen.ArenaPlayScreen ||
                    currentScreen instanceof mc.sayda.bullethell.client.screen.ArenaQuitScreen) {
                Minecraft.getInstance().setScreen(null);
            }
            return;
        }
        active = true;
        spectating = pktSpectating;
        this.debugGodMode = debugGodMode;
        this.debugArenaTick = debugArenaTick;
        this.debugPatternCooldown = debugPatternCooldown;
        this.debugEnemyBulletCount = debugEnemyBulletCount;

        net.minecraft.client.gui.screens.Screen currentScreen = Minecraft.getInstance().screen;
        if (!(currentScreen instanceof mc.sayda.bullethell.client.screen.ArenaPlayScreen) &&
                !(currentScreen instanceof mc.sayda.bullethell.client.screen.ArenaQuitScreen)) {
            Minecraft.getInstance().setScreen(new mc.sayda.bullethell.client.screen.ArenaPlayScreen());
        }

        // GUI Scale Management
        BHScaleManager.applyIdealScale();

        this.player.x = playerX;
        this.player.y = playerY;
        // Reconcile client prediction against server authority.
        // Snap on first packet or large teleport; gently blend small corrections.
        float pdx = playerX - predX, pdy = playerY - predY;
        float dist = (float) Math.sqrt(pdx * pdx + pdy * pdy);
        if (dist > 32f || (predX == 0f && predY == 0f)) {
            predX = playerX; predY = playerY;
        } else if (dist > 0.5f) {
            predX += pdx * 0.4f; predY += pdy * 0.4f;
        }
        this.player.lives = lives;
        this.player.bombs = bombs;
        this.player.graze = graze;
        this.power = power;
        this.playerIndex = pIdx;
        this.bossMoveDir = bossMoveDir;
        this.prevBossX = this.bossX;
        this.prevBossY = this.bossY;
        this.bossX = bossX;
        this.bossY = bossY;
        this.bossHp = bossHp;
        this.bossMaxHp = bossMaxHp;
        this.bossPhase = bossPhase;
        this.skillGauge = skillGauge;
        this.chargeLevel = chargeLevel;
        this.holdChargeGauge = holdChargeGauge;
        this.abilityType = abilityType;
        this.abilityTicks = abilityTicks;
        this.abilityX = abilityX;
        this.abilityY = abilityY;
        this.abilityOwner = abilityOwner;
        this.score = score;
        this.combinedScore = combinedScore;
        this.spellTimerTicks = spellTimerTicks;
        this.spellTimerTotal = spellTimerTotal;
        if (!musicTrackId.isEmpty())
            this.currentMusicTrackId = musicTrackId;
        this.spellName = spellName;
        this.declaring = declaring;
        this.activeSpellCard = activeSpellCard;
        if (!declaring)
            this.declarationFrame = 0;
        if (!characterId.isEmpty())
            this.characterId = characterId;
        if (!bossId.isEmpty())
            this.bossId = bossId;
        if (!bossName.isEmpty())
            this.bossName = bossName;
        this.bossIntroVisible = bossIntroVisible;
        if (dialogLineIndex != this.dialogLineIndex)
            this.dialogSlideInTick = 0;
        this.dialogLineIndex = dialogLineIndex;
        this.dialogSpeaker = dialogSpeaker;
        this.dialogText = dialogText;
        this.dialogReadyCount = dialogReadyCount;
        this.dialogTotalCount = dialogTotalCount;
        this.pentagramRitualTick = pentagramRitualTick;
        this.pentagramStackCompleteTick = pentagramStackCompleteTick;
        this.grazeChain = grazeChain;
        this.lifePieces = lifePieces;
        this.bombPieces = bombPieces;
    }

    public void applyPlayerBulletSync(float[][] allSlotData, boolean[] allActive) {
        for (int i = 0; i < BulletPool.PLAYER_CAPACITY; i++)
            playerBullets.setSlotData(i, allSlotData[i], allActive[i]);
    }

    public void applyItemSync(int[] slots, float[][] data) {
        items.clearAll();
        for (int j = 0; j < slots.length; j++)
            items.setSlotData(slots[j], data[j], true);
    }

    public void applyEnemySync(int[] slots, float[][] data) {
        java.util.BitSet prevActive = new java.util.BitSet(EnemyPool.CAPACITY);
        for (int i = 0; i < EnemyPool.CAPACITY; i++)
            if (enemies.isActive(i)) prevActive.set(i);
        for (int j = 0; j < slots.length; j++) {
            enemies.setSlotData(slots[j], data[j], true);
            prevActive.clear(slots[j]);
        }
        for (int i = prevActive.nextSetBit(0); i >= 0; i = prevActive.nextSetBit(i + 1))
            enemies.deactivate(i);
    }

    public void applyCoopSync(List<Entry> entries) {
        coopPlayers.clear();
        coopPlayers.addAll(entries);
    }

    public void applyLaserSync(float[] data, boolean[] active, boolean[] bidir) {
        for (int i = 0; i < LaserPool.CAPACITY; i++) {
            if (lasers.active[i]) {
                lasers.prevX[i] = lasers.data[i * LaserPool.STRIDE];
                lasers.prevY[i] = lasers.data[i * LaserPool.STRIDE + 1];
            }
        }
        System.arraycopy(active, 0, lasers.active, 0, LaserPool.CAPACITY);
        System.arraycopy(bidir, 0, lasers.bidir, 0, LaserPool.CAPACITY);
        System.arraycopy(data, 0, lasers.data, 0, data.length);
        for (int i = 0; i < LaserPool.CAPACITY; i++) {
            if (lasers.active[i] && lasers.prevX[i] == 0 && lasers.prevY[i] == 0) {
                lasers.prevX[i] = lasers.data[i * LaserPool.STRIDE];
                lasers.prevY[i] = lasers.data[i * LaserPool.STRIDE + 1];
            }
        }
    }

    // ---------------------------------------------------------------- packet
    // overloads (single-argument, called from BHClientPackets)

    public void applyArenaState(ArenaStatePacket pkt) {
        applyArenaState(pkt.active, pkt.spectating, pkt.playerX, pkt.playerY,
                pkt.lives, pkt.bombs, pkt.graze, pkt.power, pkt.playerIndex,
                pkt.bossX, pkt.bossY, pkt.bossHp, pkt.bossMaxHp, pkt.bossPhase, pkt.bossMoveDir,
                pkt.skillGauge, pkt.chargeLevel, pkt.holdChargeGauge, pkt.abilityType, pkt.abilityTicks, pkt.abilityX, pkt.abilityY, pkt.abilityOwner,
                pkt.score, pkt.combinedScore, pkt.spellTimerTicks, pkt.spellTimerTotal,
                pkt.musicTrackId, pkt.spellName, pkt.activeSpellCard, pkt.declaring,
                pkt.characterId, pkt.bossId, pkt.bossName, pkt.bossIntroVisible,
                pkt.dialogSpeaker, pkt.dialogText, pkt.dialogLineIndex, pkt.dialogReadyCount, pkt.dialogTotalCount,
                pkt.pentagramRitualTick, pkt.pentagramStackCompleteTick,
                pkt.debugGodMode, pkt.debugArenaTick, pkt.debugPatternCooldown, pkt.debugEnemyBulletCount,
                pkt.grazeChain, pkt.lifePieces, pkt.bombPieces);
        if (pkt.active) {
            this.rank = pkt.rank;
            this.pocFraction = pkt.pocFraction;
            this.pocAutoCollect = pkt.pocAutoCollect;
            this.player.speedNormal = pkt.speedNormal;
            this.player.speedFocused = pkt.speedFocused;
            this.bossTexture = pkt.bossTexture;
        }
    }

    public void applyBulletDelta(BulletDeltaPacket pkt) {
        applyBulletDelta(pkt.changedSlots, pkt.slotData, pkt.isActive);
    }

    public void applyBulletFullSync(BulletFullSyncPacket pkt) {
        applyFullSync(pkt.allSlotData, pkt.allActive);
    }

    public void applyPlayerBulletsSync(PlayerBulletSyncPacket pkt) {
        applyPlayerBulletSync(pkt.allSlotData, pkt.allActive);
    }

    public void applyItemSync(ItemSyncPacket pkt) {
        applyItemSync(pkt.slots, pkt.data);
    }

    public void applyEnemySync(EnemySyncPacket pkt) {
        applyEnemySync(pkt.slots, pkt.data);
    }

    public void applyCoopSync(CoopPlayersSyncPacket pkt) {
        applyCoopSync(pkt.entries);
    }

    public void applyLaserSync(LaserSyncPacket pkt) {
        applyLaserSync(pkt.data, pkt.active, pkt.bidir);
    }

    public void applyAllPlayerBullets(AllPlayerBulletsSyncPacket pkt) {
        for (AllPlayerBulletsSyncPacket.PlayerBullets pb : pkt.players) {
            BulletPool pool = allPlayerBullets.computeIfAbsent(
                    pb.playerIndex(), idx -> new BulletPool(BulletPool.PLAYER_CAPACITY));
            // Track which slots were active before this packet so we can deactivate
            // any that the server no longer reports (they expired or were recycled).
            java.util.BitSet prevActive = new java.util.BitSet(BulletPool.PLAYER_CAPACITY);
            for (int i = 0; i < BulletPool.PLAYER_CAPACITY; i++)
                if (pool.isActive(i)) prevActive.set(i);

            for (int j = 0; j < pb.slots().length; j++) {
                int slot = pb.slots()[j];
                pool.setSlotData(slot, pb.data()[j], true);
                prevActive.clear(slot);
            }
            // Deactivate any slot that was active but absent from this packet
            for (int i = prevActive.nextSetBit(0); i >= 0; i = prevActive.nextSetBit(i + 1))
                pool.deactivate(i);

            // Mirror own bullets into the legacy playerBullets field
            if (pb.playerIndex() == playerIndex) {
                for (int j = 0; j < pb.slots().length; j++)
                    playerBullets.setSlotData(pb.slots()[j], pb.data()[j], true);
                // Deactivate legacy slots not in packet
                for (int i = 0; i < BulletPool.PLAYER_CAPACITY; i++)
                    if (playerBullets.isActive(i) && !pool.isActive(i))
                        playerBullets.deactivate(i);
            }
        }
        if (active) {
            BulletPool local = allPlayerBullets.get(playerIndex);
            if (local != null) {
                boolean anyNew = false;
                for (int i = 0; i < BulletPool.PLAYER_CAPACITY; i++) {
                    boolean now = local.isActive(i);
                    if (now && !prevLocalPlayerBulletsActive[i])
                        anyNew = true;
                    prevLocalPlayerBulletsActive[i] = now;
                }
                if (anyNew)
                    BHSfx.play(BHSounds.SHOOT::get);
            }
        }
    }

    public void applyGameEvent(GameEventPacket pkt) {
        GameEvent ev = pkt.event;
        switch (ev) {
            case HIT -> BHSfx.play(BHSounds.DEATH::get);
            case ENEMY_KILL -> BHSfx.play(BHSounds.KILL::get);
            case ITEM_PICK_UP -> BHSfx.play(BHSounds.PICK_UP::get, 0.35f);
            case ITEM_POWER_UP -> BHSfx.play(BHSounds.POWER_UP::get);
            case ITEM_ONE_UP, SCORE_EXTEND -> BHSfx.play(BHSounds.ONE_UP::get);
            default -> {
            }
        }
        ScreenFXQueue.INSTANCE.push(ev);
    }

    public void applyAttackActivationSfx(AttackActivationSfxPacket pkt) {
        BHSfx.play(BHSounds.resolveForActivationSfx(pkt.soundId));
    }

    // ---------------------------------------------------------------- animation

    /**
     * Called every client tick with the current horizontal input (negative=left,
     * positive=right, 0=idle).
     */
    public void updateAnimation(float dx) {
        if (dx < 0f) {
            if (animRow != 1) {
                animLeanFrame = 0;
                animIdleTick = 0;
            }
            animRow = 1;
            if (++animIdleTick >= 2) {
                animIdleTick = 0;
                if (animLeanFrame == 0)
                    animLeanFrame = 1;
                else
                    animLeanFrame = (animLeanFrame < 7) ? animLeanFrame + 1 : 1;
            }
        } else if (dx > 0f) {
            if (animRow != 2) {
                animLeanFrame = 0;
                animIdleTick = 0;
            }
            animRow = 2;
            if (++animIdleTick >= 2) {
                animIdleTick = 0;
                if (animLeanFrame == 0)
                    animLeanFrame = 1;
                else
                    animLeanFrame = (animLeanFrame < 7) ? animLeanFrame + 1 : 1;
            }
        } else {
            if (animLeanFrame > 0) {
                animLeanFrame--;
                animIdleTick = 0;
            } else {
                animRow = 0;
                if (++animIdleTick >= 3) {
                    animIdleTick = 0;
                    if (++animIdleFrame >= 8)
                        animIdleFrame = 0;
                }
            }
        }
    }

    // ---------------------------------------------------------------- reset

    public void reset() {
        active = false;
        spectating = false;
        pendingEndOverlay = false;
        debugGodMode = false;
        debugArenaTick = 0;
        debugPatternCooldown = 0;
        debugEnemyBulletCount = 0;
        grazeChain = 0;
        lifePieces = 0;
        bombPieces = 0;
        power = 0;
        skillGauge = 0;
        chargeLevel = 0;
        holdChargeGauge = 0;
        abilityType = 0;
        abilityTicks = 0;
        abilityX = 0f;
        abilityY = 0f;
        abilityOwner = new java.util.UUID(0, 0);
        currentMusicTrackId = "";
        characterId = "reimu";
        bossId = "";
        bossTexture = "";
        pentagramRitualTick = -1;
        pentagramStackCompleteTick = -1;
        bossName = "";
        bossIntroVisible = false;
        dialogSpeaker = "";
        dialogText = "";
        dialogLineIndex = -1;
        dialogReadyCount = 0;
        dialogTotalCount = 0;
        dialogSlideInTick = 0;
        animRow = 0;
        animLeanFrame = 0;
        animIdleFrame = 0;
        animIdleTick = 0;
        bossAnimCounter = 0;
        arenaAnimTick = 0;
        bossMoveDir = 0;
        bossX = 0f; bossY = 0f;
        prevBossX = 0f; prevBossY = 0f;
        inputDx = 0f; inputDy = 0f; inputFocused = false;
        predX = 0f; predY = 0f;
        testMode = false;
        testPage = 0;
        testBossIds.clear();
        testStageIds.clear();
        testWaveIds.clear();
        testCharIds.clear();
        testCurrentBossId  = "";
        testCurrentStageId = "";
        testCurrentWaveId  = "";
        testCurrentCharId  = "reimu";
        testCurrentDifficulty = 1;
        testScrollOffset      = 0; testSelectedIdx      = 0;
        testStageScrollOffset = 0; testStageSelectedIdx = 0;
        testWaveScrollOffset  = 0; testWaveSelectedIdx  = 0;
        testCharScrollOffset  = 0; testCharSelectedIdx  = 0;
        bullets.clearAll();
        playerBullets.clearAll();
        allPlayerBullets.clear();
        items.clearAll();
        enemies.clearAll();
        lasers.clearAll();
        coopPlayers.clear();
        Arrays.fill(prevLocalPlayerBulletsActive, false);
        ScreenFXQueue.INSTANCE.reset();
    }
}
