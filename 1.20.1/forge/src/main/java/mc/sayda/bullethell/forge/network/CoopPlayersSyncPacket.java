package mc.sayda.bullethell.forge.network;

import mc.sayda.bullethell.forge.client.ClientArenaState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * S → C  |  every tick while arena is active (when co-op players exist)
 *
 * Sends position, lives, tintColor of all OTHER players in the match
 * so the recipient can render them on-screen.
 *
 * The packet is sent to every participant; it only contains other people's data,
 * not the recipient's own state (that comes via ArenaStatePacket).
 */
public class CoopPlayersSyncPacket {

    public final List<Entry> entries;

    public record Entry(float x, float y, int lives, int tintColor) {}

    public CoopPlayersSyncPacket(List<Entry> entries) {
        this.entries = entries;
    }

    // ---------------------------------------------------------------- codec

    public static void encode(CoopPlayersSyncPacket pkt, FriendlyByteBuf buf) {
        buf.writeVarInt(pkt.entries.size());
        for (Entry e : pkt.entries) {
            buf.writeFloat(e.x());
            buf.writeFloat(e.y());
            buf.writeVarInt(e.lives());
            buf.writeInt(e.tintColor());
        }
    }

    public static CoopPlayersSyncPacket decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<Entry> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(new Entry(buf.readFloat(), buf.readFloat(),
                               buf.readVarInt(), buf.readInt()));
        }
        return new CoopPlayersSyncPacket(list);
    }

    // ---------------------------------------------------------------- handler

    public static void handle(CoopPlayersSyncPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientArenaState.INSTANCE.applyCoopSync(pkt));
        ctx.get().setPacketHandled(true);
    }
}
