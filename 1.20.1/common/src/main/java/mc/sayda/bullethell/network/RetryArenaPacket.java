package mc.sayda.bullethell.network;

import net.minecraft.network.FriendlyByteBuf;

/** C → S | Player requests a retry of their last arena with the same stage/difficulty/character. */
public final class RetryArenaPacket {

    public final String stageId;
    public final String difficulty;
    public final String characterId;
    public final int    shotTypeOrdinal;

    public RetryArenaPacket(String stageId, String difficulty, String characterId, int shotTypeOrdinal) {
        this.stageId = stageId != null ? stageId : "";
        this.difficulty = difficulty != null ? difficulty : "NORMAL";
        this.characterId = characterId != null ? characterId : "reimu";
        this.shotTypeOrdinal = shotTypeOrdinal;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(stageId);
        buf.writeUtf(difficulty);
        buf.writeUtf(characterId);
        buf.writeByte(shotTypeOrdinal);
    }

    public static RetryArenaPacket decode(FriendlyByteBuf buf) {
        String st = buf.readUtf();
        String df = buf.readUtf();
        String ch = buf.readUtf();
        int shot = buf.readableBytes() > 0 ? (buf.readByte() & 0xFF) : 0;
        return new RetryArenaPacket(st, df, ch, shot);
    }
}
