package mc.sayda.bullethell.network;

import dev.architectury.networking.NetworkManager;
import mc.sayda.bullethell.client.ClientArenaState;
import mc.sayda.bullethell.network.ArenaEndPacket;
import mc.sayda.bullethell.client.CharacterUnlockClientState;
import mc.sayda.bullethell.network.AllPlayerBulletsSyncPacket;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;

/**
 * Registers S2C (server-to-client) handlers for Architectury NetworkManager.
 * Segregated to a client-only class to prevent dedicated server classloading issues
 * when referencing ClientArenaState or rendering classes.
 */
@Environment(EnvType.CLIENT)
public final class BHClientPackets {

    private BHClientPackets() {}

    public static void register() {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, BHPackets.DATA_SYNC, (buf, ctx) -> {
            DataSyncPacket pkt = DataSyncPacket.decode(buf);
            ctx.queue(pkt::apply);
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, BHPackets.LOBBY_STATE, (buf, ctx) -> {
            LobbyStatePacket pkt = LobbyStatePacket.decode(buf);
            ctx.queue(() -> {
                mc.sayda.bullethell.client.ClientLobbyState.INSTANCE.apply(pkt);
                Minecraft mc = Minecraft.getInstance();
                if (pkt.closed) {
                    // Only close the party screen; the arena packets that follow a start
                    // will put up their own screen.
                    if (mc.screen instanceof mc.sayda.bullethell.client.screen.LobbyScreen) {
                        mc.setScreen(null);
                    }
                } else if (mc.screen instanceof mc.sayda.bullethell.client.screen.LobbyScreen lobbyScreen) {
                    lobbyScreen.onLobbyUpdated();
                } else {
                    mc.setScreen(new mc.sayda.bullethell.client.screen.LobbyScreen());
                }
            });
        });

        // The server hands over the seed; from here the client runs its own fight.
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, BHPackets.ARENA_START, (buf, ctx) -> {
            ArenaStartPacket pkt = ArenaStartPacket.decode(buf);
            ctx.queue(() -> mc.sayda.bullethell.client.ClientArenaSim.INSTANCE.start(pkt));
        });

