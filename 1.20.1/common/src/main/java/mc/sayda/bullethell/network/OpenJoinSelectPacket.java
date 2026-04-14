package mc.sayda.bullethell.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/** S → C | signals the joiner to open the character select screen in join-mode. */
public class OpenJoinSelectPacket {
    public final UUID   hostUuid;
    public final String hostName;

    public OpenJoinSelectPacket(UUID hostUuid, String hostName) {
        this.hostUuid = hostUuid; this.hostName = hostName;
    }

    public void encode(FriendlyByteBuf buf) { buf.writeUUID(hostUuid); buf.writeUtf(hostName); }

    public static OpenJoinSelectPacket decode(FriendlyByteBuf buf) {
        return new OpenJoinSelectPacket(buf.readUUID(), buf.readUtf());
    }
}
