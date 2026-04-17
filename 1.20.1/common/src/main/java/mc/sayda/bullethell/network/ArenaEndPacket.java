package mc.sayda.bullethell.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * S → C | Sent when the arena ends (win or game-over), before the stopped packet.
 * Carries all data needed to render the end dialog and stats overlay on the client.
 */
public final class ArenaEndPacket {

    public final boolean won;
    /** Display name of the boss (e.g. "Marisa Kirisame"). */
    public final String bossName;
    /** Boss id used for portrait rendering (e.g. "marisa_boss"). */
    public final String bossId;
    /** Character id the player used (for portrait + retry). */
    public final String characterId;
    /** Display name of the character. */
    public final String characterName;
    /** Boss victory or defeat quote. Empty string = no dialog, skip straight to stats. */
    public final String bossDialog;
    public final long score;
    public final int lives;
    public final int bombs;
    public final int graze;
    public final int spellsCaptured;
    public final int spellsAttempted;
    /** 0–100 completion percentage (used for defeat display). */
    public final float completionPercent;
    /** Stage id for retry (e.g. "marisa_stage"). */
    public final String stageId;
    /** Difficulty name (EASY/NORMAL/HARD/LUNATIC) for retry. */
    public final String difficulty;

    public ArenaEndPacket(boolean won, String bossName, String bossId,
            String characterId, String characterName, String bossDialog,
            long score, int lives, int bombs, int graze,
            int spellsCaptured, int spellsAttempted, float completionPercent,
            String stageId, String difficulty) {
        this.won = won;
        this.bossName = bossName != null ? bossName : "";
        this.bossId = bossId != null ? bossId : "";
        this.characterId = characterId != null ? characterId : "reimu";
        this.characterName = characterName != null ? characterName : "";
        this.bossDialog = bossDialog != null ? bossDialog : "";
        this.score = score;
        this.lives = lives;
        this.bombs = bombs;
        this.graze = graze;
        this.spellsCaptured = spellsCaptured;
        this.spellsAttempted = spellsAttempted;
        this.completionPercent = completionPercent;
        this.stageId = stageId != null ? stageId : "";
        this.difficulty = difficulty != null ? difficulty : "NORMAL";
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(won);
        buf.writeUtf(bossName);
        buf.writeUtf(bossId);
        buf.writeUtf(characterId);
        buf.writeUtf(characterName);
        buf.writeUtf(bossDialog);
        buf.writeLong(score);
        buf.writeVarInt(lives);
        buf.writeVarInt(bombs);
        buf.writeVarInt(graze);
        buf.writeVarInt(spellsCaptured);
        buf.writeVarInt(spellsAttempted);
        buf.writeFloat(completionPercent);
        buf.writeUtf(stageId);
        buf.writeUtf(difficulty);
    }

    public static ArenaEndPacket decode(FriendlyByteBuf buf) {
        boolean won = buf.readBoolean();
        String bossName = buf.readUtf();
        String bossId = buf.readUtf();
        String characterId = buf.readUtf();
        String characterName = buf.readUtf();
        String bossDialog = buf.readUtf();
        long score = buf.readLong();
        int lives = buf.readVarInt();
        int bombs = buf.readVarInt();
        int graze = buf.readVarInt();
        int captured = buf.readVarInt();
        int attempted = buf.readVarInt();
        float completion = buf.readFloat();
        String stageId = buf.readUtf();
        String difficulty = buf.readUtf();
        return new ArenaEndPacket(won, bossName, bossId, characterId, characterName,
                bossDialog, score, lives, bombs, graze, captured, attempted,
                completion, stageId, difficulty);
    }
}
