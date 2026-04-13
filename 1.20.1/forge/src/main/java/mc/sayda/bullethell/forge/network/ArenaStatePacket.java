package mc.sayda.bullethell.forge.network;

import mc.sayda.bullethell.arena.ArenaContext;
import mc.sayda.bullethell.forge.client.ClientArenaState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S → C  |  every 2 ticks, and once on arena start / stop
 * Player HUD state + boss + score + spell timer + music track + power level.
 * active == false tells the client to hide the overlay and stop music.
 */
public class ArenaStatePacket {

    public final boolean active;

    // player
    public final float playerX, playerY;
    public final int   lives, bombs, graze, power;

    // boss
    public final float bossX, bossY;
    public final int   bossHp, bossMaxHp, bossPhase;

    // score + timer
    public final long  score;
    public final int   spellTimerTicks;
    public final int   spellTimerTotal;

    // music - empty string = no change
    public final String musicTrackId;

    // spell card declaration
    /** Spell name to display; empty if none. During declaration shows incoming card name. */
    public final String  spellName;
    /** True if the current phase is an active spell card (not during declaration). */
    public final boolean activeSpellCard;
    /** True while the boss is drifting to centre between phases (declaration animation). */
    public final boolean declaring;

    // character — id string so the client can look up the sprite texture
    public final String characterId;

    // boss — id string so the client can look up the boss sprite texture
    public final String bossId;
    // boss display name (from BossDefinition.name)
    public final String bossName;

    // pre-boss intro dialog
    /** "BOSS" / "PLAYER" while dialog is active; empty otherwise. */
    public final String dialogSpeaker;
    /** Current line text; empty when no dialog is active. */
    public final String dialogText;
    /** Increments with each new line so the client can reset animations. */
    public final int    dialogLineIndex;

    // ---------------------------------------------------------------- factory: running arena

    /** Build for the host player. */
    public ArenaStatePacket(ArenaContext ctx) {
        this(ctx, ctx.playerUuid);
    }

    /** Build for any participant (host or co-op). Uses their PlayerState2D for HUD data. */
    public ArenaStatePacket(ArenaContext ctx, java.util.UUID playerUuid) {
        mc.sayda.bullethell.arena.PlayerState2D ps = ctx.getPlayerState(playerUuid);
        if (ps == null) ps = ctx.player; // fallback

        this.active          = true;
        this.playerX         = ps.x;
        this.playerY         = ps.y;
        this.lives           = ps.lives;
        this.bombs           = ps.bombs;
        this.graze           = ps.graze;
        this.power           = ps.power;
        this.bossX           = ctx.bossX;
        this.bossY           = ctx.bossY;
        this.bossHp          = ctx.bossHp;
        this.bossMaxHp       = ctx.bossMaxHp;
        this.bossPhase       = ctx.bossPhase;
        this.score           = ctx.score.getScore();
        this.spellTimerTicks = ctx.spellcard.getRemainingTicks();
        this.spellTimerTotal = ctx.spellcard.getTotalTicks();
        String track = ctx.getCurrentMusicTrackId();
        this.musicTrackId    = (track != null) ? track : "";
        this.spellName       = ctx.getDisplaySpellName();
        this.activeSpellCard = ctx.isActiveSpellCard();
        this.declaring       = ctx.isDeclaring();
        this.characterId     = ctx.getCharacterId(playerUuid);
        this.bossId          = ctx.boss != null ? ctx.boss.id   : "";
        this.bossName        = ctx.boss != null ? ctx.boss.name : "";
        this.dialogSpeaker   = ctx.getDialogSpeaker();
        this.dialogText      = ctx.getDialogText();
        this.dialogLineIndex = ctx.getDialogLineIndex();
    }

    public static ArenaStatePacket stopped() { return new ArenaStatePacket(); }

