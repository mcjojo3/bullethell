package mc.sayda.bullethell.arena;

import mc.sayda.bullethell.BHGameRules;
import mc.sayda.bullethell.Bullethell;
import mc.sayda.bullethell.boss.CharacterDefinition;
import mc.sayda.bullethell.boss.CharacterLoader;
import mc.sayda.bullethell.event.BHCommonEvents;
import mc.sayda.bullethell.network.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.concurrent.*;

/**
 * Runs one arena's simulation on a dedicated daemon thread at a fixed 50 ms wall-clock
 * rate, completely decoupled from Minecraft's main-thread tick loop.
 *
 * Threading model
 * ---------------
 *  - This thread OWNS all ArenaContext mutable state.
 *  - MC main thread writes player inputs via {@link ArenaContext#pendingInputs}
 *    (a ConcurrentLinkedQueue); this thread drains it at the start of each tick.
 *  - When the arena ends, this thread queues post-arena logic (advancements,
 *    arena removal, etc.) to {@link ArenaContext#mainCallbacks}, which
 *    {@link BHCommonEvents} drains on the MC main thread every server tick.
 *  - Packet sending uses {@code NetworkManager.sendToPlayer()} which routes through
 *    Netty's thread-safe channel pipeline. {@code playerList.getPlayer()} is read with
 *    null-checks; the practical race risk during player join/leave is negligible.
 */
public final class ArenaThread {

    private final ArenaContext ctx;
    private final UUID hostUuid;
    private final MinecraftServer server;
    private final ScheduledExecutorService executor;
    private int syncTick = 0;

    public ArenaThread(ArenaContext ctx, UUID hostUuid, MinecraftServer server) {
        this.ctx = ctx;
        this.hostUuid = hostUuid;
        this.server = server;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "bh-arena-" + hostUuid.toString().substring(0, 8));
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        // 25 ms initial delay: lets BHPackets.startArena() finish sendFullSync before the
        // first tick reads arena state, avoiding a race on bullet/laser data.
        executor.scheduleAtFixedRate(this::tick, 25L, 50L, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        executor.shutdownNow();
    }

    // ---------------------------------------------------------------- tick

    private void tick() {
        try {
            doTick();
        } catch (Exception e) {
            Bullethell.LOGGER.error("[ArenaThread] Uncaught exception in arena tick for {}", hostUuid, e);
        }
    }

    private void doTick() {
        syncTick++;

        // Apply player inputs queued from C2S packet handlers on the MC main thread
        Runnable r;
        while ((r = ctx.pendingInputs.poll()) != null) r.run();

        ctx.setGloballyPaused(BHGameRules.isGlobalPauseEnabled(server) && ctx.hasPausedParticipants());
        ctx.tick();

        if (ctx.isOver()) {
            ctx.mainCallbacks.offer(() -> BHCommonEvents.handleArenaOver(server, hostUuid, ctx));
            executor.shutdown();
            return;
        }

        sendTick();
    }

    // ---------------------------------------------------------------- packet building

