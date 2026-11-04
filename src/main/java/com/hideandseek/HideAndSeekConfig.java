package com.hideandseek;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.loading.FMLPaths;

/** All gameplay settings. File: config/hideandseek-common.toml */
public final class HideAndSeekConfig {

    public enum CatchMethod { ATTACK, TAG, MANUAL }

    public static final ForgeConfigSpec SPEC;

    private static final Map<String, ForgeConfigSpec.ConfigValue<String>> MESSAGES = new LinkedHashMap<>();

    private static final ForgeConfigSpec.IntValue DEFAULT_MATCH_MINUTES;
    private static final ForgeConfigSpec.IntValue HIDING_SECONDS;
    private static final ForgeConfigSpec.IntValue MIN_PLAYERS;
    private static final ForgeConfigSpec.IntValue PERMISSION_LEVEL;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> ALLOWED_GAME_MODES;
    private static final ForgeConfigSpec.BooleanValue ALLOW_JOIN;
    private static final ForgeConfigSpec.BooleanValue ALLOW_LEAVE;
    private static final ForgeConfigSpec.BooleanValue RESTORE_MODES;
    private static final ForgeConfigSpec.BooleanValue PREVENT_DAMAGE;
    private static final ForgeConfigSpec.BooleanValue BLIND_SEEKER;

    private static final ForgeConfigSpec.EnumValue<CatchMethod> CATCH_METHOD;
    private static final ForgeConfigSpec.DoubleValue CATCH_DISTANCE;
    private static final ForgeConfigSpec.BooleanValue ELIM_SPECTATOR;
    private static final ForgeConfigSpec.BooleanValue ELIM_WATCH;

    private static final ForgeConfigSpec.BooleanValue COUNTDOWN_TITLE;
    private static final ForgeConfigSpec.BooleanValue BOSS_BAR;
    private static final ForgeConfigSpec.BooleanValue WARNINGS;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> WARNING_TIMES;
    private static final ForgeConfigSpec.IntValue FINAL_COUNTDOWN;
    private static final ForgeConfigSpec.BooleanValue CATCH_ANNOUNCE;
    private static final ForgeConfigSpec.BooleanValue VICTORY_ANNOUNCE;

    private static final ForgeConfigSpec.BooleanValue SOUNDS_ENABLED;
    private static final ForgeConfigSpec.BooleanValue COUNTDOWN_SOUND;
    private static final ForgeConfigSpec.DoubleValue SOUND_VOLUME;
    private static final ForgeConfigSpec.ConfigValue<String> SOUND_COUNTDOWN;
    private static final ForgeConfigSpec.ConfigValue<String> SOUND_RELEASE;
    private static final ForgeConfigSpec.ConfigValue<String> SOUND_CATCH;
    private static final ForgeConfigSpec.ConfigValue<String> SOUND_VICTORY;
    private static final ForgeConfigSpec.ConfigValue<String> SOUND_WARNING;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("General match settings").push("general");
        DEFAULT_MATCH_MINUTES = b.comment("Default match duration in minutes (used when /hideandseek start has no minutes argument).")
                .defineInRange("defaultMatchMinutes", 5, 1, 240);
        HIDING_SECONDS = b.comment("Length of the hiding countdown in seconds. The seeker is frozen during it.")
                .defineInRange("hidingCountdownSeconds", 15, 3, 300);
        MIN_PLAYERS = b.comment("Minimum number of participants (seeker + hiders) needed to start a match.")
                .defineInRange("minimumPlayers", 2, 2, 100);
        ALLOWED_GAME_MODES = b.comment("Game modes allowed to take part. Only SURVIVAL and ADVENTURE are valid.",
                        "CREATIVE and SPECTATOR players are ALWAYS excluded.")
                .defineList("allowedGameModes", Arrays.asList("SURVIVAL", "ADVENTURE"),
                        o -> o instanceof String s && (s.equalsIgnoreCase("SURVIVAL") || s.equalsIgnoreCase("ADVENTURE")));
        PERMISSION_LEVEL = b.comment("Permission level (0-4) needed for admin commands (start, stop, catch, ...). 2 = operators.")
                .defineInRange("commandPermissionLevel", 2, 0, 4);
        ALLOW_JOIN = b.comment("Allow eligible players to use /hideandseek join after a match has started.")
                .define("allowJoinDuringMatch", false);
        ALLOW_LEAVE = b.comment("Allow hiders to use /hideandseek leave during a match.")
                .define("allowLeaveDuringMatch", true);
        RESTORE_MODES = b.comment("Restore the previous game mode of eliminated hiders when the match ends.",
                        "If false, eliminated spectators stay in spectator mode.")
                .define("restorePreviousGameModes", true);
        PREVENT_DAMAGE = b.comment("Cancel all player-vs-player damage involving participants (the seeker's tag is handled separately).")
                .define("preventParticipantDamage", true);
        BLIND_SEEKER = b.comment("Give the seeker Blindness during the hiding countdown.")
                .define("blindSeekerWhileHiding", true);
        b.pop();