        // The one resync barrier: every client lands on the same phase here.
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, BHPackets.PHASE_TRANSITION, (buf, ctx) -> {
            PhaseTransitionPacket pkt = PhaseTransitionPacket.decode(buf);
            ctx.queue(() -> mc.sayda.bullethell.client.ClientArenaSim.INSTANCE.applyPhaseTransition(pkt));
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, BHPackets.SPLASH, (buf, ctx) -> {
            SplashPacket pkt = SplashPacket.decode(buf);
            ctx.queue(() -> mc.sayda.bullethell.client.SplashState.INSTANCE.playFromLeft(pkt.id));
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, BHPackets.ARENA_STATE, (buf, ctx) -> {
            ArenaStatePacket pkt = ArenaStatePacket.decode(buf);
            ctx.queue(() -> {
                ClientArenaState.INSTANCE.applyArenaState(pkt);
                // The HP bar should read the shared total, not this client's share of it.
                if (pkt.active)
                    mc.sayda.bullethell.client.ClientArenaSim.INSTANCE.applyAuthorityHp(pkt.bossHp, pkt.bossMaxHp);
            });
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, BHPackets.BULLET_DELTA, (buf, ctx) -> {
            BulletDeltaPacket pkt = BulletDeltaPacket.decode(buf);
            ctx.queue(() -> ClientArenaState.INSTANCE.applyBulletDelta(pkt));
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, BHPackets.BULLET_FULL, (buf, ctx) -> {
            BulletFullSyncPacket pkt = BulletFullSyncPacket.decode(buf);
            ctx.queue(() -> ClientArenaState.INSTANCE.applyBulletFullSync(pkt));
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, BHPackets.ALL_PLAYER_BULLETS, (buf, ctx) -> {
            AllPlayerBulletsSyncPacket pkt = AllPlayerBulletsSyncPacket.decode(buf);
            ctx.queue(() -> ClientArenaState.INSTANCE.applyAllPlayerBullets(pkt));
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, BHPackets.ITEM_SYNC, (buf, ctx) -> {
            ItemSyncPacket pkt = ItemSyncPacket.decode(buf);
            ctx.queue(() -> ClientArenaState.INSTANCE.applyItemSync(pkt));
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, BHPackets.COOP_SYNC, (buf, ctx) -> {
            CoopPlayersSyncPacket pkt = CoopPlayersSyncPacket.decode(buf);
            ctx.queue(() -> ClientArenaState.INSTANCE.applyCoopSync(pkt));
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, BHPackets.LASER_SYNC, (buf, ctx) -> {
            LaserSyncPacket pkt = LaserSyncPacket.decode(buf);
            ctx.queue(() -> ClientArenaState.INSTANCE.applyLaserSync(pkt));
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, BHPackets.GAME_EVENT, (buf, ctx) -> {
            GameEventPacket pkt = GameEventPacket.decode(buf);
            ctx.queue(() -> {
                ClientArenaState.INSTANCE.applyGameEvent(pkt);
                // While simulating, the only extend that arrives as a packet is one another
                // player earned for the party - the local sim has to actually apply it.
                if (pkt.event == mc.sayda.bullethell.arena.GameEvent.SCORE_EXTEND)
                    mc.sayda.bullethell.client.ClientArenaSim.INSTANCE.grantRelayedExtend();
            });
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, BHPackets.ATTACK_ACTIVATION_SFX, (buf, ctx) -> {
            AttackActivationSfxPacket pkt = AttackActivationSfxPacket.decode(buf);
            ctx.queue(() -> ClientArenaState.INSTANCE.applyAttackActivationSfx(pkt));
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, BHPackets.OPEN_CHALLENGE, (buf, ctx) -> {
            OpenChallengePacket pkt = OpenChallengePacket.decode(buf);
            ctx.queue(() -> Minecraft.getInstance().setScreen(
                    new mc.sayda.bullethell.client.screen.ChallengeScreen(pkt)));
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, BHPackets.CHARACTER_UNLOCKS, (buf, ctx) -> {
            CharacterUnlockSyncPacket pkt = CharacterUnlockSyncPacket.decode(buf);
            ctx.queue(() -> CharacterUnlockClientState.INSTANCE.applyFromNetwork(pkt.maxDifficultyByCharacter));
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, BHPackets.ARENA_END, (buf, ctx) -> {
            ArenaEndPacket pkt = ArenaEndPacket.decode(buf);
            ctx.queue(() -> {
                mc.sayda.bullethell.client.ClientArenaState.INSTANCE.pendingEndOverlay = true;
                Minecraft.getInstance().setScreen(new mc.sayda.bullethell.client.screen.ArenaEndScreen(pkt));
            });
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, BHPackets.TEST_MODE_OPEN, (buf, ctx) -> {
            TestModeOpenPacket pkt = TestModeOpenPacket.decode(buf);
            ctx.queue(() -> {
                mc.sayda.bullethell.client.ClientArenaState state = mc.sayda.bullethell.client.ClientArenaState.INSTANCE;
                state.testMode = true;
                state.testBossIds.clear();  state.testBossIds.addAll(pkt.bossIds);
                state.testStageIds.clear(); state.testStageIds.addAll(pkt.stageIds);
                state.testCharIds.clear();  state.testCharIds.addAll(pkt.charIds);
                state.testCurrentBossId   = pkt.currentBossId;
                state.testCurrentStageId  = pkt.currentStageId;
                if (!pkt.currentCharId.isEmpty()) state.testCurrentCharId = pkt.currentCharId;
                state.testCurrentDifficulty = pkt.difficultyOrdinal;
                // Sync selected indices to match current selections
                for (int i = 0; i < pkt.bossIds.size(); i++)
                    if (pkt.bossIds.get(i).equals(pkt.currentBossId)) { state.testSelectedIdx = i; break; }
                for (int i = 0; i < pkt.stageIds.size(); i++)
                    if (pkt.stageIds.get(i).equals(pkt.currentStageId)) { state.testStageSelectedIdx = i; break; }
                for (int i = 0; i < pkt.charIds.size(); i++)
                    if (pkt.charIds.get(i).equals(pkt.currentCharId)) { state.testCharSelectedIdx = i; break; }
            });
        });
    }
}
