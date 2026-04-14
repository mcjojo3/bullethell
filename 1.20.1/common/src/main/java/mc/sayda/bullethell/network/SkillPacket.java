package mc.sayda.bullethell.network;

import net.minecraft.network.FriendlyByteBuf;

/** C → S | on X-key release — triggers character skill. Zero payload. */
public class SkillPacket {
    public static final SkillPacket INSTANCE = new SkillPacket();
    public void encode(FriendlyByteBuf buf) {}
    public static SkillPacket decode(FriendlyByteBuf buf) { return INSTANCE; }
}
