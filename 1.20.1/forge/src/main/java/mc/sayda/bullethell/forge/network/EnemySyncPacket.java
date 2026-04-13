package mc.sayda.bullethell.forge.network;

import mc.sayda.bullethell.arena.ArenaContext;
import mc.sayda.bullethell.arena.EnemyPool;
import mc.sayda.bullethell.forge.client.ClientArenaState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S → C  |  every 2 ticks
 * Full snapshot of the enemy pool (64 slots × stride 7 floats).
 * Enemies are few enough that a full sync is cheaper than tracking dirty flags.
 */
public class EnemySyncPacket {

    private static final int CAP    = EnemyPool.CAPACITY;
    private static final int STRIDE = EnemyPool.STRIDE;

    public final float[][] allSlotData; // [CAP][STRIDE]
    public final boolean[] allActive;

    public EnemySyncPacket(float[][] data, boolean[] active) {
        this.allSlotData = data;
        this.allActive   = active;
    }

    // ---------------------------------------------------------------- factory

    public static EnemySyncPacket fromContext(ArenaContext ctx) {
        float[][] data   = new float[CAP][STRIDE];
        boolean[] active = new boolean[CAP];
        for (int i = 0; i < CAP; i++) {
            data[i]   = ctx.enemies.getSlotData(i);
            active[i] = ctx.enemies.isActive(i);
        }
        return new EnemySyncPacket(data, active);
    }

    // ---------------------------------------------------------------- codec

    public static void encode(EnemySyncPacket pkt, FriendlyByteBuf buf) {
        for (int i = 0; i < CAP; i++) {
            buf.writeBoolean(pkt.allActive[i]);
            if (pkt.allActive[i]) {
                for (float f : pkt.allSlotData[i]) buf.writeFloat(f);
            }
        }
    }

    public static EnemySyncPacket decode(FriendlyByteBuf buf) {
        float[][] data   = new float[CAP][STRIDE];
        boolean[] active = new boolean[CAP];
        for (int i = 0; i < CAP; i++) {
            active[i] = buf.readBoolean();
            if (active[i]) {
                for (int j = 0; j < STRIDE; j++) data[i][j] = buf.readFloat();
            }
        }
        return new EnemySyncPacket(data, active);
    }

    // ---------------------------------------------------------------- handler

    public static void handle(EnemySyncPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientArenaState state = ClientArenaState.INSTANCE;
            for (int i = 0; i < CAP; i++) {
                state.enemies.setSlotData(i, pkt.allSlotData[i], pkt.allActive[i]);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
