package mc.sayda.bullethell.forge.network;

import mc.sayda.bullethell.forge.client.ClientArenaState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S → C  |  every 2 ticks
 * Sends only the bullet slots that changed since the last packet (dirty flag).
 * Inactive (deactivated) slots are flagged with isActive=false; no float data follows.
 */
public class BulletDeltaPacket {

    public final int[]     changedSlots;
    public final float[][] slotData;   // [n][6]: only present when isActive[i] == true
    public final boolean[] isActive;

    public BulletDeltaPacket(int[] changedSlots, float[][] slotData, boolean[] isActive) {
        this.changedSlots = changedSlots;
        this.slotData     = slotData;
        this.isActive     = isActive;
    }

    // ---------------------------------------------------------------- codec

    public static void encode(BulletDeltaPacket pkt, FriendlyByteBuf buf) {
        buf.writeVarInt(pkt.changedSlots.length);
        for (int i = 0; i < pkt.changedSlots.length; i++) {
            buf.writeShort(pkt.changedSlots[i]);
            buf.writeBoolean(pkt.isActive[i]);
            if (pkt.isActive[i]) {
                for (float f : pkt.slotData[i]) buf.writeFloat(f);
            }
        }
    }

    public static BulletDeltaPacket decode(FriendlyByteBuf buf) {
        int      n      = buf.readVarInt();
        int[]     slots  = new int[n];
        float[][] data   = new float[n][6];
        boolean[] active = new boolean[n];
        for (int i = 0; i < n; i++) {
            slots[i]  = buf.readShort();
            active[i] = buf.readBoolean();
            if (active[i]) {
                for (int j = 0; j < 6; j++) data[i][j] = buf.readFloat();
            }
        }
        return new BulletDeltaPacket(slots, data, active);
    }

    // ---------------------------------------------------------------- handler

    public static void handle(BulletDeltaPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientArenaState.INSTANCE.applyDelta(pkt));
        ctx.get().setPacketHandled(true);
    }
}
