package mc.sayda.bullethell.network;

import net.minecraft.network.FriendlyByteBuf;

/** C → S | player requests broadcasting their last run stats to all online players. */
public final class ShareLastRunPacket {
    public static final ShareLastRunPacket INSTANCE = new ShareLastRunPacket();

    private ShareLastRunPacket() {}

    public void encode(FriendlyByteBuf buf) {}

    public static ShareLastRunPacket decode(FriendlyByteBuf buf) {
        return INSTANCE;
    }
}
