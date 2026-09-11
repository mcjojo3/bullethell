package mc.sayda.bullethell.network;

import net.minecraft.network.FriendlyByteBuf;

/** C → S | every client tick - directional input + focus/shoot flags. */
public class PlayerPos2DPacket {

    public final float   dx, dy;
    public final boolean focused, shooting;

    public PlayerPos2DPacket(float dx, float dy, boolean focused, boolean shooting) {
        this.dx = dx; this.dy = dy;
        this.focused = focused; this.shooting = shooting;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat(dx); buf.writeFloat(dy);
        buf.writeBoolean(focused); buf.writeBoolean(shooting);
    }

    public static PlayerPos2DPacket decode(FriendlyByteBuf buf) {
        return new PlayerPos2DPacket(buf.readFloat(), buf.readFloat(),
                buf.readBoolean(), buf.readBoolean());
    }
}
