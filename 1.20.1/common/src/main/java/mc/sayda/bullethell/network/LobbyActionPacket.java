package mc.sayda.bullethell.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/**
 * C → S | one lobby action.
 *
 * Folded into a single packet because every action is "a small payload plus which
 * action it is" - near-identical packet classes per action would be pure ceremony.
 */
public class LobbyActionPacket {

    public static final int SET_CHARACTER  = 0;
    public static final int SET_READY      = 1;
    /** Host only: change the difficulty. The stage is fixed by the NPC the party came from. */
    public static final int SET_DIFFICULTY = 2;
    /** Host only. */
    public static final int START          = 3;
    public static final int LEAVE          = 4;
    /** Open a party for the stage in {@link #text}, hosted by the sender - Multiplayer on an NPC challenge. */
    public static final int CREATE         = 5;
    /** Host only: let in the join request from the player whose UUID is in {@link #text}. */
    public static final int ACCEPT_JOIN    = 6;
    /** Host only: turn down that join request. */
    public static final int DENY_JOIN      = 7;

    public final int action;
    /** characterId for SET_CHARACTER, stageId for CREATE, requester UUID for ACCEPT_JOIN / DENY_JOIN; otherwise empty. */
    public final String text;
    /** ready flag for SET_READY, difficulty ordinal for SET_DIFFICULTY; otherwise 0. */
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

    public static LobbyActionPacket setDifficulty(int difficultyOrdinal) {
        return new LobbyActionPacket(SET_DIFFICULTY, "", difficultyOrdinal);
    }

    public static LobbyActionPacket start() {
        return new LobbyActionPacket(START, "", 0);
    }

    public static LobbyActionPacket leave() {
        return new LobbyActionPacket(LEAVE, "", 0);
    }

    public static LobbyActionPacket create(String stageId) {
        return new LobbyActionPacket(CREATE, stageId, 0);
    }

    public static LobbyActionPacket acceptJoin(UUID requester) {
        return new LobbyActionPacket(ACCEPT_JOIN, requester.toString(), 0);
    }

    public static LobbyActionPacket denyJoin(UUID requester) {
        return new LobbyActionPacket(DENY_JOIN, requester.toString(), 0);
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
