package mc.sayda.bullethell.network;

import net.minecraft.network.FriendlyByteBuf;

/** C → S | Player requests a retry of their last arena with the same stage/difficulty/character. */
public final class RetryArenaPacket {

    public final String stageId;
    public final String difficulty;
    public final String characterId;
    /**
     * The player left the results screen rather than asking for a rematch. In a co-op
     * party that withdraws them from the retry vote and releases everyone still waiting
     * on it; solo it does nothing.
     */
    public final boolean cancel;

    public RetryArenaPacket(String stageId, String difficulty, String characterId) {
        this(stageId, difficulty, characterId, false);
    }

    public RetryArenaPacket(String stageId, String difficulty, String characterId, boolean cancel) {
        this.stageId = stageId != null ? stageId : "";
        this.difficulty = difficulty != null ? difficulty : "NORMAL";
        this.characterId = characterId != null ? characterId : "reimu";
        this.cancel = cancel;
    }

    /** Withdrawal - the server takes the run's details from its own record. */
    public static RetryArenaPacket cancel() {
        return new RetryArenaPacket("", "NORMAL", "reimu", true);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(stageId);
        buf.writeUtf(difficulty);
        buf.writeUtf(characterId);
        buf.writeBoolean(cancel);
    }

    public static RetryArenaPacket decode(FriendlyByteBuf buf) {
        String st = buf.readUtf();
        String df = buf.readUtf();
        String ch = buf.readUtf();
        boolean cancel = buf.readBoolean();
        return new RetryArenaPacket(st, df, ch, cancel);
    }
}
