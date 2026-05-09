package mc.sayda.bullethell.network;

import mc.sayda.bullethell.arena.ArenaContext;
import mc.sayda.bullethell.arena.EnemyPool;
import net.minecraft.network.FriendlyByteBuf;

/**
 * S → C | compact enemy pool snapshot every tick.
 *
 * Format: [activeCount(varint)] { [slot(varint)] [STRIDE floats] }...
 * Only active slots are transmitted; inactive slots cost 0 bytes.
 */
public class EnemySyncPacket {

    private static final int STRIDE = EnemyPool.STRIDE;

    public final int[]    slots;
    public final float[][] data;

    public EnemySyncPacket(int[] slots, float[][] data) {
        this.slots = slots; this.data = data;
    }

    public static EnemySyncPacket fromContext(ArenaContext ctx) {
        int cap   = EnemyPool.CAPACITY;
        int count = ctx.enemies.getActiveCount();
        int[]    slots = new int[count];
        float[][] data = new float[count][];
        int j = 0;
        for (int i = 0; i < cap && j < count; i++) {
            if (ctx.enemies.isActive(i)) {
                slots[j] = i;
                data[j]  = ctx.enemies.getSlotData(i);
                j++;
            }
        }
        return new EnemySyncPacket(slots, data);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(slots.length);
        for (int i = 0; i < slots.length; i++) {
            buf.writeVarInt(slots[i]);
            for (float f : data[i]) buf.writeFloat(f);
        }
    }

    public static EnemySyncPacket decode(FriendlyByteBuf buf) {
        int count  = buf.readVarInt();
        int[]    slots = new int[count];
        float[][] data = new float[count][];
        for (int i = 0; i < count; i++) {
            slots[i] = buf.readVarInt();
            data[i]  = new float[STRIDE];
            for (int j = 0; j < STRIDE; j++) data[i][j] = buf.readFloat();
        }
        return new EnemySyncPacket(slots, data);
    }
}
