package mc.sayda.bullethell.network;

import mc.sayda.bullethell.arena.ArenaContext;
import mc.sayda.bullethell.arena.PlayerState2D;
import mc.sayda.bullethell.debug.BHDebugMode;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/**
 * S → C | every 2 ticks, and once on arena start / stop.
 * Player HUD state + boss + score + spell timer + music track + power level.
 * active == false tells the client to hide the overlay and stop music.
 */
public class ArenaStatePacket {

    public final boolean active;
    public final float playerX, playerY;
    public final int lives, bombs, graze, power, playerIndex;
    public final float bossX, bossY;
    public final int bossHp, bossMaxHp, bossPhase;
    public final int bossMoveDir;
    /** Local player's score. */
    public final long score;
    /** Sum of all co-op participants' scores (same as {@link #score} when alone). */
    public final long combinedScore;
    public final int spellTimerTicks, spellTimerTotal;
    public final String musicTrackId;
    public final String spellName;
    public final boolean activeSpellCard, declaring;
    public final String characterId, bossId, bossName;
    public final boolean bossIntroVisible;
    public final String dialogSpeaker, dialogText;
    public final int dialogLineIndex;
    public final int dialogReadyCount, dialogTotalCount;
    /** {@code PENTAGRAM_RITUAL} progress; -1 when inactive (see {@link ArenaContext#getPentagramRitualTick()}). */
    public final int pentagramRitualTick;
    /** Tick when outline stacking finished; -1 until then (see {@link ArenaContext#getPentagramStackCompleteTick()}). */
    public final int pentagramStackCompleteTick;
    /** True when this player is dead but spectating coop partners. */
    public final boolean spectating;

    /** {@link mc.sayda.bullethell.debug.BHDebugMode} god-mode bit for HUD (test mode enables on server). */
    public final boolean debugGodMode;
    /** Populated when {@link #debugGodMode} is true. */
    public final int debugArenaTick;
    public final int debugPatternCooldown;
    public final int debugEnemyBulletCount;

    /** Consecutive graze chain count (resets on hit or inactivity). */
    public final int grazeChain;
    /** Life piece count toward next extend (0 to PIECES_PER_EXTEND-1). */
    public final int lifePieces;
    /** Bomb piece count toward next extend (0 to PIECES_PER_EXTEND-1). */
    public final int bombPieces;
    /** Dynamic rank (0 = easiest, 32 = hardest). Synced so HUD can display it. */
    public final int rank;
    /** Y fraction from top defining the PoC line (mirrors {@code rules.pocFraction}). */
    public final float pocFraction;
    /** When false, PoC auto-collect is disabled (e.g. TH9 mode); hides the PoC line. */
    public final boolean pocAutoCollect;
    /** Character movement speed (normal / focused). Synced so client prediction uses correct values. */
    public final float speedNormal, speedFocused;
    /** Active boss texture override (empty = use default bossId texture). */
    public final String bossTexture;
    /**
     * True while a participant has the arena paused and the {@code globalPause} gamerule
     * is holding the fight for everyone. A client simulating locally has to be told -
     * it has no other way to know a different player opened a menu.
     */
    public final boolean globallyPaused;

    // ---------------------------------------------------------------- factory

    public ArenaStatePacket(ArenaContext ctx, UUID playerUuid, int playerIndex) {
        PlayerState2D ps = ctx.getPlayerState(playerUuid);
        if (ps == null)
            ps = ctx.player;

        this.active = true;
        this.playerX = ps.x;
        this.playerY = ps.y;
        this.lives = ps.lives;
        this.bombs = ps.bombs;
        this.graze = ps.graze;
        this.power = ps.power;
        this.playerIndex = playerIndex;
        this.bossX = ctx.bossX;
        this.bossY = ctx.bossY;
        this.bossHp = ctx.bossHp;
        this.bossMaxHp = ctx.bossMaxHp;
        this.bossPhase = ctx.bossPhase;
        this.bossMoveDir = ctx.getBossMoveDir();

        this.score = ctx.getScore(playerUuid);
        this.combinedScore = ctx.getCombinedScore();
        this.spellTimerTicks = ctx.spellcard.getRemainingTicks();
        this.spellTimerTotal = ctx.spellcard.getTotalTicks();
        String track = ctx.getCurrentMusicTrackId();
        this.musicTrackId = (track != null) ? track : "";
        this.spellName = ctx.getDisplaySpellName();
        this.activeSpellCard = ctx.isActiveSpellCard();
        this.declaring = ctx.isDeclaring();
        this.characterId = ctx.getCharacterId(playerUuid);
        this.bossId = ctx.boss != null ? ctx.boss.id : "";
        this.bossName = ctx.boss != null ? ctx.boss.name : "";
        this.bossIntroVisible = ctx.bossIntroVisible;
        this.dialogSpeaker = ctx.getDialogSpeaker(playerUuid);
        this.dialogText = ctx.getDialogText(playerUuid);
        this.dialogLineIndex = ctx.getDialogLineIndex(playerUuid);
        this.dialogReadyCount = ctx.getDialogReadyCount();
        this.dialogTotalCount = ctx.getDialogParticipantCount();
        this.pentagramRitualTick = ctx.getPentagramRitualTick();
        this.pentagramStackCompleteTick = ctx.getPentagramStackCompleteTick();
        // Player is spectating when all their lives are spent (lives < 0)
        this.spectating = (ps.lives < 0);

        boolean dbg = BHDebugMode.isGodMode(playerUuid);
        this.debugGodMode = dbg;
        boolean sendStats = dbg || ctx.testMode;
        this.debugArenaTick = sendStats ? ctx.getDebugArenaTick() : 0;
        this.debugPatternCooldown = sendStats ? ctx.getDebugBossPatternCooldown() : 0;
        this.debugEnemyBulletCount = sendStats ? ctx.bullets.getActiveCount() : 0;
        this.grazeChain = ps.grazeChain;
        this.lifePieces = ps.lifePieces;
        this.bombPieces = ps.bombPieces;
        this.rank = ctx.getRank();
        this.pocFraction = (float) ctx.rules.pocFraction;
        this.pocAutoCollect = ctx.rules.pocAutoCollect;
        this.speedNormal = ps.speedNormal;
        this.speedFocused = ps.speedFocused;
        this.bossTexture = ctx.getActiveBossTexture();
        this.globallyPaused = ctx.isGloballyPaused();
    }

