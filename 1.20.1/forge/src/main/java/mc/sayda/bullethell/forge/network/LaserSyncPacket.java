package mc.sayda.bullethell.forge.network;

import mc.sayda.bullethell.arena.LaserPool;
import mc.sayda.bullethell.forge.client.ClientArenaState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S → C  |  Sent every server tick to sync the laser pool to all arena participants.
 * Full sync every tick is cheaper than delta tracking given the small pool size.
 */
public class LaserSyncPacket {

    private final float[]   data;
    private final boolean[] active;
    private final boolean[] bidir;

    public LaserSyncPacket(LaserPool pool) {
        this.data   = java.util.Arrays.copyOf(pool.data,   pool.data.length);
        this.active = java.util.Arrays.copyOf(pool.active, pool.active.length);
        this.bidir  = java.util.Arrays.copyOf(pool.bidir,  pool.bidir.length);
    }

    private LaserSyncPacket(float[] data, boolean[] active, boolean[] bidir) {
        this.data   = data;
        this.active = active;
        this.bidir  = bidir;
    }

    // ---------------------------------------------------------------- codec

    public static void encode(LaserSyncPacket pkt, FriendlyByteBuf buf) {
        for (boolean b : pkt.active) buf.writeBoolean(b);
        for (boolean b : pkt.bidir)  buf.writeBoolean(b);
        for (float  f : pkt.data)    buf.writeFloat(f);
    }

    public static LaserSyncPacket decode(FriendlyByteBuf buf) {
        boolean[] active = new boolean[LaserPool.CAPACITY];
        for (int i = 0; i < LaserPool.CAPACITY; i++) active[i] = buf.readBoolean();
        boolean[] bidir = new boolean[LaserPool.CAPACITY];
        for (int i = 0; i < LaserPool.CAPACITY; i++) bidir[i]  = buf.readBoolean();
        float[] data = new float[LaserPool.CAPACITY * LaserPool.STRIDE];
        for (int i = 0; i < data.length; i++) data[i] = buf.readFloat();
        return new LaserSyncPacket(data, active, bidir);
    }

    // ---------------------------------------------------------------- handler

    public static void handle(LaserSyncPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            LaserPool pool = ClientArenaState.INSTANCE.lasers;
            System.arraycopy(pkt.active, 0, pool.active, 0, LaserPool.CAPACITY);
            System.arraycopy(pkt.bidir,  0, pool.bidir,  0, LaserPool.CAPACITY);
            System.arraycopy(pkt.data,   0, pool.data,   0, pkt.data.length);
        });
        ctx.get().setPacketHandled(true);
    }
}
