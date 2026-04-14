package mc.sayda.bullethell.network;

import net.minecraft.network.FriendlyByteBuf;

/** S → C | dirty enemy-bullet slots since last packet. */
public class BulletDeltaPacket {

    public final int[]     changedSlots;
    public final float[][] slotData;
    public final boolean[] isActive;

    public BulletDeltaPacket(int[] changedSlots, float[][] slotData, boolean[] isActive) {
        this.changedSlots = changedSlots;
        this.slotData     = slotData;
        this.isActive     = isActive;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(changedSlots.length);
        for (int i = 0; i < changedSlots.length; i++) {
            buf.writeShort(changedSlots[i]);
            buf.writeBoolean(isActive[i]);
            if (isActive[i]) for (float f : slotData[i]) buf.writeFloat(f);
        }
    }

    public static BulletDeltaPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        int[] slots = new int[n]; float[][] data = new float[n][6]; boolean[] active = new boolean[n];
        for (int i = 0; i < n; i++) {
            slots[i] = buf.readShort(); active[i] = buf.readBoolean();
            if (active[i]) for (int j = 0; j < 6; j++) data[i][j] = buf.readFloat();
        }
        return new BulletDeltaPacket(slots, data, active);
    }
}
