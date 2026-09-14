package mc.sayda.bullethell.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * S → C | Where a co-op party's retry vote stands, which is what the end screen's
 * RETRY button reads out ("RETRY (1/2)").
 */
public final class RetryVotePacket {

    /** Still collecting votes: the button shows the count and the screen stays open. */
    public static final int STATE_WAITING = 0;
    /** Everyone asked for it and the rematch is starting, so the end screen closes. */
    public static final int STATE_STARTED = 1;
    /** The party broke up; the button goes back to starting a run on its own. */
    public static final int STATE_SOLO = 2;

    public final int votes;
    public final int total;
    public final int state;

    public RetryVotePacket(int votes, int total, int state) {
        this.votes = votes;
        this.total = total;
        this.state = state;
    }

    public static RetryVotePacket waiting(int votes, int total) {
        return new RetryVotePacket(votes, total, STATE_WAITING);
    }

    public static RetryVotePacket started(int total) {
        return new RetryVotePacket(total, total, STATE_STARTED);
    }

    public static RetryVotePacket solo() {
        return new RetryVotePacket(0, 1, STATE_SOLO);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(votes);
        buf.writeVarInt(total);
        buf.writeVarInt(state);
    }

    public static RetryVotePacket decode(FriendlyByteBuf buf) {
        return new RetryVotePacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }
}
