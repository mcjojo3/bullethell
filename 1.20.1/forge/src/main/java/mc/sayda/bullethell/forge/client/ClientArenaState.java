package mc.sayda.bullethell.forge.client;

import mc.sayda.bullethell.arena.BulletPool;
import mc.sayda.bullethell.arena.EnemyPool;
import mc.sayda.bullethell.arena.ItemPool;
import mc.sayda.bullethell.arena.LaserPool;
import mc.sayda.bullethell.arena.PlayerState2D;
import mc.sayda.bullethell.forge.network.ArenaStatePacket;
import mc.sayda.bullethell.forge.network.BulletDeltaPacket;
import mc.sayda.bullethell.forge.network.BulletFullSyncPacket;
import mc.sayda.bullethell.forge.network.CoopPlayersSyncPacket;

import java.util.ArrayList;
import java.util.List;

/** Client-side mirror of the server ArenaContext. Updated by incoming packets. */
public class ClientArenaState {

    public static final ClientArenaState INSTANCE = new ClientArenaState();

    // ---------------------------------------------------------------- state

    public boolean active = false;

    public final BulletPool    bullets       = new BulletPool(BulletPool.ENEMY_CAPACITY);
    public final BulletPool    playerBullets = new BulletPool(BulletPool.PLAYER_CAPACITY);
    public final ItemPool      items         = new ItemPool();
    public final EnemyPool     enemies       = new EnemyPool();
    public final PlayerState2D player        = new PlayerState2D();
    public final LaserPool     lasers        = new LaserPool();

    public float bossX, bossY;
    public int   bossHp, bossMaxHp, bossPhase;

    public long  score;
    public int   spellTimerTicks, spellTimerTotal;
    public int   power;

    /** Other players sharing this arena (excludes self). Updated by CoopPlayersSyncPacket. */
    public final List<CoopPlayersSyncPacket.Entry> coopPlayers = new ArrayList<>();

    /** Track ID for the current phase's music (empty = no music). Updated by ArenaStatePacket. */
    public String currentMusicTrackId = "";

    /** Character id of the local player (e.g. "reimu"). Used to look up the player sprite. */
    public String characterId = "reimu";

    /** Boss id for the current fight (e.g. "marisa_boss"). Used to look up the boss sprite. */
    public String bossId = "";

    // --- boss sprite animation ---
    /** Running tick counter for boss animation frame cycling. */
    public int     bossAnimCounter = 0;
    /**
     * True when the boss moved horizontally since the last ArenaStatePacket.
     * Selects row 1 (movement) vs row 0 (idle) on the boss sprite sheet.
     */
    public boolean bossMoving = false;
    /** Previous bossX, used to detect movement between packets. */
    private float  prevBossX  = -1f;

    // --- sprite sheet animation (row 0=idle, 1=lean-left, 2=lean-right; 8 frames per row) ---
    /** Current sprite sheet row: 0=idle, 1=left lean, 2=right lean. */
    public int animRow = 0;
    /** Current lean frame (0–7). Used when animRow != 0. */
    public int animLeanFrame = 0;
    /** Current idle animation frame (0–7). Used when animRow == 0. */
    public int animIdleFrame = 0;
    /** Tick counter used to pace the idle animation. */
    public int animIdleTick = 0;

    // spell card declaration
    /** Spell card name to show on screen; empty when no card is active or declaring. */
    public String  spellName      = "";
    /** True while the boss is in the inter-phase declaration animation. */
    public boolean declaring      = false;
    /** True when an active spell card phase is being fought (not during declaration). */
    public boolean activeSpellCard = false;
    /**
     * Client-side frame counter for the declaration fade-in animation.
     * Increments each client tick while {@link #declaring} is true, resets otherwise.
     */
    public int declarationFrame = 0;

    // pre-boss intro dialog
    /** Human-readable boss name (e.g. "Marisa Kirisame"). */
    public String bossName      = "";
    /** "BOSS" / "PLAYER" when a dialog line is active; empty otherwise. */
    public String dialogSpeaker = "";
    /** Text of the current dialog line. */
    public String dialogText    = "";
    /**
     * Server-side dialog line index. When this changes the client resets the
     * slide-in animation counter so each new line slides in fresh.
     */
    public int    dialogLineIndex     = -1;
    /**
     * Client render-tick counter for the dialog slide-in animation.
     * Increments each rendered frame while a dialog line is showing, resets
     * to 0 on each new line so the box slides in from off-screen again.
     */
    public int    dialogSlideInTick   = 0;

    // ---------------------------------------------------------------- packet handlers

    public void applyDelta(BulletDeltaPacket pkt) {
        for (int i = 0; i < pkt.changedSlots.length; i++)
            bullets.setSlotData(pkt.changedSlots[i], pkt.slotData[i], pkt.isActive[i]);
    }

