package mc.sayda.bullethell.network;

import mc.sayda.bullethell.BossProgression;
import mc.sayda.bullethell.CharacterUnlocks;
import mc.sayda.bullethell.arena.ArenaContext;
import mc.sayda.bullethell.arena.ArenaEndShareSnapshot;
import mc.sayda.bullethell.arena.BulletHellManager;
import mc.sayda.bullethell.arena.DifficultyConfig;
import mc.sayda.bullethell.arena.LastArenaRetryState;
import mc.sayda.bullethell.arena.LastArenaShareState;
import mc.sayda.bullethell.arena.LobbySession;
import mc.sayda.bullethell.arena.RetryVoteState;
import mc.sayda.bullethell.boss.CharacterLoader;
import mc.sayda.bullethell.boss.StageLoader;
import mc.sayda.bullethell.config.BullethellConfig;
import mc.sayda.bullethell.debug.BHDebugMode;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of parties: opening one from an NPC challenge, invites and join requests
 * the other side has to accept, and the actions members send from the party screen.
 *
 * Every entry point re-checks eligibility rather than trusting the client - any member
 * can send any action, and a player's unlocks can change while a party waits.
 */
public final class PartyHandler {

    /** How long an invite or join request can still be answered. */
    private static final long REQUEST_TTL_MS = 60_000L;

    private PartyHandler() {}

    // ---------------------------------------------------------------- eligibility

    /**
     * Highest difficulty this player may play {@code stageId} on, or {@code -1} when they
     * have not unlocked it at all - the same rule the NPC challenge applies to a solo run.
     */
    public static int maxDifficultyFor(ServerPlayer player, String stageId) {
        if (player == null || stageId == null || stageId.isBlank()) return -1;
        if (BHDebugMode.isGodMode(player.getUUID())) return DifficultyConfig.LUNATIC.ordinal();
        String bossId;
        try {
            bossId = StageLoader.load(stageId).bossId;
        } catch (Exception e) {
            return -1;
        }
        return BossProgression.maxAllowedDifficultyOrdinal(player, bossId);
    }

    /**
     * Recomputes every member's cap and pulls the party difficulty down to what all of
     * them can play. A lowered difficulty is a different run, so readiness resets.
     */
    private static void refreshEligibility(MinecraftServer server, LobbySession lobby) {
        // A request from someone who has since logged off cannot be answered; drop it.
        lobby.pendingJoins().removeIf(p -> server.getPlayerList().getPlayer(p.uuid) == null);
        for (LobbySession.Member m : lobby.members()) {
            m.maxDifficultyOrdinal = maxDifficultyFor(server.getPlayerList().getPlayer(m.uuid), lobby.stageId);
        }
        int cap = lobby.partyCapOrdinal();
        if (cap >= 0 && lobby.difficulty.ordinal() > cap) {
            lobby.difficulty = DifficultyConfig.fromId(cap);
            for (LobbySession.Member m : lobby.members()) m.ready = false;
        }
    }

    /** Why {@code p} cannot be added to {@code lobby}, phrased for the reader; null when they can. */
    private static String unavailableReason(ServerPlayer p, LobbySession lobby, boolean readerIsPlayer) {
        String is = readerIsPlayer ? "You are" : p.getName().getString() + " is";
        String has = readerIsPlayer ? "You have" : p.getName().getString() + " has";
        BulletHellManager mgr = BulletHellManager.INSTANCE;
        if (mgr.isInMatch(p.getUUID())) return is + " already in an arena.";
        if (mgr.isInLobby(p.getUUID())) return is + " already in a party.";
        if (maxDifficultyFor(p, lobby.stageId) < 0)
            return has + " not unlocked " + stageTitle(lobby.stageId) + " yet.";
        return null;
    }

    // ---------------------------------------------------------------- opening a party

    /**
     * Multiplayer on an NPC challenge: opens a party for that stage with the sender as
     * host, or points the party they already host at it. The stage is set here, at
     * creation, so a party can never exist without one.
     */
    public static void create(ServerPlayer host, String stageId) {
        if (!multiplayerAllowed(host)) return;
        BulletHellManager mgr = BulletHellManager.INSTANCE;
        if (mgr.isInMatch(host.getUUID())) {
            msg(host, "You are already in an arena.");
            return;
        }
        LobbySession current = mgr.getLobby(host.getUUID());
        if (current != null && !current.isHost(host.getUUID())) {
            msg(host, "You are in someone else's party - leave it first.");
            return;
        }
        if (maxDifficultyFor(host, stageId) < 0) {
            msg(host, "You have not unlocked " + stageTitle(stageId) + " yet.");
            return;
        }
        LobbySession lobby = mgr.getOrCreateLobby(host.getUUID(), host.getName().getString(), stageId);
        if (lobby.starting) return;
        if (!stageId.equals(lobby.stageId)) {
            lobby.stageId = stageId;
            for (LobbySession.Member m : lobby.members()) m.ready = false;
        }
        refreshEligibility(host.server, lobby);
        BHPackets.broadcastLobby(host.server, lobby);
    }

