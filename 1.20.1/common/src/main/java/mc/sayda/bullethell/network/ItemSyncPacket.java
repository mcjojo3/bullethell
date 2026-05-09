package mc.sayda.bullethell.network;

import mc.sayda.bullethell.arena.ArenaContext;
import mc.sayda.bullethell.arena.ItemPool;
import net.minecraft.network.FriendlyByteBuf;

/**
 * S → C | compact item pool snapshot every 2 ticks.
 *
 * Format: [activeCount(varint)] { [slot(varint)] [STRIDE floats] }...
 * Only active slots are transmitted.
 */
public class ItemSyncPacket {

    private static final int STRIDE = ItemPool.STRIDE;

    public final int[]    slots;
    public final float[][] data;

    public ItemSyncPacket(int[] slots, float[][] data) {
        this.slots = slots; this.data = data;
    }

    public static ItemSyncPacket fromContext(ArenaContext ctx) {
        int cap   = ItemPool.CAPACITY;
        int count = ctx.items.getActiveCount();
        int[]    slots = new int[count];
        float[][] data = new float[count][];
        int j = 0;
        for (int i = 0; i < cap && j < count; i++) {
            if (ctx.items.isActive(i)) {
                slots[j] = i;
                data[j]  = ctx.items.getSlotData(i);
                j++;
            }
        }
        return new ItemSyncPacket(slots, data);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(slots.length);
        for (int i = 0; i < slots.length; i++) {
            buf.writeVarInt(slots[i]);
            for (float f : data[i]) buf.writeFloat(f);
        }
    }

    public static ItemSyncPacket decode(FriendlyByteBuf buf) {
        int count  = buf.readVarInt();
        int[]    slots = new int[count];
        float[][] data = new float[count][];
        for (int i = 0; i < count; i++) {
            slots[i] = buf.readVarInt();
            data[i]  = new float[STRIDE];
            for (int j = 0; j < STRIDE; j++) data[i][j] = buf.readFloat();
        }
        return new ItemSyncPacket(slots, data);
    }
}
