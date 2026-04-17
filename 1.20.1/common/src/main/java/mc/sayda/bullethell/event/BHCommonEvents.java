package mc.sayda.bullethell.event;

import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import mc.sayda.bullethell.BHGameRules;
import mc.sayda.bullethell.BossProgression;
import mc.sayda.bullethell.BossRushMode;
import mc.sayda.bullethell.CharacterUnlocks;
import mc.sayda.bullethell.arena.ArenaContext;
import mc.sayda.bullethell.arena.ArenaEndShareSnapshot;
import mc.sayda.bullethell.arena.BulletHellManager;
import mc.sayda.bullethell.arena.LastArenaShareState;
import mc.sayda.bullethell.arena.BulletPool;
import mc.sayda.bullethell.arena.GameEvent;
import mc.sayda.bullethell.boss.CharacterDefinition;
import mc.sayda.bullethell.boss.CharacterLoader;
import mc.sayda.bullethell.command.BulletHellCommands;
import mc.sayda.bullethell.debug.BHDebugMode;
import mc.sayda.bullethell.network.ArenaStatePacket;
import mc.sayda.bullethell.network.BHPackets;
import mc.sayda.bullethell.network.BulletDeltaPacket;
import mc.sayda.bullethell.network.CoopPlayersSyncPacket;
import mc.sayda.bullethell.network.EnemySyncPacket;
import mc.sayda.bullethell.network.GameEventPacket;
import mc.sayda.bullethell.network.ItemSyncPacket;
import mc.sayda.bullethell.network.LaserSyncPacket;
import mc.sayda.bullethell.network.AllPlayerBulletsSyncPacket;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BHCommonEvents {

    private static int syncTick = 0;
    private record CarryState(int lives, int bombs, int graze, int power,
            double storedChargeProgress, double holdChargeProgress) {
    }

    public static void register() {
        TickEvent.SERVER_POST.register(server -> {
            syncTick++;

            BulletHellManager manager = BulletHellManager.INSTANCE;
            Map<UUID, ArenaContext> arenas = manager.getAll();
            List<UUID> toRemove = new ArrayList<>();

            for (var entry : arenas.entrySet()) {
                UUID uuid = entry.getKey();
                ArenaContext ctx = entry.getValue();

                ctx.setGloballyPaused(BHGameRules.isGlobalPauseEnabled(server) && ctx.hasPausedParticipants());
                ctx.tick();

                ServerPlayer host = server.getPlayerList().getPlayer(uuid);
                if (host == null) {
                    toRemove.add(uuid);
                    continue;
                }

                if (ctx.isOver()) {
                    if (ctx.isWon()) {
                        String bossId = (ctx.boss != null) ? ctx.boss.id : "";
                        // Derive character id from boss id: "<charId>_boss" → "<charId>"
                        String charReward = (bossId != null && bossId.endsWith("_boss"))
                                ? bossId.substring(0, bossId.length() - 5) : "";
                        for (UUID pid : ctx.allParticipants()) {
                            ServerPlayer p = server.getPlayerList().getPlayer(pid);
                            if (p == null || bossId == null || bossId.isBlank())
                                continue;
                            boolean improved = BossProgression.grantClearThroughDifficulty(p, bossId, ctx.difficulty);
                            if (improved) {
                                p.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                        "[BulletHell] Recorded clear: " + bossId + " (" + ctx.difficulty.name() + ")."));
                            }
                            // Notify if this clear unlocked a playable character
                            if (!charReward.isBlank()) {
                                boolean charUnlocked = CharacterUnlocks.grantThroughDifficulty(p, charReward, ctx.difficulty);
                                if (charUnlocked) {
                                    String charName = charReward.substring(0, 1).toUpperCase() + charReward.substring(1);
                                    p.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                            "[BulletHell] " + charName + " is now playable on " + ctx.difficulty.name() + " and below!"));
                                }
                                // Always sync unlock state after a win so character select reflects it
                                BHPackets.sendCharacterUnlocks(p, new mc.sayda.bullethell.network.CharacterUnlockSyncPacket(
                                        CharacterUnlocks.snapshot(p)));
                            }
                        }
                    }
                    if (tryContinueToNextStage(server, uuid, ctx)) {
                        // tryContinueToNextStage() already replaces the arena for this host UUID.
                        // Do NOT queue removal here, or end-of-tick cleanup can delete the new arena.
                        continue;
                    }
                    for (UUID pid : ctx.allParticipants()) {
                        ServerPlayer p = server.getPlayerList().getPlayer(pid);
                        if (p == null) continue;
                        // Record snapshot + send end overlay BEFORE stopped() so
                        // the client can open ArenaEndScreen before the arena clears
                        sendEndStats(p, ctx);
                        BHPackets.sendToPlayer(p, ArenaStatePacket.stopped());
                    }
                    toRemove.add(uuid);
                    continue;
                }

                List<GameEvent> globalEvents = new ArrayList<>();
                GameEvent ge;
                while ((ge = ctx.pendingEvents.poll()) != null)
                    globalEvents.add(ge);

                BulletDeltaPacket deltaPacket = buildBulletDelta(ctx);
                AllPlayerBulletsSyncPacket allBulletsPacket = AllPlayerBulletsSyncPacket.fromContext(ctx);
                ItemSyncPacket itemPacket = (syncTick % 4 == 0) ? ItemSyncPacket.fromContext(ctx) : null;
                EnemySyncPacket enemyPacket = (syncTick % 2 == 0) ? EnemySyncPacket.fromContext(ctx) : null;

                ctx.bullets.clearDirty();

                java.util.Map<UUID, CoopPlayersSyncPacket> coopPackets = new java.util.HashMap<>();
                java.util.Set<UUID> all = ctx.allParticipants();
                if (all.size() > 1) {
                    List<CoopPlayersSyncPacket.Entry> allEntries = new ArrayList<>();
                    int idx = 1;
                    for (UUID pid : all) {
                        mc.sayda.bullethell.arena.PlayerState2D ps = ctx.getPlayerState(pid);
                        if (ps != null) {
                            String charId = ctx.getCharacterId(pid);
                            mc.sayda.bullethell.boss.CharacterDefinition cd = mc.sayda.bullethell.boss.CharacterLoader
                                    .load(charId);
                            allEntries.add(new CoopPlayersSyncPacket.Entry(
                                    ps.x, ps.y, ps.lives, cd.tintColor, charId, idx));
                        }
                        idx++;
                    }

                    int recipientIdx = 0;
                    for (UUID pid : all) {
                        List<CoopPlayersSyncPacket.Entry> others = new ArrayList<>(allEntries);
                        others.remove(recipientIdx);
                        coopPackets.put(pid, new CoopPlayersSyncPacket(others));
                        recipientIdx++;
                    }
                }

                for (UUID pid : all) {
                    ServerPlayer p = server.getPlayerList().getPlayer(pid);
                    if (p == null)
                        continue;

                    // Send global events
                    for (GameEvent g : globalEvents)
                        BHPackets.sendGameEvent(p, new GameEventPacket(g));

                    // Send personal events
                    mc.sayda.bullethell.arena.PlayerState2D ps2d = ctx.getPlayerState(pid);
                    if (ps2d != null) {
                        GameEvent pe;
                        while ((pe = ps2d.personalEvents.poll()) != null)
                            BHPackets.sendGameEvent(p, new GameEventPacket(pe));
                    }

                    if (deltaPacket != null)
                        BHPackets.sendBulletDelta(p, deltaPacket);

                    BHPackets.sendAllPlayerBullets(p, allBulletsPacket);

                    int pIdx = (pid.equals(ctx.playerUuid)) ? 1 : 0;
                    if (pIdx == 0) {
                        int count = 2;
                        for (UUID cid : ctx.getCoopPlayers().keySet()) {
                            if (cid.equals(pid)) {
                                pIdx = count;
                                break;
                            }
                            count++;
                        }
                    }
                    BHPackets.sendToPlayer(p, new ArenaStatePacket(ctx, pid, pIdx));

                    if (itemPacket != null)
                        BHPackets.sendItemSync(p, itemPacket);

                    if (enemyPacket != null)
                        BHPackets.sendEnemySync(p, enemyPacket);

                    CoopPlayersSyncPacket cpp = coopPackets.get(pid);
                    if (cpp != null)
                        BHPackets.sendCoopSync(p, cpp);

                    BHPackets.sendLaserSync(p, new LaserSyncPacket(ctx.lasers));
                }
            }

            toRemove.forEach(manager::stopArena);
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

    /**
     * If stage chaining is configured, start the next stage immediately after a clear.
     * Keeps difficulty and each participant's character selection; this is a fresh stage.
     */
    private static boolean tryContinueToNextStage(net.minecraft.server.MinecraftServer server, UUID hostUuid, ArenaContext ctx) {
        if (!BossRushMode.isEnabled())
            return false;
        if (!ctx.isWon())
            return false;
        String nextStageId = ctx.stage.nextStageId;
        if (nextStageId == null || nextStageId.isBlank())
            return false;

        ServerPlayer host = server.getPlayerList().getPlayer(hostUuid);
        if (host == null)
            return false;

        java.util.LinkedHashMap<UUID, CarryState> carry = new java.util.LinkedHashMap<>();
        for (UUID pid : ctx.allParticipants()) {
            var ps = ctx.getPlayerState(pid);
            if (ps != null) {
                carry.put(pid, new CarryState(
                        ps.lives, ps.bombs, ps.graze, ps.power,
                        ps.storedChargeProgress, ps.holdChargeProgress));
            }
        }
        long carryScore = ctx.score.getScore();

        for (UUID pid : ctx.allParticipants()) {
            ServerPlayer p = server.getPlayerList().getPlayer(pid);
            if (p != null) {
                sendEndStats(p, ctx);
                p.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "[BulletHell] Continuing to next stage: " + nextStageId));
            }
        }

        java.util.LinkedHashMap<UUID, String> coopChars = new java.util.LinkedHashMap<>();
        for (UUID pid : ctx.getCoopPlayers().keySet()) {
            coopChars.put(pid, ctx.getCharacterId(pid));
        }

        BHPackets.startArena(host, ctx.difficulty, nextStageId, ctx.characterId);
        ArenaContext nextCtx = BulletHellManager.INSTANCE.getArenaForPlayer(hostUuid);
        if (nextCtx == null)
            return true;

        for (var e : coopChars.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null)
                continue;
            CharacterDefinition charDef = CharacterLoader.load(e.getValue());
            BulletHellManager.INSTANCE.joinMatch(p.getUUID(), hostUuid, charDef, p);
            BHPackets.sendFullSync(p, nextCtx);
            int pIdx = 0;
            int c = 2;
            for (UUID cid : nextCtx.getCoopPlayers().keySet()) {
                if (cid.equals(p.getUUID())) {
                    pIdx = c;
                    break;
                }
                c++;
            }
            BHPackets.sendToPlayer(p, new ArenaStatePacket(nextCtx, p.getUUID(), pIdx));
        }

        // Shared run score/stats carry over through chained stages.
        nextCtx.score.importCarriedScore(carryScore, nextCtx.rules.scoreExtendEvery);
        for (var e : carry.entrySet()) {
            var ps = nextCtx.getPlayerState(e.getKey());
            if (ps == null)
                continue;
            var cs = e.getValue();
            ps.lives = cs.lives();
            ps.bombs = cs.bombs();
            ps.graze = cs.graze();
            ps.power = cs.power();
            ps.storedChargeProgress = cs.storedChargeProgress();
            ps.holdChargeProgress = Math.min(cs.holdChargeProgress(), cs.storedChargeProgress());
            ps.syncChargePacketFields();
        }

        // Push refreshed per-player state immediately after carry-over.
        for (UUID pid : nextCtx.allParticipants()) {
            ServerPlayer p = server.getPlayerList().getPlayer(pid);
            if (p == null)
                continue;
            int pIdx = (pid.equals(nextCtx.playerUuid)) ? 1 : 0;
            if (pIdx == 0) {
                int c = 2;
                for (UUID cid : nextCtx.getCoopPlayers().keySet()) {
                    if (cid.equals(pid)) {
                        pIdx = c;
                        break;
                    }
                    c++;
                }
            }
            BHPackets.sendToPlayer(p, new ArenaStatePacket(nextCtx, pid, pIdx));
        }
        return true;
    }

    public static BulletDeltaPacket buildBulletDelta(ArenaContext ctx) {
        List<Integer> dirty = new ArrayList<>();
        for (int i = 0; i < BulletPool.ENEMY_CAPACITY; i++) {
            if (ctx.bullets.isDirty(i))
                dirty.add(i);
        }
        if (dirty.isEmpty())
            return null;

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

    private static void sendEndStats(ServerPlayer player, ArenaContext ctx) {
        ArenaEndShareSnapshot snap = ArenaEndShareSnapshot.capture(player, ctx);
        LastArenaShareState.record(player.getUUID(), snap);

        // Build ArenaEndPacket for the client-side overlay
        java.util.UUID pid = player.getUUID();
        mc.sayda.bullethell.arena.PlayerState2D ps = ctx.getPlayerState(pid);
        if (ps == null) ps = ctx.player;
        String bossId    = ctx.boss != null ? ctx.boss.id   : "";
        String bossName  = ctx.boss != null ? ctx.boss.name : "";
        String charId    = ctx.getCharacterId(pid);
        String charName  = mc.sayda.bullethell.boss.CharacterLoader.load(charId).name;
        String stageId   = ctx.stage != null ? ctx.stage.id : "";
        // Resolve boss quote (victory or defeat)
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

        BHPackets.sendArenaEnd(player, new mc.sayda.bullethell.network.ArenaEndPacket(
                ctx.isWon(), bossName, bossId, charId, charName, bossDialog,
                ctx.score.getScore(), ps.lives, ps.bombs, ps.graze,
                ctx.getSpellsCaptured(), ctx.getSpellsAttempted(),
                (float) ctx.getCompletionPercentage(),
                stageId, ctx.difficulty.name()));
    }
}
