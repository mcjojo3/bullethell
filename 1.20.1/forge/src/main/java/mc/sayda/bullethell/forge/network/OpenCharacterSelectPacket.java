package mc.sayda.bullethell.forge.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S → C  |  Sent when the player runs /bullethell start.
 * Tells the client to open the difficulty selection screen (first step).
 * Difficulty and character are both chosen client-side; the final selection
 * is sent back via {@link CharacterSelectPacket}.
 */
public class OpenCharacterSelectPacket {

    // No payload — the server just signals "open the flow"
    private static final OpenCharacterSelectPacket INSTANCE = new OpenCharacterSelectPacket();

    // ---------------------------------------------------------------- codec

    public static void encode(OpenCharacterSelectPacket pkt, FriendlyByteBuf buf) { /* no payload */ }

    public static OpenCharacterSelectPacket decode(FriendlyByteBuf buf) { return INSTANCE; }

    // ---------------------------------------------------------------- handler

    public static void handle(OpenCharacterSelectPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new mc.sayda.bullethell.forge.client.LevelSelectScreen());
        });
        ctx.get().setPacketHandled(true);
    }
}
