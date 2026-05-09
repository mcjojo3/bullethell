package mc.sayda.bullethell.network;

import net.minecraft.network.FriendlyByteBuf;

/** C → S: select a boss/stage/wave from the test list, change phase/difficulty, or reload. */
public class TestSelectPacket {

    public static final int TYPE_BOSS  = 0;
    public static final int TYPE_STAGE = 1;
    public static final int TYPE_WAVE  = 2;
    public static final int TYPE_CHAR_REFRESH = 3;

    public final int testType;
    public final String id;           // bossId, stageId, or waveId
    public final int phaseIdx;        // boss only, ignored for stage/wave
    public final int difficultyOrdinal;
    public final String characterId;  // empty = keep current
    public final int shotTypeOrdinal;

    public TestSelectPacket(int testType, String id, int phaseIdx, int difficultyOrdinal, String characterId, int shotTypeOrdinal) {
        this.testType          = testType;
        this.id                = id != null ? id : "";
        this.phaseIdx          = phaseIdx;
        this.difficultyOrdinal = difficultyOrdinal;
        this.characterId       = characterId != null ? characterId : "";
        this.shotTypeOrdinal   = shotTypeOrdinal;
    }

    /** Backward-compat constructor: BOSS type, no character override. */
    public TestSelectPacket(String bossId, int phaseIdx, int difficultyOrdinal) {
        this(TYPE_BOSS, bossId, phaseIdx, difficultyOrdinal, "", 0);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(testType);
        buf.writeUtf(id);
        buf.writeInt(phaseIdx);
        buf.writeInt(difficultyOrdinal);
        buf.writeUtf(characterId);
        buf.writeInt(shotTypeOrdinal);
    }

    public static TestSelectPacket decode(FriendlyByteBuf buf) {
        return new TestSelectPacket(buf.readInt(), buf.readUtf(), buf.readInt(), buf.readInt(), buf.readUtf(), buf.readInt());
    }
}
