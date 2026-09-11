package mc.sayda.bullethell.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * S → C | play a character cut-in.
 *
 * Carries the id rather than relying on a bare event, so a co-op partner sees the
 * bomber's portrait instead of their own. Boss cut-ins are driven client-side off the
 * existing declaration state and do not need this.
 */
public class SplashPacket {

    /** Texture basename under {@code textures/splash/}. */
    public final String id;

    public SplashPacket(String id) {
        this.id = id != null ? id : "";
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(id);
    }

    public static SplashPacket decode(FriendlyByteBuf buf) {
        return new SplashPacket(buf.readUtf());
    }
}
