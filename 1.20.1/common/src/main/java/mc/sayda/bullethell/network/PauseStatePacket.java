package mc.sayda.bullethell.network;

import net.minecraft.network.FriendlyByteBuf;

/** C -> S | player opened/closed pause menu while in arena. */
public final class PauseStatePacket {
    public final boolean paused;

    public PauseStatePacket(boolean paused) {
        this.paused = paused;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(paused);
    }

    public static PauseStatePacket decode(FriendlyByteBuf buf) {
        return new PauseStatePacket(buf.readBoolean());
    }
}
