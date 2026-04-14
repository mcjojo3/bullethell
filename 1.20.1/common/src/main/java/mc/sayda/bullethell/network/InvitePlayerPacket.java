package mc.sayda.bullethell.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/** C2S: Host invites a specific player to join their arena. */
public class InvitePlayerPacket {

    public final UUID targetUuid;

    public InvitePlayerPacket(UUID targetUuid) {
        this.targetUuid = targetUuid;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(targetUuid);
    }

    public static InvitePlayerPacket decode(FriendlyByteBuf buf) {
        return new InvitePlayerPacket(buf.readUUID());
    }
}
