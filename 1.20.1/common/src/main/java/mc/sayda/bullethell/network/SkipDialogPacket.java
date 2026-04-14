package mc.sayda.bullethell.network;

import net.minecraft.network.FriendlyByteBuf;

/** C → S | advance (skipAll=false) or skip all (skipAll=true) dialog. */
public class SkipDialogPacket {
    public final boolean skipAll;
    public SkipDialogPacket(boolean skipAll) { this.skipAll = skipAll; }
    public void encode(FriendlyByteBuf buf) { buf.writeBoolean(skipAll); }
    public static SkipDialogPacket decode(FriendlyByteBuf buf) { return new SkipDialogPacket(buf.readBoolean()); }
}
