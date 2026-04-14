package mc.sayda.bullethell.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import mc.sayda.bullethell.arena.BulletHellManager;
import mc.sayda.bullethell.network.BHPackets;
import mc.sayda.bullethell.network.OpenJoinSelectPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * /bullethell start - opens difficulty → character screens
 * /bullethell join &lt;playerName&gt; - join another player's active arena
 * /bullethell stop - end own arena or leave a coop match
 * /bullethell status - print current arena stats to chat
 */
public final class BulletHellCommands {

    private BulletHellCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bullethell")
                .requires(src -> src.hasPermission(0)) // any player
                .executes(ctx -> start(ctx.getSource()))

                // ---- start ----
                .then(Commands.literal("start")
                        .executes(ctx -> start(ctx.getSource())))

                // ---- join <playerName> ----
                .then(Commands.literal("join")
                        .then(Commands.argument("player", StringArgumentType.word())
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
                                player.sendSystemMessage(Component.literal(
                                        "[BulletHell] Active | bullets=" + arena.bullets.getActiveCount()
                                                + " | phase=" + (arena.bossPhase + 1)
                                                + " | lives=" + ps.lives
                                                + " | bombs=" + ps.bombs));
                            } else {
                                player.sendSystemMessage(Component.literal("[BulletHell] Not in an arena."));
                            }
                            return 1;
                        })));
    }

    // ---------------------------------------------------------------- helpers

    private static int start(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        BHPackets.sendOpenCharSelect(player);
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
}