    private ArenaStatePacket() {
        active = false;
        playerX = playerY = bossX = bossY = 0;
        lives = bombs = graze = power = bossHp = bossMaxHp = bossPhase = 0;
        spellTimerTicks = spellTimerTotal = 0;
        score = 0L;
        musicTrackId = ""; spellName = "";
        activeSpellCard = false; declaring = false;
        characterId = "reimu"; bossId = ""; bossName = "";
        dialogSpeaker = ""; dialogText = ""; dialogLineIndex = 0;
    }

    // Full-field constructor for decode
    private ArenaStatePacket(float px, float py, int lives, int bombs, int graze, int power,
                              float bx, float by, int hp, int maxHp, int phase,
                              long score, int timerTicks, int timerTotal, String musicTrackId,
                              String spellName, boolean activeSpellCard, boolean declaring,
                              String characterId, String bossId, String bossName,
                              String dialogSpeaker, String dialogText, int dialogLineIndex) {
        this.active          = true;
        this.playerX         = px;    this.playerY  = py;
        this.lives           = lives; this.bombs    = bombs;
        this.graze           = graze; this.power    = power;
        this.bossX           = bx;    this.bossY    = by;
        this.bossHp          = hp;    this.bossMaxHp= maxHp; this.bossPhase = phase;
        this.score           = score;
        this.spellTimerTicks = timerTicks;
        this.spellTimerTotal = timerTotal;
        this.musicTrackId    = musicTrackId;
        this.spellName       = spellName;
        this.activeSpellCard = activeSpellCard;
        this.declaring       = declaring;
        this.characterId     = characterId;
        this.bossId          = bossId;
        this.bossName        = bossName;
        this.dialogSpeaker   = dialogSpeaker;
        this.dialogText      = dialogText;
        this.dialogLineIndex = dialogLineIndex;
    }

    // ---------------------------------------------------------------- codec

    public static void encode(ArenaStatePacket pkt, FriendlyByteBuf buf) {
        buf.writeBoolean(pkt.active);
        if (!pkt.active) return;
        buf.writeFloat(pkt.playerX);   buf.writeFloat(pkt.playerY);
        buf.writeVarInt(pkt.lives);    buf.writeVarInt(pkt.bombs);
        buf.writeVarInt(pkt.graze);    buf.writeVarInt(pkt.power);
        buf.writeFloat(pkt.bossX);     buf.writeFloat(pkt.bossY);
        buf.writeVarInt(pkt.bossHp);   buf.writeVarInt(pkt.bossMaxHp); buf.writeVarInt(pkt.bossPhase);
        buf.writeLong(pkt.score);
        buf.writeVarInt(pkt.spellTimerTicks);
        buf.writeVarInt(pkt.spellTimerTotal);
        buf.writeUtf(pkt.musicTrackId);
        buf.writeUtf(pkt.spellName);
        buf.writeBoolean(pkt.activeSpellCard);
        buf.writeBoolean(pkt.declaring);
        buf.writeUtf(pkt.characterId);
        buf.writeUtf(pkt.bossId);
        buf.writeUtf(pkt.bossName);
        buf.writeUtf(pkt.dialogSpeaker);
        buf.writeUtf(pkt.dialogText);
        buf.writeVarInt(pkt.dialogLineIndex);
    }

    public static ArenaStatePacket decode(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) return stopped();
        return new ArenaStatePacket(
                buf.readFloat(),    buf.readFloat(),
                buf.readVarInt(),   buf.readVarInt(),
                buf.readVarInt(),   buf.readVarInt(),
                buf.readFloat(),    buf.readFloat(),
                buf.readVarInt(),   buf.readVarInt(),   buf.readVarInt(),
                buf.readLong(),
                buf.readVarInt(),   buf.readVarInt(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readVarInt()
        );
    }

    // ---------------------------------------------------------------- handler

    public static void handle(ArenaStatePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientArenaState.INSTANCE.applyArenaState(pkt));
        ctx.get().setPacketHandled(true);
    }
}
