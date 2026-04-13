package mc.sayda.bullethell.forge.network;

import mc.sayda.bullethell.arena.BulletHellManager;
import mc.sayda.bullethell.boss.CharacterDefinition;
import mc.sayda.bullethell.boss.CharacterLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * C → S  |  sent by the joiner after picking a character on the CharacterSelectScreen.
 *
 * Carries:
 *  - hostUuid    — UUID of the player whose arena to join
 *  - characterId — character the joiner selected
 */
public class JoinMatchPacket {

    public final UUID   hostUuid;
    public final String characterId;

    public JoinMatchPacket(UUID hostUuid, String characterId) {
        this.hostUuid    = hostUuid;
        this.characterId = characterId;
    }

    // ---------------------------------------------------------------- codec

    public static void encode(JoinMatchPacket pkt, FriendlyByteBuf buf) {
        buf.writeUUID(pkt.hostUuid);
        buf.writeUtf(pkt.characterId);
    }

    public static JoinMatchPacket decode(FriendlyByteBuf buf) {
        return new JoinMatchPacket(buf.readUUID(), buf.readUtf());
    }

    // ---------------------------------------------------------------- handler  (runs on server)

    public static void handle(JoinMatchPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;
            // Don't let the host join their own arena as a coop player
            if (sender.getUUID().equals(pkt.hostUuid)) return;
            // Must not already be in a match
            if (BulletHellManager.INSTANCE.isInMatch(sender.getUUID())) return;

            CharacterDefinition charDef = CharacterLoader.load(pkt.characterId);
            BulletHellManager.INSTANCE.joinMatch(sender.getUUID(), pkt.hostUuid, charDef);

            // Send the initial full sync to the new participant
            mc.sayda.bullethell.arena.ArenaContext ctx2 =
                    BulletHellManager.INSTANCE.getArenaForPlayer(sender.getUUID());
            if (ctx2 == null) return;
            BHNetwork.CHANNEL.sendTo(
                    BulletFullSyncPacket.fromContext(ctx2),
                    sender.connection.connection,
                    net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
            BHNetwork.CHANNEL.sendTo(
                    new ArenaStatePacket(ctx2),
                    sender.connection.connection,
                    net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
        });
        ctx.get().setPacketHandled(true);
    }
}