    // ---------------------------------------------------------------- invites and join requests

    /** Host invites a player. Nothing happens until they accept. */
    public static void invite(ServerPlayer host, UUID targetUuid) {
        if (!multiplayerAllowed(host)) return;
        LobbySession lobby = hostedLobby(host);
        if (lobby == null) {
            msg(host, "Open a party first: accept an NPC challenge and choose Multiplayer.");
            return;
        }
        ServerPlayer target = host.server.getPlayerList().getPlayer(targetUuid);
        if (target == null) {
            msg(host, "That player is no longer online.");
            return;
        }
        if (target.getUUID().equals(host.getUUID())) return;
        String why = unavailableReason(target, lobby, false);
        if (why != null) {
            msg(host, why);
            return;
        }
        BulletHellManager.INSTANCE.putPartyRequest(new BulletHellManager.PartyRequest(
                host.getUUID(), target.getUUID(), host.getUUID(), false, expiry()));
        target.sendSystemMessage(prompt(host.getName().getString() + " invited you to a party for "
                + stageTitle(lobby.stageId) + "."));
        msg(host, "Invite sent to " + target.getName().getString() + ".");
    }

    /** A player asks a host to let them in. Nothing happens until the host accepts. */
    public static void requestJoin(ServerPlayer requester, ServerPlayer host) {
        if (!multiplayerAllowed(requester)) return;
        if (host.getUUID().equals(requester.getUUID())) {
            msg(requester, "You cannot join your own party.");
            return;
        }
        LobbySession lobby = hostedLobby(host);
        if (lobby == null) {
            msg(requester, host.getName().getString() + " is not hosting a party.");
            return;
        }
        String why = unavailableReason(requester, lobby, true);
        if (why != null) {
            msg(requester, why);
            return;
        }
        BulletHellManager.INSTANCE.putPartyRequest(new BulletHellManager.PartyRequest(
                requester.getUUID(), host.getUUID(), host.getUUID(), true, expiry()));
        host.sendSystemMessage(prompt(requester.getName().getString() + " wants to join your party."));
        msg(requester, "Join request sent to " + host.getName().getString() + ".");
        // Listed in the party screen as well: chat cannot be opened from a screen, so a
        // chat-only prompt is unanswerable for a host sitting in the lobby.
        lobby.addPendingJoin(requester.getUUID(), requester.getName().getString());
        BHPackets.broadcastLobby(host.server, lobby);
    }

    /** {@code /bullethell accept}: answers the player's latest invite or join request. */
    public static void accept(ServerPlayer player) {
        // Checked again here: the option may have been turned off since the request was sent.
        if (!multiplayerAllowed(player)) return;
        BulletHellManager.PartyRequest request = BulletHellManager.INSTANCE.takePartyRequest(player.getUUID());
        if (request == null) {
            msg(player, "You have no pending party request.");
            return;
        }
        MinecraftServer server = player.server;
        // For an invite, whoever accepts is the one joining; for a join request, whoever asked.
        ServerPlayer joiner = request.joinRequest()
                ? server.getPlayerList().getPlayer(request.from())
                : player;
        ServerPlayer host = server.getPlayerList().getPlayer(request.hostUuid());
        LobbySession lobby = host != null ? hostedLobby(host) : null;
        if (joiner == null || lobby == null) {
            msg(player, "That party is no longer available.");
            return;
        }
        // Checked again: the request may be up to a minute old.
        String why = unavailableReason(joiner, lobby, joiner == player);
        if (why != null) {
            msg(player, why);
            return;
        }
        lobby.removePendingJoin(joiner.getUUID());
        BulletHellManager.INSTANCE.joinLobby(host.getUUID(), joiner.getUUID(), joiner.getName().getString());
        refreshEligibility(server, lobby);
        msg(joiner, "You joined " + host.getName().getString() + "'s party.");
        msg(host, joiner.getName().getString() + " joined your party.");
        BHPackets.broadcastLobby(server, lobby);
    }

