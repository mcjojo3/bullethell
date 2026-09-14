package mc.sayda.bullethell.client;

import mc.sayda.bullethell.network.LobbyStatePacket;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Client mirror of the party. Replaced wholesale by every {@link LobbyStatePacket}. */
@Environment(EnvType.CLIENT)
public final class ClientLobbyState {

    public static final ClientLobbyState INSTANCE = new ClientLobbyState();

    public boolean active;
    public UUID hostUuid;
    public String stageId = "";
    public int difficultyOrdinal = 1;
    public boolean starting;
    public final List<LobbyStatePacket.Member> members = new ArrayList<>();
    /** Join requests waiting on the host; empty for everyone else. */
    public final List<LobbyStatePacket.Pending> pending = new ArrayList<>();

    private ClientLobbyState() {}

    public void apply(LobbyStatePacket pkt) {
        if (pkt.closed) {
            reset();
            return;
        }
        active = true;
        hostUuid = pkt.hostUuid;
        stageId = pkt.stageId;
        difficultyOrdinal = pkt.difficultyOrdinal;
        starting = pkt.starting;
        members.clear();
        members.addAll(pkt.members);
        pending.clear();
        pending.addAll(pkt.pending);
    }

    public void reset() {
        active = false;
        hostUuid = null;
        stageId = "";
        difficultyOrdinal = 1;
        starting = false;
        members.clear();
        pending.clear();
    }

    public boolean isSelfHost() {
        UUID self = selfUuid();
        return self != null && self.equals(hostUuid);
    }

    public LobbyStatePacket.Member self() {
        UUID self = selfUuid();
        if (self == null) return null;
        for (LobbyStatePacket.Member m : members) {
            if (m.uuid().equals(self)) return m;
        }
        return null;
    }

    private static UUID selfUuid() {
        var player = Minecraft.getInstance().player;
        return player != null ? player.getUUID() : null;
    }

    public int readyCount() {
        int n = 0;
        for (LobbyStatePacket.Member m : members) {
            if (m.ready()) n++;
        }
        return n;
    }

    public boolean allReady() {
        return !members.isEmpty() && readyCount() == members.size();
    }

    /** Highest difficulty everyone in the party has unlocked; -1 if anyone cannot play the stage. */
    public int partyCapOrdinal() {
        if (members.isEmpty()) return -1;
        int cap = Integer.MAX_VALUE;
        for (LobbyStatePacket.Member m : members) cap = Math.min(cap, m.maxDifficultyOrdinal());
        return cap;
    }
}
