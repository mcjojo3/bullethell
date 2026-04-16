package mc.sayda.bullethell.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import mc.sayda.bullethell.BHControlScheme;
import mc.sayda.bullethell.BHControlSettings;
import mc.sayda.bullethell.BullethellDataIndex;
import mc.sayda.bullethell.arena.BulletHellManager;
import mc.sayda.bullethell.arena.DifficultyConfig;
import mc.sayda.bullethell.boss.BossLoader;
import mc.sayda.bullethell.boss.StageDefinition;
import mc.sayda.bullethell.boss.StageLoader;
import mc.sayda.bullethell.debug.BHDebugMode;
import mc.sayda.bullethell.network.BHPackets;
import mc.sayda.bullethell.network.ControlSchemePacket;
import mc.sayda.bullethell.network.OpenJoinSelectPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * /bullethell start — admin (perm 2): same as bare /bullethell (char select), or
 * {@code /bullethell start &lt;stageOrBossId&gt;} [phase] [character]. Tab-complete lists
 * stage and boss JSON ids (e.g. {@code sakuya_boss}, {@code remilia_stage}). A boss id
 * starts a boss-only arena (no fairy waves). Phase: 0 = from beginning; ≥1 = skip to that
 * boss phase (1-based).
 * /bullethell join &lt;playerName&gt; - join another player's active arena
 * /bullethell stop - end own arena or leave a coop match
 * /bullethell status - print current arena stats to chat
 * /bullethell controls [&lt;th19|th9&gt;] - show saved layout, or set when a scheme is given
 * /bullethell debug - operator (perm 2+): toggle god mode for testing
 */
public final class BulletHellCommands {

    private BulletHellCommands() {
    }

    private static CompletableFuture<Suggestions> suggestJoinHosts(CommandContext<CommandSourceStack> ctx,
            SuggestionsBuilder builder) {
        var server = ctx.getSource().getServer();
        if (server == null)
            return Suggestions.empty();

        UUID self = null;
        if (ctx.getSource().getEntity() instanceof ServerPlayer sp)
            self = sp.getUUID();

        List<String> names = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (self != null && p.getUUID().equals(self))
                continue;
            if (BulletHellManager.INSTANCE.hasArena(p.getUUID()))
                names.add(p.getGameProfile().getName());
        }
        return SharedSuggestionProvider.suggest(names, builder);
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bullethell")
                .requires(src -> src.hasPermission(0)) // any player
                .executes(ctx -> start(ctx.getSource()))

