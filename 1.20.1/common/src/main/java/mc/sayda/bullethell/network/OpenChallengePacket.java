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

    public OpenChallengePacket(String npcId, String stageId, String npcName, String challengeText) {
        this.npcId = npcId;
        this.stageId = stageId;
        this.npcName = npcName;
        this.challengeText = challengeText;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(npcId);
        buf.writeUtf(stageId);
        buf.writeUtf(npcName);
        buf.writeUtf(challengeText);
    }

    public static OpenChallengePacket decode(FriendlyByteBuf buf) {
        return new OpenChallengePacket(
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf());
    }
}
