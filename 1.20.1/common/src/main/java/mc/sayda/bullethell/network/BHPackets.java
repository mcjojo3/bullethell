package mc.sayda.bullethell.network;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import mc.sayda.bullethell.Bullethell;
import mc.sayda.bullethell.arena.ArenaContext;
import mc.sayda.bullethell.arena.BulletHellManager;
import mc.sayda.bullethell.arena.DifficultyConfig;
import mc.sayda.bullethell.arena.PlayerState2D;
import mc.sayda.bullethell.boss.CharacterDefinition;
import mc.sayda.bullethell.boss.CharacterLoader;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Cross-platform packet registry using Architectury NetworkManager.
 * <p>
 * This class registers C2S (client→server) handlers and provides send helpers
 * for both directions. S2C handlers are registered separately in
 * {@link BHClientPackets} to avoid loading client-only classes on the server.
 * <p>
 * Call {@link #register()} once during common mod init.
 */
public final class BHPackets {

    private BHPackets() {}

    // ---------------------------------------------------------------- Packet IDs

    // S → C
    public static final ResourceLocation ARENA_STATE       = id("arena_state");
    public static final ResourceLocation BULLET_DELTA      = id("bullet_delta");
    public static final ResourceLocation BULLET_FULL       = id("bullet_full");
    public static final ResourceLocation ALL_PLAYER_BULLETS = id("all_player_bullets");
    public static final ResourceLocation ITEM_SYNC         = id("item_sync");
    public static final ResourceLocation ENEMY_SYNC        = id("enemy_sync");
    public static final ResourceLocation COOP_SYNC         = id("coop_sync");
    public static final ResourceLocation LASER_SYNC        = id("laser_sync");
    public static final ResourceLocation GAME_EVENT        = id("game_event");
    public static final ResourceLocation OPEN_CHAR_SELECT  = id("open_char_select");
    public static final ResourceLocation OPEN_JOIN_SELECT  = id("open_join_select");
    public static final ResourceLocation OPEN_CHALLENGE    = id("open_challenge");

    // C → S
    public static final ResourceLocation PLAYER_POS        = id("player_pos");
    public static final ResourceLocation BOMB              = id("bomb");
    public static final ResourceLocation SKILL             = id("skill");
    public static final ResourceLocation SKIP_DIALOG       = id("skip_dialog");
    public static final ResourceLocation QUIT_ARENA        = id("quit_arena");
    public static final ResourceLocation CHAR_SELECT       = id("char_select");
    public static final ResourceLocation JOIN_MATCH        = id("join_match");
    public static final ResourceLocation INVITE_PLAYER     = id("invite_player");

    private static ResourceLocation id(String path) {
        return new ResourceLocation(Bullethell.MODID, path);
    }

    // ---------------------------------------------------------------- Registration

    public static void register() {
        // C2S: player input every tick
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, PLAYER_POS, (buf, ctx) -> {
            PlayerPos2DPacket pkt = PlayerPos2DPacket.decode(buf);
            ctx.queue(() -> {
                ServerPlayer sender = (ServerPlayer) ctx.getPlayer();
                ArenaContext arena = BulletHellManager.INSTANCE.getArenaForPlayer(sender.getUUID());
                if (arena == null) return;
                PlayerState2D ps = arena.getPlayerState(sender.getUUID());
                if (ps == null) return;
                ps.focused = pkt.focused; ps.shooting = pkt.shooting; ps.isCharging = pkt.charging;
                if (arena.canPlayerMove(sender.getUUID())) ps.move(pkt.dx, pkt.dy);
            });
        });

        // C2S: bomb
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, BOMB, (buf, ctx) -> {
            ctx.queue(() -> {
                ServerPlayer sender = (ServerPlayer) ctx.getPlayer();
                ArenaContext arena = BulletHellManager.INSTANCE.getArenaForPlayer(sender.getUUID());
                if (arena != null) arena.activateBomb(sender.getUUID());
            });
        });

        // C2S: skill (X release)
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, SKILL, (buf, ctx) -> {
            ctx.queue(() -> {
                ServerPlayer sender = (ServerPlayer) ctx.getPlayer();
                ArenaContext arena = BulletHellManager.INSTANCE.getArenaForPlayer(sender.getUUID());
                if (arena != null) arena.activateSkill(sender.getUUID());
            });
        });

        // C2S: dialog skip
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, SKIP_DIALOG, (buf, ctx) -> {
            SkipDialogPacket pkt = SkipDialogPacket.decode(buf);
            ctx.queue(() -> {
                ServerPlayer sender = (ServerPlayer) ctx.getPlayer();
                ArenaContext arena = BulletHellManager.INSTANCE.getArenaForPlayer(sender.getUUID());
                if (arena != null) arena.skipDialog(sender.getUUID(), pkt.skipAll);
            });
        });

        // C2S: quit arena
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, QUIT_ARENA, (buf, ctx) -> {
            ctx.queue(() -> {
                ServerPlayer player = (ServerPlayer) ctx.getPlayer();
                UUID uuid = player.getUUID();
                ArenaContext arena = BulletHellManager.INSTANCE.getArenaForPlayer(uuid);
                if (arena != null) {
                    arena.forceGameOver();
                } else if (BulletHellManager.INSTANCE.isInMatch(uuid)) {
                    BulletHellManager.INSTANCE.leaveMatch(uuid);
                }
            });
        });

        // C2S: character/difficulty selection
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, CHAR_SELECT, (buf, ctx) -> {
            CharacterSelectPacket pkt = CharacterSelectPacket.decode(buf);
            ctx.queue(() -> {
                ServerPlayer player = (ServerPlayer) ctx.getPlayer();
                if (player != null) startArena(player, pkt.difficulty, pkt.stageId, pkt.characterId);
            });
        });

        // C2S: join co-op match
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, JOIN_MATCH, (buf, ctx) -> {
            JoinMatchPacket pkt = JoinMatchPacket.decode(buf);
            ctx.queue(() -> {
                ServerPlayer sender = (ServerPlayer) ctx.getPlayer();
                if (sender == null) return;
                if (sender.getUUID().equals(pkt.hostUuid)) return;
                if (BulletHellManager.INSTANCE.isInMatch(sender.getUUID())) return;

                CharacterDefinition charDef = CharacterLoader.load(pkt.characterId);
                ArenaContext arena = BulletHellManager.INSTANCE.getArenaForPlayer(pkt.hostUuid);

                if (arena != null) {
                    // Join active match immediately
                    BulletHellManager.INSTANCE.joinMatch(sender.getUUID(), pkt.hostUuid, charDef);
                    sendFullSync(sender, arena);
                    int pIdx = 0;
                    int c = 2;
                    for (UUID cid : arena.getCoopPlayers().keySet()) {
                        if (cid.equals(sender.getUUID())) { pIdx = c; break; }
                        c++;
                    }
                    sendToPlayer(sender, new ArenaStatePacket(arena, sender.getUUID(), pIdx));
                } else {
                    // Mark as pending for when host starts
                    BulletHellManager.INSTANCE.addPendingInvite(pkt.hostUuid,
                            new BulletHellManager.ParticipantInfo(sender.getUUID(), charDef));
                    sender.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "[BulletHell] Accepted invitation. Waiting for host to start..."));
                    ServerPlayer hostPlayer = sender.server.getPlayerList().getPlayer(pkt.hostUuid);
                    if (hostPlayer != null) {
                        hostPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                "[BulletHell] " + sender.getName().getString() + " is ready to join."));
                    }
                }
            });
        });

        // C2S: host invites specific player
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, INVITE_PLAYER, (buf, ctx) -> {
            InvitePlayerPacket pkt = InvitePlayerPacket.decode(buf);
            ctx.queue(() -> {
                ServerPlayer sender = (ServerPlayer) ctx.getPlayer();
                if (sender == null) return;
                
                // Remove old arena check - can invite from select screen
                // if (!BulletHellManager.INSTANCE.hasArena(sender.getUUID())) return;
                
                ServerPlayer target = sender.server.getPlayerList().getPlayer(pkt.targetUuid);
                if (target == null) {
                    sender.sendSystemMessage(net.minecraft.network.chat.Component.literal("[BulletHell] Player is no longer online."));
                    return;
                }
                
                if (BulletHellManager.INSTANCE.isInMatch(target.getUUID())) {
                    sender.sendSystemMessage(net.minecraft.network.chat.Component.literal("[BulletHell] Player is already in an arena."));
                    return;
                }
                
                sendOpenJoinSelect(target, new OpenJoinSelectPacket(sender.getUUID(), sender.getName().getString()));
                sender.sendSystemMessage(net.minecraft.network.chat.Component.literal("[BulletHell] Invite sent to " + target.getName().getString() + "."));
            });
        });
    }

    // ---------------------------------------------------------------- Server → Client helpers

    private static FriendlyByteBuf buf() { return new FriendlyByteBuf(Unpooled.buffer()); }

    public static void sendToPlayer(ServerPlayer player, ArenaStatePacket pkt) {
        FriendlyByteBuf b = buf(); pkt.encode(b); NetworkManager.sendToPlayer(player, ARENA_STATE, b);
    }

    public static void sendBulletDelta(ServerPlayer player, BulletDeltaPacket pkt) {
        if (pkt == null) return;
        FriendlyByteBuf b = buf(); pkt.encode(b); NetworkManager.sendToPlayer(player, BULLET_DELTA, b);
    }

    public static void sendFullSync(ServerPlayer player, ArenaContext ctx) {
        BulletFullSyncPacket pkt = BulletFullSyncPacket.fromContext(ctx);
        FriendlyByteBuf b = buf(); pkt.encode(b); NetworkManager.sendToPlayer(player, BULLET_FULL, b);
    }

    public static void sendAllPlayerBullets(ServerPlayer player, AllPlayerBulletsSyncPacket pkt) {
        FriendlyByteBuf b = buf(); pkt.encode(b); NetworkManager.sendToPlayer(player, ALL_PLAYER_BULLETS, b);
    }

    public static void sendItemSync(ServerPlayer player, ItemSyncPacket pkt) {
        FriendlyByteBuf b = buf(); pkt.encode(b); NetworkManager.sendToPlayer(player, ITEM_SYNC, b);
    }

    public static void sendEnemySync(ServerPlayer player, EnemySyncPacket pkt) {
        FriendlyByteBuf b = buf(); pkt.encode(b); NetworkManager.sendToPlayer(player, ENEMY_SYNC, b);
    }

    public static void sendCoopSync(ServerPlayer player, CoopPlayersSyncPacket pkt) {
        FriendlyByteBuf b = buf(); pkt.encode(b); NetworkManager.sendToPlayer(player, COOP_SYNC, b);
    }

    public static void sendLaserSync(ServerPlayer player, LaserSyncPacket pkt) {
        FriendlyByteBuf b = buf(); pkt.encode(b); NetworkManager.sendToPlayer(player, LASER_SYNC, b);
    }

    public static void sendGameEvent(ServerPlayer player, GameEventPacket pkt) {
        FriendlyByteBuf b = buf(); pkt.encode(b); NetworkManager.sendToPlayer(player, GAME_EVENT, b);
    }

    public static void sendOpenCharSelect(ServerPlayer player) {
        NetworkManager.sendToPlayer(player, OPEN_CHAR_SELECT, buf());
    }

    public static void sendOpenJoinSelect(ServerPlayer player, OpenJoinSelectPacket pkt) {
        FriendlyByteBuf b = buf(); pkt.encode(b); NetworkManager.sendToPlayer(player, OPEN_JOIN_SELECT, b);
    }

    public static void sendOpenChallenge(ServerPlayer player, OpenChallengePacket pkt) {
        FriendlyByteBuf b = buf(); pkt.encode(b); NetworkManager.sendToPlayer(player, OPEN_CHALLENGE, b);
    }

    // ---------------------------------------------------------------- Client → Server helpers

    @Environment(EnvType.CLIENT)
    public static void sendPlayerPos(float dx, float dy, boolean focused, boolean shooting, boolean charging) {
        FriendlyByteBuf b = buf();
        new PlayerPos2DPacket(dx, dy, focused, shooting, charging).encode(b);
        NetworkManager.sendToServer(PLAYER_POS, b);
    }

    @Environment(EnvType.CLIENT)
    public static void sendBomb() {
        NetworkManager.sendToServer(BOMB, buf());
    }

    @Environment(EnvType.CLIENT)
    public static void sendSkill() {
        NetworkManager.sendToServer(SKILL, buf());
    }

    @Environment(EnvType.CLIENT)
    public static void sendSkipDialog(boolean skipAll) {
        FriendlyByteBuf b = buf(); new SkipDialogPacket(skipAll).encode(b); NetworkManager.sendToServer(SKIP_DIALOG, b);
    }

    @Environment(EnvType.CLIENT)
    public static void sendQuitArena() {
        NetworkManager.sendToServer(QUIT_ARENA, buf());
    }

    @Environment(EnvType.CLIENT)
    public static void sendCharSelect(String characterId, DifficultyConfig difficulty, String stageId) {
        FriendlyByteBuf b = buf();
        new CharacterSelectPacket(characterId, difficulty, stageId).encode(b);
        NetworkManager.sendToServer(CHAR_SELECT, b);
    }

    @Environment(EnvType.CLIENT)
    public static void sendJoinMatch(UUID hostUuid, String characterId) {
        FriendlyByteBuf b = buf();
        new JoinMatchPacket(hostUuid, characterId).encode(b);
        NetworkManager.sendToServer(JOIN_MATCH, b);
    }

    @Environment(EnvType.CLIENT)
    public static void sendInvitePlayer(UUID targetUuid) {
        FriendlyByteBuf b = buf();
        new InvitePlayerPacket(targetUuid).encode(b);
        NetworkManager.sendToServer(INVITE_PLAYER, b);
    }

    // ---------------------------------------------------------------- Server-side helpers (used by BHCommonEvents)

    /** Start an arena for a player, send initial full sync + state. */
    public static void startArena(ServerPlayer player, DifficultyConfig diff,
                                   String stageId, String characterId) {
        BulletHellManager.INSTANCE.stopArena(player.getUUID());
        ArenaContext ctx = BulletHellManager.INSTANCE.startArena(
                player.getUUID(), diff, stageId, characterId);
        sendFullSync(player, ctx);
        sendToPlayer(player, new ArenaStatePacket(ctx, player.getUUID(), 1));

        // Auto-join pending participants
        java.util.List<BulletHellManager.ParticipantInfo> pending = BulletHellManager.INSTANCE
                .getAndClearPendingInvites(player.getUUID());
        if (pending != null) {
            for (BulletHellManager.ParticipantInfo info : pending) {
                ServerPlayer p = player.server.getPlayerList().getPlayer(info.uuid());
                if (p != null) {
                    BulletHellManager.INSTANCE.joinMatch(p.getUUID(), player.getUUID(), info.charDef());
                    sendFullSync(p, ctx);
                    int pIdx = 0;
                    int c = 2;
                    for (UUID cid : ctx.getCoopPlayers().keySet()) {
                        if (cid.equals(p.getUUID())) { pIdx = c; break; }
                        c++;
                    }
                    sendToPlayer(p, new ArenaStatePacket(ctx, p.getUUID(), pIdx));
                }
            }
        }
    }

    public static void startArena(ServerPlayer player, DifficultyConfig diff) {
        startArena(player, diff, "stage_1", "reimu");
    }
}
