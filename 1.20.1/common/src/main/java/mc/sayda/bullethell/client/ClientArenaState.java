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
import mc.sayda.bullethell.network.GameEventPacket;
import mc.sayda.bullethell.network.ItemSyncPacket;
import mc.sayda.bullethell.network.LaserSyncPacket;
import mc.sayda.bullethell.network.PlayerBulletSyncPacket;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;

import mc.sayda.bullethell.network.CoopPlayersSyncPacket.Entry;
import java.util.ArrayList;
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

    public float bossX, bossY;
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

    /** True when this player is dead but watching the coop partner's run. */
    public boolean spectating = false;

    /** Operator {@code /bullethell debug} god-mode (from server). */
    public boolean debugGodMode = false;
    public int debugArenaTick = 0;
    public int debugPatternCooldown = 0;
    public int debugEnemyBulletCount = 0;

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
    /**
     * True during the pre-boss dialog intro so the boss sprite renders before the
     * fight starts.
     */
    public boolean bossIntroVisible = false;

    // --- boss sprite animation ---
    public int bossAnimCounter = 0;
    public boolean bossMoving = false;
    private float prevBossX = -1f;

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
            float bossX, float bossY, int bossHp, int bossMaxHp, int bossPhase,
            int skillGauge, int chargeLevel, int holdChargeGauge, int abilityType, int abilityTicks, float abilityX, float abilityY, java.util.UUID abilityOwner,
            long score, int spellTimerTicks, int spellTimerTotal,
            String musicTrackId, String spellName, boolean activeSpellCard, boolean declaring,
            String characterId, String bossId, String bossName, boolean bossIntroVisible,
            String dialogSpeaker, String dialogText, int dialogLineIndex, int dialogReadyCount, int dialogTotalCount,
            boolean debugGodMode, int debugArenaTick, int debugPatternCooldown, int debugEnemyBulletCount) {

        active = pktActive;
        spectating = pktSpectating;
        this.debugGodMode = debugGodMode;
        this.debugArenaTick = debugArenaTick;
        this.debugPatternCooldown = debugPatternCooldown;
        this.debugEnemyBulletCount = debugEnemyBulletCount;
        if (!active) {
            BHScaleManager.restoreOriginalScale();
            reset();
            net.minecraft.client.gui.screens.Screen currentScreen = Minecraft.getInstance().screen;
            if (currentScreen instanceof mc.sayda.bullethell.client.screen.ArenaPlayScreen ||
                    currentScreen instanceof mc.sayda.bullethell.client.screen.ArenaQuitScreen) {
                Minecraft.getInstance().setScreen(null);
            }
            return;
        }

        net.minecraft.client.gui.screens.Screen currentScreen = Minecraft.getInstance().screen;
        if (!(currentScreen instanceof mc.sayda.bullethell.client.screen.ArenaPlayScreen) &&
                !(currentScreen instanceof mc.sayda.bullethell.client.screen.ArenaQuitScreen)) {
            Minecraft.getInstance().setScreen(new mc.sayda.bullethell.client.screen.ArenaPlayScreen());
        }

        // GUI Scale Management
        BHScaleManager.applyIdealScale();

        this.player.x = playerX;
        this.player.y = playerY;
        this.player.lives = lives;
        this.player.bombs = bombs;
        this.player.graze = graze;
        this.power = power;
        this.playerIndex = pIdx;
        this.bossMoving = (prevBossX >= 0f) && (Math.abs(bossX - prevBossX) > 0.3f);
        this.prevBossX = bossX;
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
    }

    public void applyPlayerBulletSync(float[][] allSlotData, boolean[] allActive) {
        for (int i = 0; i < BulletPool.PLAYER_CAPACITY; i++)
            playerBullets.setSlotData(i, allSlotData[i], allActive[i]);
    }

    public void applyItemSync(float[][] allSlotData, boolean[] allActive) {
        for (int i = 0; i < ItemPool.CAPACITY; i++)
            items.setSlotData(i, allSlotData[i], allActive[i]);
    }

    public void applyEnemySync(float[][] allSlotData, boolean[] allActive) {
        for (int i = 0; i < EnemyPool.CAPACITY; i++)
            enemies.setSlotData(i, allSlotData[i], allActive[i]);
    }

    public void applyCoopSync(List<Entry> entries) {
        coopPlayers.clear();
        coopPlayers.addAll(entries);
    }

    public void applyLaserSync(float[] data, boolean[] active, boolean[] bidir) {
        System.arraycopy(active, 0, lasers.active, 0, LaserPool.CAPACITY);
        System.arraycopy(bidir, 0, lasers.bidir, 0, LaserPool.CAPACITY);
        System.arraycopy(data, 0, lasers.data, 0, data.length);
    }

    // ---------------------------------------------------------------- packet
    // overloads (single-argument, called from BHClientPackets)

    public void applyArenaState(ArenaStatePacket pkt) {
        applyArenaState(pkt.active, pkt.spectating, pkt.playerX, pkt.playerY,
                pkt.lives, pkt.bombs, pkt.graze, pkt.power, pkt.playerIndex,
                pkt.bossX, pkt.bossY, pkt.bossHp, pkt.bossMaxHp, pkt.bossPhase,
                pkt.skillGauge, pkt.chargeLevel, pkt.holdChargeGauge, pkt.abilityType, pkt.abilityTicks, pkt.abilityX, pkt.abilityY, pkt.abilityOwner,
                pkt.score, pkt.spellTimerTicks, pkt.spellTimerTotal,
                pkt.musicTrackId, pkt.spellName, pkt.activeSpellCard, pkt.declaring,
                pkt.characterId, pkt.bossId, pkt.bossName, pkt.bossIntroVisible,
                pkt.dialogSpeaker, pkt.dialogText, pkt.dialogLineIndex, pkt.dialogReadyCount, pkt.dialogTotalCount,
                pkt.debugGodMode, pkt.debugArenaTick, pkt.debugPatternCooldown, pkt.debugEnemyBulletCount);
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
        applyItemSync(pkt.allSlotData, pkt.allActive);
    }

    public void applyEnemySync(EnemySyncPacket pkt) {
        applyEnemySync(pkt.allSlotData, pkt.allActive);
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
            for (int i = 0; i < BulletPool.PLAYER_CAPACITY; i++)
                pool.setSlotData(i, pb.data()[i], pb.active()[i]);
            // Mirror own bullets into the legacy playerBullets field for renderers
            // that haven't been updated yet
            if (pb.playerIndex() == playerIndex)
                for (int i = 0; i < BulletPool.PLAYER_CAPACITY; i++)
                    playerBullets.setSlotData(i, pb.data()[i], pb.active()[i]);
        }
    }

    public void applyGameEvent(GameEventPacket pkt) {
        ScreenFXQueue.INSTANCE.push(pkt.event);
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
        debugGodMode = false;
        debugArenaTick = 0;
        debugPatternCooldown = 0;
        debugEnemyBulletCount = 0;
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
        bossMoving = false;
        prevBossX = -1f;
        bullets.clearAll();
        playerBullets.clearAll();
        allPlayerBullets.clear();
        items.clearAll();
        enemies.clearAll();
        lasers.clearAll();
        coopPlayers.clear();
        ScreenFXQueue.INSTANCE.reset();
    }
}
