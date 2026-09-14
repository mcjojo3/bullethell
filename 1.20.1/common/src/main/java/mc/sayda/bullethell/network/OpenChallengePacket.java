package mc.sayda.bullethell.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * S → C: sent when a player right-clicks a {@link mc.sayda.bullethell.entity.BHNpc}.
 * Opens {@link mc.sayda.bullethell.client.screen.ChallengeScreen} on the client.
 */
public class OpenChallengePacket {

    public final String npcId;
    public final String stageId;
    public final String npcName;
    public final String challengeText;
    public final int maxAllowedDifficultyOrdinal;
    public final String requirementText;
    /** The server's AllowMultiplayer setting; false skips the singleplayer / multiplayer choice. */
    public final boolean allowMultiplayer;

    public OpenChallengePacket(String npcId, String stageId, String npcName, String challengeText,
            int maxAllowedDifficultyOrdinal, String requirementText, boolean allowMultiplayer) {
        this.npcId = npcId;
        this.stageId = stageId;
        this.npcName = npcName;
        this.challengeText = challengeText;
        this.maxAllowedDifficultyOrdinal = maxAllowedDifficultyOrdinal;
        this.requirementText = requirementText;
        this.allowMultiplayer = allowMultiplayer;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(npcId);
        buf.writeUtf(stageId);
        buf.writeUtf(npcName);
        buf.writeUtf(challengeText);
        buf.writeInt(maxAllowedDifficultyOrdinal);
        buf.writeUtf(requirementText);
        buf.writeBoolean(allowMultiplayer);
    }

    public static OpenChallengePacket decode(FriendlyByteBuf buf) {
        return new OpenChallengePacket(
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readInt(),
                buf.readUtf(),
                buf.readBoolean());
    }
}