        b.comment("Catch detection and elimination").push("catching");
        CATCH_METHOD = b.comment("ATTACK = seeker hits a hider (most reliable, default).",
                        "TAG    = seeker comes within catchDistance blocks of a hider.",
                        "MANUAL = only /hideandseek catch <player> eliminates hiders.",
                        "/hideandseek catch always works for admins regardless of this setting.")
                .defineEnum("catchMethod", CatchMethod.ATTACK);
        CATCH_DISTANCE = b.comment("Distance in blocks for the TAG catch method.")
                .defineInRange("catchDistance", 2.0D, 0.5D, 10.0D);
        ELIM_SPECTATOR = b.comment("Switch caught hiders to spectator mode.")
                .define("eliminatedBecomeSpectators", true);
        ELIM_WATCH = b.comment("Caught hiders keep seeing the boss bar and match titles/announcements.")
                .define("eliminatedCanWatch", true);
        b.pop();

        b.comment("Announcements").push("announcements");
        COUNTDOWN_TITLE = b.define("countdownTitleEnabled", true);
        BOSS_BAR = b.define("bossBarEnabled", true);
        WARNINGS = b.comment("Time remaining warnings and the final countdown.").define("matchWarningsEnabled", true);
        WARNING_TIMES = b.comment("Seconds remaining at which a warning is announced.")
                .defineList("warningTimesSeconds", Arrays.asList(180, 120, 60, 30, 10), o -> o instanceof Integer i && i > 0);
        FINAL_COUNTDOWN = b.comment("Show a big countdown during the last N seconds (0 disables).")
                .defineInRange("finalCountdownSeconds", 5, 0, 30);
        CATCH_ANNOUNCE = b.define("catchAnnouncementEnabled", true);
        VICTORY_ANNOUNCE = b.define("victoryAnnouncementEnabled", true);
        b.pop();

        b.comment("Sounds. Any sound id works, including resource pack sounds.").push("sounds");
        SOUNDS_ENABLED = b.comment("Master switch for all sounds except the countdown sound.").define("soundsEnabled", true);
        COUNTDOWN_SOUND = b.define("countdownSoundEnabled", true);
        SOUND_VOLUME = b.defineInRange("volume", 1.0D, 0.0D, 4.0D);
        SOUND_COUNTDOWN = b.define("countdown", "minecraft:block.note_block.hat");
        SOUND_RELEASE = b.define("release", "minecraft:entity.ender_dragon.growl");
        SOUND_CATCH = b.define("catch", "minecraft:entity.lightning_bolt.thunder");
        SOUND_VICTORY = b.define("victory", "minecraft:ui.toast.challenge_complete");
        SOUND_WARNING = b.define("warning", "minecraft:block.note_block.pling");
        b.pop();

