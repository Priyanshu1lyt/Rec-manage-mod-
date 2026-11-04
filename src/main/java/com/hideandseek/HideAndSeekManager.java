package com.hideandseek;

import static com.hideandseek.HideAndSeekAnnouncements.clock;
import static com.hideandseek.HideAndSeekAnnouncements.fill;
import static com.hideandseek.HideAndSeekAnnouncements.legacy;
import static com.hideandseek.HideAndSeekAnnouncements.words;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;

/**
 * Owns the authoritative match state. Everything runs on the server thread and is driven by
 * {@link #tick()}; there is exactly one timer, which is cancelled whenever a match ends.
 */
public final class HideAndSeekManager {

    public enum Phase { IDLE, HIDING, RUNNING }

    public enum EndReason { SEEKER_WIN, TIME_UP, STOPPED, RESET, CANCELLED, SHUTDOWN }

    public record Result(boolean success, String message) {
        public static Result ok(String message) { return new Result(true, message); }
        public static Result fail(String message) { return new Result(false, message); }
    }

    private final MinecraftServer server;
    private final HideAndSeekPlayerManager players = new HideAndSeekPlayerManager();
    private final HideAndSeekTimer timer = new HideAndSeekTimer();
    private final HideAndSeekBossBar bossBar = new HideAndSeekBossBar();
    private final HideAndSeekAnnouncements announce;

    private Phase phase = Phase.IDLE;
    private UUID preselectedSeeker;
    private String preselectedName;
    private int matchSeconds;
    private int tickCounter;
    private boolean internalModeChange;
    private boolean seekerFrozen;
    private double frozenX;
    private double frozenZ;

    public HideAndSeekManager(MinecraftServer server) {
        this.server = server;
        this.announce = new HideAndSeekAnnouncements(server);
    }

    // ================================================================== queries

    public Phase phase() { return phase; }
    public boolean isActive() { return phase != Phase.IDLE; }
    public boolean isPaused() { return timer.isPaused(); }

    public boolean isSeekerLocked(UUID id) {
        return phase == Phase.HIDING && players.isSeeker(id);
    }

    private ServerPlayer online(UUID id) {
        return id == null ? null : server.getPlayerList().getPlayer(id);
    }

    private static String name(ServerPlayer p) {
        return p.getGameProfile().getName();
    }

    private List<ServerPlayer> participants() {
        List<ServerPlayer> list = new ArrayList<>();
        ServerPlayer s = online(players.getSeeker());
        if (s != null) {
            list.add(s);
        }
        for (UUID id : players.activeHiders()) {
            ServerPlayer p = online(id);
            if (p != null) {
                list.add(p);
            }
        }
        return list;
    }

    private List<ServerPlayer> viewers() {
        List<ServerPlayer> list = participants();
        if (HideAndSeekConfig.eliminatedCanWatch()) {
            for (UUID id : players.caught()) {
                if (players.isDeparted(id)) {
                    continue;
                }
                ServerPlayer p = online(id);
                if (p != null) {
                    list.add(p);
                }
            }
        }
        return list;
    }

    private List<String> names(List<UUID> ids) {
        List<String> out = new ArrayList<>();
        for (UUID id : ids) {
            out.add(players.nameOf(id));
        }
        return out;
    }

    private void setMode(ServerPlayer p, GameType type) {
        internalModeChange = true;
        try {
            p.setGameMode(type);
        } finally {
            internalModeChange = false;
        }
    }

    // ================================================================== start

    public Result startWithPreselected(Integer minutes) {
        if (preselectedSeeker == null) {
            return Result.fail("No seeker selected. Use /hideandseek start <seeker> [minutes] or /hideandseek setseeker <player>.");
        }
        ServerPlayer seeker = online(preselectedSeeker);
        if (seeker == null) {
            return Result.fail("The preselected seeker (" + preselectedName + ") is not online.");
        }
        return start(seeker, minutes);
    }

