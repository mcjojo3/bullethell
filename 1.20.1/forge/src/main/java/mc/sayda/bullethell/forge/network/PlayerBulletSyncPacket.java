package mc.sayda.bullethell.forge.network;

import mc.sayda.bullethell.arena.ArenaContext;
import mc.sayda.bullethell.arena.BulletPool;
import mc.sayda.bullethell.forge.client.ClientArenaState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S → C  |  every 2 ticks
 * Full snapshot of the player bullet pool (capacity 64 - small and cheap).
 */
public class PlayerBulletSyncPacket {

    private static final int CAP = BulletPool.PLAYER_CAPACITY;

    public final float[][] allSlotData; // [CAP][6]
    public final boolean[] allActive;

    public PlayerBulletSyncPacket(float[][] data, boolean[] active) {
        this.allSlotData = data;
        this.allActive   = active;
    }

    // ---------------------------------------------------------------- factory

    public static PlayerBulletSyncPacket fromContext(ArenaContext ctx) {
        return fromContextForPlayer(ctx, ctx.playerUuid);
    }

    /** Build a packet for a specific participant (host or co-op player). */
    public static PlayerBulletSyncPacket fromContextForPlayer(ArenaContext ctx, java.util.UUID playerUuid) {
        mc.sayda.bullethell.arena.BulletPool pool = ctx.getBulletPool(playerUuid);
        if (pool == null) pool = ctx.playerBullets; // fallback to host
        float[][] data   = new float[CAP][6];
        boolean[] active = new boolean[CAP];
        for (int i = 0; i < CAP; i++) {
            data[i]   = pool.getSlotData(i);
            active[i] = pool.isActive(i);
        }
        return new PlayerBulletSyncPacket(data, active);
    }

    // ---------------------------------------------------------------- codec

    public static void encode(PlayerBulletSyncPacket pkt, FriendlyByteBuf buf) {
        for (int i = 0; i < CAP; i++) {
            buf.writeBoolean(pkt.allActive[i]);
            if (pkt.allActive[i]) {
                for (float f : pkt.allSlotData[i]) buf.writeFloat(f);
            }
        }
    }

    public static PlayerBulletSyncPacket decode(FriendlyByteBuf buf) {
        float[][] data   = new float[CAP][6];
        boolean[] active = new boolean[CAP];
        for (int i = 0; i < CAP; i++) {
            active[i] = buf.readBoolean();
            if (active[i]) {
                for (int j = 0; j < 6; j++) data[i][j] = buf.readFloat();
            }
        }
        return new PlayerBulletSyncPacket(data, active);
    }

    // ---------------------------------------------------------------- handler

    public static void handle(PlayerBulletSyncPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientArenaState state = ClientArenaState.INSTANCE;
            for (int i = 0; i < CAP; i++) {
                state.playerBullets.setSlotData(i, pkt.allSlotData[i], pkt.allActive[i]);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
