package mc.sayda.bullethell.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * C → S | one lobby action.
 *
 * Folded into a single packet because every action is "a small payload plus which
 * action it is" - seven near-identical packet classes would be pure ceremony.
 */
public class LobbyActionPacket {

    public static final int SET_CHARACTER = 0;
    public static final int SET_READY     = 1;
    /** Host only: change the stage and difficulty everyone is about to play. */
    public static final int SET_RUN       = 2;
    /** Host only. */
    public static final int START         = 3;
    public static final int LEAVE         = 4;

    public final int action;
    /** characterId for SET_CHARACTER, stageId for SET_RUN; otherwise empty. */
    public final String text;
    /** ready flag for SET_READY, difficulty ordinal for SET_RUN; otherwise 0. */
    public final int value;

    public LobbyActionPacket(int action, String text, int value) {
        this.action = action;
        this.text = text != null ? text : "";
        this.value = value;
    }

    public static LobbyActionPacket setCharacter(String characterId) {
        return new LobbyActionPacket(SET_CHARACTER, characterId, 0);
    }

    public static LobbyActionPacket setReady(boolean ready) {
        return new LobbyActionPacket(SET_READY, "", ready ? 1 : 0);
    }

    public static LobbyActionPacket setRun(String stageId, int difficultyOrdinal) {
        return new LobbyActionPacket(SET_RUN, stageId, difficultyOrdinal);
    }

    public static LobbyActionPacket start() {
        return new LobbyActionPacket(START, "", 0);
    }

    public static LobbyActionPacket leave() {
        return new LobbyActionPacket(LEAVE, "", 0);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(action);
        buf.writeUtf(text);
        buf.writeVarInt(value);
    }

    public static LobbyActionPacket decode(FriendlyByteBuf buf) {
        return new LobbyActionPacket(buf.readVarInt(), buf.readUtf(), buf.readVarInt());
    }
}
