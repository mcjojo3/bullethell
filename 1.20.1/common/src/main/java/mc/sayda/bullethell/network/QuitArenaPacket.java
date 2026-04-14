package mc.sayda.bullethell.network;

import net.minecraft.network.FriendlyByteBuf;

/** C → S | player chose "Yes" in ArenaQuitScreen. Zero payload. */
public class QuitArenaPacket {
    public static final QuitArenaPacket INSTANCE = new QuitArenaPacket();
    public void encode(FriendlyByteBuf buf) {}
    public static QuitArenaPacket decode(FriendlyByteBuf buf) { return INSTANCE; }
}
