package mc.sayda.bullethell.network;

import mc.sayda.bullethell.arena.ArenaContext;
import mc.sayda.bullethell.arena.BulletPool;
import net.minecraft.network.FriendlyByteBuf;

/** S → C | full bullet pool snapshot on arena start or desync recovery. */
public class BulletFullSyncPacket {

    public final float[][] allSlotData;
    public final boolean[] allActive;

    public BulletFullSyncPacket(float[][] allSlotData, boolean[] allActive) {
        this.allSlotData = allSlotData;
        this.allActive   = allActive;
    }

    public static BulletFullSyncPacket fromContext(ArenaContext ctx) {
        int cap = BulletPool.ENEMY_CAPACITY;
        float[][] data = new float[cap][6]; boolean[] active = new boolean[cap];
        for (int i = 0; i < cap; i++) { data[i] = ctx.bullets.getSlotData(i); active[i] = ctx.bullets.isActive(i); }
        return new BulletFullSyncPacket(data, active);
    }

    public void encode(FriendlyByteBuf buf) {
        for (int i = 0; i < BulletPool.ENEMY_CAPACITY; i++) {
            buf.writeBoolean(allActive[i]);
            if (allActive[i]) for (float f : allSlotData[i]) buf.writeFloat(f);
        }
    }

    public static BulletFullSyncPacket decode(FriendlyByteBuf buf) {
        int cap = BulletPool.ENEMY_CAPACITY;
        float[][] data = new float[cap][6]; boolean[] active = new boolean[cap];
        for (int i = 0; i < cap; i++) {
            active[i] = buf.readBoolean();
            if (active[i]) for (int j = 0; j < 6; j++) data[i][j] = buf.readFloat();
        }
        return new BulletFullSyncPacket(data, active);
    }
}
