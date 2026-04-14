package mc.sayda.bullethell.network;

import mc.sayda.bullethell.arena.ArenaContext;
import mc.sayda.bullethell.arena.ItemPool;
import net.minecraft.network.FriendlyByteBuf;

/** S → C | full item pool snapshot every 4 ticks. */
public class ItemSyncPacket {

    public final float[][] allSlotData;
    public final boolean[] allActive;

    public ItemSyncPacket(float[][] data, boolean[] active) {
        this.allSlotData = data; this.allActive = active;
    }

    public static ItemSyncPacket fromContext(ArenaContext ctx) {
        int cap = ItemPool.CAPACITY, stride = ItemPool.STRIDE;
        float[][] data = new float[cap][stride]; boolean[] active = new boolean[cap];
        for (int i = 0; i < cap; i++) { data[i] = ctx.items.getSlotData(i); active[i] = ctx.items.isActive(i); }
        return new ItemSyncPacket(data, active);
    }

    public void encode(FriendlyByteBuf buf) {
        for (int i = 0; i < ItemPool.CAPACITY; i++) {
            buf.writeBoolean(allActive[i]);
            if (allActive[i]) for (float f : allSlotData[i]) buf.writeFloat(f);
        }
    }

    public static ItemSyncPacket decode(FriendlyByteBuf buf) {
        int cap = ItemPool.CAPACITY, stride = ItemPool.STRIDE;
        float[][] data = new float[cap][stride]; boolean[] active = new boolean[cap];
        for (int i = 0; i < cap; i++) {
            active[i] = buf.readBoolean();
            if (active[i]) for (int j = 0; j < stride; j++) data[i][j] = buf.readFloat();
        }
        return new ItemSyncPacket(data, active);
    }
}
