package mc.sayda.bullethell.client;

import mc.sayda.bullethell.Bullethell;
import mc.sayda.bullethell.arena.ArenaContext;
import mc.sayda.bullethell.arena.DifficultyConfig;
import mc.sayda.bullethell.arena.GameEvent;
import mc.sayda.bullethell.arena.PlayerState2D;
import mc.sayda.bullethell.boss.StageDefinition;
import mc.sayda.bullethell.boss.StageLoader;
import mc.sayda.bullethell.network.ArenaProgressPacket;
import mc.sayda.bullethell.network.ArenaStartPacket;
import mc.sayda.bullethell.network.BHPackets;
import mc.sayda.bullethell.network.PhaseTransitionPacket;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;

import java.util.UUID;

/**
 * A full copy of the fight, running on this client.
 *
 * The mod used to push every bullet from the server to every client each tick - tens of
 * kilobytes per tick, which the host never felt and everyone else played through as
 * rubber-banding. Here the client owns the bullets instead: it builds the same
 * {@link ArenaContext} the server would have, from the same seed, and runs it locally.
 * Input is applied with no round trip at all, so movement and shooting are exact.
 *
 * What still crosses the wire is only what the players genuinely share:
 * <ul>
 *   <li>up: boss damage and this player's own state ({@link ArenaProgressPacket}),</li>
 *   <li>down: boss HP and phase, dialog, music - the server's {@code ArenaStatePacket},</li>
 *   <li>down: {@link PhaseTransitionPacket}, the one point where every client is
 *       snapped back onto the same phase so drift cannot accumulate.</li>
 * </ul>
 *
 * Two clients therefore see slightly different bullet layouts between transitions. That
 * is the deliberate trade: identical screens were what cost the bandwidth in the first
 * place, and nothing that decides the outcome - HP, phase, lives, score - is left to
 * diverge.
 */
@Environment(EnvType.CLIENT)
public final class ClientArenaSim {

    public static final ClientArenaSim INSTANCE = new ClientArenaSim();

    private ArenaContext sim;
    private UUID localUuid;
    /** Highest phase epoch already applied; guards against duplicate or late packets. */
    private int appliedEpoch = -1;
    /** Local score at the last report, so only the difference is sent. */
    private long lastReportedScore = 0L;

    private ClientArenaSim() {}

    public boolean isActive() {
        return sim != null;
    }

    public ArenaContext context() {
        return sim;
    }

    // ---------------------------------------------------------------- lifecycle

    /** Builds the local copy of the fight described by {@code pkt}. */
    public void start(ArenaStartPacket pkt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        stop();
        try {
            StageDefinition stage = StageLoader.load(pkt.stageId);
            DifficultyConfig[] all = DifficultyConfig.values();
            DifficultyConfig diff = all[Math.max(0, Math.min(pkt.difficultyOrdinal, all.length - 1))];
            localUuid = mc.player.getUUID();
            ArenaContext ctx = new ArenaContext(localUuid, diff, stage, pkt.characterId,
                    pkt.bonuses(), pkt.seed);
            // Simulate everything, decide nothing.
            ctx.simulatesWorld = true;
            ctx.ownsPhaseTransitions = false;
            sim = ctx;
            appliedEpoch = ctx.phaseEpoch;
            lastReportedScore = 0L;
            ClientArenaState.INSTANCE.playerIndex = pkt.playerIndex;
            ClientArenaState.INSTANCE.bindSim(ctx.bullets, ctx.playerBullets, ctx.items, ctx.lasers);
        } catch (Exception e) {
            // Without a sim the client still receives the server's own arena packets, so
            // log and carry on rather than dropping the player out of the run.
            Bullethell.LOGGER.error("[BulletHell] Failed to start client sim for stage {}", pkt.stageId, e);
            sim = null;
            localUuid = null;
        }
    }

    public void stop() {
        if (sim == null) return;
        sim = null;
        localUuid = null;
        appliedEpoch = -1;
        lastReportedScore = 0L;
        ClientArenaState.INSTANCE.unbindSim();
    }

    // ---------------------------------------------------------------- authority input

    /** Snaps the local fight onto the phase the server decided. */
    public void applyPhaseTransition(PhaseTransitionPacket pkt) {
        if (sim == null) return;
        if (pkt.phaseEpoch <= appliedEpoch) return; // already applied, or a stale packet
        appliedEpoch = pkt.phaseEpoch;
        sim.forcePhase(pkt.phaseIndex, pkt.bossHp, pkt.bossMaxHp, pkt.musicId, pkt.phaseEpoch);
    }

    /**
     * Adds a life the server says this player was given - the co-op rule where one
     * player crossing a score milestone extends everyone. Only that case reaches here:
     * a player's own extend is granted inside their own simulation.
     */
    public void grantRelayedExtend() {
        if (sim == null || localUuid == null) return;
        PlayerState2D ps = sim.getPlayerState(localUuid);
        if (ps != null && ps.lives >= 0) ps.lives++;
    }

