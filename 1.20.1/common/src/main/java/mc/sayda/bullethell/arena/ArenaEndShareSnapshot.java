package mc.sayda.bullethell.arena;

import mc.sayda.bullethell.boss.CharacterLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Captures end-of-run stats so they can be re-sent later (e.g. global chat share)
 * using the same formatted lines as {@code sendEndStats}.
 */
public final class ArenaEndShareSnapshot {

    private final boolean won;
    private final String bossName;
    private final String endBossLine;
    private final String charName;
    private final long score;
    private final int lives;
    private final int bombs;
    private final int graze;
    private final int captured;
    private final int attempted;
    private final double completionPercent;

    private ArenaEndShareSnapshot(boolean won, String bossName, String endBossLine, String charName,
            long score, int lives, int bombs, int graze, int captured, int attempted,
            double completionPercent) {
        this.won = won;
        this.bossName = bossName != null ? bossName : "";
        this.endBossLine = endBossLine != null ? endBossLine : "";
        this.charName = charName != null ? charName : "?";
        this.score = score;
        this.lives = lives;
        this.bombs = bombs;
        this.graze = graze;
        this.captured = captured;
        this.attempted = attempted;
        this.completionPercent = completionPercent;
    }

    public static ArenaEndShareSnapshot capture(ServerPlayer player, ArenaContext ctx) {
        UUID pid = player.getUUID();
        PlayerState2D ps = ctx.getPlayerState(pid);
        if (ps == null)
            ps = ctx.player;
        long score = ctx.score.getScore();
        String charName = CharacterLoader.load(ctx.getCharacterId(pid)).name;
        String bossName = (ctx.boss != null) ? ctx.boss.name : "";
        String endBossLine = resolveEndBossDialog(ctx, pid);
        return new ArenaEndShareSnapshot(
                ctx.isWon(),
                bossName,
                endBossLine,
                charName,
                score,
                ps.lives,
                ps.bombs,
                ps.graze,
                ctx.getSpellsCaptured(),
                ctx.getSpellsAttempted(),
                ctx.getCompletionPercentage());
    }

    private static String resolveEndBossDialog(ArenaContext ctx, UUID playerUuid) {
        if (ctx == null || ctx.boss == null)
            return "";
        String charId = ctx.getCharacterId(playerUuid);
        if (ctx.isWon()) {
            String perChar = ctx.boss.victoryDialogByCharacter.getOrDefault(charId, "");
            return !perChar.isBlank() ? perChar : ctx.boss.victoryDialog;
        }
        String perChar = ctx.boss.defeatDialogByCharacter.getOrDefault(charId, "");
        return !perChar.isBlank() ? perChar : ctx.boss.defeatDialog;
    }

    /**
     * Same system-message lines as the original private end stats (boss line + boxed stats).
     */
    public List<Component> buildLines() {
        List<Component> out = new ArrayList<>();
        if (!endBossLine.isBlank()) {
            out.add(Component.literal("[" + bossName + "] " + endBossLine));
        }
        if (won) {
            out.add(Component.literal("§6§l╔══════ STAGE CLEAR ══════╗"));
            out.add(Component.literal("§e  Character: §f" + charName));
            out.add(Component.literal("§e  Score: §f" + String.format("%,d", score)));
            out.add(Component.literal("§e  Lives: §f" + lives + "  §eBombs: §f" + bombs));
            out.add(Component.literal("§e  Graze: §f" + graze));
            out.add(Component.literal("§e  Spells: §f" + captured + " / " + attempted + " captured"
                    + (attempted > 0 && captured == attempted ? " §a§l(PERFECT!)" : "")));
            out.add(Component.literal("§6§l╚═══════════════════════╝"));
        } else {
            out.add(Component.literal("§c§l╔════════ GAME OVER ════════╗"));
            out.add(Component.literal("§e  Character: §f" + charName));
            out.add(Component.literal("§e  Score: §f" + String.format("%,d", score)));
            out.add(Component.literal("§e  Graze: §f" + graze));
            out.add(Component.literal("§e  Spells: §f" + captured + " / " + attempted + " captured"));
            out.add(Component.literal("§e  Progress: §f" + String.format("%.1f%%", completionPercent)));
            out.add(Component.literal("§c§l╚══════════════════════════╝"));
        }
        return out;
    }
}
