package mc.sayda.bullethell.network;

import mc.sayda.bullethell.arena.LobbySession;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * S → C | the whole party, pushed to every member on any change.
 *
 * Small enough to resend wholesale rather than diff, which keeps the client a pure
 * mirror - there is no partial state to get out of step.
 */
public class LobbyStatePacket {

    public record Member(UUID uuid, String name, String characterId, boolean ready) {}

    /** Sent when the lobby is gone; the client closes its screen. */
    public final boolean closed;
    public final UUID hostUuid;
    public final String stageId;
    public final int difficultyOrdinal;
    public final boolean starting;
    public final List<Member> members;

    public LobbyStatePacket(boolean closed, UUID hostUuid, String stageId, int difficultyOrdinal,
            boolean starting, List<Member> members) {
        this.closed = closed;
        this.hostUuid = hostUuid;
        this.stageId = stageId != null ? stageId : "";
        this.difficultyOrdinal = difficultyOrdinal;
        this.starting = starting;
        this.members = members;
    }

    public static LobbyStatePacket of(LobbySession lobby) {
        List<Member> members = new ArrayList<>();
        for (LobbySession.Member m : lobby.members()) {
            members.add(new Member(m.uuid, m.name, m.characterId, m.isReady()));
        }
        return new LobbyStatePacket(false, lobby.hostUuid, lobby.stageId,
                lobby.difficulty.ordinal(), lobby.starting, members);
    }

    public static LobbyStatePacket closed() {
        return new LobbyStatePacket(true, new UUID(0, 0), "", 0, false, List.of());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(closed);
        buf.writeUUID(hostUuid);
        buf.writeUtf(stageId);
        buf.writeVarInt(difficultyOrdinal);
        buf.writeBoolean(starting);
        buf.writeVarInt(members.size());
        for (Member m : members) {
            buf.writeUUID(m.uuid());
            buf.writeUtf(m.name());
            buf.writeUtf(m.characterId());
            buf.writeBoolean(m.ready());
        }
    }

    public static LobbyStatePacket decode(FriendlyByteBuf buf) {
        boolean closed = buf.readBoolean();
        UUID host = buf.readUUID();
        String stageId = buf.readUtf();
        int diff = buf.readVarInt();
        boolean starting = buf.readBoolean();
        int n = buf.readVarInt();
        List<Member> members = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            members.add(new Member(buf.readUUID(), buf.readUtf(), buf.readUtf(), buf.readBoolean()));
        }
        return new LobbyStatePacket(closed, host, stageId, diff, starting, members);
    }
}
