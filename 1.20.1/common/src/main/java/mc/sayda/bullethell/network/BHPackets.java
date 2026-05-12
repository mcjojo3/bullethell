package mc.sayda.bullethell.network;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import mc.sayda.bullethell.BHControlSettings;
import mc.sayda.bullethell.BossProgression;
import mc.sayda.bullethell.CharacterUnlocks;
import mc.sayda.bullethell.Bullethell;
import mc.sayda.bullethell.arena.ArenaContext;
import mc.sayda.bullethell.arena.ArenaEndShareSnapshot;
import mc.sayda.bullethell.arena.BulletHellManager;
import mc.sayda.bullethell.arena.LastArenaShareState;
import mc.sayda.bullethell.arena.DifficultyConfig;
import mc.sayda.bullethell.arena.PlayerState2D;
import mc.sayda.bullethell.boss.BossLoader;
import mc.sayda.bullethell.boss.CharacterDefinition;
import mc.sayda.bullethell.boss.CharacterLoader;
import mc.sayda.bullethell.boss.FairyWaveDefinition;
import mc.sayda.bullethell.boss.FairyWaveLoader;
import mc.sayda.bullethell.boss.StageDefinition;
import mc.sayda.bullethell.boss.StageLoader;
import mc.sayda.bullethell.debug.BHDebugMode;
import mc.sayda.bullethell.pattern.BulletType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
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
    public static final ResourceLocation ATTACK_ACTIVATION_SFX = id("attack_activation_sfx");
    public static final ResourceLocation OPEN_CHAR_SELECT  = id("open_char_select");
    public static final ResourceLocation OPEN_JOIN_SELECT  = id("open_join_select");
    public static final ResourceLocation OPEN_CHALLENGE    = id("open_challenge");
    public static final ResourceLocation CONTROL_SCHEME    = id("control_scheme");
    public static final ResourceLocation CHARACTER_UNLOCKS = id("character_unlocks");
    /** S → C | arena ended; carry stats + boss quote for the end overlay. */
    public static final ResourceLocation ARENA_END         = id("arena_end");

    // C → S
    public static final ResourceLocation PLAYER_POS        = id("player_pos");
    public static final ResourceLocation BOMB              = id("bomb");
    public static final ResourceLocation SKILL             = id("skill");
    public static final ResourceLocation SKIP_DIALOG       = id("skip_dialog");
    public static final ResourceLocation QUIT_ARENA        = id("quit_arena");
    public static final ResourceLocation PAUSE_STATE       = id("pause_state");
    public static final ResourceLocation CHAR_SELECT       = id("char_select");
    public static final ResourceLocation JOIN_MATCH        = id("join_match");
    public static final ResourceLocation INVITE_PLAYER     = id("invite_player");
    public static final ResourceLocation SHARE_LAST_RUN    = id("share_last_run");
    /** C → S | player requests a retry of the last arena. */
    public static final ResourceLocation RETRY_ARENA       = id("retry_arena");
    /** S → C | opens test-mode overlay with boss list and current selection. */
    public static final ResourceLocation TEST_MODE_OPEN    = id("test_mode_open");
    /** C → S | select / reload a boss in test mode. */
    public static final ResourceLocation TEST_SELECT       = id("test_select");
    public static final ResourceLocation TEST_CONTROL      = id("test_control");

    private static ResourceLocation id(String path) {
        return new ResourceLocation(Bullethell.MODID, path);
    }

    // ---------------------------------------------------------------- Registration

    public static void register() {
        // C2S: player input every tick — queued to arena thread via pendingInputs
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
                    ps.focused = pkt.focused; ps.shooting = pkt.shooting; ps.isCharging = pkt.charging;
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
                if (arena != null) arena.pendingInputs.offer(() -> arena.activateBomb(id));
            });
        });

        // C2S: skill (X release)
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, SKILL, (buf, ctx) -> {
            ctx.queue(() -> {
                ServerPlayer sender = (ServerPlayer) ctx.getPlayer();
                UUID id = sender.getUUID();
                ArenaContext arena = BulletHellManager.INSTANCE.getArenaForPlayer(id);
                if (arena != null) arena.pendingInputs.offer(() -> arena.activateSkill(id));
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
                    // forceGameOver() sets a volatile boolean — safe to call from any thread
                    arena.pendingInputs.offer(() -> arena.setParticipantPaused(uuid, false));
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
                ArenaContext arena = BulletHellManager.INSTANCE.getArenaForPlayer(id);
                if (arena != null)
                    arena.pendingInputs.offer(() -> arena.setParticipantPaused(id, pkt.paused));
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
                startArena(player, pkt.difficulty, pkt.stageId, pkt.characterId, pkt.shotTypeOrdinal, pkt.practice);
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
                boolean debugBypass = BHDebugMode.isGodMode(sender.getUUID());
                if (!debugBypass && !CharacterUnlocks.isUnlockedAny(sender, pkt.characterId)) {
                    sender.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "[BulletHell] Character '" + pkt.characterId + "' is locked."));
                    return;
                }

                CharacterDefinition charDef = CharacterLoader.load(pkt.characterId);
                ArenaContext arena = BulletHellManager.INSTANCE.getArenaForPlayer(pkt.hostUuid);

                if (arena != null) {
                    // Join active match immediately
                    BulletHellManager.INSTANCE.joinMatch(sender.getUUID(), pkt.hostUuid, charDef, sender,
                            pkt.shotTypeOrdinal);
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
                            new BulletHellManager.ParticipantInfo(sender.getUUID(), charDef, pkt.shotTypeOrdinal));
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

        // C2S: retry arena - restart with same stage/difficulty/character
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, RETRY_ARENA, (buf, ctx) -> {
            RetryArenaPacket pkt = RetryArenaPacket.decode(buf);
            ctx.queue(() -> {
                ServerPlayer player = (ServerPlayer) ctx.getPlayer();
                if (player == null) return;
                if (BulletHellManager.INSTANCE.hasArena(player.getUUID())) return;
                DifficultyConfig diff;
                try { diff = DifficultyConfig.valueOf(pkt.difficulty); }
                catch (Exception e) { diff = DifficultyConfig.NORMAL; }
                if (!pkt.stageId.isBlank() && !pkt.characterId.isBlank()) {
                    startArena(player, diff, pkt.stageId, pkt.characterId, pkt.shotTypeOrdinal);
                }
            });
        });

        // C2S: test mode - select / reload boss, stage, or wave
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, TEST_SELECT, (buf, ctx) -> {
            TestSelectPacket pkt = TestSelectPacket.decode(buf);
            ctx.queue(() -> {
                ServerPlayer player = (ServerPlayer) ctx.getPlayer();
                if (player == null) return;
                ArenaContext current = BulletHellManager.INSTANCE.getArenaForPlayer(player.getUUID());
                if (current == null || !current.testMode) return;

                // Character refresh: restart arena with new character AND send updated shot list
                if (pkt.testType == TestSelectPacket.TYPE_CHAR_REFRESH) {
                    String characterId = pkt.id.isBlank() ? current.characterId : pkt.id;
                    restartTestArena(player, current, pkt, characterId, pkt.shotTypeOrdinal);
                    return;
                }

                // Character: use packet override if provided, else keep current
                String characterId = (pkt.characterId != null && !pkt.characterId.isBlank())
                        ? pkt.characterId : current.characterId;
                DifficultyConfig diff = DifficultyConfig.fromId(pkt.difficultyOrdinal);
                StageDefinition stage;
                String currentBossId  = current.boss != null ? current.boss.id : "";
                String currentStageId = "";
                String currentWaveId  = "";
                int phaseIdx = 0;
                // Invalidate all caches so every edited JSON is picked up fresh
                BossLoader.invalidateAll();
                StageLoader.invalidateAll();
                FairyWaveLoader.invalidateAll();
                CharacterLoader.invalidateAll();

                if (pkt.testType == TestSelectPacket.TYPE_STAGE) {
                    String stageId = pkt.id.isBlank() ? "cirno_stage" : pkt.id;
                    stage = StageLoader.loadWithDevPath(stageId);
                    currentStageId = stageId;
                } else if (pkt.testType == TestSelectPacket.TYPE_WAVE) {
                    String waveId = pkt.id.isBlank() ? "fw_shared_001" : pkt.id;
                    FairyWaveDefinition waveDef = FairyWaveLoader.loadWithDevPath(waveId);
                    stage = StageLoader.syntheticWaveOnly(waveId, waveDef != null ? waveDef.enemies : null);
                    currentWaveId = waveId;
                } else { // TYPE_BOSS
                    String bossId = pkt.id.isBlank()
                            ? (currentBossId.isBlank() ? "marisa_boss" : currentBossId) : pkt.id;
                    phaseIdx = Math.max(0, pkt.phaseIdx);
                    BossLoader.loadWithDevPath(bossId);
                    stage = StageLoader.syntheticBossOnly(bossId);
                    currentBossId = bossId;
                }
                BulletHellManager.INSTANCE.stopArena(player.getUUID());
                int startPhase = (pkt.testType == TestSelectPacket.TYPE_BOSS) ? phaseIdx + 1 : 0;
                ArenaContext newCtx = BulletHellManager.INSTANCE.startArena(player, diff, stage, characterId, startPhase);
                newCtx.hostShotTypeOrdinal = pkt.shotTypeOrdinal;
                newCtx.testMode = true;
                newCtx.player.power = PlayerState2D.MAX_POWER;
                BHDebugMode.setGodMode(player.getUUID(), true);
                sendFullSync(player, newCtx);
                sendToPlayer(player, new ArenaStatePacket(newCtx, player.getUUID(), 1));
                
                // Build shot type list for new character
                CharacterDefinition charDef = CharacterLoader.loadWithDevPath(characterId);
                java.util.List<String> shotTypeLabels = new java.util.ArrayList<>();
                if (charDef != null) {
                    for (int i = 0; i < charDef.shotOptions.size(); i++) {
                        shotTypeLabels.add(charDef.shotOptions.get(i).label);
                    }
                }
                
                sendTestModeOpen(player, new TestModeOpenPacket(
                        BossLoader.allBossIds(), StageLoader.allStageIds(),
                        FairyWaveLoader.allWaveIds(), CharacterLoader.allCharIds(),
                        shotTypeLabels,
                        currentBossId, currentStageId, currentWaveId, characterId, pkt.shotTypeOrdinal,
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
                if (sender == null)
                    return;
                ArenaEndShareSnapshot snap = LastArenaShareState.get(sender.getUUID());
                if (snap == null) {
                    sender.sendSystemMessage(Component.literal("[BulletHell] No finished run to share yet."));
                    return;
                }
                String who = sender.getGameProfile().getName();
                for (ServerPlayer target : sender.server.getPlayerList().getPlayers()) {
                    target.sendSystemMessage(Component.literal("[BulletHell] " + who + " shared a run:"));
                    for (var line : snap.buildLines())
                        target.sendSystemMessage(line);
                }
            });
        });
    }

    private static void restartTestArena(ServerPlayer player, ArenaContext current, TestSelectPacket pkt, String characterId, int shotIdx) {
        DifficultyConfig diff = DifficultyConfig.fromId(pkt.difficultyOrdinal);
        String currentBossId  = current.boss != null ? current.boss.id : "";
        String currentStageId = "";
        String currentWaveId  = "";
        int phaseIdx = 0;
        StageDefinition stage;

        // Invalidate all caches
        BossLoader.invalidateAll();
        StageLoader.invalidateAll();
        FairyWaveLoader.invalidateAll();
        CharacterLoader.invalidateAll();

        if (pkt.testType == TestSelectPacket.TYPE_STAGE) {
            String stageId = pkt.id.isBlank() ? "cirno_stage" : pkt.id;
            stage = StageLoader.loadWithDevPath(stageId);
            currentStageId = stageId;
        } else if (pkt.testType == TestSelectPacket.TYPE_WAVE) {
            String waveId = pkt.id.isBlank() ? "fw_shared_001" : pkt.id;
            FairyWaveDefinition waveDef = FairyWaveLoader.loadWithDevPath(waveId);
            stage = StageLoader.syntheticWaveOnly(waveId, waveDef != null ? waveDef.enemies : null);
            currentWaveId = waveId;
        } else if (pkt.testType == TestSelectPacket.TYPE_BOSS || pkt.testType == TestSelectPacket.TYPE_CHAR_REFRESH) {
            String bossId = (pkt.testType == TestSelectPacket.TYPE_BOSS && !pkt.id.isBlank())
                    ? pkt.id : (currentBossId.isBlank() ? "marisa_boss" : currentBossId);
            phaseIdx = Math.max(0, pkt.phaseIdx);
            BossLoader.loadWithDevPath(bossId);
            stage = StageLoader.syntheticBossOnly(bossId);
            currentBossId = bossId;
        } else {
            // Fallback for safety
            stage = StageLoader.syntheticBossOnly("marisa_boss");
        }
        
        BulletHellManager.INSTANCE.stopArena(player.getUUID());
        int startPhase = (pkt.testType == TestSelectPacket.TYPE_BOSS) ? phaseIdx + 1 : 0;
        ArenaContext newCtx = BulletHellManager.INSTANCE.startArena(player, diff, stage, characterId, startPhase);
        newCtx.hostShotTypeOrdinal = shotIdx;
        newCtx.testMode = true;
        newCtx.player.power = PlayerState2D.MAX_POWER;
        BHDebugMode.setGodMode(player.getUUID(), true);
        sendFullSync(player, newCtx);
        sendToPlayer(player, new ArenaStatePacket(newCtx, player.getUUID(), 1));
        
        // Build shot type list
        CharacterDefinition charDef = CharacterLoader.loadWithDevPath(characterId);
        java.util.List<String> shotTypeLabels = new java.util.ArrayList<>();
        if (charDef != null) {
            for (int i = 0; i < charDef.shotOptions.size(); i++) {
                shotTypeLabels.add(charDef.shotOptions.get(i).label);
            }
        }
        
        sendTestModeOpen(player, new TestModeOpenPacket(
                BossLoader.allBossIds(), StageLoader.allStageIds(),
                FairyWaveLoader.allWaveIds(), CharacterLoader.allCharIds(),
                shotTypeLabels,
                currentBossId, currentStageId, currentWaveId, characterId, shotIdx,
                phaseIdx, diff.ordinal()));
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
        // Send current laser state so joining players see active lasers immediately
        sendLaserSync(player, new LaserSyncPacket(ctx.lasers));
        sendControlScheme(player, new ControlSchemePacket(BHControlSettings.serverGetPreference(player)));
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

    public static void sendAttackActivationSfx(ServerPlayer player, AttackActivationSfxPacket pkt) {
        FriendlyByteBuf b = buf();
        pkt.encode(b);
        NetworkManager.sendToPlayer(player, ATTACK_ACTIVATION_SFX, b);
    }

    public static void sendOpenCharSelect(ServerPlayer player) {
        sendCharacterUnlocks(player, new CharacterUnlockSyncPacket(CharacterUnlocks.snapshot(player)));
        NetworkManager.sendToPlayer(player, OPEN_CHAR_SELECT, buf());
    }

    public static void sendOpenJoinSelect(ServerPlayer player, OpenJoinSelectPacket pkt) {
        sendCharacterUnlocks(player, new CharacterUnlockSyncPacket(CharacterUnlocks.snapshot(player)));
        FriendlyByteBuf b = buf(); pkt.encode(b); NetworkManager.sendToPlayer(player, OPEN_JOIN_SELECT, b);
    }

    public static void sendOpenChallenge(ServerPlayer player, OpenChallengePacket pkt) {
        sendCharacterUnlocks(player, new CharacterUnlockSyncPacket(CharacterUnlocks.snapshot(player)));
        FriendlyByteBuf b = buf(); pkt.encode(b); NetworkManager.sendToPlayer(player, OPEN_CHALLENGE, b);
    }

    public static void sendControlScheme(ServerPlayer player, ControlSchemePacket pkt) {
        FriendlyByteBuf b = buf();
        pkt.encode(b);
        NetworkManager.sendToPlayer(player, CONTROL_SCHEME, b);
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
    public static void sendPauseState(boolean paused) {
        FriendlyByteBuf b = buf();
        new PauseStatePacket(paused).encode(b);
        NetworkManager.sendToServer(PAUSE_STATE, b);
    }

    @Environment(EnvType.CLIENT)
    public static void sendCharSelect(String characterId, DifficultyConfig difficulty, String stageId,
            int shotTypeOrdinal) {
        sendCharSelect(characterId, difficulty, stageId, shotTypeOrdinal, false);
    }

    @Environment(EnvType.CLIENT)
    public static void sendCharSelect(String characterId, DifficultyConfig difficulty, String stageId,
            int shotTypeOrdinal, boolean practice) {
        FriendlyByteBuf b = buf();
        new CharacterSelectPacket(characterId, difficulty, stageId, shotTypeOrdinal, practice).encode(b);
        NetworkManager.sendToServer(CHAR_SELECT, b);
    }

    @Environment(EnvType.CLIENT)
    public static void sendJoinMatch(UUID hostUuid, String characterId, int shotTypeOrdinal) {
        FriendlyByteBuf b = buf();
        new JoinMatchPacket(hostUuid, characterId, shotTypeOrdinal).encode(b);
        NetworkManager.sendToServer(JOIN_MATCH, b);
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
        startArena(player, diff, stageId, characterId, 0);
    }

    public static void startArena(ServerPlayer player, DifficultyConfig diff,
            String stageId, String characterId, int hostShotTypeOrdinal) {
        startArena(player, diff, stageId, characterId, hostShotTypeOrdinal, false);
    }

    public static void startArena(ServerPlayer player, DifficultyConfig diff,
            String stageId, String characterId, int hostShotTypeOrdinal, boolean practice) {
        BulletHellManager.INSTANCE.stopArena(player.getUUID());
        ArenaContext ctx = BulletHellManager.INSTANCE.startArena(
                player, diff, stageId, characterId);
        ctx.hostShotTypeOrdinal = Math.max(0, hostShotTypeOrdinal);
        ctx.practiceMode = practice;
        if (practice) {
            ctx.debugSkipToBossPhase(0);
            ctx.player.power = mc.sayda.bullethell.arena.PlayerState2D.MAX_POWER;
            ctx.player.reachedMaxPowerInThisLife = true;
        }

        // Apply forced control scheme if the stage rules dictate it
        if (ctx.rules.forceControlScheme != null && !ctx.rules.forceControlScheme.isEmpty()) {
            var forced = mc.sayda.bullethell.BHControlScheme.tryParse(ctx.rules.forceControlScheme);
            forced.ifPresent(scheme -> sendControlScheme(player, new ControlSchemePacket(scheme)));
        }

        sendFullSync(player, ctx);
        sendToPlayer(player, new ArenaStatePacket(ctx, player.getUUID(), 1));

        // Auto-join pending participants
        java.util.List<BulletHellManager.ParticipantInfo> pending = BulletHellManager.INSTANCE
                .getAndClearPendingInvites(player.getUUID());
        if (pending != null) {
            for (BulletHellManager.ParticipantInfo info : pending) {
                ServerPlayer p = player.server.getPlayerList().getPlayer(info.uuid());
                if (p != null) {
                    BulletHellManager.INSTANCE.joinMatch(p.getUUID(), player.getUUID(), info.charDef(), p,
                            info.shotTypeOrdinal());
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

    /**
     * Same as {@link #startArena(ServerPlayer, DifficultyConfig, String, String)} but with a
     * resolved {@link StageDefinition} and optional 1-based boss phase skip (0 = play from waves).
     */
    public static void startArena(ServerPlayer player, DifficultyConfig diff,
            StageDefinition stage, String characterId, int bossPhase1Based) {
        startArena(player, diff, stage, characterId, bossPhase1Based, 0);
    }

    public static void startArena(ServerPlayer player, DifficultyConfig diff,
            StageDefinition stage, String characterId, int bossPhase1Based, int hostShotTypeOrdinal) {
        BulletHellManager.INSTANCE.stopArena(player.getUUID());
        ArenaContext ctx = BulletHellManager.INSTANCE.startArena(player, diff, stage, characterId, bossPhase1Based);
        ctx.hostShotTypeOrdinal = Math.max(0, hostShotTypeOrdinal);
        sendFullSync(player, ctx);
        sendToPlayer(player, new ArenaStatePacket(ctx, player.getUUID(), 1));

        java.util.List<BulletHellManager.ParticipantInfo> pending = BulletHellManager.INSTANCE
                .getAndClearPendingInvites(player.getUUID());
        if (pending != null) {
            for (BulletHellManager.ParticipantInfo info : pending) {
                ServerPlayer p = player.server.getPlayerList().getPlayer(info.uuid());
                if (p != null) {
                    BulletHellManager.INSTANCE.joinMatch(p.getUUID(), player.getUUID(), info.charDef(), p,
                            info.shotTypeOrdinal());
                    sendFullSync(p, ctx);
                    int pIdx = 0;
                    int c = 2;
                    for (UUID cid : ctx.getCoopPlayers().keySet()) {
                        if (cid.equals(p.getUUID())) {
                            pIdx = c;
                            break;
                        }
                        c++;
                    }
                    sendToPlayer(p, new ArenaStatePacket(ctx, p.getUUID(), pIdx));
                }
            }
        }
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
