package com.hideandseek;

import java.util.Collection;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.registries.ForgeRegistries;

/** Titles, chat, action bar and sounds. Uses only vanilla packets, so vanilla clients see everything. */
public final class HideAndSeekAnnouncements {
    private final MinecraftServer server;

    public HideAndSeekAnnouncements(MinecraftServer server) {
        this.server = server;
    }

    // ------------------------------------------------------------------ text helpers

    /** Replaces %key% placeholders. Arguments are key, value, key, value ... */
    public static String fill(String template, Object... kv) {
        String out = template;
        for (int i = 0; i + 1 < kv.length; i += 2) {
            out = out.replace("%" + kv[i] + "%", String.valueOf(kv[i + 1]));
        }
        return out;
    }

    /** Converts &-colour-coded text into a Component. */
    public static MutableComponent legacy(String text) {
        MutableComponent root = Component.empty();
        Style style = Style.EMPTY;
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c == '&' || c == '\u00a7') && i + 1 < text.length()) {
                ChatFormatting f = ChatFormatting.getByCode(text.charAt(i + 1));
                if (f != null) {
                    flush(root, buf, style);
                    if (f == ChatFormatting.RESET) {
                        style = Style.EMPTY;
                    } else if (f.isColor()) {
                        style = Style.EMPTY.withColor(f);
                    } else {
                        style = style.applyFormat(f);
                    }
                    i++;
                    continue;
                }
            }
            buf.append(c);
        }
        flush(root, buf, style);
        return root;
    }

    private static void flush(MutableComponent root, StringBuilder buf, Style style) {
        if (buf.length() > 0) {
            root.append(Component.literal(buf.toString()).withStyle(style));
            buf.setLength(0);
        }
    }

    public MutableComponent prefixed(String text) {
        return legacy(HideAndSeekConfig.prefix()).append(legacy(text));
    }

    public static String clock(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    public static String words(int seconds) {
        if (seconds >= 60 && seconds % 60 == 0) {
            int m = seconds / 60;
            return m + (m == 1 ? " minute" : " minutes");
        }
        return seconds + (seconds == 1 ? " second" : " seconds");
    }

    // ------------------------------------------------------------------ delivery

    public void broadcast(Component message) {
        server.getPlayerList().broadcastSystemMessage(message, false);
    }

    public void chat(Collection<ServerPlayer> targets, Component message) {
        for (ServerPlayer p : targets) {
            p.sendSystemMessage(message);
        }
    }

    public void actionBar(Collection<ServerPlayer> targets, Component message) {
        for (ServerPlayer p : targets) {
            p.sendSystemMessage(message, true);
        }
    }

    public void title(Collection<ServerPlayer> targets, Component title, Component subtitle,
                      int fadeIn, int stay, int fadeOut) {
        for (ServerPlayer p : targets) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
            p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            p.connection.send(new ClientboundSetTitleTextPacket(title));
        }
    }

    /** Plays a configurable sound for the targets (respects the soundsEnabled switch). */
    public void sound(Collection<ServerPlayer> targets, String soundId, float pitch) {
        if (HideAndSeekConfig.soundsEnabled()) {
            play(targets, soundId, pitch);
        }
    }

    /** Plays the countdown sound (respects the countdownSoundEnabled switch). */
    public void countdownSound(Collection<ServerPlayer> targets, float pitch) {
        if (HideAndSeekConfig.countdownSoundEnabled()) {
            play(targets, HideAndSeekConfig.soundCountdown(), pitch);
        }
    }

    private void play(Collection<ServerPlayer> targets, String soundId, float pitch) {
        ResourceLocation id = ResourceLocation.tryParse(soundId);
        if (id == null) {
            return;
        }
        SoundEvent event = ForgeRegistries.SOUND_EVENTS.getValue(id);
        if (event == null) {
            event = SoundEvent.createVariableRangeEvent(id);
        }
        float volume = HideAndSeekConfig.soundVolume();
        for (ServerPlayer p : targets) {
            p.connection.send(new ClientboundSoundPacket(Holder.direct(event), SoundSource.MASTER,
                    p.getX(), p.getY(), p.getZ(), volume, pitch, p.getRandom().nextLong()));
        }
    }
}