    public static ArenaStatePacket stopped() {
        return new ArenaStatePacket(false, false,
                0f, 0f, 0, 0, 0, 0, 0,
                0f, 0f, 0, 0, 0, 0,
                0L, 0L, 0, 0, "", "", false, false,
                "reimu", "", "", false, "", "", 0, 0, 0, -1, -1,
                false, 0, 0, 0,
                0, 0, 0,
                16, 0.20f, true,
                PlayerState2D.SPEED_NORMAL, PlayerState2D.SPEED_FOCUSED, "", false);
    }

    private ArenaStatePacket(boolean active, boolean spectating,
            float px, float py, int lives, int bombs, int graze, int power, int pIdx,
            float bx, float by, int hp, int maxHp, int phase, int bossMoveDir,
            long score, long combinedScore, int timerTicks, int timerTotal, String musicTrackId,
            String spellName, boolean activeSpellCard, boolean declaring,
            String characterId, String bossId, String bossName, boolean bossIntroVisible,
            String dialogSpeaker, String dialogText, int dialogLineIndex, int dialogReadyCount, int dialogTotalCount,
            int pentagramRitualTick, int pentagramStackCompleteTick,
            boolean debugGodMode, int debugArenaTick, int debugPatternCooldown, int debugEnemyBulletCount,
            int grazeChain, int lifePieces, int bombPieces,
            int rank, float pocFraction, boolean pocAutoCollect,
            float speedNormal, float speedFocused, String bossTexture, boolean globallyPaused) {
        this.active = active;
        this.spectating = spectating;
        this.playerX = px;
        this.playerY = py;
        this.lives = lives;
        this.bombs = bombs;
        this.graze = graze;
        this.power = power;
        this.playerIndex = pIdx;
        this.bossX = bx;
        this.bossY = by;
        this.bossHp = hp;
        this.bossMaxHp = maxHp;
        this.bossPhase = phase;
        this.bossMoveDir = bossMoveDir;
        this.score = score;
        this.combinedScore = combinedScore;
        this.spellTimerTicks = timerTicks;
        this.spellTimerTotal = timerTotal;
        this.musicTrackId = musicTrackId;
        this.spellName = spellName;
        this.activeSpellCard = activeSpellCard;
        this.declaring = declaring;
        this.characterId = characterId;
        this.bossId = bossId;
        this.bossName = bossName;
        this.bossIntroVisible = bossIntroVisible;
        this.dialogSpeaker = dialogSpeaker;
        this.dialogText = dialogText;
        this.dialogLineIndex = dialogLineIndex;
        this.dialogReadyCount = dialogReadyCount;
        this.dialogTotalCount = dialogTotalCount;
        this.pentagramRitualTick = pentagramRitualTick;
        this.pentagramStackCompleteTick = pentagramStackCompleteTick;
        this.debugGodMode = debugGodMode;
        this.debugArenaTick = debugArenaTick;
        this.debugPatternCooldown = debugPatternCooldown;
        this.debugEnemyBulletCount = debugEnemyBulletCount;
        this.grazeChain = grazeChain;
        this.lifePieces = lifePieces;
        this.bombPieces = bombPieces;
        this.rank = rank;
        this.pocFraction = pocFraction;
        this.pocAutoCollect = pocAutoCollect;
        this.speedNormal = speedNormal;
        this.speedFocused = speedFocused;
        this.bossTexture = bossTexture;
        this.globallyPaused = globallyPaused;
    }

    // ---------------------------------------------------------------- codec