        b.comment("Messages. Use & colour codes (&c, &l, &r ...).",
                "Placeholders: %player% %seeker% %seconds% %time% %remaining% %hiders% %caught% %survivors% %players% %reason%")
                .push("messages");
        message(b, "prefix", "&6&l[Hide & Seek] &r");
        message(b, "separator", "&6&m                                        ");
        message(b, "startChat", "&e&lHIDE & SEEK IS STARTING!");
        message(b, "hideChat", "&e&lYOU HAVE &c&l%seconds% SECONDS &e&lTO HIDE!");
        message(b, "roleSeeker", "&c&lYou are the SEEKER! &7You are frozen until the countdown ends.");
        message(b, "roleHider", "&a&lYou are a HIDER! &7Find a hiding spot before the seeker is released!");
        message(b, "hideCountdownSubtitle", "&f&lSECONDS TO HIDE!");
        message(b, "releaseTitle", "&c&lSEEKER RELEASED!");
        message(b, "releaseSubtitle", "&7The hunt begins...");
        message(b, "releaseChat", "&c&lSEEKER RELEASED! &7%seeker% is hunting you down!");
        message(b, "caughtTitle", "&c&lPLAYER CAUGHT!");
        message(b, "caughtSubtitle", "&f%player% &7has been caught by the seeker!");
        message(b, "caughtChat", "&c&l%player% HAS BEEN CAUGHT! &7(%remaining% hider(s) left)");
        message(b, "uncaughtChat", "&a%player% was returned to the game. &7(%remaining% hider(s) left)");
        message(b, "warningChat", "&e&l%time% REMAINING!");
        message(b, "timeUpChat", "&c&lTIME'S UP!");
        message(b, "allCaughtChat", "&c&lALL HIDERS HAVE BEEN CAUGHT!");
        message(b, "seekerWinsTitle", "&c&lSEEKER WINS!");
        message(b, "hidersWinTitle", "&a&lHIDERS WIN!");
        message(b, "allCaughtSubtitle", "&7All hiders have been caught!");
        message(b, "timeUpSubtitle", "&c&lTIME'S UP! &7%survivors% hider(s) survived.");
        message(b, "resultSummary", "&7Seeker: &f%seeker% &8| &7Caught: &c%caught% &8| &7Survivors: &a%survivors%");
        message(b, "survivorList", "&7Surviving hiders: &a%players%");
        message(b, "bossBarHiding", "&b&lHIDE! &f%time%");
        message(b, "bossBarRunning", "&e&lTime Left: &f%time% &8| &a%hiders% &7hider(s) left");
        message(b, "bossBarPaused", " &c&l[PAUSED]");
        message(b, "stoppedChat", "&cThe match was stopped.");
        message(b, "resetChat", "&cHide & Seek has been reset.");
        message(b, "cancelledChat", "&cMatch cancelled: &f%reason%");
        message(b, "pausedChat", "&e&lThe match is PAUSED.");
        message(b, "resumedChat", "&a&lThe match has RESUMED.");
        message(b, "joinedChat", "&a%player% joined the match as a hider.");
        message(b, "leftChat", "&7%player% left the match. &8(%remaining% hider(s) left)");
        b.pop();

