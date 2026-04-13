package mc.sayda.bullethell.forge.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * S → C  |  Sent when the player runs /bullethell join &lt;hostName&gt;.
 * Opens the character select screen in "join" mode so the player picks a
 * character; on confirm a {@link JoinMatchPacket} is sent back.
 */
public class OpenJoinSelectPacket {

    public final UUID   hostUuid;
    public final String hostName;

    public OpenJoinSelectPacket(UUID hostUuid, String hostName) {
        this.hostUuid = hostUuid;
        this.hostName = hostName;
    }

    // ---------------------------------------------------------------- codec

    public static void encode(OpenJoinSelectPacket pkt, FriendlyByteBuf buf) {
        buf.writeUUID(pkt.hostUuid);
        buf.writeUtf(pkt.hostName);
    }

    public static OpenJoinSelectPacket decode(FriendlyByteBuf buf) {
        return new OpenJoinSelectPacket(buf.readUUID(), buf.readUtf());
    }

    // ---------------------------------------------------------------- handler

    public static void handle(OpenJoinSelectPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new mc.sayda.bullethell.forge.client.JoinCharacterSelectScreen(
                    pkt.hostUuid, pkt.hostName));
        });
        ctx.get().setPacketHandled(true);
    }
}
