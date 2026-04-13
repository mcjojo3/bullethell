package mc.sayda.bullethell.forge.event;

import mc.sayda.bullethell.Bullethell;
import mc.sayda.bullethell.arena.ArenaContext;
import mc.sayda.bullethell.arena.BulletHellManager;
import mc.sayda.bullethell.arena.BulletPool;
import mc.sayda.bullethell.arena.DifficultyConfig;
import mc.sayda.bullethell.arena.GameEvent;
import mc.sayda.bullethell.forge.command.BulletHellCommands;
import mc.sayda.bullethell.forge.network.ArenaStatePacket;
import mc.sayda.bullethell.forge.network.BHNetwork;
import mc.sayda.bullethell.forge.network.BulletDeltaPacket;
import mc.sayda.bullethell.forge.network.BulletFullSyncPacket;
import mc.sayda.bullethell.forge.network.CoopPlayersSyncPacket;
import mc.sayda.bullethell.forge.network.EnemySyncPacket;
import mc.sayda.bullethell.forge.network.GameEventPacket;
import mc.sayda.bullethell.forge.network.ItemSyncPacket;
import mc.sayda.bullethell.forge.network.LaserSyncPacket;
import mc.sayda.bullethell.forge.network.PlayerBulletSyncPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = Bullethell.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BulletHellServerEvents {

    private static int syncTick = 0;

    // ---------------------------------------------------------------- server tick

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        syncTick++;

        BulletHellManager       manager  = BulletHellManager.INSTANCE;
        Map<UUID, ArenaContext>  arenas   = manager.getAll();
        List<UUID>               toRemove = new ArrayList<>();

        for (var entry : arenas.entrySet()) {
            UUID         uuid = entry.getKey();
            ArenaContext ctx  = entry.getValue();

            ctx.tick();

            ServerPlayer host = server.getPlayerList().getPlayer(uuid);
            if (host == null) {
                toRemove.add(uuid);
                continue;
            }

            if (ctx.isOver()) {
                // Notify host and all co-op participants
                for (UUID pid : ctx.allParticipants()) {
                    ServerPlayer p = server.getPlayerList().getPlayer(pid);
                    if (p != null) {
                        BHNetwork.CHANNEL.sendTo(ArenaStatePacket.stopped(),
                                p.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
                        sendEndStats(p, ctx);
                    }
                }
                toRemove.add(uuid);
                continue;
            }

            // ---- Drain game events → broadcast to all participants ----
            List<GameEventPacket> evtPackets = new ArrayList<>();
            GameEvent evt;
            while ((evt = ctx.pendingEvents.poll()) != null)
                evtPackets.add(new GameEventPacket(evt));

            // ---- Build shared packets (same data for every participant) ----
            BulletDeltaPacket   deltaPacket  = buildBulletDelta(ctx);
            ItemSyncPacket      itemPacket   = (syncTick % 4 == 0) ? ItemSyncPacket.fromContext(ctx)   : null;
            EnemySyncPacket     enemyPacket  = (syncTick % 2 == 0) ? EnemySyncPacket.fromContext(ctx)  : null;

            ctx.bullets.clearDirty();

            // ---- Build co-op positions packet for each participant ----
            // Contains entries for everyone EXCEPT the recipient
            java.util.Map<UUID, CoopPlayersSyncPacket> coopPackets = new java.util.HashMap<>();
            java.util.Set<UUID> all = ctx.allParticipants();
            if (all.size() > 1) {
                // Pre-build full list of entries
                List<CoopPlayersSyncPacket.Entry> allEntries = new ArrayList<>();
                for (UUID pid : all) {
                    mc.sayda.bullethell.arena.PlayerState2D ps = ctx.getPlayerState(pid);
                    if (ps != null) {
                        mc.sayda.bullethell.boss.CharacterDefinition cd =
                                mc.sayda.bullethell.boss.CharacterLoader.load(ctx.getCharacterId(pid));
                        allEntries.add(new CoopPlayersSyncPacket.Entry(
                                ps.x, ps.y, ps.lives, cd.tintColor));
                    }
                }
                // Each participant receives everyone else
                int idx = 0;
                for (UUID pid : all) {
                    List<CoopPlayersSyncPacket.Entry> others = new ArrayList<>(allEntries);
                    others.remove(idx);
                    coopPackets.put(pid, new CoopPlayersSyncPacket(others));
                    idx++;
                }
            }

            // ---- Send to each participant ----
            for (UUID pid : all) {
                ServerPlayer p = server.getPlayerList().getPlayer(pid);
                if (p == null) continue;

                // Events
                for (GameEventPacket gep : evtPackets)
                    BHNetwork.CHANNEL.sendTo(gep, p.connection.connection, NetworkDirection.PLAY_TO_CLIENT);

                // Enemy bullet delta
                if (deltaPacket != null)
                    BHNetwork.CHANNEL.sendTo(deltaPacket, p.connection.connection, NetworkDirection.PLAY_TO_CLIENT);

                // Player bullets (per-participant - each player sees their own bullets)
                BHNetwork.CHANNEL.sendTo(
                        PlayerBulletSyncPacket.fromContextForPlayer(ctx, pid),
                        p.connection.connection, NetworkDirection.PLAY_TO_CLIENT);

                // Arena state / HUD (per-participant for player-specific lives/bombs/etc)
                BHNetwork.CHANNEL.sendTo(
                        new ArenaStatePacket(ctx, pid),
                        p.connection.connection, NetworkDirection.PLAY_TO_CLIENT);

                // Items
                if (itemPacket != null)
                    BHNetwork.CHANNEL.sendTo(itemPacket, p.connection.connection, NetworkDirection.PLAY_TO_CLIENT);

                // Enemies
                if (enemyPacket != null)
                    BHNetwork.CHANNEL.sendTo(enemyPacket, p.connection.connection, NetworkDirection.PLAY_TO_CLIENT);

                // Co-op player positions
                CoopPlayersSyncPacket cpp = coopPackets.get(pid);
                if (cpp != null)
                    BHNetwork.CHANNEL.sendTo(cpp, p.connection.connection, NetworkDirection.PLAY_TO_CLIENT);

                // Lasers (always sent; pool is small so full sync is cheap)
                BHNetwork.CHANNEL.sendTo(new LaserSyncPacket(ctx.lasers),
                        p.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
            }
        }

        toRemove.forEach(manager::stopArena);
    }

    // ---------------------------------------------------------------- commands

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        BulletHellCommands.register(event.getDispatcher());
    }

    // ---------------------------------------------------------------- player disconnect

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            UUID uuid = sp.getUUID();
            // If this player is a co-op participant (not a host), just remove them
            if (!BulletHellManager.INSTANCE.hasArena(uuid)
                    && BulletHellManager.INSTANCE.isInMatch(uuid)) {
                BulletHellManager.INSTANCE.leaveMatch(uuid);
            } else {
                BulletHellManager.INSTANCE.stopArena(uuid);
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Collect dirty enemy-bullet slots, build a BulletDeltaPacket.
     * Returns null if nothing changed.
     */
    public static BulletDeltaPacket buildBulletDelta(ArenaContext ctx) {
        List<Integer> dirty = new ArrayList<>();
        for (int i = 0; i < BulletPool.ENEMY_CAPACITY; i++) {
            if (ctx.bullets.isDirty(i)) dirty.add(i);
        }
        if (dirty.isEmpty()) return null;

        int       n      = dirty.size();
        int[]     slots  = new int[n];
        float[][] data   = new float[n][];
        boolean[] active = new boolean[n];
        for (int i = 0; i < n; i++) {
            int s    = dirty.get(i);
            slots[i]  = s;
            data[i]   = ctx.bullets.getSlotData(s);
            active[i] = ctx.bullets.isActive(s);
        }
        return new BulletDeltaPacket(slots, data, active);
    }

    /** @deprecated Use {@link #buildBulletDelta(ArenaContext)} and send to each participant. */
    public static void sendBulletDelta(ArenaContext ctx, ServerPlayer player) {
        BulletDeltaPacket pkt = buildBulletDelta(ctx);
        if (pkt == null) return;
        ctx.bullets.clearDirty();
        BHNetwork.CHANNEL.sendTo(pkt, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }

    /**
     * Kick off an arena for a player and send the initial full sync.
     * Called by {@link BulletHellCommands} (default character) or CharacterSelectPacket handler.
     */
    public static void startArena(ServerPlayer player, DifficultyConfig diff) {
        startArena(player, diff, "stage_1", "reimu");
    }

    public static void startArena(ServerPlayer player, DifficultyConfig diff, String characterId) {
        startArena(player, diff, "stage_1", characterId);
    }

    public static void startArena(ServerPlayer player, DifficultyConfig diff,
                                   String stageId, String characterId) {
        BulletHellManager.INSTANCE.stopArena(player.getUUID());
        ArenaContext ctx = BulletHellManager.INSTANCE.startArena(
                player.getUUID(), diff, stageId, characterId);
        BHNetwork.CHANNEL.sendTo(
                BulletFullSyncPacket.fromContext(ctx),
                player.connection.connection,
                NetworkDirection.PLAY_TO_CLIENT);
        BHNetwork.CHANNEL.sendTo(
                new ArenaStatePacket(ctx),
                player.connection.connection,
                NetworkDirection.PLAY_TO_CLIENT);
    }

    // ---------------------------------------------------------------- end-of-run stats

    private static void sendEndStats(ServerPlayer player, ArenaContext ctx) {
        mc.sayda.bullethell.arena.PlayerState2D ps = ctx.getPlayerState(player.getUUID());
        if (ps == null) ps = ctx.player;
        long   score    = ctx.score.getScore();
        int    lives    = ps.lives;
        int    bombs    = ps.bombs;
        int    graze    = ps.graze;
        int    captured = ctx.getSpellsCaptured();
        int    attempted= ctx.getSpellsAttempted();
        String charName = mc.sayda.bullethell.boss.CharacterLoader.load(ctx.getCharacterId(player.getUUID())).name;

        if (ctx.isWon()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§6§l╔══════ STAGE CLEAR ══════╗"));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§e  Character : §f" + charName));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§e  Score     : §f" + String.format("%,d", score)));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§e  Lives     : §f" + lives + "  §eBombs : §f" + bombs));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§e  Graze     : §f" + graze));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§e  Spells    : §f" + captured + " / " + attempted + " captured"
                    + (attempted > 0 && captured == attempted ? " §a§l(PERFECT!)" : "")));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§6§l╚═══════════════════════╝"));
        } else {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§c§l╔════════ GAME OVER ════════╗"));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§e  Character : §f" + charName));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§e  Score     : §f" + String.format("%,d", score)));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§e  Graze     : §f" + graze));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§e  Spells    : §f" + captured + " / " + attempted + " captured"));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§c§l╚══════════════════════════╝"));
        }
    }
}
