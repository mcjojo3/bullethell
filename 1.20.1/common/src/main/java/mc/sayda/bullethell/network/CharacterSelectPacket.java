package mc.sayda.bullethell.network;

import mc.sayda.bullethell.arena.DifficultyConfig;
import net.minecraft.network.FriendlyByteBuf;

/** C → S | player confirmed character + difficulty on the select screen. */
public class CharacterSelectPacket {

    public final String           characterId;
    public final DifficultyConfig difficulty;
    public final String           stageId;
    public final boolean          practice;

    public CharacterSelectPacket(String characterId, DifficultyConfig difficulty, String stageId) {
        this(characterId, difficulty, stageId, false);
    }

    public CharacterSelectPacket(String characterId, DifficultyConfig difficulty, String stageId,
            boolean practice) {
        this.characterId = characterId;
        this.difficulty = difficulty;
        this.stageId = stageId;
        this.practice = practice;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(characterId);
        buf.writeByte(difficulty.ordinal());
        buf.writeUtf(stageId);
        buf.writeBoolean(practice);
    }

    public static CharacterSelectPacket decode(FriendlyByteBuf buf) {
        String cid = buf.readUtf();
        DifficultyConfig d = DifficultyConfig.fromId(buf.readByte() & 0xFF);
        String sid = buf.readUtf();
        boolean prac = (buf.readableBytes() > 0) && buf.readBoolean();
        return new CharacterSelectPacket(cid, d, sid, prac);
    }
}
