package mc.sayda.bullethell.forge.network;

import mc.sayda.bullethell.arena.ArenaContext;
import mc.sayda.bullethell.arena.BulletHellManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C → S  |  on bomb key press
 * Zero-payload; the server looks up the sender's arena and activates the bomb.
 */
public class BombPacket {

    private static final BombPacket INSTANCE = new BombPacket();

    // ---------------------------------------------------------------- codec

    public static void encode(BombPacket pkt, FriendlyByteBuf buf) { /* no payload */ }

    public static BombPacket decode(FriendlyByteBuf buf) { return INSTANCE; }

    // ---------------------------------------------------------------- handler  (runs on server)

    public static void handle(BombPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;
            ArenaContext arena = BulletHellManager.INSTANCE.getArenaForPlayer(sender.getUUID());
            if (arena != null) arena.activateBomb(sender.getUUID());
        });
        ctx.get().setPacketHandled(true);
    }
}
