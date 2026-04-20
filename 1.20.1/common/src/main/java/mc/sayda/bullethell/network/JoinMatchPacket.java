package mc.sayda.bullethell.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/** C → S | joiner confirmed a character - joins the host's arena as co-op. */
public class JoinMatchPacket {

    public final UUID   hostUuid;
    public final String characterId;
    public final int    shotTypeOrdinal;

    public JoinMatchPacket(UUID hostUuid, String characterId, int shotTypeOrdinal) {
        this.hostUuid = hostUuid;
        this.characterId = characterId;
        this.shotTypeOrdinal = shotTypeOrdinal;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(hostUuid);
        buf.writeUtf(characterId);
        buf.writeByte(shotTypeOrdinal);
    }

    public static JoinMatchPacket decode(FriendlyByteBuf buf) {
        UUID h = buf.readUUID();
        String cid = buf.readUtf();
        int shot = buf.readableBytes() > 0 ? (buf.readByte() & 0xFF) : 0;
        return new JoinMatchPacket(h, cid, shot);
    }
}