    /** {@code /bullethell deny}: turns down the player's latest invite or join request. */
    public static void deny(ServerPlayer player) {
        BulletHellManager.PartyRequest request = BulletHellManager.INSTANCE.takePartyRequest(player.getUUID());
        if (request == null) {
            msg(player, "You have no pending party request.");
            return;
        }
        msg(player, "Declined.");
        // The party screen lists the same request; drop it there too so it cannot linger.
        if (request.joinRequest()) {
            ServerPlayer requestHost = player.server.getPlayerList().getPlayer(request.hostUuid());
            LobbySession hostLobby = requestHost != null ? hostedLobby(requestHost) : null;
            if (hostLobby != null) {
                hostLobby.removePendingJoin(request.from());
                BHPackets.broadcastLobby(player.server, hostLobby);
            }
        }
        ServerPlayer from = player.server.getPlayerList().getPlayer(request.from());
        if (from != null) {
            msg(from, player.getName().getString()
                    + (request.joinRequest() ? " declined your join request." : " declined your invite."));
        }
    }

    // ---------------------------------------------------------------- party screen actions

    /** One action from the party screen. Host-only actions are checked, not trusted. */
    public static void handleAction(ServerPlayer sender, LobbyActionPacket pkt) {
        if (pkt.action == LobbyActionPacket.CREATE) {
            create(sender, pkt.text);
            return;
        }
        LobbySession lobby = BulletHellManager.INSTANCE.getLobby(sender.getUUID());
        if (lobby == null) return;
        LobbySession.Member self = lobby.get(sender.getUUID());
        if (self == null) return;
        MinecraftServer server = sender.server;

        switch (pkt.action) {
            case LobbyActionPacket.SET_CHARACTER -> {
                if (lobby.starting) return;
                if (!CharacterUnlocks.isUnlockedFor(sender, pkt.text, lobby.difficulty)
                        && !BHDebugMode.isGodMode(sender.getUUID())) {
                    msg(sender, "Character \"" + pkt.text + "\" is locked on " + lobby.difficulty.name() + ".");
                    return;
                }
                self.characterId = pkt.text;
                BHPackets.broadcastLobby(server, lobby);
            }
            case LobbyActionPacket.SET_READY -> {
                if (lobby.starting) return;
                self.ready = pkt.value != 0;
                BHPackets.broadcastLobby(server, lobby);
            }
            case LobbyActionPacket.SET_DIFFICULTY -> {
                if (lobby.starting || !lobby.isHost(sender.getUUID())) return;
                refreshEligibility(server, lobby);
                if (pkt.value < 0 || pkt.value > lobby.partyCapOrdinal()) {
                    msg(sender, "Not everyone in the party has unlocked that difficulty.");
                    BHPackets.broadcastLobby(server, lobby);
                    return;
                }
                lobby.difficulty = DifficultyConfig.fromId(pkt.value);
                // A different difficulty is a different run; everyone agrees again.
                for (LobbySession.Member m : lobby.members()) m.ready = false;
                BHPackets.broadcastLobby(server, lobby);
            }
            case LobbyActionPacket.START -> {
                if (!lobby.isHost(sender.getUUID())) return;
                refreshEligibility(server, lobby);
                String blocker = startBlocker(server, lobby);
                if (blocker != null) {
                    msg(sender, blocker);
                    BHPackets.broadcastLobby(server, lobby);
                    return;
                }
                startRun(server, lobby);
            }
            case LobbyActionPacket.ACCEPT_JOIN -> {
                if (lobby.starting || !lobby.isHost(sender.getUUID())) return;
                answerJoin(server, lobby, sender, pkt.text, true);
            }
            case LobbyActionPacket.DENY_JOIN -> {
                if (!lobby.isHost(sender.getUUID())) return;
                answerJoin(server, lobby, sender, pkt.text, false);
            }
            case LobbyActionPacket.LEAVE -> leave(server, sender.getUUID());
            default -> { }
        }
    }

