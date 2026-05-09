package mc.sayda.bullethell.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * S → C | Sent when the arena ends (win or game-over), before the stopped
 * packet.
 * Carries all data needed to render the end dialog and stats overlay on the
 * client.
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
    /**
     * Boss victory or defeat quote. Empty string = no dialog, skip straight to
     * stats.
     */
    public final String bossDialog;
    /** This player's score (co-op: not the team sum). */
    public final long score;
    /** Sum of all participants' scores; equals {@link #score} in solo. */
    public final long scoreCombined;
    /**
     * Minecraft experience points granted on victory (0 if loss or intermediate
     * boss-rush stage).
     */
    public final int victoryXp;
    public final int lives;
    public final int bombs;
    public final int graze;
    public final int spellsCaptured;
    public final int spellsAttempted;
    /** 0-100 completion percentage (used for defeat display). */
    public final float completionPercent;
    /** Stage id for retry (e.g. "marisa_stage"). */
    public final String stageId;
    /** Difficulty name (EASY/NORMAL/HARD/LUNATIC) for retry. */
    public final String difficulty;
    /** Shot type index for retry / display. */
    public final int shotTypeOrdinal;

    public ArenaEndPacket(boolean won, String bossName, String bossId,
            String characterId, String characterName, String bossDialog,
            long score, long scoreCombined, int victoryXp, int lives, int bombs, int graze,
            int spellsCaptured, int spellsAttempted, float completionPercent,
            String stageId, String difficulty, int shotTypeOrdinal) {
        this.won = won;
        this.bossName = bossName != null ? bossName : "";
        this.bossId = bossId != null ? bossId : "";
        this.characterId = characterId != null ? characterId : "reimu";
        this.characterName = characterName != null ? characterName : "";
        this.bossDialog = bossDialog != null ? bossDialog : "";
        this.score = score;
        this.scoreCombined = scoreCombined;
        this.victoryXp = victoryXp;
        this.lives = lives;
        this.bombs = bombs;
        this.graze = graze;
        this.spellsCaptured = spellsCaptured;
        this.spellsAttempted = spellsAttempted;
        this.completionPercent = completionPercent;
        this.stageId = stageId != null ? stageId : "";
        this.difficulty = difficulty != null ? difficulty : "NORMAL";
        this.shotTypeOrdinal = shotTypeOrdinal;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(won);
        buf.writeUtf(bossName);
        buf.writeUtf(bossId);
        buf.writeUtf(characterId);
        buf.writeUtf(characterName);
        buf.writeUtf(bossDialog);
        buf.writeLong(score);
        buf.writeLong(scoreCombined);
        buf.writeVarInt(victoryXp);
        buf.writeVarInt(lives);
        buf.writeVarInt(bombs);
        buf.writeVarInt(graze);
        buf.writeVarInt(spellsCaptured);
        buf.writeVarInt(spellsAttempted);
        buf.writeFloat(completionPercent);
        buf.writeUtf(stageId);
        buf.writeUtf(difficulty);
        buf.writeByte(shotTypeOrdinal);
    }

    public static ArenaEndPacket decode(FriendlyByteBuf buf) {
        boolean won = buf.readBoolean();
        String bossName = buf.readUtf();
        String bossId = buf.readUtf();
        String characterId = buf.readUtf();
        String characterName = buf.readUtf();
        String bossDialog = buf.readUtf();
        long score = buf.readLong();
        long scoreCombined = buf.readLong();
        int victoryXp = buf.readVarInt();
        int lives = buf.readVarInt();
        int bombs = buf.readVarInt();
        int graze = buf.readVarInt();
        int captured = buf.readVarInt();
        int attempted = buf.readVarInt();
        float completion = buf.readFloat();
        String stageId = buf.readUtf();
        String difficulty = buf.readUtf();
        int shot = buf.readableBytes() > 0 ? (buf.readByte() & 0xFF) : 0;
        return new ArenaEndPacket(won, bossName, bossId, characterId, characterName,
                bossDialog, score, scoreCombined, victoryXp, lives, bombs, graze, captured, attempted,
                completion, stageId, difficulty, shot);
    }
}
