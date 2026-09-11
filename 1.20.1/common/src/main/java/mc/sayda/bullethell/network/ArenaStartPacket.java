package mc.sayda.bullethell.network;

import mc.sayda.bullethell.arena.ArenaContext;
import mc.sayda.bullethell.arena.PlayerBonuses;
import net.minecraft.network.FriendlyByteBuf;

/**
 * S → C | everything a client needs to build its own copy of the fight.
 *
 * Sent once, when an arena starts with the client-sim gamerule on. The seed is the
 * whole point: both sides construct the same {@link ArenaContext} from it, so bullet
 * patterns match without a single bullet ever crossing the wire. Character and bonuses
 * travel too because they change starting lives, bombs and shot patterns, and the
 * client has no access to the player's attribute modifiers.
 */
public class ArenaStartPacket {

    public final long seed;
    public final String stageId;
    public final String characterId;
    public final int difficultyOrdinal;
    public final int extraLives;
    public final int extraBombs;
    public final int playerIndex;

    public ArenaStartPacket(long seed, String stageId, String characterId, int difficultyOrdinal,
            int extraLives, int extraBombs, int playerIndex) {
        this.seed = seed;
        this.stageId = stageId;
        this.characterId = characterId;
        this.difficultyOrdinal = difficultyOrdinal;
        this.extraLives = extraLives;
        this.extraBombs = extraBombs;
        this.playerIndex = playerIndex;
    }

    public PlayerBonuses bonuses() {
        return new PlayerBonuses(extraLives, extraBombs);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeLong(seed);
        buf.writeUtf(stageId);
        buf.writeUtf(characterId);
        buf.writeVarInt(difficultyOrdinal);
        buf.writeVarInt(extraLives);
        buf.writeVarInt(extraBombs);
        buf.writeVarInt(playerIndex);
    }

    public static ArenaStartPacket decode(FriendlyByteBuf buf) {
        return new ArenaStartPacket(
                buf.readLong(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt());
    }
}
