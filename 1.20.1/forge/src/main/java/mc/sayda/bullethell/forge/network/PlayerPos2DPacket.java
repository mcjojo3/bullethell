package mc.sayda.bullethell.forge.network;

import mc.sayda.bullethell.arena.ArenaContext;
import mc.sayda.bullethell.arena.BulletHellManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C → S  |  every client tick while arena is active
 * Carries directional input, focus/shoot flags for this tick so the server
 * can update the authoritative PlayerState2D.
 *
 * dx / dy are in {-1, 0, 1}; speed is applied server-side in PlayerState2D.move().
 */
public class PlayerPos2DPacket {

    public final float   dx;
    public final float   dy;
    public final boolean focused;   // Left Shift held
    public final boolean shooting;  // Z key held

    public PlayerPos2DPacket(float dx, float dy, boolean focused, boolean shooting) {
        this.dx       = dx;
        this.dy       = dy;
        this.focused  = focused;
        this.shooting = shooting;
    }

    // ---------------------------------------------------------------- codec

    public static void encode(PlayerPos2DPacket pkt, FriendlyByteBuf buf) {
        buf.writeFloat(pkt.dx);
        buf.writeFloat(pkt.dy);
        buf.writeBoolean(pkt.focused);
        buf.writeBoolean(pkt.shooting);
    }

    public static PlayerPos2DPacket decode(FriendlyByteBuf buf) {
        return new PlayerPos2DPacket(
                buf.readFloat(), buf.readFloat(),
                buf.readBoolean(), buf.readBoolean());
    }

    // ---------------------------------------------------------------- handler  (runs on server)

    public static void handle(PlayerPos2DPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;
            ArenaContext arena = BulletHellManager.INSTANCE.getArenaForPlayer(sender.getUUID());
            if (arena == null) return;
            mc.sayda.bullethell.arena.PlayerState2D ps = arena.getPlayerState(sender.getUUID());
            if (ps == null) return;
            ps.focused  = pkt.focused;
            ps.shooting = pkt.shooting;
            ps.move(pkt.dx, pkt.dy);
        });
        ctx.get().setPacketHandled(true);
    }
}
