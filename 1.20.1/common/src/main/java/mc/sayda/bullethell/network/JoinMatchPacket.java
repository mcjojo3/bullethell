package mc.sayda.bullethell.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/** C → S | joiner confirmed a character - joins the host's arena as co-op. */
public class JoinMatchPacket {

    public final UUID   hostUuid;
    public final String characterId;

    public JoinMatchPacket(UUID hostUuid, String characterId) {
        this.hostUuid = hostUuid; this.characterId = characterId;
    }

    public void encode(FriendlyByteBuf buf) { buf.writeUUID(hostUuid); buf.writeUtf(characterId); }

    public static JoinMatchPacket decode(FriendlyByteBuf buf) {
        return new JoinMatchPacket(buf.readUUID(), buf.readUtf());
    }
}