    /** What stops the party from starting right now, or null when nothing does. */
    private static String startBlocker(MinecraftServer server, LobbySession lobby) {
        for (LobbySession.Member m : lobby.members()) {
            if (m.maxDifficultyOrdinal < lobby.difficulty.ordinal())
                return m.name + " has not unlocked " + lobby.difficulty.name() + " on this stage.";
        }
        if (!lobby.canStart())
            return "Not everyone is ready (" + lobby.readyCount() + "/" + lobby.size() + ").";
        for (LobbySession.Member m : lobby.members()) {
            ServerPlayer p = server.getPlayerList().getPlayer(m.uuid);
            if (p == null) return m.name + " is no longer online.";
            if (!CharacterUnlocks.isUnlockedFor(p, m.characterId, lobby.difficulty)
                    && !BHDebugMode.isGodMode(p.getUUID()))
                return m.name + "'s character is locked on " + lobby.difficulty.name() + ".";
        }
        return null;
    }

    /**
     * Leaves whatever party this player is in. A host leaving disbands it - nobody else
     * owns its settings.
     *
     * @return true if they were in one
     */
    public static boolean leave(MinecraftServer server, UUID uuid) {
        if (server == null) return false;
        BulletHellManager mgr = BulletHellManager.INSTANCE;
        LobbySession lobby = mgr.getLobby(uuid);
        if (lobby == null) return false;
        boolean wasHost = lobby.isHost(uuid);
        List<UUID> members = lobby.memberIds();
        mgr.leaveLobby(uuid);

        ServerPlayer self = server.getPlayerList().getPlayer(uuid);
        if (self != null) BHPackets.sendLobbyState(self, LobbyStatePacket.closed());
        if (wasHost) {
            for (UUID member : members) {
                if (member.equals(uuid)) continue;
                ServerPlayer p = server.getPlayerList().getPlayer(member);
                if (p == null) continue;
                msg(p, "The party was disbanded.");
                BHPackets.sendLobbyState(p, LobbyStatePacket.closed());
            }
        } else {
            refreshEligibility(server, lobby);
            BHPackets.broadcastLobby(server, lobby);
        }
        return true;
    }

    /** Builds the arena, then joins every member before the first tick so nobody enters mid-phase. */
    private static void startRun(MinecraftServer server, LobbySession lobby) {
        lobby.starting = true;
        BHPackets.broadcastLobby(server, lobby);

        ServerPlayer host = server.getPlayerList().getPlayer(lobby.hostUuid);
        if (host == null) {
            lobby.starting = false;
            return;
        }

        List<UUID> members = lobby.memberIds();
        Map<UUID, String> characters = new LinkedHashMap<>();
        for (LobbySession.Member m : lobby.members()) characters.put(m.uuid, m.characterId);

        // Close the lobby before syncing, so the client swaps the screen exactly once.
        BulletHellManager.INSTANCE.closeLobby(lobby);
        for (UUID member : members) {
            ServerPlayer p = server.getPlayerList().getPlayer(member);
            if (p != null) BHPackets.sendLobbyState(p, LobbyStatePacket.closed());
        }

        startRun(server, lobby.hostUuid, members, characters, lobby.stageId, lobby.difficulty);
    }

    /**
     * Starts one arena for a whole group and drops everybody into it together - used by
     * both the lobby's start button and a party's retry vote.
     */
    private static ArenaContext startRun(MinecraftServer server, UUID hostUuid, List<UUID> members,
            Map<UUID, String> characters, String stageId, DifficultyConfig difficulty) {
        ServerPlayer host = server.getPlayerList().getPlayer(hostUuid);
        if (host == null) return null;

        BulletHellManager.INSTANCE.stopArena(hostUuid);
        ArenaContext ctx = BulletHellManager.INSTANCE.startArena(
                host, difficulty, stageId, characters.getOrDefault(hostUuid, "reimu"));

        // Every non-host member joins before the arena has ticked once.
        for (UUID member : members) {
            if (member.equals(hostUuid)) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(member);
            if (p == null) continue;
            BulletHellManager.INSTANCE.joinMatch(member, hostUuid,
                    CharacterLoader.load(characters.getOrDefault(member, "reimu")), p);
        }

        for (UUID member : members) {
            ServerPlayer p = server.getPlayerList().getPlayer(member);
            if (p == null) continue;
            BHPackets.sendFullSync(p, ctx);
            int pIdx = 1;
            if (!member.equals(hostUuid)) {
                int c = 2;
                for (UUID cid : ctx.getCoopPlayers().keySet()) {
                    if (cid.equals(member)) { pIdx = c; break; }
                    c++;
                }
            }
            BHPackets.sendToPlayer(p, new ArenaStatePacket(ctx, member, pIdx));
        }
        return ctx;
    }

