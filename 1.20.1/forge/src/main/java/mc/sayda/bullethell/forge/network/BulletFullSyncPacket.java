package mc.sayda.bullethell.forge.network;

import mc.sayda.bullethell.arena.ArenaContext;
import mc.sayda.bullethell.arena.BulletPool;
import mc.sayda.bullethell.forge.client.ClientArenaState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S → C  |  on arena start / join / desync recovery
 * Full snapshot of every slot in the bullet pool.
 */
public class BulletFullSyncPacket {

    public final float[][] allSlotData; // [CAPACITY][6]
    public final boolean[] allActive;   // [CAPACITY]

    public BulletFullSyncPacket(float[][] allSlotData, boolean[] allActive) {
        this.allSlotData = allSlotData;
        this.allActive   = allActive;
    }

    // ---------------------------------------------------------------- factory

    public static BulletFullSyncPacket fromContext(ArenaContext ctx) {
        int        cap    = BulletPool.CAPACITY;
        float[][]  data   = new float[cap][6];
        boolean[]  active = new boolean[cap];
        for (int i = 0; i < cap; i++) {
            data[i]   = ctx.bullets.getSlotData(i);
            active[i] = ctx.bullets.isActive(i);
        }
        return new BulletFullSyncPacket(data, active);
    }

    // ---------------------------------------------------------------- codec

    public static void encode(BulletFullSyncPacket pkt, FriendlyByteBuf buf) {
        for (int i = 0; i < BulletPool.CAPACITY; i++) {
            buf.writeBoolean(pkt.allActive[i]);
            if (pkt.allActive[i]) {
                for (float f : pkt.allSlotData[i]) buf.writeFloat(f);
            }
        }
    }

    public static BulletFullSyncPacket decode(FriendlyByteBuf buf) {
        int       cap    = BulletPool.CAPACITY;
        float[][] data   = new float[cap][6];
        boolean[] active = new boolean[cap];
        for (int i = 0; i < cap; i++) {
            active[i] = buf.readBoolean();
            if (active[i]) {
                for (int j = 0; j < 6; j++) data[i][j] = buf.readFloat();
            }
        }
        return new BulletFullSyncPacket(data, active);
    }

    // ---------------------------------------------------------------- handler

    public static void handle(BulletFullSyncPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientArenaState.INSTANCE.applyFullSync(pkt));
        ctx.get().setPacketHandled(true);
    }
}
