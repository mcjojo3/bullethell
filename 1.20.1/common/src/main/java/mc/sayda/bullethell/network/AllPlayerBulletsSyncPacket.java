package mc.sayda.bullethell.network;

import mc.sayda.bullethell.arena.ArenaContext;
import mc.sayda.bullethell.arena.BulletPool;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * S → C | snapshot of ALL participants' player-bullet pools, sent to every
 * client in the arena each tick so coop partners' shots are visible.
 *
 * Format: [playerCount] { [playerIndex(varint)] [active[CAP]] [data if active] }...
 */
public class AllPlayerBulletsSyncPacket {

    public record PlayerBullets(int playerIndex, float[][] data, boolean[] active) {}

    public final List<PlayerBullets> players;

    public AllPlayerBulletsSyncPacket(List<PlayerBullets> players) {
        this.players = players;
    }

    // ---------------------------------------------------------------- factory

    public static AllPlayerBulletsSyncPacket fromContext(ArenaContext ctx) {
        List<PlayerBullets> list = new ArrayList<>();
        int idx = 1;
        for (UUID pid : ctx.allParticipants()) {
            BulletPool pool = ctx.getBulletPool(pid);
            if (pool == null) { idx++; continue; }
            int cap = BulletPool.PLAYER_CAPACITY;
            float[][] data   = new float[cap][6];
            boolean[] active = new boolean[cap];
            for (int i = 0; i < cap; i++) {
                data[i]   = pool.getSlotData(i);
                active[i] = pool.isActive(i);
            }
            list.add(new PlayerBullets(idx, data, active));
            idx++;
        }
        return new AllPlayerBulletsSyncPacket(list);
    }

    // ---------------------------------------------------------------- codec

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(players.size());
        for (PlayerBullets pb : players) {
            buf.writeVarInt(pb.playerIndex());
            for (int i = 0; i < BulletPool.PLAYER_CAPACITY; i++) {
                buf.writeBoolean(pb.active()[i]);
                if (pb.active()[i]) for (float f : pb.data()[i]) buf.writeFloat(f);
            }
        }
    }

    public static AllPlayerBulletsSyncPacket decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<PlayerBullets> list = new ArrayList<>(count);
        for (int p = 0; p < count; p++) {
            int pidx = buf.readVarInt();
            float[][] data   = new float[BulletPool.PLAYER_CAPACITY][6];
            boolean[] active = new boolean[BulletPool.PLAYER_CAPACITY];
            for (int i = 0; i < BulletPool.PLAYER_CAPACITY; i++) {
                active[i] = buf.readBoolean();
                if (active[i]) for (int j = 0; j < 6; j++) data[i][j] = buf.readFloat();
            }
            list.add(new PlayerBullets(pidx, data, active));
        }
        return new AllPlayerBulletsSyncPacket(list);
    }
}
