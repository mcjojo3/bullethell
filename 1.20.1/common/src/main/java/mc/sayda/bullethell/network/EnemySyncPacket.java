package mc.sayda.bullethell.network;

import mc.sayda.bullethell.arena.ArenaContext;
import mc.sayda.bullethell.arena.EnemyPool;
import net.minecraft.network.FriendlyByteBuf;

/** S → C | full enemy pool snapshot every 2 ticks. */
public class EnemySyncPacket {

    private static final int CAP    = EnemyPool.CAPACITY;
    private static final int STRIDE = EnemyPool.STRIDE;

    public final float[][] allSlotData;
    public final boolean[] allActive;

    public EnemySyncPacket(float[][] data, boolean[] active) {
        this.allSlotData = data; this.allActive = active;
    }

    public static EnemySyncPacket fromContext(ArenaContext ctx) {
        float[][] data = new float[CAP][STRIDE]; boolean[] active = new boolean[CAP];
        for (int i = 0; i < CAP; i++) { data[i] = ctx.enemies.getSlotData(i); active[i] = ctx.enemies.isActive(i); }
        return new EnemySyncPacket(data, active);
    }

    public void encode(FriendlyByteBuf buf) {
        for (int i = 0; i < CAP; i++) {
            buf.writeBoolean(allActive[i]);
            if (allActive[i]) for (float f : allSlotData[i]) buf.writeFloat(f);
        }
    }

    public static EnemySyncPacket decode(FriendlyByteBuf buf) {
        float[][] data = new float[CAP][STRIDE]; boolean[] active = new boolean[CAP];
        for (int i = 0; i < CAP; i++) {
            active[i] = buf.readBoolean();
            if (active[i]) for (int j = 0; j < STRIDE; j++) data[i][j] = buf.readFloat();
        }
        return new EnemySyncPacket(data, active);
    }
}
