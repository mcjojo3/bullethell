package mc.sayda.bullethell.network;

import mc.sayda.bullethell.arena.LaserPool;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Arrays;

/** S → C | full laser pool every tick (small pool, cheap). */
public class LaserSyncPacket {

    public final float[]   data;
    public final boolean[] active;
    public final boolean[] bidir;

    public LaserSyncPacket(LaserPool pool) {
        this.data   = Arrays.copyOf(pool.data,   pool.data.length);
        this.active = Arrays.copyOf(pool.active, pool.active.length);
        this.bidir  = Arrays.copyOf(pool.bidir,  pool.bidir.length);
    }

    private LaserSyncPacket(float[] data, boolean[] active, boolean[] bidir) {
        this.data = data; this.active = active; this.bidir = bidir;
    }

    public void encode(FriendlyByteBuf buf) {
        for (boolean b : active) buf.writeBoolean(b);
        for (boolean b : bidir)  buf.writeBoolean(b);
        for (float  f : data)    buf.writeFloat(f);
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
}
