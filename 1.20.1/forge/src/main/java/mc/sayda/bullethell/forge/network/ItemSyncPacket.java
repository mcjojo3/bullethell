package mc.sayda.bullethell.forge.network;

import mc.sayda.bullethell.arena.ArenaContext;
import mc.sayda.bullethell.arena.ItemPool;
import mc.sayda.bullethell.forge.client.ClientArenaState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S → C  |  every 4 ticks
 * Full snapshot of the item pool (small capacity, so full sync is cheap).
 */
public class ItemSyncPacket {

    public final float[][] allSlotData; // [CAPACITY][5]
    public final boolean[] allActive;

    public ItemSyncPacket(float[][] data, boolean[] active) {
        this.allSlotData = data;
        this.allActive   = active;
    }

    // ---------------------------------------------------------------- factory

    public static ItemSyncPacket fromContext(ArenaContext ctx) {
        int        cap    = ItemPool.CAPACITY;
        float[][]  data   = new float[cap][5];
        boolean[]  active = new boolean[cap];
        for (int i = 0; i < cap; i++) {
            data[i]   = ctx.items.getSlotData(i);
            active[i] = ctx.items.isActive(i);
        }
        return new ItemSyncPacket(data, active);
    }

    // ---------------------------------------------------------------- codec

    public static void encode(ItemSyncPacket pkt, FriendlyByteBuf buf) {
        for (int i = 0; i < ItemPool.CAPACITY; i++) {
            buf.writeBoolean(pkt.allActive[i]);
            if (pkt.allActive[i]) {
                for (float f : pkt.allSlotData[i]) buf.writeFloat(f);
            }
        }
    }

    public static ItemSyncPacket decode(FriendlyByteBuf buf) {
        int       cap    = ItemPool.CAPACITY;
        float[][] data   = new float[cap][5];
        boolean[] active = new boolean[cap];
        for (int i = 0; i < cap; i++) {
            active[i] = buf.readBoolean();
            if (active[i]) {
                for (int j = 0; j < 5; j++) data[i][j] = buf.readFloat();
            }
        }
        return new ItemSyncPacket(data, active);
    }

    // ---------------------------------------------------------------- handler

    public static void handle(ItemSyncPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientArenaState state = ClientArenaState.INSTANCE;
            for (int i = 0; i < ItemPool.CAPACITY; i++) {
                state.items.setSlotData(i, pkt.allSlotData[i], pkt.allActive[i]);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
