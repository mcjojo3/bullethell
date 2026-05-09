package mc.sayda.bullethell.network;

import net.minecraft.network.FriendlyByteBuf;

/** C → S: debug control actions (power, godmode) that don't restart the arena. */
public class TestControlPacket {

    public static final int TYPE_SET_POWER      = 0;
    public static final int TYPE_TOGGLE_GODMODE = 1;

    public final int type;
    public final int value;

    public TestControlPacket(int type, int value) {
        this.type  = type;
        this.value = value;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(type);
        buf.writeInt(value);
    }

    public static TestControlPacket decode(FriendlyByteBuf buf) {
        return new TestControlPacket(buf.readInt(), buf.readInt());
    }
}
