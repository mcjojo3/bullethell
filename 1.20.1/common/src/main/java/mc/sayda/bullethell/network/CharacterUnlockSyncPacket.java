package mc.sayda.bullethell.network;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.network.FriendlyByteBuf;

/** S -> C | full snapshot of character unlock progress for this player. */
public final class CharacterUnlockSyncPacket {
    public final Map<String, Integer> maxDifficultyByCharacter;

    public CharacterUnlockSyncPacket(Map<String, Integer> maxDifficultyByCharacter) {
        this.maxDifficultyByCharacter = maxDifficultyByCharacter != null ? maxDifficultyByCharacter : Map.of();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(maxDifficultyByCharacter.size());
        for (var e : maxDifficultyByCharacter.entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeVarInt(e.getValue());
        }
    }

    public static CharacterUnlockSyncPacket decode(FriendlyByteBuf buf) {
        int n = Math.max(0, buf.readVarInt());
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            String id = buf.readUtf();
            int max = buf.readVarInt();
            map.put(id, max);
        }
        return new CharacterUnlockSyncPacket(map);
    }
}
