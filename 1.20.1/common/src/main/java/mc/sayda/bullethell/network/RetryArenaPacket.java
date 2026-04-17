package mc.sayda.bullethell.network;

import net.minecraft.network.FriendlyByteBuf;

/** C → S | Player requests a retry of their last arena with the same stage/difficulty/character. */
public final class RetryArenaPacket {

    public final String stageId;
    public final String difficulty;
    public final String characterId;

    public RetryArenaPacket(String stageId, String difficulty, String characterId) {
        this.stageId = stageId != null ? stageId : "";
        this.difficulty = difficulty != null ? difficulty : "NORMAL";
        this.characterId = characterId != null ? characterId : "reimu";
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(stageId);
        buf.writeUtf(difficulty);
        buf.writeUtf(characterId);
    }

    public static RetryArenaPacket decode(FriendlyByteBuf buf) {
        return new RetryArenaPacket(buf.readUtf(), buf.readUtf(), buf.readUtf());
    }
}
