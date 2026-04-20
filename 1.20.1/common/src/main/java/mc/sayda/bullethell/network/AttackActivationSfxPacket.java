package mc.sayda.bullethell.network;

import net.minecraft.network.FriendlyByteBuf;

/** S → C | play a short SFX when a boss attack step activates (see {@code PatternStep#activationSound}). */
public final class AttackActivationSfxPacket {

    private static final int MAX_ID_LEN = 192;

    public final String soundId;

    public AttackActivationSfxPacket(String soundId) {
        this.soundId = soundId;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(soundId, MAX_ID_LEN);
    }

    public static AttackActivationSfxPacket decode(FriendlyByteBuf buf) {
        return new AttackActivationSfxPacket(buf.readUtf(MAX_ID_LEN));
    }
}