    public Result start(ServerPlayer seeker, Integer minutes) {
        if (phase != Phase.IDLE) {
            return Result.fail("A match is already running. Use /hideandseek stop first.");
        }
        if (seeker == null) {
            return Result.fail("The seeker must be an online player.");
        }
        GameType seekerMode = seeker.gameMode.getGameModeForPlayer();
        if (!HideAndSeekConfig.isEligible(seekerMode)) {
            return Result.fail(name(seeker) + " is in " + seekerMode.getName()
                    + " mode and cannot be the seeker (Creative and Spectator players never take part).");
        }

        List<ServerPlayer> hiders = new ArrayList<>();
        int excluded = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p == seeker) {
                continue;
            }
            if (HideAndSeekConfig.isEligible(p.gameMode.getGameModeForPlayer())) {
                hiders.add(p);
            } else {
                excluded++;
            }
        }
        if (hiders.isEmpty()) {
            return Result.fail("There is no eligible hider besides the seeker (" + excluded
                    + " player(s) excluded because of their game mode).");
        }
        int total = hiders.size() + 1;
        if (total < HideAndSeekConfig.minPlayers()) {
            return Result.fail("Not enough players: " + total + " eligible, " + HideAndSeekConfig.minPlayers() + " required.");
        }

        int minutesToUse = minutes != null ? minutes : HideAndSeekConfig.defaultMatchMinutes();
        int hideSeconds = HideAndSeekConfig.hidingSeconds();

        players.clear();
        players.setSeeker(seeker);
        for (ServerPlayer h : hiders) {
            players.addHider(h);
        }
        matchSeconds = minutesToUse * 60;
        preselectedSeeker = null;
        preselectedName = null;
        tickCounter = 0;
        phase = Phase.HIDING;

        freezeSeeker(seeker);

        List<ServerPlayer> view = participants();
        if (HideAndSeekConfig.bossBarEnabled()) {
            bossBar.create(Component.empty(), BossEvent.BossBarColor.BLUE);
            for (ServerPlayer p : view) {
                bossBar.addPlayer(p);
            }
        }

        announce.broadcast(announce.prefixed(HideAndSeekConfig.msg("startChat")));
        announce.broadcast(announce.prefixed(fill(HideAndSeekConfig.msg("hideChat"), "seconds", hideSeconds)));
        seeker.sendSystemMessage(announce.prefixed(HideAndSeekConfig.msg("roleSeeker")));
        for (ServerPlayer h : hiders) {
            h.sendSystemMessage(announce.prefixed(HideAndSeekConfig.msg("roleHider")));
        }

        timer.start(hideSeconds, this::onHidingSecond, this::release);
        onHidingSecond(hideSeconds);

        return Result.ok("Match started: seeker " + name(seeker) + ", " + hiders.size() + " hider(s), "
                + minutesToUse + " minute(s). " + excluded + " player(s) excluded.");
    }

    // ================================================================== tick / phases

    /** Called once per server tick. Does nothing while idle. */
    public void tick() {
        if (phase == Phase.IDLE) {
            return;
        }
        tickCounter++;
        if (phase == Phase.HIDING && seekerFrozen) {
            enforceFreeze();
        }
        if (phase == Phase.RUNNING && !timer.isPaused()
                && HideAndSeekConfig.catchMethod() == HideAndSeekConfig.CatchMethod.TAG && tickCounter % 5 == 0) {
            checkTagging();
        }
        timer.tick();
    }

    private void onHidingSecond(int seconds) {
        if (phase != Phase.HIDING) {
            return;
        }
        List<ServerPlayer> view = participants();
        if (HideAndSeekConfig.countdownTitleEnabled()) {
            String color = seconds <= 3 ? "&c" : seconds <= 5 ? "&6" : seconds <= 10 ? "&e" : "&a";
            announce.title(view, legacy(color + "&l" + seconds),
                    legacy(fill(HideAndSeekConfig.msg("hideCountdownSubtitle"), "seconds", seconds)), 0, 25, 5);
        }
        announce.countdownSound(view, seconds <= 3 ? 1.6F : 1.0F);
        refreshBar();
    }

    private void release() {
        if (phase != Phase.HIDING) {
            return;
        }
        phase = Phase.RUNNING;
        unfreezeSeeker(players.getSeeker());

        List<ServerPlayer> view = viewers();
        String seekerName = players.nameOf(players.getSeeker());
        announce.title(view, legacy(HideAndSeekConfig.msg("releaseTitle")),
                legacy(HideAndSeekConfig.msg("releaseSubtitle")), 5, 50, 15);
        announce.sound(view, HideAndSeekConfig.soundRelease(), 1.0F);
        announce.broadcast(announce.prefixed(fill(HideAndSeekConfig.msg("releaseChat"), "seeker", seekerName)));

        timer.start(matchSeconds, this::onRunningSecond, this::onTimeUp);
        refreshBar();
    }

    private void onRunningSecond(int seconds) {
        if (phase != Phase.RUNNING) {
            return;
        }
        refreshBar();
        if (!HideAndSeekConfig.warningsEnabled()) {
            return;
        }
        List<ServerPlayer> view = viewers();
        if (HideAndSeekConfig.warningTimes().contains(seconds)) {
            String text = fill(HideAndSeekConfig.msg("warningChat"), "time", words(seconds));
            announce.chat(view, announce.prefixed(text));
            announce.actionBar(view, legacy(text));
            announce.sound(view, HideAndSeekConfig.soundWarning(), 1.0F);
        } else if (seconds > 0 && seconds <= HideAndSeekConfig.finalCountdownSeconds()) {
            announce.title(view, legacy("&c&l" + seconds), Component.empty(), 0, 15, 5);
            announce.sound(view, HideAndSeekConfig.soundWarning(), 1.4F);
        }
    }

    private void onTimeUp() {
        if (phase != Phase.RUNNING) {
            return;
        }
        endMatch(EndReason.TIME_UP, null);
    }

    private void checkTagging() {
        ServerPlayer seeker = online(players.getSeeker());
        if (seeker == null) {
            return;
        }
        double max = HideAndSeekConfig.catchDistance();
        double maxSq = max * max;
        for (UUID id : players.activeHiders()) {
            ServerPlayer hider = online(id);
            if (hider == null || hider.level() != seeker.level()) {
                continue;
            }
            if (seeker.distanceToSqr(hider) <= maxSq) {
                performCatch(hider);
                if (phase != Phase.RUNNING) {
                    return;
                }
            }
        }
    }

    // ================================================================== seeker freeze

    private void freezeSeeker(ServerPlayer seeker) {
        seekerFrozen = true;
        frozenX = seeker.getX();
        frozenZ = seeker.getZ();
        seeker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, MobEffectInstance.INFINITE_DURATION, 255, false, false, false));
        seeker.addEffect(new MobEffectInstance(MobEffects.JUMP, MobEffectInstance.INFINITE_DURATION, 128, false, false, false));
        if (HideAndSeekConfig.blindSeeker()) {
            seeker.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
        }
    }

    private void enforceFreeze() {
        ServerPlayer seeker = online(players.getSeeker());
        if (seeker == null) {
            return;
        }
        double dx = seeker.getX() - frozenX;
        double dz = seeker.getZ() - frozenZ;
        if (dx * dx + dz * dz > 0.25D) {
            seeker.connection.teleport(frozenX, seeker.getY(), frozenZ, seeker.getYRot(), seeker.getXRot());
        }
    }

    private void unfreezeSeeker(UUID seekerId) {
        if (!seekerFrozen) {
            return;
        }
        seekerFrozen = false;
        ServerPlayer seeker = online(seekerId);
        if (seeker != null) {
            seeker.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            seeker.removeEffect(MobEffects.JUMP);
            seeker.removeEffect(MobEffects.BLINDNESS);
        }
    }

    // ================================================================== catching

    /** Called from the attack event. Returns true if the attack should be cancelled. */
    public boolean onPlayerAttack(ServerPlayer attacker, ServerPlayer victim) {
        if (phase == Phase.IDLE) {
            return false;
        }
        UUID a = attacker.getUUID();
        UUID v = victim.getUUID();
        if (players.isSeeker(a) && phase == Phase.HIDING) {
            return true;
        }
        if (!players.isParticipant(v)) {
            return false;
        }
        if (phase == Phase.HIDING && players.isSeeker(v)) {
            return true;
        }
        if (players.isSeeker(a) && players.isActiveHider(v) && phase == Phase.RUNNING && !timer.isPaused()
                && HideAndSeekConfig.catchMethod() == HideAndSeekConfig.CatchMethod.ATTACK) {
            performCatch(victim);
            return true;
        }
        return HideAndSeekConfig.preventParticipantDamage();
    }

    public Result catchHider(ServerPlayer hider) {
        if (phase == Phase.IDLE) {
            return Result.fail("No match is running.");
        }
        if (!players.isActiveHider(hider.getUUID())) {
            return Result.fail(name(hider) + " is not an active hider.");
        }
        performCatch(hider);
        return Result.ok(name(hider) + " was eliminated.");
    }

    private void performCatch(ServerPlayer hider) {
        UUID id = hider.getUUID();
        if (!players.markCaught(id)) {
            return; // already caught: never announce twice
        }
        if (HideAndSeekConfig.eliminatedBecomeSpectators()) {
            players.rememberMode(id, hider.gameMode.getGameModeForPlayer());
            setMode(hider, GameType.SPECTATOR);
        }
        if (!HideAndSeekConfig.eliminatedCanWatch()) {
            bossBar.removePlayer(hider);
        }
        int remaining = players.activeHiders().size();

        if (HideAndSeekConfig.catchAnnouncementEnabled()) {
            Set<ServerPlayer> audience = new LinkedHashSet<>(viewers());
            audience.add(hider);
            String n = name(hider);
            announce.broadcast(announce.prefixed(fill(HideAndSeekConfig.msg("caughtChat"), "player", n, "remaining", remaining)));
            announce.title(audience, legacy(HideAndSeekConfig.msg("caughtTitle")),
                    legacy(fill(HideAndSeekConfig.msg("caughtSubtitle"), "player", n)), 5, 40, 10);
            announce.sound(audience, HideAndSeekConfig.soundCatch(), 1.0F);
        }
        refreshBar();
        evaluateAfterRemoval();
    }

    public Result uncatchHider(ServerPlayer p) {
        if (phase == Phase.IDLE) {
            return Result.fail("No match is running.");
        }
        UUID id = p.getUUID();
        if (!players.unCatch(id)) {
            return Result.fail(name(p) + " has not been caught.");
        }
        GameType previous = players.takeMode(id);
        if (previous != null) {
            setMode(p, previous);
        } else if (p.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
            setMode(p, GameType.SURVIVAL);
        }
        bossBar.addPlayer(p);
        announce.broadcast(announce.prefixed(fill(HideAndSeekConfig.msg("uncaughtChat"),
                "player", name(p), "remaining", players.activeHiders().size())));
        refreshBar();
        return Result.ok(name(p) + " is a hider again.");
    }

    // ================================================================== join / leave / disconnects

    public Result join(ServerPlayer p) {
        if (phase == Phase.IDLE) {
            return Result.fail("No match is running.");
        }
        if (!HideAndSeekConfig.allowJoin()) {
            return Result.fail("Joining after the match has started is disabled.");
        }
        UUID id = p.getUUID();
        if (players.isParticipant(id)) {
            return Result.fail("You are already in the match.");
        }
        if (players.isCaught(id)) {
            return Result.fail("You have already been caught.");
        }
        if (!HideAndSeekConfig.isEligible(p.gameMode.getGameModeForPlayer())) {
            return Result.fail("Creative and Spectator players cannot join.");
        }
        players.addHider(p);
        bossBar.addPlayer(p);
        announce.broadcast(announce.prefixed(fill(HideAndSeekConfig.msg("joinedChat"), "player", name(p))));
        p.sendSystemMessage(announce.prefixed(HideAndSeekConfig.msg("roleHider")));
        refreshBar();
        return Result.ok("");
    }

    public Result leave(ServerPlayer p) {
        if (phase == Phase.IDLE) {
            return Result.fail("No match is running.");
        }
        UUID id = p.getUUID();
        if (players.isSeeker(id)) {
            return Result.fail("The seeker cannot leave. Ask an administrator to use /hideandseek stop.");
        }
        boolean hider = players.isActiveHider(id);
        boolean caught = players.isCaught(id) && !players.isDeparted(id);
        if (!hider && !caught) {
            return Result.fail("You are not in the match.");
        }
        if (!HideAndSeekConfig.allowLeave()) {
            return Result.fail("Leaving during a match is disabled.");
        }
        bossBar.removePlayer(p);
        if (hider) {
            players.removeHider(id);
            announce.broadcast(announce.prefixed(fill(HideAndSeekConfig.msg("leftChat"),
                    "player", name(p), "remaining", players.activeHiders().size())));
            refreshBar();
            evaluateAfterRemoval();
        } else {
            players.markDeparted(id);
            GameType previous = players.takeMode(id);
            if (previous != null && HideAndSeekConfig.restoreGameModes()) {
                setMode(p, previous);
            }
        }
        return Result.ok("");
    }

    public void onLogout(ServerPlayer p) {
        bossBar.removePlayer(p);
        if (phase == Phase.IDLE) {
            return;
        }
        UUID id = p.getUUID();
        if (players.isSeeker(id)) {
            endMatch(EndReason.CANCELLED, "The seeker left the game.");
        } else if (players.isActiveHider(id)) {
            players.removeHider(id);
            announce.broadcast(announce.prefixed(fill(HideAndSeekConfig.msg("leftChat"),
                    "player", name(p), "remaining", players.activeHiders().size())));
            refreshBar();
            evaluateAfterRemoval();
        } else if (players.isCaught(id)) {
            // Restore the game mode now so the player does not come back as a spectator.
            GameType previous = players.takeMode(id);
            if (previous != null) {
                setMode(p, previous);
            }
        }
    }

    /** Called before a player's game mode changes (not for changes made by this mod). */
    public void onGameModeChange(ServerPlayer p, GameType newMode) {
        if (internalModeChange || phase == Phase.IDLE) {
            return;
        }
        UUID id = p.getUUID();
        if (players.isCaught(id)) {
            players.forgetMode(id); // someone else changed it: do not override later
            return;
        }
        if (HideAndSeekConfig.isEligible(newMode)) {
            return;
        }
        if (players.isSeeker(id)) {
            endMatch(EndReason.CANCELLED, "The seeker switched to " + newMode.getName() + " mode.");
        } else if (players.isActiveHider(id)) {
            players.removeHider(id);
            bossBar.removePlayer(p);
            p.sendSystemMessage(announce.prefixed("&7You were removed from the match (game mode changed)."));
            refreshBar();
            evaluateAfterRemoval();
        }
    }

    private void evaluateAfterRemoval() {
        if (phase == Phase.IDLE || !players.activeHiders().isEmpty()) {
            return;
        }
        if (players.caught().isEmpty()) {
            endMatch(EndReason.CANCELLED, "No hiders are left in the match.");
        } else {
            endMatch(EndReason.SEEKER_WIN, null);
        }
    }

    // ================================================================== end / stop / reset

    public Result stop() {
        if (phase == Phase.IDLE) {
            return Result.fail("No match is running.");
        }
        endMatch(EndReason.STOPPED, null);
        return Result.ok("Match stopped.");
    }

    public Result reset() {
        if (phase != Phase.IDLE) {
            endMatch(EndReason.RESET, null);
        }
        forceReset();
        return Result.ok("Hide & Seek was reset.");
    }

    /** Hard reset used by /reset and as an error fallback. Never throws. */
    public void forceReset() {
        try {
            if (phase != Phase.IDLE) {
                endMatch(EndReason.SHUTDOWN, null);
            }
        } catch (RuntimeException e) {
            HideAndSeekMod.LOGGER.error("Error while resetting Hide & Seek", e);
        }
        timer.cancel();
        bossBar.remove();
        players.clear();
        phase = Phase.IDLE;
        preselectedSeeker = null;
        preselectedName = null;
        seekerFrozen = false;
        internalModeChange = false;
        tickCounter = 0;
    }

    public void shutdown() {
        forceReset();
    }

    private void endMatch(EndReason reason, String detail) {
        if (phase == Phase.IDLE) {
            return;
        }
        UUID seekerId = players.getSeeker();
        String seekerName = seekerId == null ? "?" : players.nameOf(seekerId);
        List<String> survivorNames = names(players.activeHiders());
        int caughtCount = players.caught().size();
        int survivors = survivorNames.size();

        Set<ServerPlayer> audience = new LinkedHashSet<>(viewers());
        for (UUID id : players.caught()) {
            ServerPlayer p = online(id);
            if (p != null && !players.isDeparted(id)) {
                audience.add(p);
            }
        }

        phase = Phase.IDLE;
        timer.cancel();
        bossBar.remove();
        unfreezeSeeker(seekerId);
        restoreModes(reason == EndReason.SHUTDOWN);
        players.clear();
        tickCounter = 0;

        switch (reason) {
            case SEEKER_WIN, TIME_UP -> announceWinner(reason, audience, seekerName, caughtCount, survivors, survivorNames);
            case STOPPED -> announce.broadcast(announce.prefixed(HideAndSeekConfig.msg("stoppedChat")));
            case RESET -> announce.broadcast(announce.prefixed(HideAndSeekConfig.msg("resetChat")));
            case CANCELLED -> announce.broadcast(announce.prefixed(fill(HideAndSeekConfig.msg("cancelledChat"),
                    "reason", detail == null ? "unknown" : detail)));
            case SHUTDOWN -> { }
        }
    }

    private void announceWinner(EndReason reason, Collection<ServerPlayer> audience, String seekerName,
                                int caught, int survivors, List<String> survivorNames) {
        if (!HideAndSeekConfig.victoryAnnouncementEnabled()) {
            return;
        }
        boolean seekerWon = reason == EndReason.SEEKER_WIN;
        Object[] vars = {"seeker", seekerName, "caught", caught, "survivors", survivors};
        String winnerTitle = HideAndSeekConfig.msg(seekerWon ? "seekerWinsTitle" : "hidersWinTitle");

        announce.broadcast(legacy(HideAndSeekConfig.msg("separator")));
        announce.broadcast(legacy(HideAndSeekConfig.msg(seekerWon ? "allCaughtChat" : "timeUpChat")));
        announce.broadcast(legacy(winnerTitle));
        announce.broadcast(legacy(fill(HideAndSeekConfig.msg("resultSummary"), vars)));
        if (!survivorNames.isEmpty()) {
            announce.broadcast(legacy(fill(HideAndSeekConfig.msg("survivorList"), "players", String.join(", ", survivorNames))));
        }
        announce.broadcast(legacy(HideAndSeekConfig.msg("separator")));

        announce.title(audience, legacy(fill(winnerTitle, vars)),
                legacy(fill(HideAndSeekConfig.msg(seekerWon ? "allCaughtSubtitle" : "timeUpSubtitle"), vars)), 10, 80, 20);
        announce.sound(audience, HideAndSeekConfig.soundVictory(), 1.0F);
    }

    private void restoreModes(boolean force) {
        for (UUID id : players.modeHolders()) {
            GameType mode = players.takeMode(id);
            if (mode == null || (!force && !HideAndSeekConfig.restoreGameModes())) {
                continue;
            }
            ServerPlayer p = online(id);
            if (p != null) {
                setMode(p, mode);
            }
        }
    }

    // ================================================================== pause / resume

    public Result pause() {
        if (phase == Phase.IDLE) {
            return Result.fail("No match is running.");
        }
        if (timer.isPaused()) {
            return Result.fail("The match is already paused.");
        }
        timer.setPaused(true);
        announce.chat(viewers(), announce.prefixed(HideAndSeekConfig.msg("pausedChat")));
        refreshBar();
        return Result.ok("Match paused.");
    }

    public Result resume() {
        if (phase == Phase.IDLE) {
            return Result.fail("No match is running.");
        }
        if (!timer.isPaused()) {
            return Result.fail("The match is not paused.");
        }
        timer.setPaused(false);
        announce.chat(viewers(), announce.prefixed(HideAndSeekConfig.msg("resumedChat")));
        refreshBar();
        return Result.ok("Match resumed.");
    }

    // ================================================================== boss bar

    private void refreshBar() {
        if (!bossBar.exists() || phase == Phase.IDLE) {
            return;
        }
        int seconds = timer.getRemainingSeconds();
        String template = phase == Phase.HIDING ? HideAndSeekConfig.msg("bossBarHiding") : HideAndSeekConfig.msg("bossBarRunning");
        String text = fill(template, "time", clock(seconds), "seconds", seconds, "hiders", players.activeHiders().size());
        if (timer.isPaused()) {
            text += HideAndSeekConfig.msg("bossBarPaused");
        }
        float progress = timer.progress();
        BossEvent.BossBarColor color;
        if (phase == Phase.HIDING) {
            color = BossEvent.BossBarColor.BLUE;
        } else if (progress > 0.5F) {
            color = BossEvent.BossBarColor.GREEN;
        } else if (progress > 0.2F) {
            color = BossEvent.BossBarColor.YELLOW;
        } else {
            color = BossEvent.BossBarColor.RED;
        }
        bossBar.update(legacy(text), progress, color);
    }

    // ================================================================== misc commands

    public Result setSeeker(ServerPlayer p) {
        if (phase != Phase.IDLE) {
            return Result.fail("A match is running. Stop it first.");
        }
        if (!HideAndSeekConfig.isEligible(p.gameMode.getGameModeForPlayer())) {
            return Result.fail(name(p) + " is in a game mode that cannot take part (Creative/Spectator).");
        }
        preselectedSeeker = p.getUUID();
        preselectedName = name(p);
        return Result.ok("Seeker set to " + name(p) + ". Use /hideandseek start to begin.");
    }

    public List<Component> statusLines() {
        List<Component> out = new ArrayList<>();
        out.add(legacy("&6&l=== Hide & Seek Status ==="));
        if (phase == Phase.IDLE) {
            out.add(legacy("&7Phase: &fNo match running"));
            if (preselectedName != null) {
                out.add(legacy("&7Preselected seeker: &f" + preselectedName));
            }
            return out;
        }
        String phaseText = phase == Phase.HIDING ? "Hiding countdown" : "Seeker released";
        out.add(legacy("&7Phase: &f" + phaseText + (timer.isPaused() ? " &c(PAUSED)" : "")));
        out.add(legacy("&7Seeker: &c" + players.nameOf(players.getSeeker())));
        out.add(legacy("&7Time left in this phase: &f" + clock(timer.getRemainingSeconds())));
        out.add(legacy("&7Hiders left: &a" + players.activeHiders().size() + " &8| &7Caught: &c" + players.caught().size()));
        out.add(legacy("&7Catch method: &f" + HideAndSeekConfig.catchMethod()));
        return out;
    }

    public List<Component> listLines() {
        List<Component> out = new ArrayList<>();
        out.add(legacy("&6&l=== Hide & Seek Players ==="));
        if (phase == Phase.IDLE) {
            out.add(legacy("&7No match is running."));
            return out;
        }
        out.add(legacy("&7Seeker: &c" + players.nameOf(players.getSeeker())));
        List<String> hiders = names(players.activeHiders());
        out.add(legacy("&7Hiders (" + hiders.size() + "): &a" + (hiders.isEmpty() ? "-" : String.join(", ", hiders))));
        List<String> caught = names(players.caught());
        out.add(legacy("&7Caught (" + caught.size() + "): &c" + (caught.isEmpty() ? "-" : String.join(", ", caught))));
        return out;
    }
}
