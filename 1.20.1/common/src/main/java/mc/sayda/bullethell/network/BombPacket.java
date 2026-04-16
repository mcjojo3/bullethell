package mc.sayda.bullethell.network;

import net.minecraft.network.FriendlyByteBuf;

/** C → S | on bomb key press - zero payload. */
public class BombPacket {
    public static final BombPacket INSTANCE = new BombPacket();
    public void encode(FriendlyByteBuf buf) {}
    public static BombPacket decode(FriendlyByteBuf buf) { return INSTANCE; }
}