    /** Keeps the local HP bar on the shared total rather than this client's share of it. */
    public void applyAuthorityHp(int hp, int maxHp) {
        if (sim != null) sim.applyAuthorityHp(hp, maxHp);
    }

    // ---------------------------------------------------------------- per-tick

    /**
     * Advances the local fight one tick and reports the result upstream.
     * Called from the client tick once input has been read.
     *
     * @param acceptInput false while a menu is open or the player is spectating
     */
    public void tick(float dx, float dy, boolean focused, boolean shooting, boolean acceptInput) {
        if (sim == null || localUuid == null) return;
        if (ClientArenaState.INSTANCE.globallyPaused) return;

        PlayerState2D ps = sim.getPlayerState(localUuid);
        if (ps != null) {
            if (acceptInput) {
                ps.focused = focused;
                ps.shooting = shooting;
                if (sim.canPlayerMove(localUuid)) ps.move(dx, dy);
            } else {
                ps.shooting = false;
            }
        }

        sim.tick();

        publish(ClientArenaState.INSTANCE);
        drainLocalEvents();
        BHPackets.sendArenaProgress(buildProgress(sim.getPlayerState(localUuid)));
    }

    /** Fires a bomb locally, with no round trip - the cut-in has to answer the key press. */
    public void bomb() {
        if (sim != null && localUuid != null) sim.activateBomb(localUuid);
    }

    private ArenaProgressPacket buildProgress(PlayerState2D ps) {
        int damage = sim.consumeBossDamage();
        long score = sim.getScore(localUuid);
        long scoreDelta = Math.max(0L, score - lastReportedScore);
        lastReportedScore = score;
        if (ps == null) {
            return new ArenaProgressPacket(damage, scoreDelta, 0f, 0f, 0, 0, 0, 0, 0,
                    0, 0, 0, sim.phaseEpoch);
        }
        return new ArenaProgressPacket(damage, scoreDelta, ps.x, ps.y, ps.lives, ps.bombs,
                ps.power, ps.graze, ps.grazeChain,
                ps.lifePieces, ps.bombPieces, ps.invulnTicks, sim.phaseEpoch);
    }

    /**
     * Copies the half of the HUD the sim owns into {@link ClientArenaState}.
     *
     * The server's state packet writes the whole struct when it arrives; this runs every
     * tick afterwards and takes back the fields the local fight is authoritative for, so
     * the two never fight over the same value. Boss HP, phase, dialog, spell name and
     * music are deliberately absent - those stay the server's.
     */
    private void publish(ClientArenaState state) {
        PlayerState2D ps = sim.getPlayerState(localUuid);
        if (ps != null) {
            state.player.x = ps.x;
            state.player.y = ps.y;
            state.player.lives = ps.lives;
            state.player.bombs = ps.bombs;
            state.player.graze = ps.graze;
            state.player.hitRadius = ps.hitRadius;
            state.player.grazeRadius = ps.grazeRadius;
            state.player.speedNormal = ps.speedNormal;
            state.player.speedFocused = ps.speedFocused;
            // No prediction needed when the position is computed here: it is the truth.
            state.predX = ps.x;
            state.predY = ps.y;
            state.power = ps.power;
            state.grazeChain = ps.grazeChain;
            state.lifePieces = ps.lifePieces;
            state.bombPieces = ps.bombPieces;
            state.spectating = ps.lives < 0;
        }
        state.prevBossX = state.bossX;
        state.prevBossY = state.bossY;
        state.bossX = sim.bossX;
        state.bossY = sim.bossY;
        state.bossMoveDir = sim.getBossMoveDir();
        state.pentagramRitualTick = sim.getPentagramRitualTick();
        state.pentagramStackCompleteTick = sim.getPentagramStackCompleteTick();
        state.debugEnemyBulletCount = sim.bullets.getActiveCount();
        state.playShootSfxForNewBullets();
    }

    /**
     * Plays the sounds and screen effects the local fight produced. These used to arrive
     * as {@code GameEventPacket}s; produced here they land on the same tick as the hit
     * that caused them.
     */
    private void drainLocalEvents() {
        ClientArenaState state = ClientArenaState.INSTANCE;

        GameEvent e;
        while ((e = sim.pendingEvents.poll()) != null)
            state.applyGameEvent(e);

        PlayerState2D ps = sim.getPlayerState(localUuid);
        if (ps != null) {
            while ((e = ps.personalEvents.poll()) != null)
                state.applyGameEvent(e);
        }

        // The bomb cut-in: the local player's own portrait, drawn the moment they press.
        ArenaContext.SplashOrder order;
        while ((order = sim.pendingSplashes.poll()) != null)
            SplashState.INSTANCE.playFromLeft(order.characterId());

        // Attack-activation sounds are part of the pattern, so they come from the sim too.
        String sfx;
        while ((sfx = sim.pendingAttackActivationSounds.poll()) != null)
            state.applyAttackActivationSfx(sfx);
    }
}
