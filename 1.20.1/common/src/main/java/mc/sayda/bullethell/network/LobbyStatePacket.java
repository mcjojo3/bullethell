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

    /**
     * @param maxDifficultyOrdinal highest difficulty this member has unlocked the stage
     *                             on; {@code -1} when they cannot play it
     */
    public record Member(UUID uuid, String name, String characterId, boolean ready, int maxDifficultyOrdinal) {}

    /** Someone asking to join, shown under the roster for the host to answer. */
    public record Pending(UUID uuid, String name) {}

    /** Sent when the lobby is gone; the client closes its screen. */
    public final boolean closed;
    public final UUID hostUuid;
    public final String stageId;
    public final int difficultyOrdinal;
    public final boolean starting;
    public final List<Member> members;
    public final List<Pending> pending;

    public LobbyStatePacket(boolean closed, UUID hostUuid, String stageId, int difficultyOrdinal,
            boolean starting, List<Member> members, List<Pending> pending) {
        this.closed = closed;
        this.hostUuid = hostUuid;
        this.stageId = stageId != null ? stageId : "";
        this.difficultyOrdinal = difficultyOrdinal;
        this.starting = starting;
        this.members = members;
        this.pending = pending != null ? pending : List.of();
    }

    public static LobbyStatePacket of(LobbySession lobby) {
        List<Member> members = new ArrayList<>();
        for (LobbySession.Member m : lobby.members()) {
            members.add(new Member(m.uuid, m.name, m.characterId, m.isReady(), m.maxDifficultyOrdinal));
        }
        List<Pending> pending = new ArrayList<>();
        for (LobbySession.PendingJoin p : lobby.pendingJoins()) {
            pending.add(new Pending(p.uuid, p.name));
        }
        return new LobbyStatePacket(false, lobby.hostUuid, lobby.stageId,
                lobby.difficulty.ordinal(), lobby.starting, members, pending);
    }

    public static LobbyStatePacket closed() {
        return new LobbyStatePacket(true, new UUID(0, 0), "", 0, false, List.of(), List.of());
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
            buf.writeVarInt(m.maxDifficultyOrdinal() + 1); // -1 = locked, so shift into VarInt range
        }
        buf.writeVarInt(pending.size());
        for (Pending p : pending) {
            buf.writeUUID(p.uuid());
            buf.writeUtf(p.name());
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
            members.add(new Member(buf.readUUID(), buf.readUtf(), buf.readUtf(), buf.readBoolean(),
                    buf.readVarInt() - 1));
        }
        int np = buf.readVarInt();
        List<Pending> pending = new ArrayList<>(np);
        for (int i = 0; i < np; i++) {
            pending.add(new Pending(buf.readUUID(), buf.readUtf()));
        }
        return new LobbyStatePacket(closed, host, stageId, diff, starting, members, pending);
    }
}
