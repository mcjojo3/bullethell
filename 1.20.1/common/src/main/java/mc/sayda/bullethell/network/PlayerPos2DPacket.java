package mc.sayda.bullethell.network;

import net.minecraft.network.FriendlyByteBuf;

/** C → S | every client tick — directional input + focus/shoot/charge flags. */
public class PlayerPos2DPacket {

    public final float   dx, dy;
    public final boolean focused, shooting, charging;

    public PlayerPos2DPacket(float dx, float dy, boolean focused, boolean shooting, boolean charging) {
        this.dx = dx; this.dy = dy;
        this.focused = focused; this.shooting = shooting; this.charging = charging;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat(dx); buf.writeFloat(dy);
        buf.writeBoolean(focused); buf.writeBoolean(shooting); buf.writeBoolean(charging);
    }

    public static PlayerPos2DPacket decode(FriendlyByteBuf buf) {
        return new PlayerPos2DPacket(buf.readFloat(), buf.readFloat(),
                buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
    }
}