    @SuppressWarnings("null")
    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(active);
        if (!active)
            return;
        buf.writeBoolean(spectating);
        buf.writeFloat(playerX);
        buf.writeFloat(playerY);
        buf.writeVarInt(lives);
        buf.writeVarInt(bombs);
        buf.writeVarInt(graze);
        buf.writeVarInt(power);
        buf.writeVarInt(playerIndex);
        buf.writeFloat(bossX);
        buf.writeFloat(bossY);
        buf.writeVarInt(bossHp);
        buf.writeVarInt(bossMaxHp);
        buf.writeVarInt(bossPhase);
        buf.writeVarInt(bossMoveDir);
        buf.writeLong(score);
        buf.writeLong(combinedScore);
        buf.writeVarInt(spellTimerTicks);
        buf.writeVarInt(spellTimerTotal);
        buf.writeUtf(musicTrackId);
        buf.writeUtf(spellName);
        buf.writeBoolean(activeSpellCard);
        buf.writeBoolean(declaring);
        buf.writeUtf(characterId);
        buf.writeUtf(bossId);
        buf.writeUtf(bossName);
        buf.writeBoolean(bossIntroVisible);
        buf.writeUtf(dialogSpeaker);
        buf.writeUtf(dialogText);
        buf.writeVarInt(dialogLineIndex);
        buf.writeVarInt(dialogReadyCount);
        buf.writeVarInt(dialogTotalCount);
        buf.writeVarInt(pentagramRitualTick);
        buf.writeVarInt(pentagramStackCompleteTick);
        buf.writeBoolean(debugGodMode);
        buf.writeVarInt(debugArenaTick);
        buf.writeVarInt(debugPatternCooldown);
        buf.writeVarInt(debugEnemyBulletCount);
        buf.writeVarInt(grazeChain);
        buf.writeVarInt(lifePieces);
        buf.writeVarInt(bombPieces);
        buf.writeVarInt(rank);
        buf.writeFloat(pocFraction);
        buf.writeBoolean(pocAutoCollect);
        buf.writeFloat(speedNormal);
        buf.writeFloat(speedFocused);
        buf.writeUtf(bossTexture);
        buf.writeBoolean(globallyPaused);
    }

    @SuppressWarnings("null")
    public static ArenaStatePacket decode(FriendlyByteBuf buf) {
        if (!buf.readBoolean())
            return stopped();
        boolean spectating = buf.readBoolean();
        float px = buf.readFloat();
        float py = buf.readFloat();
        int lives = buf.readVarInt();
        int bombs = buf.readVarInt();
        int graze = buf.readVarInt();
        int power = buf.readVarInt();
        int pIdx = buf.readVarInt();
        float bx = buf.readFloat();
        float by = buf.readFloat();
        int hp = buf.readVarInt();
        int maxHp = buf.readVarInt();
        int phase = buf.readVarInt();
        int bossMoveDir = buf.readVarInt();
        long score = buf.readLong();
        long combinedScore = buf.readLong();
        int timerTicks = buf.readVarInt();
        int timerTotal = buf.readVarInt();
        String musicTrackId = buf.readUtf();
        String spellName = buf.readUtf();
        boolean activeSpellCard = buf.readBoolean();
        boolean declaring = buf.readBoolean();
        String characterId = buf.readUtf();
        String bossId = buf.readUtf();
        String bossName = buf.readUtf();
        boolean bossIntroVisible = buf.readBoolean();
        String dialogSpeaker = buf.readUtf();
        String dialogText = buf.readUtf();
        int dialogLineIndex = buf.readVarInt();
        int dialogReadyCount = buf.readVarInt();
        int dialogTotalCount = buf.readVarInt();
        int pentagramRitualTick = buf.readVarInt();
        int pentagramStackCompleteTick = buf.readVarInt();
        boolean dbgGod = buf.readBoolean();
        int dTick = buf.readVarInt();
        int dCd = buf.readVarInt();
        int dBul = buf.readVarInt();
        int grazeChain = buf.readVarInt();
        int lifePieces  = buf.readVarInt();
        int bombPieces  = buf.readVarInt();
        int rank = buf.readVarInt();
        float pocFraction = buf.readFloat();
        boolean pocAutoCollect = buf.readBoolean();
        float speedNormal = buf.readFloat();
        float speedFocused = buf.readFloat();
        String bossTexture = buf.readUtf();
        boolean globallyPaused = buf.readBoolean();
        return new ArenaStatePacket(true, spectating,
                px, py, lives, bombs, graze, power, pIdx,
                bx, by, hp, maxHp, phase, bossMoveDir,
                score, combinedScore, timerTicks, timerTotal,
                musicTrackId, spellName, activeSpellCard, declaring,
                characterId, bossId, bossName, bossIntroVisible,
                dialogSpeaker, dialogText, dialogLineIndex, dialogReadyCount, dialogTotalCount,
                pentagramRitualTick, pentagramStackCompleteTick,
                dbgGod, dTick, dCd, dBul,
                grazeChain, lifePieces, bombPieces,
                rank, pocFraction, pocAutoCollect,
                speedNormal, speedFocused, bossTexture, globallyPaused);
    }
}
