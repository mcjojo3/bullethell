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
 * Format: [playerCount] { [playerIndex(varint)] [activeCount(varint)] [slot(varint) data...] }...
 * Only active slots are transmitted, saving ~2 KB per player of boolean overhead
 * compared to the previous full-boolean-array layout.
 */
public class AllPlayerBulletsSyncPacket {

    public record PlayerBullets(int playerIndex, int[] slots, float[][] data) {}

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
            int count = pool.getActiveCount();
            int[] slots  = new int[count];
            float[][] data = new float[count][];
            int j = 0;
            for (int i = 0; i < cap && j < count; i++) {
                if (pool.isActive(i)) {
                    slots[j] = i;
                    data[j]  = pool.getSlotData(i);
                    j++;
                }
            }
            list.add(new PlayerBullets(idx, slots, data));
            idx++;
        }
        return new AllPlayerBulletsSyncPacket(list);
    }

    // ---------------------------------------------------------------- codec

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(players.size());
        for (PlayerBullets pb : players) {
            buf.writeVarInt(pb.playerIndex());
            buf.writeVarInt(pb.slots().length);
            for (int i = 0; i < pb.slots().length; i++) {
                buf.writeVarInt(pb.slots()[i]);
                for (float f : pb.data()[i]) buf.writeFloat(f);
            }
        }
    }

    public static AllPlayerBulletsSyncPacket decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<PlayerBullets> list = new ArrayList<>(count);
        for (int p = 0; p < count; p++) {
            int pidx   = buf.readVarInt();
            int active = buf.readVarInt();
            int[]    slots = new int[active];
            float[][] data = new float[active][];
            for (int i = 0; i < active; i++) {
                slots[i] = buf.readVarInt();
                data[i]  = new float[BulletPool.STRIDE];
                for (int j = 0; j < BulletPool.STRIDE; j++) data[i][j] = buf.readFloat();
            }
            list.add(new PlayerBullets(pidx, slots, data));
        }
        return new AllPlayerBulletsSyncPacket(list);
    }
}
