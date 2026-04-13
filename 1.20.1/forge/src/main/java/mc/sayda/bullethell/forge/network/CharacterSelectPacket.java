package mc.sayda.bullethell.forge.network;

import mc.sayda.bullethell.arena.DifficultyConfig;
import mc.sayda.bullethell.forge.event.BulletHellServerEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C → S  |  Player confirmed a character and pressed Start on the select screen.
 * The server starts the arena with the chosen character and difficulty.
 */
public class CharacterSelectPacket {

    public final String           characterId;
    public final DifficultyConfig difficulty;
    public final String           stageId;

    public CharacterSelectPacket(String characterId, DifficultyConfig difficulty, String stageId) {
        this.characterId = characterId;
        this.difficulty  = difficulty;
        this.stageId     = stageId;
    }

    // ---------------------------------------------------------------- codec

    public static void encode(CharacterSelectPacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.characterId);
        buf.writeByte(pkt.difficulty.ordinal());
        buf.writeUtf(pkt.stageId);
    }

    public static CharacterSelectPacket decode(FriendlyByteBuf buf) {
        String           charId = buf.readUtf();
        DifficultyConfig diff   = DifficultyConfig.values()[buf.readByte()];
        String           stage  = buf.readUtf();
        return new CharacterSelectPacket(charId, diff, stage);
    }

    // ---------------------------------------------------------------- handler

    public static void handle(CharacterSelectPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                BulletHellServerEvents.startArena(player, pkt.difficulty, pkt.stageId, pkt.characterId);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
