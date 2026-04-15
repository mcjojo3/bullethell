package mc.sayda.bullethell.event;

import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import mc.sayda.bullethell.arena.ArenaContext;
import mc.sayda.bullethell.arena.BulletHellManager;
import mc.sayda.bullethell.arena.BulletPool;
import mc.sayda.bullethell.arena.GameEvent;
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

    public static void register() {
        TickEvent.SERVER_POST.register(server -> {
            syncTick++;

            BulletHellManager manager = BulletHellManager.INSTANCE;
            Map<UUID, ArenaContext> arenas = manager.getAll();
            List<UUID> toRemove = new ArrayList<>();

            for (var entry : arenas.entrySet()) {
                UUID uuid = entry.getKey();
                ArenaContext ctx = entry.getValue();

                ctx.tick();

                ServerPlayer host = server.getPlayerList().getPlayer(uuid);
                if (host == null) {
                    toRemove.add(uuid);
                    continue;
                }

                if (ctx.isOver()) {
                    for (UUID pid : ctx.allParticipants()) {
                        ServerPlayer p = server.getPlayerList().getPlayer(pid);
                        if (p != null) {
                            BHPackets.sendToPlayer(p, ArenaStatePacket.stopped());
                            sendEndStats(p, ctx);
                        }
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

        CommandRegistrationEvent.EVENT.register((dispatcher, registryAccess, environment) -> {
            BulletHellCommands.register(dispatcher);
        });
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
        mc.sayda.bullethell.arena.PlayerState2D ps = ctx.getPlayerState(player.getUUID());
        if (ps == null)
            ps = ctx.player;
        long score = ctx.score.getScore();
        int lives = ps.lives;
        int bombs = ps.bombs;
        int graze = ps.graze;
        int captured = ctx.getSpellsCaptured();
        int attempted = ctx.getSpellsAttempted();
        String charName = mc.sayda.bullethell.boss.CharacterLoader.load(ctx.getCharacterId(player.getUUID())).name;

        if (ctx.isWon()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§6§l╔══════ STAGE CLEAR ══════╗"));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§e  Character: §f" + charName));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§e  Score: §f" + String.format("%,d", score)));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§e  Lives: §f" + lives + "  §eBombs: §f" + bombs));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§e  Graze: §f" + graze));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§e  Spells: §f" + captured + " / " + attempted + " captured"
                            + (attempted > 0 && captured == attempted ? " §a§l(PERFECT!)" : "")));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§6§l╚═══════════════════════╝"));
        } else {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§c§l╔════════ GAME OVER ════════╗"));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§e  Character: §f" + charName));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§e  Score: §f" + String.format("%,d", score)));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§e  Graze: §f" + graze));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§e  Spells: §f" + captured + " / " + attempted + " captured"));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§e  Progress: §f" + String.format("%.1f%%", ctx.getCompletionPercentage())));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§c§l╚══════════════════════════╝"));
        }
    }
}