    private void sendTick() {
        List<GameEvent> globalEvents = drain(ctx.pendingEvents);
        List<String> attackSfx     = drain(ctx.pendingAttackActivationSounds);

        // All arena state is owned by this thread between ticks — safe to read.
        BulletDeltaPacket       deltaPacket      = BHCommonEvents.buildBulletDelta(ctx);
        AllPlayerBulletsSyncPacket allBulletsPacket = AllPlayerBulletsSyncPacket.fromContext(ctx);
        boolean laserDirty = ctx.lasers.isDirty();
        LaserSyncPacket         laserPacket      = laserDirty ? new LaserSyncPacket(ctx.lasers) : null;
        ItemSyncPacket          itemPacket       = (syncTick % 2 == 0) ? ItemSyncPacket.fromContext(ctx) : null;
        EnemySyncPacket         enemyPacket      = EnemySyncPacket.fromContext(ctx);

        ctx.bullets.clearDirty();
        if (laserDirty) ctx.lasers.clearDirty();

        Set<UUID> all = new LinkedHashSet<>(ctx.allParticipants());

        Map<UUID, Integer> pIdxMap = new HashMap<>();
        if (ctx.playerUuid != null) pIdxMap.put(ctx.playerUuid, 1);
        int coopCount = 2;
        for (UUID cid : ctx.getCoopPlayers().keySet()) pIdxMap.put(cid, coopCount++);

        Map<UUID, ArenaStatePacket> statePackets = new HashMap<>();
        for (UUID pid : all)
            statePackets.put(pid, new ArenaStatePacket(ctx, pid, pIdxMap.getOrDefault(pid, 1)));

        Map<UUID, List<GameEvent>> personalEvents = new HashMap<>();
        for (UUID pid : all) {
            PlayerState2D ps2d = ctx.getPlayerState(pid);
            if (ps2d == null) continue;
            List<GameEvent> pe = new ArrayList<>();
            GameEvent g;
            while ((g = ps2d.personalEvents.poll()) != null) pe.add(g);
            personalEvents.put(pid, pe);
        }

        Map<UUID, CoopPlayersSyncPacket> coopPackets = buildCoopPackets(all);

        for (UUID pid : all) {
            // NetworkManager.sendToPlayer() routes through Netty — safe from this thread.
            // playerList.getPlayer() is a non-synchronized HashMap read; null-checked below.
            ServerPlayer p = server.getPlayerList().getPlayer(pid);
            if (p == null) continue;

            for (GameEvent ge : globalEvents)
                BHPackets.sendGameEvent(p, new GameEventPacket(ge));
            for (String sfx : attackSfx)
                BHPackets.sendAttackActivationSfx(p, new AttackActivationSfxPacket(sfx));

            List<GameEvent> pe = personalEvents.get(pid);
            if (pe != null)
                for (GameEvent g : pe) BHPackets.sendGameEvent(p, new GameEventPacket(g));

            if (deltaPacket != null) BHPackets.sendBulletDelta(p, deltaPacket);
            BHPackets.sendAllPlayerBullets(p, allBulletsPacket);
            BHPackets.sendToPlayer(p, statePackets.get(pid));
            if (itemPacket  != null) BHPackets.sendItemSync(p, itemPacket);
            if (enemyPacket != null) BHPackets.sendEnemySync(p, enemyPacket);
            CoopPlayersSyncPacket cpp = coopPackets.get(pid);
            if (cpp != null) BHPackets.sendCoopSync(p, cpp);
            if (laserPacket != null) BHPackets.sendLaserSync(p, laserPacket);
        }
    }

    private Map<UUID, CoopPlayersSyncPacket> buildCoopPackets(Set<UUID> all) {
        if (all.size() <= 1) return Collections.emptyMap();
        List<CoopPlayersSyncPacket.Entry> allEntries = new ArrayList<>();
        int idx = 1;
        for (UUID pid : all) {
            PlayerState2D ps = ctx.getPlayerState(pid);
            if (ps != null) {
                String charId = ctx.getCharacterId(pid);
                CharacterDefinition cd = CharacterLoader.load(charId);
                allEntries.add(new CoopPlayersSyncPacket.Entry(
                        ps.x, ps.y, ps.lives, cd.tintColor, charId, idx, ctx.getScore(pid)));
            }
            idx++;
        }
        Map<UUID, CoopPlayersSyncPacket> result = new HashMap<>();
        int recipientIdx = 0;
        for (UUID pid : all) {
            List<CoopPlayersSyncPacket.Entry> others = new ArrayList<>(allEntries.size() - 1);
            for (int i = 0; i < allEntries.size(); i++)
                if (i != recipientIdx) others.add(allEntries.get(i));
            result.put(pid, new CoopPlayersSyncPacket(others));
            recipientIdx++;
        }
        return result;
    }

    private static <T> List<T> drain(Queue<T> queue) {
        if (queue.isEmpty()) return Collections.emptyList();
        List<T> list = new ArrayList<>();
        T item;
        while ((item = queue.poll()) != null) list.add(item);
        return list;
    }
}
