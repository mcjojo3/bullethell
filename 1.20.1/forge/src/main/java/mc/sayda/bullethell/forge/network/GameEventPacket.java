package mc.sayda.bullethell.forge.network;

import mc.sayda.bullethell.arena.GameEvent;
import mc.sayda.bullethell.forge.client.ClientArenaState;
import mc.sayda.bullethell.forge.client.ScreenFXQueue;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S → C  |  on game events (HIT, DEATH, PHASE_CHANGE, BOMB_USED, etc.)
 * Triggers screen FX on the client.
 */
public class GameEventPacket {

    public final GameEvent event;

    public GameEventPacket(GameEvent event) { this.event = event; }

    // ---------------------------------------------------------------- codec

    public static void encode(GameEventPacket pkt, FriendlyByteBuf buf) {
        buf.writeByte(pkt.event.ordinal());
    }

    public static GameEventPacket decode(FriendlyByteBuf buf) {
        return new GameEventPacket(GameEvent.fromId(buf.readByte()));
    }

    // ---------------------------------------------------------------- handler

    public static void handle(GameEventPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ScreenFXQueue.INSTANCE.push(pkt.event);

            // Handle arena-stop event
            if (pkt.event == GameEvent.DEATH && ClientArenaState.INSTANCE.player.lives <= 0) {
                ClientArenaState.INSTANCE.reset();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