                // ---- start (admin: char-select shortcut or direct arena start) ----
                .then(Commands.literal("start")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> start(ctx.getSource()))
                        .then(Commands.argument("target", StringArgumentType.word())
                                .suggests(BullethellDataIndex.suggestStartArenaTargets())
                                .executes(ctx -> startDirect(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "target"), 0, "reimu"))
                                .then(Commands.argument("phase", IntegerArgumentType.integer(0, 999))
                                        .executes(ctx -> startDirect(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "target"),
                                                IntegerArgumentType.getInteger(ctx, "phase"), "reimu"))
                                        .then(Commands.argument("character", StringArgumentType.word())
                                                .suggests(BullethellDataIndex.suggestCharacters())
                                                .executes(ctx -> startDirect(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "target"),
                                                        IntegerArgumentType.getInteger(ctx, "phase"),
                                                        StringArgumentType.getString(ctx, "character")))))))

                // ---- join <playerName> ----
                .then(Commands.literal("join")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(BulletHellCommands::suggestJoinHosts)
                                .executes(ctx -> join(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "player")))))

                // ---- stop / leave ----
                .then(Commands.literal("stop")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            UUID uuid = player.getUUID();
                            if (BulletHellManager.INSTANCE.hasArena(uuid)) {
                                mc.sayda.bullethell.arena.ArenaContext arena = BulletHellManager.INSTANCE
                                        .getArenaForPlayer(uuid);
                                if (arena != null) {
                                    arena.forceGameOver();
                                    player.sendSystemMessage(Component.literal("[BulletHell] Arena stopped."));
                                }
                            } else if (BulletHellManager.INSTANCE.isInMatch(uuid)) {
                                BulletHellManager.INSTANCE.leaveMatch(uuid);
                                player.sendSystemMessage(Component.literal("[BulletHell] Left the match."));
                            } else {
                                player.sendSystemMessage(Component.literal("[BulletHell] Not in an arena."));
                            }
                            return 1;
                        }))

                // ---- status ----
                .then(Commands.literal("status")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            UUID uuid = player.getUUID();
                            var arena = BulletHellManager.INSTANCE.getArenaForPlayer(uuid);
                            if (arena != null) {
                                mc.sayda.bullethell.arena.PlayerState2D ps = arena.getPlayerState(uuid);
                                if (ps == null)
                                    ps = arena.player;
                                StringBuilder msg = new StringBuilder(
                                        "[BulletHell] Active | bullets=" + arena.bullets.getActiveCount()
                                                + " | phase=" + (arena.bossPhase + 1)
                                                + " | lives=" + ps.lives
                                                + " | bombs=" + ps.bombs);
                                if (BHDebugMode.isGodMode(uuid)) {
                                    msg.append(" | DBG tick=").append(arena.getDebugArenaTick())
                                            .append(" patCD=").append(arena.getDebugBossPatternCooldown())
                                            .append(" enemyBul=").append(arena.bullets.getActiveCount());
                                }
                                player.sendSystemMessage(Component.literal(msg.toString()));
                            } else {
                                player.sendSystemMessage(Component.literal("[BulletHell] Not in an arena."));
                            }
                            return 1;
                        }))

                // ---- controls: bare = show saved layout; <scheme> = set + S2C apply ----
                .then(Commands.literal("controls")
                        .executes(ctx -> controlsGet(ctx.getSource()))
                        .then(Commands.argument("scheme", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(BHControlScheme.allIds(), b))
                                .executes(ctx -> controlsSet(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "scheme")))))

                // ---- debug (operator) ----
                .then(Commands.literal("debug")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            boolean on = BHDebugMode.toggleGodMode(player.getUUID());
                            player.sendSystemMessage(Component.literal(
                                    "[BulletHell] Debug " + (on ? "ON" : "OFF")
                                            + " - while in arena: max lives/bombs, invuln, bombs cost nothing."));
                            return 1;
                        })));
    }

    // ---------------------------------------------------------------- helpers

    private static int start(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        BHPackets.sendOpenCharSelect(player);
        return 1;
    }

    /**
     * @param phase1BasedOrZero 0 = play from wave phase; ≥1 = skip to that boss phase (1-based index)
     */
    private static int startDirect(CommandSourceStack src, String target, int phase1BasedOrZero, String characterId)
            throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        if (BulletHellManager.INSTANCE.hasArena(player.getUUID())) {
            player.sendSystemMessage(Component.literal(
                    "[BulletHell] Already in an arena. Use /bullethell stop first."));
            return 0;
        }
        StageDefinition stage;
        if (StageLoader.resourceExists(target)) {
            stage = StageLoader.load(target);
        } else if (BossLoader.resourceExists(target)) {
            stage = StageLoader.syntheticBossOnly(target);
        } else {
            player.sendSystemMessage(Component.literal(
                    "[BulletHell] No stage or boss JSON named \"" + target
                            + "\" (tab-complete: stage and boss ids under data/bullethell)."));
            return 0;
        }
        return beginResolvedArena(player, stage, characterId, phase1BasedOrZero);
    }

    private static int beginResolvedArena(ServerPlayer player, StageDefinition stage, String characterId,
            int phase1BasedOrZero) {
        if (!BullethellDataIndex.characterIdsSorted().contains(characterId)) {
            player.sendSystemMessage(Component.literal(
                    "[BulletHell] Unknown character \"" + characterId + "\"."));
            return 0;
        }
        BHPackets.startArena(player, DifficultyConfig.NORMAL, stage, characterId, phase1BasedOrZero);
        StringBuilder msg = new StringBuilder("[BulletHell] Started: ").append(stage.id);
        if (phase1BasedOrZero > 0)
            msg.append(" at boss phase ").append(phase1BasedOrZero);
        msg.append(" as ").append(characterId);
        player.sendSystemMessage(Component.literal(msg.toString()));
        return 1;
    }

    private static int join(CommandSourceStack src, String hostName) throws CommandSyntaxException {
        ServerPlayer joiner = src.getPlayerOrException();

        if (BulletHellManager.INSTANCE.isInMatch(joiner.getUUID())) {
            joiner.sendSystemMessage(Component.literal(
                    "[BulletHell] You are already in a match. Use /bullethell stop first."));
            return 0;
        }

        ServerPlayer host = src.getServer().getPlayerList().getPlayerByName(hostName);
        if (host == null || !BulletHellManager.INSTANCE.hasArena(host.getUUID())) {
            joiner.sendSystemMessage(Component.literal(
                    "[BulletHell] Player '" + hostName + "' has no active arena."));
            return 0;
        }
        if (host.getUUID().equals(joiner.getUUID())) {
            joiner.sendSystemMessage(Component.literal(
                    "[BulletHell] You cannot join your own arena."));
            return 0;
        }

        BHPackets.sendOpenJoinSelect(joiner, new OpenJoinSelectPacket(host.getUUID(), host.getName().getString()));
        return 1;
    }

    private static int controlsGet(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        BHControlScheme cur = BHControlSettings.serverGetPreference(player);
        String id = cur.id();
        player.sendSystemMessage(Component.literal(
                "[BulletHell] Current scheme: " + id + " - " + BHControlSettings.describe(cur)));
        return 1;
    }

    private static int controlsSet(CommandSourceStack src, String raw) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        var parsed = BHControlScheme.tryParse(raw);
        if (parsed.isEmpty()) {
            String id = BHControlSettings.serverGetPreferenceId(player.getUUID());
            player.sendSystemMessage(Component.literal(
                    "[BulletHell] Unknown scheme \"" + raw + "\". Current layout: " + id
                            + ". Valid: " + String.join(", ", BHControlScheme.allIds()) + "."));
            return 0;
        }
        BHControlScheme scheme = parsed.get();
        BHControlSettings.serverSetPreference(player, scheme);
        BHPackets.sendControlScheme(player, new ControlSchemePacket(scheme));
        player.sendSystemMessage(Component.literal(
                "[BulletHell] Control layout set to " + scheme.id() + " - " + BHControlSettings.describe(scheme)));
        return 1;
    }
}
