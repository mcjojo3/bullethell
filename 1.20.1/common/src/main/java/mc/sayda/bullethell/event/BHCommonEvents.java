package mc.sayda.bullethell.event;

import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import mc.sayda.bullethell.BossProgression;
import mc.sayda.bullethell.BossRushMode;
import mc.sayda.bullethell.CharacterUnlocks;
import mc.sayda.bullethell.arena.ArenaContext;
import mc.sayda.bullethell.arena.BulletPool;
import mc.sayda.bullethell.arena.BulletHellManager;
import mc.sayda.bullethell.arena.LastArenaShareState;
import mc.sayda.bullethell.arena.ArenaEndShareSnapshot;
import mc.sayda.bullethell.arena.VictoryXpRewards;
import mc.sayda.bullethell.boss.CharacterDefinition;
import mc.sayda.bullethell.boss.CharacterLoader;
import mc.sayda.bullethell.command.BulletHellCommands;
import mc.sayda.bullethell.debug.BHDebugMode;
import mc.sayda.bullethell.network.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BHCommonEvents {

    private record CarryState(int lives, int bombs, int graze, int power,
            double storedChargeProgress, double holdChargeProgress) {
    }

    public static void register() {
        TickEvent.SERVER_POST.register(server -> {
            // Cache server reference once so UUID-only startArena() overloads can start threads.
            BulletHellManager.INSTANCE.setServerOnce(server);

            // Drain per-arena callbacks queued by arena threads onto the MC main thread
            // (win/loss advancement grants, arena removal, boss-rush continuation, etc.).
            for (ArenaContext ctx : BulletHellManager.INSTANCE.getAll().values()) {
                Runnable r;
                while ((r = ctx.mainCallbacks.poll()) != null) r.run();
            }
        });

        PlayerEvent.PLAYER_QUIT.register(player -> {
            UUID uuid = player.getUUID();
            BHDebugMode.clear(uuid);
            if (!BulletHellManager.INSTANCE.hasArena(uuid)
                    && BulletHellManager.INSTANCE.isInMatch(uuid)) {
                BulletHellManager.INSTANCE.leaveMatch(uuid);
            } else {
                BulletHellManager.INSTANCE.stopArena(uuid);
            }
        });

        PlayerEvent.PLAYER_JOIN.register(player -> {
            BossProgression.ensureRootAdvancement((ServerPlayer) player);
        });

        CommandRegistrationEvent.EVENT.register((dispatcher, registryAccess, environment) -> {
            BulletHellCommands.register(dispatcher);
        });
    }

    // ---------------------------------------------------------------- arena-over (called on MC main thread from ArenaThread via mainCallbacks)

    /**
     * Handles win/loss bookkeeping after an arena ends. Called on the MC main thread
     * from the {@link ArenaContext#mainCallbacks} queue drained in {@code SERVER_POST}.
     */
    public static void handleArenaOver(MinecraftServer server, UUID hostUuid, ArenaContext ctx) {
        if (ctx.isWon()) {
            String bossId = (ctx.boss != null) ? ctx.boss.id : "";
            String charReward = (bossId != null && bossId.endsWith("_boss"))
                    ? bossId.substring(0, bossId.length() - 5) : "";
            if (!ctx.practiceMode) {
                for (UUID pid : ctx.allParticipants()) {
                    ServerPlayer p = server.getPlayerList().getPlayer(pid);
                    if (p == null || bossId == null || bossId.isBlank()) continue;
                    boolean improved = BossProgression.grantClearThroughDifficulty(p, bossId, ctx.difficulty);
                    if (improved) {
                        p.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                "[BulletHell] Recorded clear: " + bossId + " (" + ctx.difficulty.name() + ")."));
                    }
                    if (!charReward.isBlank()) {
                        boolean charUnlocked = CharacterUnlocks.grantThroughDifficulty(p, charReward, ctx.difficulty);
                        if (charUnlocked) {
                            String charName = charReward.substring(0, 1).toUpperCase() + charReward.substring(1);
                            p.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                    "[BulletHell] " + charName + " is now playable on " + ctx.difficulty.name() + " and below!"));
                        }
                        BHPackets.sendCharacterUnlocks(p, new CharacterUnlockSyncPacket(
                                CharacterUnlocks.snapshot(p)));
                    }
                }
            }
        }

        if (tryContinueToNextStage(server, hostUuid, ctx)) {
            // tryContinueToNextStage() already replaced the arena; nothing more to do.
            return;
        }

        for (UUID pid : ctx.allParticipants()) {
            ServerPlayer p = server.getPlayerList().getPlayer(pid);
            if (p == null) continue;
            sendEndStats(p, ctx, true);
            BHPackets.sendToPlayer(p, ArenaStatePacket.stopped());
        }

        BulletHellManager.INSTANCE.stopArena(hostUuid);
    }

    // ---------------------------------------------------------------- stage chaining

    private static boolean tryContinueToNextStage(MinecraftServer server, UUID hostUuid, ArenaContext ctx) {
        if (!BossRushMode.isEnabled()) return false;
        if (!ctx.isWon()) return false;
        String nextStageId = ctx.stage.nextStageId;
        if (nextStageId == null || nextStageId.isBlank()) return false;

        ServerPlayer host = server.getPlayerList().getPlayer(hostUuid);
        if (host == null) return false;

        java.util.LinkedHashMap<UUID, CarryState> carry = new java.util.LinkedHashMap<>();
        for (UUID pid : ctx.allParticipants()) {
            var ps = ctx.getPlayerState(pid);
            if (ps != null) {
                carry.put(pid, new CarryState(
                        ps.lives, ps.bombs, ps.graze, ps.power,
                        ps.storedChargeProgress, ps.holdChargeProgress));
            }
        }
        java.util.List<UUID> partList = new java.util.ArrayList<>(ctx.allParticipants());
        int np = Math.max(1, partList.size());
        long carryCombined = ctx.getCombinedScore();
        long eachBase = carryCombined / np;
        long carryRem = carryCombined % np;

        for (UUID pid : ctx.allParticipants()) {
            ServerPlayer p = server.getPlayerList().getPlayer(pid);
            if (p != null) {
                sendEndStats(p, ctx, false);
                p.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "[BulletHell] Continuing to next stage: " + nextStageId));
            }
        }

        java.util.LinkedHashMap<UUID, String>  coopChars = new java.util.LinkedHashMap<>();
        java.util.LinkedHashMap<UUID, Integer> coopShots = new java.util.LinkedHashMap<>();
        for (UUID pid : ctx.getCoopPlayers().keySet()) {
            coopChars.put(pid, ctx.getCharacterId(pid));
            coopShots.put(pid, ctx.getShotTypeOrdinal(pid));
        }

        BHPackets.startArena(host, ctx.difficulty, nextStageId, ctx.characterId, ctx.hostShotTypeOrdinal);
        ArenaContext nextCtx = BulletHellManager.INSTANCE.getArenaForPlayer(hostUuid);
        if (nextCtx == null) return true;

        for (var e : coopChars.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null) continue;
            CharacterDefinition charDef = CharacterLoader.load(e.getValue());
            BulletHellManager.INSTANCE.joinMatch(p.getUUID(), hostUuid, charDef, p,
                    coopShots.getOrDefault(p.getUUID(), 0));
            BHPackets.sendFullSync(p, nextCtx);
            int pIdx = 0, c = 2;
            for (UUID cid : nextCtx.getCoopPlayers().keySet()) {
                if (cid.equals(p.getUUID())) { pIdx = c; break; }
                c++;
            }
            BHPackets.sendToPlayer(p, new ArenaStatePacket(nextCtx, p.getUUID(), pIdx));
        }

        for (int i = 0; i < partList.size(); i++) {
            UUID pid = partList.get(i);
            long c = eachBase + (i < carryRem ? 1L : 0L);
            nextCtx.importCarriedScoreFor(pid, c, nextCtx.rules.scoreExtendEvery);
        }
        for (var e : carry.entrySet()) {
            var ps = nextCtx.getPlayerState(e.getKey());
            if (ps == null) continue;
            var cs = e.getValue();
            ps.lives = cs.lives(); ps.bombs = cs.bombs(); ps.graze = cs.graze();
            ps.power = cs.power();
            ps.storedChargeProgress = cs.storedChargeProgress();
            ps.holdChargeProgress = Math.min(cs.holdChargeProgress(), cs.storedChargeProgress());
            ps.syncChargePacketFields();
        }

        for (UUID pid : nextCtx.allParticipants()) {
            ServerPlayer p = server.getPlayerList().getPlayer(pid);
            if (p == null) continue;
            int pIdx = pid.equals(nextCtx.playerUuid) ? 1 : 0;
            if (pIdx == 0) {
                int c = 2;
                for (UUID cid : nextCtx.getCoopPlayers().keySet()) {
                    if (cid.equals(pid)) { pIdx = c; break; }
                    c++;
                }
            }
            BHPackets.sendToPlayer(p, new ArenaStatePacket(nextCtx, pid, pIdx));
        }
        return true;
    }

    // ---------------------------------------------------------------- helpers used by ArenaThread

    /** Builds a delta packet for all dirty enemy-bullet slots. Returns null when nothing changed. */
    public static BulletDeltaPacket buildBulletDelta(ArenaContext ctx) {
        List<Integer> dirty = new ArrayList<>();
        for (int i = 0; i < BulletPool.ENEMY_CAPACITY; i++) {
            if (ctx.bullets.isDirty(i)) dirty.add(i);
        }
        if (dirty.isEmpty()) return null;

        int n = dirty.size();
        int[] slots = new int[n];
        float[][] data = new float[n][];
        boolean[] active = new boolean[n];
        for (int i = 0; i < n; i++) {
            int s = dirty.get(i);
            slots[i] = s;
            data[i] = ctx.bullets.getSlotData(s);
            active[i] = ctx.bullets.isActive(s);
        }
        return new BulletDeltaPacket(slots, data, active);
    }

    // ---------------------------------------------------------------- end-stats

    private static void sendEndStats(ServerPlayer player, ArenaContext ctx, boolean grantVictoryXp) {
        ArenaEndShareSnapshot snap = ArenaEndShareSnapshot.capture(player, ctx);
        LastArenaShareState.record(player.getUUID(), snap);

        java.util.UUID pid = player.getUUID();
        mc.sayda.bullethell.arena.PlayerState2D ps = ctx.getPlayerState(pid);
        if (ps == null) ps = ctx.player;
        String bossId   = ctx.boss  != null ? ctx.boss.id   : "";
        String bossName = ctx.boss  != null ? ctx.boss.name : "";
        String charId   = ctx.getCharacterId(pid);
        String charName = CharacterLoader.load(charId).name;
        String stageId  = ctx.stage != null ? ctx.stage.id  : "";

        String bossDialog;
        if (ctx.isWon()) {
            String perChar = ctx.boss != null
                    ? ctx.boss.victoryDialogByCharacter.getOrDefault(charId, "") : "";
            bossDialog = !perChar.isBlank() ? perChar
                    : (ctx.boss != null ? ctx.boss.victoryDialog : "");
        } else {
            String perChar = ctx.boss != null
                    ? ctx.boss.defeatDialogByCharacter.getOrDefault(charId, "") : "";
            bossDialog = !perChar.isBlank() ? perChar
                    : (ctx.boss != null ? ctx.boss.defeatDialog : "");
        }

        long scoreSelf = ctx.getScore(pid);
        long scoreTeam = ctx.getCombinedScore();
        int victoryXp = 0;
        if (ctx.isWon() && grantVictoryXp && !ctx.practiceMode) {
            victoryXp = VictoryXpRewards.computePoints(scoreSelf, ctx.difficulty);
            if (victoryXp > 0) player.giveExperiencePoints(victoryXp);
        }

        BHPackets.sendArenaEnd(player, new ArenaEndPacket(
                ctx.isWon(), bossName, bossId, charId, charName, bossDialog,
                scoreSelf, scoreTeam, victoryXp, ps.lives, ps.bombs, ps.graze,
                ctx.getSpellsCaptured(), ctx.getSpellsAttempted(),
                (float) ctx.getCompletionPercentage(),
                stageId, ctx.difficulty.name(), ctx.getShotTypeOrdinal(pid)));

        if (ctx.stage != null && ctx.stage.rewards != null) {
            List<String> cmds = ctx.isWon()
                    ? ctx.stage.rewards.onWin : ctx.stage.rewards.onLoss;
            if (cmds != null) {
                for (String template : cmds) {
                    String cmd = template
                            .replace("{player}", player.getGameProfile().getName())
                            .replace("{score}", String.valueOf(scoreSelf))
                            .replace("{difficulty}", ctx.difficulty.name());
                    player.getServer().getCommands().performPrefixedCommand(
                            player.getServer().createCommandSourceStack(), cmd);
                }
            }
        }
    }
}