    /**
     * The host answering a join request from the party screen. The chat prompt points at
     * the same request, and both spend it, so it can never be answered twice.
     */
    private static void answerJoin(MinecraftServer server, LobbySession lobby, ServerPlayer host,
            String requesterUuid, boolean accept) {
        UUID uuid;
        try {
            uuid = UUID.fromString(requesterUuid);
        } catch (IllegalArgumentException e) {
            return;
        }
        LobbySession.PendingJoin pending = lobby.takePendingJoin(uuid);
        if (pending == null) return;
        BulletHellManager.INSTANCE.dropPartyRequests(uuid);

        ServerPlayer joiner = server.getPlayerList().getPlayer(uuid);
        if (joiner == null) {
            msg(host, pending.name + " is no longer online.");
            BHPackets.broadcastLobby(server, lobby);
            return;
        }
        if (!accept) {
            msg(host, "Declined " + pending.name + ".");
            msg(joiner, host.getName().getString() + " declined your join request.");
            BHPackets.broadcastLobby(server, lobby);
            return;
        }
        // Re-checked here: the request may have been sitting in the list for a while.
        String why = unavailableReason(joiner, lobby, false);
        if (why != null) {
            msg(host, why);
            BHPackets.broadcastLobby(server, lobby);
            return;
        }
        BulletHellManager.INSTANCE.joinLobby(lobby.hostUuid, uuid, joiner.getName().getString());
        refreshEligibility(server, lobby);
        msg(joiner, "You joined " + host.getName().getString() + "'s party.");
        msg(host, joiner.getName().getString() + " joined your party.");
        BHPackets.broadcastLobby(server, lobby);
    }

    // ---------------------------------------------------------------- retry

    /**
     * The results screen's RETRY button. A solo run restarts on the spot; a co-op run
     * needs everyone who played it to ask, so this counts votes and starts the rematch
     * on the last one.
     *
     * @param cancel the player left the results screen instead - withdraw them, which
     *               releases anyone else still waiting on the vote
     */
    public static void retry(ServerPlayer player, boolean cancel) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        UUID uuid = player.getUUID();

        if (cancel) {
            dropRetry(server, uuid, player.getGameProfile().getName());
            return;
        }
        if (BulletHellManager.INSTANCE.isInMatch(uuid)) return;

        RetryVoteState.Group group = RetryVoteState.get(uuid);
        if (group == null) {
            retrySolo(player);
            return;
        }

        // Re-checked per vote: unlocks can change while a party sits on the results screen.
        String blocked = retryBlockReason(player, group.stageId, group.difficulty,
                group.characters.getOrDefault(uuid, "reimu"), false);
        if (blocked != null) {
            msg(player, blocked);
            return;
        }

        group.vote(uuid);
        if (!group.everyoneVoted()) {
            BHPackets.sendRetryVote(player, RetryVotePacket.waiting(group.voteCount(), group.total()));
            broadcastRetryVote(server, group, RetryVotePacket.waiting(group.voteCount(), group.total()));
            return;
        }

        // Last vote in - but only if the party is all still here and free to play.
        for (UUID member : group.members) {
            ServerPlayer p = server.getPlayerList().getPlayer(member);
            if (p != null && !BulletHellManager.INSTANCE.isInMatch(member)) continue;
            String who = p != null ? p.getGameProfile().getName() : "Someone";
            dropRetryGroup(server, group, null,
                    who + " is no longer available; retry will start a solo run.");
            return;
        }

