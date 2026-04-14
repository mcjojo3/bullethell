package mc.sayda.bullethell.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * S → C | positions/states of all OTHER players in the arena (excludes
 * recipient).
 */
public class CoopPlayersSyncPacket {

    public record Entry(float x, float y, int lives, int tintColor, String characterId, int playerIndex) {
    }

    public final List<Entry> entries;

    public CoopPlayersSyncPacket(List<Entry> entries) {
        this.entries = entries;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (Entry e : entries) {
            buf.writeFloat(e.x());
            buf.writeFloat(e.y());
            buf.writeVarInt(e.lives());
            buf.writeInt(e.tintColor());
            buf.writeUtf(e.characterId());
            buf.writeVarInt(e.playerIndex());
        }
    }

    public static CoopPlayersSyncPacket decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<Entry> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            list.add(new Entry(buf.readFloat(), buf.readFloat(),
                    buf.readVarInt(), buf.readInt(), buf.readUtf(), buf.readVarInt()));
        return new CoopPlayersSyncPacket(list);
    }
}
