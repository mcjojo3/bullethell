package mc.sayda.bullethell.network;

import mc.sayda.bullethell.arena.ArenaContext;
import mc.sayda.bullethell.arena.BulletPool;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/** S → C | player bullet pool snapshot, per-participant. */
public class PlayerBulletSyncPacket {

    private static final int CAP = BulletPool.PLAYER_CAPACITY;

    public final float[][] allSlotData;
    public final boolean[] allActive;

    public PlayerBulletSyncPacket(float[][] data, boolean[] active) {
        this.allSlotData = data; this.allActive = active;
    }

    public static PlayerBulletSyncPacket fromContextForPlayer(ArenaContext ctx, UUID playerUuid) {
        BulletPool pool = ctx.getBulletPool(playerUuid);
        if (pool == null) pool = ctx.playerBullets;
        float[][] data = new float[CAP][6]; boolean[] active = new boolean[CAP];
        for (int i = 0; i < CAP; i++) { data[i] = pool.getSlotData(i); active[i] = pool.isActive(i); }
        return new PlayerBulletSyncPacket(data, active);
    }

    public void encode(FriendlyByteBuf buf) {
        for (int i = 0; i < CAP; i++) {
            buf.writeBoolean(allActive[i]);
            if (allActive[i]) for (float f : allSlotData[i]) buf.writeFloat(f);
        }
    }

    public static PlayerBulletSyncPacket decode(FriendlyByteBuf buf) {
        float[][] data = new float[CAP][6]; boolean[] active = new boolean[CAP];
        for (int i = 0; i < CAP; i++) {
            active[i] = buf.readBoolean();
            if (active[i]) for (int j = 0; j < 6; j++) data[i][j] = buf.readFloat();
        }
        return new PlayerBulletSyncPacket(data, active);
    }
}
