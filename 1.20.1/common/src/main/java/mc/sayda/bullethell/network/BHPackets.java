package mc.sayda.bullethell.network;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import mc.sayda.bullethell.BossProgression;
import mc.sayda.bullethell.CharacterUnlocks;
import mc.sayda.bullethell.Bullethell;
import mc.sayda.bullethell.arena.ArenaContext;
import mc.sayda.bullethell.arena.BulletHellManager;
import mc.sayda.bullethell.arena.LobbySession;
import mc.sayda.bullethell.arena.DifficultyConfig;
import mc.sayda.bullethell.arena.PlayerState2D;
import mc.sayda.bullethell.boss.BossLoader;
import mc.sayda.bullethell.boss.CharacterLoader;
import mc.sayda.bullethell.boss.StageDefinition;
import mc.sayda.bullethell.boss.StageLoader;
import mc.sayda.bullethell.debug.BHDebugMode;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
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
    public static final ResourceLocation DATA_SYNC         = id("data_sync");
    public static final ResourceLocation LOBBY_STATE       = id("lobby_state");
    public static final ResourceLocation ARENA_STATE       = id("arena_state");
    public static final ResourceLocation BULLET_DELTA      = id("bullet_delta");
    public static final ResourceLocation BULLET_FULL       = id("bullet_full");
    public static final ResourceLocation ALL_PLAYER_BULLETS = id("all_player_bullets");
    public static final ResourceLocation ITEM_SYNC         = id("item_sync");
    public static final ResourceLocation COOP_SYNC         = id("coop_sync");
    public static final ResourceLocation LASER_SYNC        = id("laser_sync");
    public static final ResourceLocation GAME_EVENT        = id("game_event");
    public static final ResourceLocation ATTACK_ACTIVATION_SFX = id("attack_activation_sfx");
    public static final ResourceLocation SPLASH             = id("splash");
    public static final ResourceLocation OPEN_CHALLENGE    = id("open_challenge");
    public static final ResourceLocation CHARACTER_UNLOCKS = id("character_unlocks");
    /** S → C | arena ended; carry stats + boss quote for the end overlay. */
    public static final ResourceLocation ARENA_END         = id("arena_end");
    /** S → C | seed + run settings so the client can build its own copy of the fight. */
    public static final ResourceLocation ARENA_START       = id("arena_start");
    /** S → C | the boss changed phase; the resync barrier for client-side simulation. */
    public static final ResourceLocation PHASE_TRANSITION  = id("phase_transition");

    // C → S
    public static final ResourceLocation PLAYER_POS        = id("player_pos");
    public static final ResourceLocation BOMB              = id("bomb");
    public static final ResourceLocation REQUEST_DATA_SYNC = id("request_data_sync");
    public static final ResourceLocation SKIP_DIALOG       = id("skip_dialog");
    public static final ResourceLocation QUIT_ARENA        = id("quit_arena");
    public static final ResourceLocation PAUSE_STATE       = id("pause_state");
    public static final ResourceLocation CHAR_SELECT       = id("char_select");
    public static final ResourceLocation INVITE_PLAYER     = id("invite_player");
    public static final ResourceLocation LOBBY_ACTION      = id("lobby_action");
    public static final ResourceLocation SHARE_LAST_RUN    = id("share_last_run");
    /** C → S | player requests a retry of the last arena. */
    public static final ResourceLocation RETRY_ARENA       = id("retry_arena");
    /** S → C | where a co-op party's retry vote stands, for the RETRY button's label. */
    public static final ResourceLocation RETRY_VOTE        = id("retry_vote");
    /** S → C | opens test-mode overlay with boss list and current selection. */
    public static final ResourceLocation TEST_MODE_OPEN    = id("test_mode_open");
    /** C → S | select / reload a boss in test mode. */
    public static final ResourceLocation TEST_SELECT       = id("test_select");
    public static final ResourceLocation TEST_CONTROL      = id("test_control");
    /** C → S | one tick of results from a client-side simulation. */
    public static final ResourceLocation ARENA_PROGRESS    = id("arena_progress");

    private static ResourceLocation id(String path) {
        return new ResourceLocation(Bullethell.MODID, path);
    }

    // ---------------------------------------------------------------- Registration

    public static void register() {
        // C2S: player input every tick - queued to arena thread via pendingInputs
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, PLAYER_POS, (buf, ctx) -> {
            PlayerPos2DPacket pkt = PlayerPos2DPacket.decode(buf);
            ctx.queue(() -> {
                ServerPlayer sender = (ServerPlayer) ctx.getPlayer();
                UUID id = sender.getUUID();
                ArenaContext arena = BulletHellManager.INSTANCE.getArenaForPlayer(id);
                if (arena == null) return;
                arena.pendingInputs.offer(() -> {
                    PlayerState2D ps = arena.getPlayerState(id);
                    if (ps == null) return;
                    ps.focused = pkt.focused; ps.shooting = pkt.shooting;
                    if (arena.canPlayerMove(id)) ps.move(pkt.dx, pkt.dy);
                });
            });
        });

        // C2S: bomb
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, BOMB, (buf, ctx) -> {
            ctx.queue(() -> {
                ServerPlayer sender = (ServerPlayer) ctx.getPlayer();
                UUID id = sender.getUUID();
                ArenaContext arena = BulletHellManager.INSTANCE.getArenaForPlayer(id);
                if (arena == null) return;
                if (arena.simulatesWorld)
                    arena.pendingInputs.offer(() -> arena.activateBomb(id));
                else
                    // The bomber already ran it locally; all the server owes the others
                    // is the cut-in, so they see who bombed.
                    arena.pendingInputs.offer(() -> arena.relayBombSplash(id));
            });
        });

        // C2S: results of one tick of a client-side simulation. Replaces the bullet
        // stream that used to go the other way.
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, ARENA_PROGRESS, (buf, ctx) -> {
            ArenaProgressPacket pkt = ArenaProgressPacket.decode(buf);
            ctx.queue(() -> {
                ServerPlayer sender = (ServerPlayer) ctx.getPlayer();
                if (sender == null) return;
                UUID id = sender.getUUID();
                ArenaContext arena = BulletHellManager.INSTANCE.getArenaForPlayer(id);
                // Ignore progress from a context that is simulating for itself: that is a
                // client reporting into a server that never handed out the sim.
                if (arena == null || arena.simulatesWorld) return;
                arena.pendingInputs.offer(() -> {
                    // A client one phase behind is still shooting at the previous boss;
                    // drop its damage rather than let it bleed into the new phase.
                    if (pkt.phaseEpoch == arena.phaseEpoch)
                        arena.applyRemoteDamage(pkt.bossDamage, id);
                    arena.applyRemoteScore(id, pkt.scoreDelta);
                    arena.applyRemoteProgress(id, pkt.x, pkt.y, pkt.lives, pkt.bombs, pkt.power,
                            pkt.graze, pkt.grazeChain,
                            pkt.lifePieces, pkt.bombPieces, pkt.invulnTicks);
                });
            });
        });

        // C2S: client asks for content data. Avoids the PLAYER_JOIN race on dedicated
        // servers, where the join event can fire before the client can receive.
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, REQUEST_DATA_SYNC, (buf, ctx) -> {
            ctx.queue(() -> {
                ServerPlayer sender = (ServerPlayer) ctx.getPlayer();
                if (sender != null) sendDataSync(sender);
            });
        });

        // C2S: every lobby action (character pick, ready, run settings, start, leave)
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, LOBBY_ACTION, (buf, ctx) -> {
            LobbyActionPacket pkt = LobbyActionPacket.decode(buf);
            ctx.queue(() -> {
                ServerPlayer sender = (ServerPlayer) ctx.getPlayer();
                if (sender == null) return;
                PartyHandler.handleAction(sender, pkt);
            });
        });

        // C2S: dialog skip
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, SKIP_DIALOG, (buf, ctx) -> {
            SkipDialogPacket pkt = SkipDialogPacket.decode(buf);
            ctx.queue(() -> {
                ServerPlayer sender = (ServerPlayer) ctx.getPlayer();
                UUID id = sender.getUUID();
                ArenaContext arena = BulletHellManager.INSTANCE.getArenaForPlayer(id);
                if (arena != null) arena.pendingInputs.offer(() -> arena.skipDialog(id, pkt.skipAll));
            });
        });

        // C2S: quit arena
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, QUIT_ARENA, (buf, ctx) -> {
            ctx.queue(() -> {
                ServerPlayer player = (ServerPlayer) ctx.getPlayer();
                UUID uuid = player.getUUID();
                ArenaContext arena = BulletHellManager.INSTANCE.getArenaForPlayer(uuid);
                if (arena != null) {
                    // forceGameOver() sets a volatile boolean - safe to call from any thread
                    // Quitting clears any hold this player had; the name is only used while paused.
                    arena.pendingInputs.offer(() -> arena.setParticipantPaused(uuid, false, ""));
                    arena.forceGameOver();
                } else if (BulletHellManager.INSTANCE.isInMatch(uuid)) {
                    BulletHellManager.INSTANCE.leaveMatch(uuid);
                }
            });
        });

        // C2S: pause state (opened/closed arena pause menu)
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, PAUSE_STATE, (buf, ctx) -> {
            PauseStatePacket pkt = PauseStatePacket.decode(buf);
            ctx.queue(() -> {
                ServerPlayer sender = (ServerPlayer) ctx.getPlayer();
                if (sender == null) return;
                UUID id = sender.getUUID();
                // Read here on the main thread - the offer below runs on the arena thread.
                String name = sender.getGameProfile().getName();
                ArenaContext arena = BulletHellManager.INSTANCE.getArenaForPlayer(id);
                if (arena != null)
                    arena.pendingInputs.offer(() -> arena.setParticipantPaused(id, pkt.paused, name));
            });
        });

        // C2S: character/difficulty selection
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, CHAR_SELECT, (buf, ctx) -> {
            CharacterSelectPacket pkt = CharacterSelectPacket.decode(buf);
            ctx.queue(() -> {
                ServerPlayer player = (ServerPlayer) ctx.getPlayer();
                if (player == null)
                    return;
                boolean debugBypass = BHDebugMode.isGodMode(player.getUUID());
                if (!debugBypass && !CharacterUnlocks.isUnlockedFor(player, pkt.characterId, pkt.difficulty)) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "[BulletHell] Character '" + pkt.characterId + "' is locked for " + pkt.difficulty.name() + "."));
                    return;
                }
                if (!debugBypass && !pkt.practice && !BossProgression.canChallengeStage(player, pkt.stageId, pkt.difficulty)) {
                    String bossId;
                    try {
                        bossId = mc.sayda.bullethell.boss.StageLoader.load(pkt.stageId).bossId;
                    } catch (Exception e) {
                        bossId = "";
                    }
                    var cap = BossProgression.maxAllowedDifficulty(player, bossId);
                    String capText = (cap == null) ? "none" : cap.name();
                    String why = BossProgression.requirementSummary(bossId);
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "[BulletHell] " + pkt.stageId + " is currently capped at " + capText + ". " + why));
                    return;
                }
                // Going solo from inside a party: leave it rather than hold a slot open.
                if (PartyHandler.leave(player.server, player.getUUID()))
                    player.sendSystemMessage(Component.literal("[BulletHell] You left your party to play solo."));
                startArena(player, pkt.difficulty, pkt.stageId, pkt.characterId, pkt.practice);
            });
        });


        // C2S: invite a player into the host's party. They have to accept.
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, INVITE_PLAYER, (buf, ctx) -> {
            InvitePlayerPacket pkt = InvitePlayerPacket.decode(buf);
            ctx.queue(() -> {
                ServerPlayer sender = (ServerPlayer) ctx.getPlayer();
                if (sender != null) PartyHandler.invite(sender, pkt.targetUuid);
            });
        });

        // C2S: retry arena - restarts from the server's own record of the run. A co-op run
        // belongs to everyone who played it, so PartyHandler collects a vote first.
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, RETRY_ARENA, (buf, ctx) -> {
            RetryArenaPacket pkt = RetryArenaPacket.decode(buf);
            ctx.queue(() -> {
                ServerPlayer player = (ServerPlayer) ctx.getPlayer();
                if (player == null) return;
                PartyHandler.retry(player, pkt.cancel);
            });
        });

        // C2S: test mode - select / reload boss or stage
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, TEST_SELECT, (buf, ctx) -> {
            TestSelectPacket pkt = TestSelectPacket.decode(buf);
            ctx.queue(() -> {
                ServerPlayer player = (ServerPlayer) ctx.getPlayer();
                if (player == null) return;
                ArenaContext current = BulletHellManager.INSTANCE.getArenaForPlayer(player.getUUID());
                if (current == null || !current.testMode) return;

                // Character refresh: restart arena with the new character
                if (pkt.testType == TestSelectPacket.TYPE_CHAR_REFRESH) {
                    String characterId = pkt.id.isBlank() ? current.characterId : pkt.id;
                    restartTestArena(player, current, pkt, characterId);
                    return;
                }

                // Character: use packet override if provided, else keep current
                String characterId = (pkt.characterId != null && !pkt.characterId.isBlank())
                        ? pkt.characterId : current.characterId;
                DifficultyConfig diff = DifficultyConfig.fromId(pkt.difficultyOrdinal);
                StageDefinition stage;
                String currentBossId  = current.boss != null ? current.boss.id : "";
                String currentStageId = "";
                int phaseIdx = 0;
                // Invalidate all caches so every edited JSON is picked up fresh
                BossLoader.invalidateAll();
                StageLoader.invalidateAll();
                CharacterLoader.invalidateAll();

                if (pkt.testType == TestSelectPacket.TYPE_STAGE) {
                    String stageId = pkt.id.isBlank() ? "cirno_stage" : pkt.id;
                    stage = StageLoader.load(stageId);
                    currentStageId = stageId;
                } else { // TYPE_BOSS
                    String bossId = pkt.id.isBlank()
                            ? (currentBossId.isBlank() ? "marisa_boss" : currentBossId) : pkt.id;
                    phaseIdx = Math.max(0, pkt.phaseIdx);
                    BossLoader.load(bossId);
                    stage = StageLoader.syntheticBossOnly(bossId);
                    currentBossId = bossId;
                }
                BulletHellManager.INSTANCE.stopArena(player.getUUID());
                int startPhase = (pkt.testType == TestSelectPacket.TYPE_BOSS) ? phaseIdx + 1 : 0;
                ArenaContext newCtx = BulletHellManager.INSTANCE.startArena(player, diff, stage, characterId, startPhase);
                newCtx.testMode = true;
                newCtx.player.power = PlayerState2D.MAX_POWER;
                BHDebugMode.setGodMode(player.getUUID(), true);
                sendFullSync(player, newCtx);
                sendToPlayer(player, new ArenaStatePacket(newCtx, player.getUUID(), 1));

                sendTestModeOpen(player, new TestModeOpenPacket(
                        BossLoader.allBossIds(), StageLoader.allStageIds(),
                        CharacterLoader.allCharIds(),
                        currentBossId, currentStageId, characterId,
                        phaseIdx, diff.ordinal()));
            });
        });

        // C2S: test mode - real-time control (power, godmode)
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, TEST_CONTROL, (buf, ctx) -> {
            TestControlPacket pkt = TestControlPacket.decode(buf);
            ctx.queue(() -> {
                ServerPlayer player = (ServerPlayer) ctx.getPlayer();
                if (player == null) return;
                ArenaContext current = BulletHellManager.INSTANCE.getArenaForPlayer(player.getUUID());
                if (current == null || !current.testMode) return;

                PlayerState2D ps = current.getPlayerState(player.getUUID());
                if (ps == null) return;

                if (pkt.type == TestControlPacket.TYPE_SET_POWER) {
                    ps.power = Math.max(0, Math.min(PlayerState2D.MAX_POWER, pkt.value));
                    sendToPlayer(player, new ArenaStatePacket(current, player.getUUID(), 1));
                } else if (pkt.type == TestControlPacket.TYPE_TOGGLE_GODMODE) {
                    mc.sayda.bullethell.debug.BHDebugMode.toggleGodMode(player.getUUID());
                    if (!mc.sayda.bullethell.debug.BHDebugMode.isGodMode(player.getUUID()))
                        ps.invulnTicks = 0;
                    sendToPlayer(player, new ArenaStatePacket(current, player.getUUID(), 1));
                }
            });
        });


        // C2S: broadcast last arena end stats (same chat format) to all online players
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, SHARE_LAST_RUN, (buf, ctx) -> {
            ShareLastRunPacket.decode(buf);
            ctx.queue(() -> {
                ServerPlayer sender = (ServerPlayer) ctx.getPlayer();
                if (sender != null) PartyHandler.shareLastRun(sender);
            });
        });
    }

    private static void restartTestArena(ServerPlayer player, ArenaContext current, TestSelectPacket pkt, String characterId) {
        DifficultyConfig diff = DifficultyConfig.fromId(pkt.difficultyOrdinal);
        String currentBossId  = current.boss != null ? current.boss.id : "";
        String currentStageId = "";
        int phaseIdx = 0;
        StageDefinition stage;

        // Invalidate all caches
        BossLoader.invalidateAll();
        StageLoader.invalidateAll();
        CharacterLoader.invalidateAll();

        if (pkt.testType == TestSelectPacket.TYPE_STAGE) {
            String stageId = pkt.id.isBlank() ? "cirno_stage" : pkt.id;
            stage = StageLoader.load(stageId);
            currentStageId = stageId;
        } else if (pkt.testType == TestSelectPacket.TYPE_BOSS || pkt.testType == TestSelectPacket.TYPE_CHAR_REFRESH) {
            String bossId = (pkt.testType == TestSelectPacket.TYPE_BOSS && !pkt.id.isBlank())
                    ? pkt.id : (currentBossId.isBlank() ? "marisa_boss" : currentBossId);
            phaseIdx = Math.max(0, pkt.phaseIdx);
            BossLoader.load(bossId);
            stage = StageLoader.syntheticBossOnly(bossId);
            currentBossId = bossId;
        } else {
            // Fallback for safety
            stage = StageLoader.syntheticBossOnly("marisa_boss");
        }
        
        BulletHellManager.INSTANCE.stopArena(player.getUUID());
        int startPhase = (pkt.testType == TestSelectPacket.TYPE_BOSS) ? phaseIdx + 1 : 0;
        ArenaContext newCtx = BulletHellManager.INSTANCE.startArena(player, diff, stage, characterId, startPhase);
        newCtx.testMode = true;
        newCtx.player.power = PlayerState2D.MAX_POWER;
        BHDebugMode.setGodMode(player.getUUID(), true);
        sendFullSync(player, newCtx);
        sendToPlayer(player, new ArenaStatePacket(newCtx, player.getUUID(), 1));


        sendTestModeOpen(player, new TestModeOpenPacket(
                BossLoader.allBossIds(), StageLoader.allStageIds(),
                CharacterLoader.allCharIds(),
                currentBossId, currentStageId, characterId,
                phaseIdx, diff.ordinal()));
    }

    // ---------------------------------------------------------------- Server → Client helpers

    private static FriendlyByteBuf buf() { return new FriendlyByteBuf(Unpooled.buffer()); }

    public static void sendSplash(ServerPlayer player, SplashPacket pkt) {
        FriendlyByteBuf b = buf(); pkt.encode(b); NetworkManager.sendToPlayer(player, SPLASH, b);
    }

    public static void sendLobbyState(ServerPlayer player, LobbyStatePacket pkt) {
        FriendlyByteBuf b = buf(); pkt.encode(b); NetworkManager.sendToPlayer(player, LOBBY_STATE, b);
    }

    /** Push the roster to every member, so nobody sees a stale party. */
    public static void broadcastLobby(MinecraftServer server, LobbySession lobby) {
        if (server == null || lobby == null) return;
        LobbyStatePacket pkt = LobbyStatePacket.of(lobby);
        for (UUID member : lobby.memberIds()) {
            ServerPlayer p = server.getPlayerList().getPlayer(member);
            if (p != null) sendLobbyState(p, pkt);
        }
    }

    /** Push all content json to one player. */
    public static void sendDataSync(ServerPlayer player) {
        FriendlyByteBuf b = buf();
        DataSyncPacket.fromCurrent().encode(b);
        NetworkManager.sendToPlayer(player, DATA_SYNC, b);
    }

    /** Push all content json to everyone online (called after a datapack reload). */
    public static void sendDataSyncToAll(net.minecraft.server.MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) sendDataSync(p);
    }

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
        // Send current laser state so joining players see active lasers immediately
        sendLaserSync(player, new LaserSyncPacket(ctx.lasers));
    }

    public static void sendAllPlayerBullets(ServerPlayer player, AllPlayerBulletsSyncPacket pkt) {
        FriendlyByteBuf b = buf(); pkt.encode(b); NetworkManager.sendToPlayer(player, ALL_PLAYER_BULLETS, b);
    }

    public static void sendItemSync(ServerPlayer player, ItemSyncPacket pkt) {
        FriendlyByteBuf b = buf(); pkt.encode(b); NetworkManager.sendToPlayer(player, ITEM_SYNC, b);
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

    public static void sendAttackActivationSfx(ServerPlayer player, AttackActivationSfxPacket pkt) {
        FriendlyByteBuf b = buf();
        pkt.encode(b);
        NetworkManager.sendToPlayer(player, ATTACK_ACTIVATION_SFX, b);
    }


    public static void sendOpenChallenge(ServerPlayer player, OpenChallengePacket pkt) {
        sendCharacterUnlocks(player, new CharacterUnlockSyncPacket(CharacterUnlocks.snapshot(player)));
        FriendlyByteBuf b = buf(); pkt.encode(b); NetworkManager.sendToPlayer(player, OPEN_CHALLENGE, b);
    }

    public static void sendCharacterUnlocks(ServerPlayer player, CharacterUnlockSyncPacket pkt) {
        FriendlyByteBuf b = buf();
        pkt.encode(b);
        NetworkManager.sendToPlayer(player, CHARACTER_UNLOCKS, b);
    }

    public static void sendArenaEnd(ServerPlayer player, ArenaEndPacket pkt) {
        FriendlyByteBuf b = buf();
        pkt.encode(b);
        NetworkManager.sendToPlayer(player, ARENA_END, b);
    }

    public static void sendRetryVote(ServerPlayer player, RetryVotePacket pkt) {
        FriendlyByteBuf b = buf();
        pkt.encode(b);
        NetworkManager.sendToPlayer(player, RETRY_VOTE, b);
    }

    public static void sendArenaStart(ServerPlayer player, ArenaStartPacket pkt) {
        FriendlyByteBuf b = buf();
        pkt.encode(b);
        NetworkManager.sendToPlayer(player, ARENA_START, b);
    }

    public static void sendPhaseTransition(ServerPlayer player, PhaseTransitionPacket pkt) {
        FriendlyByteBuf b = buf();
        pkt.encode(b);
        NetworkManager.sendToPlayer(player, PHASE_TRANSITION, b);
    }

    // ---------------------------------------------------------------- Client → Server helpers

    @Environment(EnvType.CLIENT)
    public static void sendArenaProgress(ArenaProgressPacket pkt) {
        FriendlyByteBuf b = buf();
        pkt.encode(b);
        NetworkManager.sendToServer(ARENA_PROGRESS, b);
    }

    @Environment(EnvType.CLIENT)
    public static void sendPlayerPos(float dx, float dy, boolean focused, boolean shooting) {
        FriendlyByteBuf b = buf();
        new PlayerPos2DPacket(dx, dy, focused, shooting).encode(b);
        NetworkManager.sendToServer(PLAYER_POS, b);
    }

    @Environment(EnvType.CLIENT)
    public static void sendBomb() {
        NetworkManager.sendToServer(BOMB, buf());
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
    public static void sendPauseState(boolean paused) {
        FriendlyByteBuf b = buf();
        new PauseStatePacket(paused).encode(b);
        NetworkManager.sendToServer(PAUSE_STATE, b);
    }

    @Environment(EnvType.CLIENT)
    public static void sendLobbyAction(LobbyActionPacket pkt) {
        FriendlyByteBuf b = buf(); pkt.encode(b); NetworkManager.sendToServer(LOBBY_ACTION, b);
    }

    /** C2S: ask the server for content data (see REQUEST_DATA_SYNC). */
    @Environment(EnvType.CLIENT)
    public static void sendRequestDataSync() {
        NetworkManager.sendToServer(REQUEST_DATA_SYNC, buf());
    }

    @Environment(EnvType.CLIENT)
    public static void sendCharSelect(String characterId, DifficultyConfig difficulty, String stageId) {
        sendCharSelect(characterId, difficulty, stageId, false);
    }

    @Environment(EnvType.CLIENT)
    public static void sendCharSelect(String characterId, DifficultyConfig difficulty, String stageId,
            boolean practice) {
        FriendlyByteBuf b = buf();
        new CharacterSelectPacket(characterId, difficulty, stageId, practice).encode(b);
        NetworkManager.sendToServer(CHAR_SELECT, b);
    }


    @Environment(EnvType.CLIENT)
    public static void sendInvitePlayer(UUID targetUuid) {
        FriendlyByteBuf b = buf();
        new InvitePlayerPacket(targetUuid).encode(b);
        NetworkManager.sendToServer(INVITE_PLAYER, b);
    }

    @Environment(EnvType.CLIENT)
    public static void sendRetryArena(RetryArenaPacket pkt) {
        FriendlyByteBuf b = buf();
        pkt.encode(b);
        NetworkManager.sendToServer(RETRY_ARENA, b);
    }

    @Environment(EnvType.CLIENT)
    public static void sendShareLastRun() {
        FriendlyByteBuf b = buf();
        ShareLastRunPacket.INSTANCE.encode(b);
        NetworkManager.sendToServer(SHARE_LAST_RUN, b);
    }

    // ---------------------------------------------------------------- Server-side helpers (used by BHCommonEvents)

    /** Start an arena for a player, send initial full sync + state. */
    public static void startArena(ServerPlayer player, DifficultyConfig diff,
                                   String stageId, String characterId) {
        startArena(player, diff, stageId, characterId, false);
    }

    public static void startArena(ServerPlayer player, DifficultyConfig diff,
            String stageId, String characterId, boolean practice) {
        BulletHellManager.INSTANCE.stopArena(player.getUUID());
        ArenaContext ctx = BulletHellManager.INSTANCE.startArena(
                player, diff, stageId, characterId);
        ctx.practiceMode = practice;
        if (practice) {
            ctx.debugSkipToBossPhase(0);
            ctx.player.power = mc.sayda.bullethell.arena.PlayerState2D.MAX_POWER;
            ctx.player.reachedMaxPowerInThisLife = true;
        }

        sendFullSync(player, ctx);
        sendToPlayer(player, new ArenaStatePacket(ctx, player.getUUID(), 1));

    }

    /**
     * Same as {@link #startArena(ServerPlayer, DifficultyConfig, String, String)} but with a
     * resolved {@link StageDefinition} and optional 1-based boss phase skip (0 = play from the start).
     */
    public static void startArena(ServerPlayer player, DifficultyConfig diff,
            StageDefinition stage, String characterId, int bossPhase1Based) {
        BulletHellManager.INSTANCE.stopArena(player.getUUID());
        ArenaContext ctx = BulletHellManager.INSTANCE.startArena(player, diff, stage, characterId, bossPhase1Based);
        sendFullSync(player, ctx);
        sendToPlayer(player, new ArenaStatePacket(ctx, player.getUUID(), 1));

    }

    public static void startArena(ServerPlayer player, DifficultyConfig diff) {
        startArena(player, diff, "marisa_stage", "reimu");
    }

    public static void sendTestModeOpen(ServerPlayer player, TestModeOpenPacket pkt) {
        FriendlyByteBuf b = buf(); pkt.encode(b); NetworkManager.sendToPlayer(player, TEST_MODE_OPEN, b);
    }

    @Environment(EnvType.CLIENT)
    public static void sendTestSelect(TestSelectPacket pkt) {
        FriendlyByteBuf b = buf(); pkt.encode(b); NetworkManager.sendToServer(TEST_SELECT, b);
    }

    @Environment(EnvType.CLIENT)
    public static void sendTestControl(TestControlPacket pkt) {
        FriendlyByteBuf b = buf(); pkt.encode(b); NetworkManager.sendToServer(TEST_CONTROL, b);
    }
}
