package mc.sayda.bullethell.network;

import mc.sayda.bullethell.BHControlScheme;
import net.minecraft.network.FriendlyByteBuf;

/** S → C | applies {@link BHControlScheme} on the client (e.g. after a {@code /bullethell controls} scheme argument). */
public final class ControlSchemePacket {

    public final BHControlScheme scheme;

    public ControlSchemePacket(BHControlScheme scheme) {
        this.scheme = scheme != null ? scheme : BHControlScheme.TH19;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(scheme.ordinal());
    }

    public static ControlSchemePacket decode(FriendlyByteBuf buf) {
        int o = buf.readUnsignedByte();
        BHControlScheme[] vals = BHControlScheme.values();
        if (o < 0 || o >= vals.length)
            return new ControlSchemePacket(BHControlScheme.TH19);
        return new ControlSchemePacket(vals[o]);
    }
}
