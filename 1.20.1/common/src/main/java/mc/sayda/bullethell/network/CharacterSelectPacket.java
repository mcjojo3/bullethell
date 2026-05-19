package mc.sayda.bullethell.network;

import mc.sayda.bullethell.arena.DifficultyConfig;
import net.minecraft.network.FriendlyByteBuf;

/** C → S | player confirmed character + difficulty on the select screen. */
public class CharacterSelectPacket {

    public final String           characterId;
    public final DifficultyConfig difficulty;
    public final String           stageId;
    /** Index into shot options ({@link mc.sayda.bullethell.boss.CharacterDefinition#shotOptions} or legacy {@code shotTypes}). */
    public final int              shotTypeOrdinal;
    public final boolean          practice;

    public CharacterSelectPacket(String characterId, DifficultyConfig difficulty, String stageId,
            int shotTypeOrdinal) {
        this(characterId, difficulty, stageId, shotTypeOrdinal, false);
    }

    public CharacterSelectPacket(String characterId, DifficultyConfig difficulty, String stageId,
            int shotTypeOrdinal, boolean practice) {
        this.characterId = characterId;
        this.difficulty = difficulty;
        this.stageId = stageId;
        this.shotTypeOrdinal = shotTypeOrdinal;
        this.practice = practice;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(characterId);
        buf.writeByte(difficulty.ordinal());
        buf.writeUtf(stageId);
        buf.writeByte(shotTypeOrdinal);
        buf.writeBoolean(practice);
    }

    public static CharacterSelectPacket decode(FriendlyByteBuf buf) {
        String cid = buf.readUtf();
        DifficultyConfig d = DifficultyConfig.fromId(buf.readByte() & 0xFF);
        String sid = buf.readUtf();
        int shot = (buf.readableBytes() > 0) ? (buf.readByte() & 0xFF) : 0;
        boolean prac = (buf.readableBytes() > 0) && buf.readBoolean();
        return new CharacterSelectPacket(cid, d, sid, shot, prac);
    }
}
