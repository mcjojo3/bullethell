package mc.sayda.bullethell.network;

import mc.sayda.bullethell.arena.ArenaContext;
import net.minecraft.network.FriendlyByteBuf;

/**
 * S → C | the boss moved to a new phase.
 *
 * The one resync barrier in the client-sim design. Between transitions the clients run
 * free and are allowed to disagree about bullet positions; at a transition they are all
 * snapped back onto the authority's phase, HP and music, so drift can never accumulate
 * across a whole fight. {@code epoch} makes the snap idempotent - a client that already
 * applied this transition ignores a repeat.
 */
public class PhaseTransitionPacket {

    public final int phaseIndex;
    public final int bossHp;
    public final int bossMaxHp;
    public final String musicId;
    public final int phaseEpoch;

    public PhaseTransitionPacket(int phaseIndex, int bossHp, int bossMaxHp, String musicId, int phaseEpoch) {
        this.phaseIndex = phaseIndex;
        this.bossHp = bossHp;
        this.bossMaxHp = bossMaxHp;
        this.musicId = musicId == null ? "" : musicId;
        this.phaseEpoch = phaseEpoch;
    }

    public static PhaseTransitionPacket fromContext(ArenaContext ctx) {
        return new PhaseTransitionPacket(ctx.bossPhase, ctx.bossHp, ctx.bossMaxHp,
                ctx.getCurrentMusicTrackId(), ctx.phaseEpoch);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(phaseIndex);
        buf.writeVarInt(bossHp);
        buf.writeVarInt(bossMaxHp);
        buf.writeUtf(musicId);
        buf.writeVarInt(phaseEpoch);
    }

    public static PhaseTransitionPacket decode(FriendlyByteBuf buf) {
        return new PhaseTransitionPacket(
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readUtf(),
                buf.readVarInt());
    }
}
