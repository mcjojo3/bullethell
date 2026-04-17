package mc.sayda.bullethell.network;

import dev.architectury.networking.NetworkManager;
import mc.sayda.bullethell.BHControlSettings;
import mc.sayda.bullethell.client.ClientArenaState;
import mc.sayda.bullethell.network.ArenaEndPacket;
import mc.sayda.bullethell.client.CharacterUnlockClientState;
import mc.sayda.bullethell.network.AllPlayerBulletsSyncPacket;
import mc.sayda.bullethell.client.screen.JoinCharacterSelectScreen;
import mc.sayda.bullethell.client.screen.LevelSelectScreen;
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
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, BHPackets.ARENA_STATE, (buf, ctx) -> {
            ArenaStatePacket pkt = ArenaStatePacket.decode(buf);
            ctx.queue(() -> ClientArenaState.INSTANCE.applyArenaState(pkt));
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

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, BHPackets.ENEMY_SYNC, (buf, ctx) -> {
            EnemySyncPacket pkt = EnemySyncPacket.decode(buf);
            ctx.queue(() -> ClientArenaState.INSTANCE.applyEnemySync(pkt));
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
            ctx.queue(() -> ClientArenaState.INSTANCE.applyGameEvent(pkt));
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, BHPackets.OPEN_CHAR_SELECT, (buf, ctx) -> {
            ctx.queue(() -> Minecraft.getInstance().setScreen(new LevelSelectScreen()));
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, BHPackets.OPEN_JOIN_SELECT, (buf, ctx) -> {
            OpenJoinSelectPacket pkt = OpenJoinSelectPacket.decode(buf);
            ctx.queue(() -> Minecraft.getInstance().setScreen(new JoinCharacterSelectScreen(pkt.hostUuid, pkt.hostName)));
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, BHPackets.OPEN_CHALLENGE, (buf, ctx) -> {
            OpenChallengePacket pkt = OpenChallengePacket.decode(buf);
            ctx.queue(() -> Minecraft.getInstance().setScreen(
                    new mc.sayda.bullethell.client.screen.ChallengeScreen(pkt)));
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, BHPackets.CONTROL_SCHEME, (buf, ctx) -> {
            ControlSchemePacket pkt = ControlSchemePacket.decode(buf);
            ctx.queue(() -> BHControlSettings.applyFromNetwork(pkt.scheme));
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
    }
}
