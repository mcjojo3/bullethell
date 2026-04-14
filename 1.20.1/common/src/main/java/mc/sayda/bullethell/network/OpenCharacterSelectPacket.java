package mc.sayda.bullethell.network;

import net.minecraft.network.FriendlyByteBuf;

/** S → C | server signals client to open the level/difficulty select flow. */
public class OpenCharacterSelectPacket {
    public static final OpenCharacterSelectPacket INSTANCE = new OpenCharacterSelectPacket();
    public void encode(FriendlyByteBuf buf) {}
    public static OpenCharacterSelectPacket decode(FriendlyByteBuf buf) { return INSTANCE; }
}
