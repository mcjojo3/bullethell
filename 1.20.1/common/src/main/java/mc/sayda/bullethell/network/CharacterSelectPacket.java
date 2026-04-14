package mc.sayda.bullethell.network;

import mc.sayda.bullethell.arena.DifficultyConfig;
import net.minecraft.network.FriendlyByteBuf;

/** C → S | player confirmed character + difficulty on the select screen. */
public class CharacterSelectPacket {

    public final String           characterId;
    public final DifficultyConfig difficulty;
    public final String           stageId;

    public CharacterSelectPacket(String characterId, DifficultyConfig difficulty, String stageId) {
        this.characterId = characterId; this.difficulty = difficulty; this.stageId = stageId;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(characterId); buf.writeByte(difficulty.ordinal()); buf.writeUtf(stageId);
    }

    public static CharacterSelectPacket decode(FriendlyByteBuf buf) {
        return new CharacterSelectPacket(buf.readUtf(), DifficultyConfig.values()[buf.readByte()], buf.readUtf());
    }
}
