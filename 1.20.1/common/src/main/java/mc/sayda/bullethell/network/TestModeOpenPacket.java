package mc.sayda.bullethell.network;

import net.minecraft.network.FriendlyByteBuf;
import java.util.ArrayList;
import java.util.List;

/** S → C: activates test mode overlay; carries all list data and current selection per page. */
public class TestModeOpenPacket {

    public final List<String> bossIds;
    public final List<String> stageIds;
    public final List<String> waveIds;
    public final List<String> charIds;
    public final String currentBossId;
    public final String currentStageId;
    public final String currentWaveId;
    public final String currentCharId;
    public final int phaseIdx;
    public final int difficultyOrdinal;

    public TestModeOpenPacket(List<String> bossIds, List<String> stageIds, List<String> waveIds,
                              List<String> charIds, String currentBossId, String currentStageId,
                              String currentWaveId, String currentCharId,
                              int phaseIdx, int difficultyOrdinal) {
        this.bossIds          = bossIds;
        this.stageIds         = stageIds;
        this.waveIds          = waveIds;
        this.charIds          = charIds;
        this.currentBossId    = currentBossId;
        this.currentStageId   = currentStageId;
        this.currentWaveId    = currentWaveId;
        this.currentCharId    = currentCharId;
        this.phaseIdx         = phaseIdx;
        this.difficultyOrdinal = difficultyOrdinal;
    }

    private static void writeList(FriendlyByteBuf buf, List<String> list) {
        buf.writeInt(list.size());
        for (String s : list) buf.writeUtf(s);
    }

    private static List<String> readList(FriendlyByteBuf buf) {
        int n = buf.readInt();
        List<String> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(buf.readUtf());
        return list;
    }

    public void encode(FriendlyByteBuf buf) {
        writeList(buf, bossIds);
        writeList(buf, stageIds);
        writeList(buf, waveIds);
        writeList(buf, charIds);
        buf.writeUtf(currentBossId);
        buf.writeUtf(currentStageId);
        buf.writeUtf(currentWaveId);
        buf.writeUtf(currentCharId);
        buf.writeInt(phaseIdx);
        buf.writeInt(difficultyOrdinal);
    }

    public static TestModeOpenPacket decode(FriendlyByteBuf buf) {
        List<String> bossIds  = readList(buf);
        List<String> stageIds = readList(buf);
        List<String> waveIds  = readList(buf);
        List<String> charIds  = readList(buf);
        return new TestModeOpenPacket(bossIds, stageIds, waveIds, charIds,
                buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                buf.readInt(), buf.readInt());
    }
}