        RetryVoteState.drop(group);
        broadcastRetryVote(server, group, RetryVotePacket.started(group.total()));
        startRun(server, group.hostUuid, group.members, group.characters,
                group.stageId, group.difficulty);
    }

    /**
     * Ends a party retry because someone left the results screen or the server. Whoever
     * is left gets their ordinary solo retry button back rather than waiting forever.
     */
    public static void dropRetry(MinecraftServer server, UUID leaver, String leaverName) {
        if (server == null) return;
        RetryVoteState.Group group = RetryVoteState.get(leaver);
        if (group == null) return;
        dropRetryGroup(server, group, leaver,
                leaverName + " left the results screen; retry will start a solo run.");
    }

    private static void dropRetryGroup(MinecraftServer server, RetryVoteState.Group group,
            UUID except, String notice) {
        RetryVoteState.drop(group);
        for (UUID member : group.members) {
            ServerPlayer p = server.getPlayerList().getPlayer(member);
            if (p == null) continue;
            BHPackets.sendRetryVote(p, RetryVotePacket.solo());
            // Only the people actually held up by the vote need telling.
            if (group.hasVoted(member) && !member.equals(except)) msg(p, notice);
        }
    }

    private static void broadcastRetryVote(MinecraftServer server, RetryVoteState.Group group,
            RetryVotePacket pkt) {
        for (UUID member : group.members) {
            ServerPlayer p = server.getPlayerList().getPlayer(member);
            if (p != null) BHPackets.sendRetryVote(p, pkt);
        }
    }

    /** Restarts one player's own last run, from the server's record of it. */
    private static void retrySolo(ServerPlayer player) {
        LastArenaRetryState.Params last = LastArenaRetryState.get(player.getUUID());
        if (last == null || last.stageId().isBlank() || last.characterId().isBlank()) {
            msg(player, "No finished run to retry yet.");
            return;
        }
        if (last.testMode()) {
            msg(player, "Retry is not available in test mode.");
            return;
        }
        String blocked = retryBlockReason(player, last.stageId(), last.difficulty(),
                last.characterId(), last.practice());
        if (blocked != null) {
            msg(player, blocked);
            return;
        }
        BHPackets.startArena(player, last.difficulty(), last.stageId(), last.characterId(),
                last.practice());
    }

    /** Why this player cannot replay that run, or null when they can. */
    private static String retryBlockReason(ServerPlayer player, String stageId,
            DifficultyConfig difficulty, String characterId, boolean practice) {
        if (BHDebugMode.isGodMode(player.getUUID())) return null;

        if (!CharacterUnlocks.isUnlockedFor(player, characterId, difficulty))
            return "Character '" + characterId + "' is locked for " + difficulty.name() + ".";

        if (!practice && !BossProgression.canChallengeStage(player, stageId, difficulty)) {
            String bossId;
            try {
                bossId = StageLoader.load(stageId).bossId;
            } catch (Exception e) {
                bossId = "";
            }
            DifficultyConfig cap = BossProgression.maxAllowedDifficulty(player, bossId);
            return stageId + " is currently capped at " + (cap == null ? "none" : cap.name())
                    + ". " + BossProgression.requirementSummary(bossId);
        }
        return null;
    }

    // ---------------------------------------------------------------- sharing

    /** Posts the player's last finished run to everyone online ({@code /bullethell share}). */
    public static void shareLastRun(ServerPlayer sender) {
        ArenaEndShareSnapshot snap = LastArenaShareState.get(sender.getUUID());
        if (snap == null) {
            msg(sender, "No finished run to share yet.");
            return;
        }
        String who = sender.getGameProfile().getName();
        for (ServerPlayer target : sender.server.getPlayerList().getPlayers()) {
            target.sendSystemMessage(Component.literal("[BulletHell] " + who + " shared a run:"));
            for (var line : snap.buildLines())
                target.sendSystemMessage(line);
        }
    }

    // ---------------------------------------------------------------- helpers

    /** The AllowMultiplayer config gate; tells the player why when it is closed. */
    private static boolean multiplayerAllowed(ServerPlayer p) {
        if (BullethellConfig.ALLOW_MULTIPLAYER.get()) return true;
        msg(p, "Multiplayer is disabled on this server.");
        return false;
    }

    /** The party this player hosts and that can still take people, or null. */
    private static LobbySession hostedLobby(ServerPlayer host) {
        LobbySession lobby = BulletHellManager.INSTANCE.getLobby(host.getUUID());
        return (lobby != null && lobby.isHost(host.getUUID()) && !lobby.starting) ? lobby : null;
    }

    private static long expiry() {
        return System.currentTimeMillis() + REQUEST_TTL_MS;
    }

    private static String stageTitle(String stageId) {
        try {
            String title = StageLoader.load(stageId).title;
            return (title != null && !title.isBlank()) ? title : stageId;
        } catch (Exception e) {
            return stageId;
        }
    }

    private static void msg(ServerPlayer p, String text) {
        p.sendSystemMessage(Component.literal("[BulletHell] " + text));
    }

    /** A request line ending in a clickable "accept / deny" - accept in green, deny in red. */
    private static Component prompt(String text) {
        MutableComponent accept = Component.literal("accept").withStyle(s -> s
                .withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/bullethell accept"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Accept"))));
        MutableComponent deny = Component.literal("deny").withStyle(s -> s
                .withColor(ChatFormatting.RED)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/bullethell deny"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Decline"))));
        return Component.literal("[BulletHell] " + text + " ")
                .append(accept)
                .append(Component.literal(" / ").withStyle(ChatFormatting.GRAY))
                .append(deny);
    }
}