    public void applyFullSync(BulletFullSyncPacket pkt) {
        for (int i = 0; i < BulletPool.ENEMY_CAPACITY; i++)
            bullets.setSlotData(i, pkt.allSlotData[i], pkt.allActive[i]);
        active = true;
    }

    public void applyArenaState(ArenaStatePacket pkt) {
        active = pkt.active;
        if (!active) { reset(); return; }
        player.x     = pkt.playerX;
        player.y     = pkt.playerY;
        player.lives = pkt.lives;
        player.bombs = pkt.bombs;
        player.graze = pkt.graze;
        power        = pkt.power;
        bossMoving   = (prevBossX >= 0f) && (Math.abs(pkt.bossX - prevBossX) > 0.3f);
        prevBossX    = pkt.bossX;
        bossX        = pkt.bossX;
        bossY        = pkt.bossY;
        bossHp       = pkt.bossHp;
        bossMaxHp    = pkt.bossMaxHp;
        bossPhase    = pkt.bossPhase;
        score        = pkt.score;
        spellTimerTicks      = pkt.spellTimerTicks;
        spellTimerTotal      = pkt.spellTimerTotal;
        if (!pkt.musicTrackId.isEmpty()) currentMusicTrackId = pkt.musicTrackId;
        spellName       = pkt.spellName;
        declaring       = pkt.declaring;
        activeSpellCard = pkt.activeSpellCard;
        if (!declaring) declarationFrame = 0;
        if (!pkt.characterId.isEmpty()) characterId = pkt.characterId;
        if (!pkt.bossId.isEmpty())      bossId      = pkt.bossId;
        if (!pkt.bossName.isEmpty())    bossName    = pkt.bossName;
        // Reset slide-in animation when a new dialog line arrives
        if (pkt.dialogLineIndex != dialogLineIndex) dialogSlideInTick = 0;
        dialogLineIndex = pkt.dialogLineIndex;
        dialogSpeaker   = pkt.dialogSpeaker;
        dialogText      = pkt.dialogText;
    }

    /**
     * Called every client tick with the current horizontal input (negative=left, positive=right, 0=idle).
     * Advances the sprite sheet lean animation toward the target direction, or unwinds back to idle.
     */
    public void updateAnimation(float dx) {
        if (dx < 0f) {
            if (animRow != 1) { animLeanFrame = 0; animIdleTick = 0; } // entering left lean
            animRow = 1;
            // Frame 0 is a one-shot transition; frames 1-7 are the loop.
            // Advance at 2 ticks/frame (~10 fps). When past frame 0, loop frames 1-7.
            if (++animIdleTick >= 2) {
                animIdleTick = 0;
                if (animLeanFrame == 0) {
                    animLeanFrame = 1; // play transition frame once, then enter loop
                } else {
                    animLeanFrame = (animLeanFrame < 7) ? animLeanFrame + 1 : 1;
                }
            }
        } else if (dx > 0f) {
            if (animRow != 2) { animLeanFrame = 0; animIdleTick = 0; } // entering right lean
            animRow = 2;
            if (++animIdleTick >= 2) {
                animIdleTick = 0;
                if (animLeanFrame == 0) {
                    animLeanFrame = 1;
                } else {
                    animLeanFrame = (animLeanFrame < 7) ? animLeanFrame + 1 : 1;
                }
            }
        } else {
            // No horizontal movement — unwind lean back toward idle (fast, 1 frame/tick)
            if (animLeanFrame > 0) {
                animLeanFrame--;
                animIdleTick = 0;
            } else {
                animRow = 0;
                if (++animIdleTick >= 3) { // ~7 fps idle cycle at 20 tps
                    animIdleTick = 0;
                    if (++animIdleFrame >= 8) animIdleFrame = 0;
                }
            }
        }
    }

    public void applyCoopSync(CoopPlayersSyncPacket pkt) {
        coopPlayers.clear();
        coopPlayers.addAll(pkt.entries);
    }

    // ---------------------------------------------------------------- reset

    public void reset() {
        active = false;
        currentMusicTrackId = "";
        characterId = "reimu";
        bossId = ""; bossName = "";
        dialogSpeaker = ""; dialogText = ""; dialogLineIndex = -1; dialogSlideInTick = 0;
        animRow = 0; animLeanFrame = 0; animIdleFrame = 0; animIdleTick = 0;
        bossAnimCounter = 0; bossMoving = false; prevBossX = -1f;
        bullets.clearAll();
        playerBullets.clearAll();
        items.clearAll();
        enemies.clearAll();
        lasers.clearAll();
        coopPlayers.clear();
        ScreenFXQueue.INSTANCE.reset();
    }
}