        SPEC = b.build();
    }

    private HideAndSeekConfig() {}

    private static void message(ForgeConfigSpec.Builder b, String key, String def) {
        MESSAGES.put(key, b.define(key, def));
    }

    // ------------------------------------------------------------------ getters

    public static String msg(String key) {
        ForgeConfigSpec.ConfigValue<String> v = MESSAGES.get(key);
        return v == null ? key : v.get();
    }

    public static String prefix() { return msg("prefix"); }
    public static int defaultMatchMinutes() { return DEFAULT_MATCH_MINUTES.get(); }
    public static int hidingSeconds() { return HIDING_SECONDS.get(); }
    public static int minPlayers() { return MIN_PLAYERS.get(); }
    public static boolean allowJoin() { return ALLOW_JOIN.get(); }
    public static boolean allowLeave() { return ALLOW_LEAVE.get(); }
    public static boolean restoreGameModes() { return RESTORE_MODES.get(); }
    public static boolean preventParticipantDamage() { return PREVENT_DAMAGE.get(); }
    public static boolean blindSeeker() { return BLIND_SEEKER.get(); }
    public static CatchMethod catchMethod() { return CATCH_METHOD.get(); }
    public static double catchDistance() { return CATCH_DISTANCE.get(); }
    public static boolean eliminatedBecomeSpectators() { return ELIM_SPECTATOR.get(); }
    public static boolean eliminatedCanWatch() { return ELIM_WATCH.get(); }
    public static boolean countdownTitleEnabled() { return COUNTDOWN_TITLE.get(); }
    public static boolean bossBarEnabled() { return BOSS_BAR.get(); }
    public static boolean warningsEnabled() { return WARNINGS.get(); }
    public static int finalCountdownSeconds() { return FINAL_COUNTDOWN.get(); }
    public static boolean catchAnnouncementEnabled() { return CATCH_ANNOUNCE.get(); }
    public static boolean victoryAnnouncementEnabled() { return VICTORY_ANNOUNCE.get(); }
    public static boolean soundsEnabled() { return SOUNDS_ENABLED.get(); }
    public static boolean countdownSoundEnabled() { return COUNTDOWN_SOUND.get(); }
    public static float soundVolume() { return SOUND_VOLUME.get().floatValue(); }
    public static String soundCountdown() { return SOUND_COUNTDOWN.get(); }
    public static String soundRelease() { return SOUND_RELEASE.get(); }
    public static String soundCatch() { return SOUND_CATCH.get(); }
    public static String soundVictory() { return SOUND_VICTORY.get(); }
    public static String soundWarning() { return SOUND_WARNING.get(); }

    /** Permission level for admin commands. Falls back to 2 if the config is not loaded yet. */
    public static int permissionLevel() {
        try {
            return PERMISSION_LEVEL.get();
        } catch (RuntimeException e) {
            return 2;
        }
    }

    public static Set<Integer> warningTimes() {
        Set<Integer> out = new HashSet<>();
        for (Object o : WARNING_TIMES.get()) {
            if (o instanceof Number n) {
                out.add(n.intValue());
            }
        }
        return out;
    }

    public static Set<String> allowedModes() {
        Set<String> out = new HashSet<>();
        for (String s : ALLOWED_GAME_MODES.get()) {
            out.add(s.toUpperCase(Locale.ROOT));
        }
        return out;
    }

    /** Creative and Spectator are never eligible, whatever the config says. */
    public static boolean isEligible(GameType mode) {
        if (mode == null || mode == GameType.CREATIVE || mode == GameType.SPECTATOR) {
            return false;
        }
        return allowedModes().contains(mode.getName().toUpperCase(Locale.ROOT));
    }

    // ------------------------------------------------------------------ reload / describe

    /** Re-reads the toml file from disk. Returns null on success or an error message. */
    public static String reloadFromDisk() {
        Path path = FMLPaths.CONFIGDIR.get().resolve("hideandseek-common.toml");
        if (!Files.exists(path)) {
            return "Config file not found: " + path;
        }
        try {
            CommentedFileConfig file = CommentedFileConfig.builder(path).sync().build();
            file.load();
            SPEC.acceptConfig(file);
            return null;
        } catch (RuntimeException e) {
            return String.valueOf(e.getMessage());
        }
    }

    public static List<String> describe() {
        List<String> l = new ArrayList<>();
        l.add("&6&l=== Hide & Seek Config &7(config/hideandseek-common.toml) ===");
        l.add("&7Default match: &f" + defaultMatchMinutes() + " min &8| &7Hiding countdown: &f" + hidingSeconds() + " s");
        l.add("&7Catch method: &f" + catchMethod() + " &8| &7Catch distance: &f" + catchDistance());
        l.add("&7Allowed game modes: &f" + allowedModes() + " &8(Creative/Spectator always excluded)");
        l.add("&7Minimum players: &f" + minPlayers() + " &8| &7Command permission level: &f" + permissionLevel());
        l.add("&7Eliminated -> spectator: &f" + eliminatedBecomeSpectators() + " &8| &7can watch: &f" + eliminatedCanWatch());
        l.add("&7Join during match: &f" + allowJoin() + " &8| &7Leave during match: &f" + allowLeave());
        l.add("&7Restore game modes: &f" + restoreGameModes() + " &8| &7Block participant PvP: &f" + preventParticipantDamage());
        l.add("&7Boss bar: &f" + bossBarEnabled() + " &8| &7Countdown title: &f" + countdownTitleEnabled()
                + " &8| &7Warnings: &f" + warningsEnabled() + " " + warningTimes());
        l.add("&7Catch announce: &f" + catchAnnouncementEnabled() + " &8| &7Victory announce: &f" + victoryAnnouncementEnabled()
                + " &8| &7Sounds: &f" + soundsEnabled() + " &8| &7Countdown sound: &f" + countdownSoundEnabled());
        return l;
    }
}
