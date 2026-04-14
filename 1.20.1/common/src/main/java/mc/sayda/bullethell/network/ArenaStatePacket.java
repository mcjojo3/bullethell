package mc.sayda.bullethell.network;

import mc.sayda.bullethell.arena.ArenaContext;
import mc.sayda.bullethell.arena.PlayerState2D;
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
    public final int skillGauge, chargeLevel;
    public final int abilityType, abilityTicks;
    public final float abilityX, abilityY;
    public final UUID abilityOwner;
    public final long score;
    public final int spellTimerTicks, spellTimerTotal;
    public final String musicTrackId;
    public final String spellName;
    public final boolean activeSpellCard, declaring;
    public final String characterId, bossId, bossName;
    public final boolean bossIntroVisible;
    public final String dialogSpeaker, dialogText;
    public final int dialogLineIndex;
    public final int dialogReadyCount, dialogTotalCount;
    /** True when this player is dead but spectating coop partners. */
    public final boolean spectating;

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
        this.skillGauge = ps.skillGauge;
        this.chargeLevel = ps.chargeLevel;

        if (ctx.timeStopTicks > 0) {
            this.abilityType = 1;
            this.abilityTicks = ctx.timeStopTicks;
            this.abilityOwner = ctx.timeStopOwner;
            this.abilityX = 0f;
            this.abilityY = 0f;
        } else if (ctx.masterSparkTicks > 0) {
            this.abilityType = 2;
            this.abilityTicks = ctx.masterSparkTicks;
            this.abilityOwner = ctx.masterSparkOwner;
            this.abilityX = ctx.masterSparkX;
            this.abilityY = ctx.masterSparkY;
        } else {
            this.abilityType = 0;
            this.abilityTicks = 0;
            this.abilityOwner = new UUID(0, 0);
            this.abilityX = 0f;
            this.abilityY = 0f;
        }

        this.score = ctx.score.getScore();
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
        // Player is spectating when all their lives are spent (lives < 0)
        this.spectating = (ps.lives < 0);
    }

    public static ArenaStatePacket stopped() {
        return new ArenaStatePacket(false, false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0f, 0f, new UUID(0, 0),
                0, 0, 0, "", "", false, false, "reimu", "", "", false, "", "", 0, 0, 0);
    }

    private ArenaStatePacket(boolean active, boolean spectating,
            float px, float py, int lives, int bombs, int graze, int power, int pIdx,
            float bx, float by, int hp, int maxHp, int phase,
            int skillGauge, int chargeLevel, int abilityType, int abilityTicks, float abilityX, float abilityY, UUID abilityOwner,
            long score, int timerTicks, int timerTotal, String musicTrackId,
            String spellName, boolean activeSpellCard, boolean declaring,
            String characterId, String bossId, String bossName, boolean bossIntroVisible,
            String dialogSpeaker, String dialogText, int dialogLineIndex, int dialogReadyCount, int dialogTotalCount) {
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
        this.skillGauge = skillGauge;
        this.chargeLevel = chargeLevel;
        this.abilityType = abilityType;
        this.abilityTicks = abilityTicks;
        this.abilityX = abilityX;
        this.abilityY = abilityY;
        this.abilityOwner = abilityOwner;
        this.score = score;
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
        buf.writeVarInt(skillGauge);
        buf.writeVarInt(chargeLevel);
        buf.writeVarInt(abilityType);
        buf.writeVarInt(abilityTicks);
        buf.writeFloat(abilityX);
        buf.writeFloat(abilityY);
        buf.writeUUID(abilityOwner);
        buf.writeLong(score);
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
    }

    @SuppressWarnings("null")
    public static ArenaStatePacket decode(FriendlyByteBuf buf) {
        if (!buf.readBoolean())
            return stopped();
        boolean spectating = buf.readBoolean();
        return new ArenaStatePacket(true, spectating,
                buf.readFloat(), buf.readFloat(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readFloat(), buf.readFloat(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readUUID(),
                buf.readLong(),
                buf.readVarInt(), buf.readVarInt(),
                buf.readUtf(), buf.readUtf(),
                buf.readBoolean(), buf.readBoolean(),
                buf.readUtf(), buf.readUtf(), buf.readUtf(),
                buf.readBoolean(),
                buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }
}
