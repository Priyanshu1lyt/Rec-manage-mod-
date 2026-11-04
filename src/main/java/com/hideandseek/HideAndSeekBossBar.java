package com.hideandseek;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

/** Thin wrapper around a vanilla boss bar (works for vanilla clients). */
public final class HideAndSeekBossBar {
    private ServerBossEvent event;

    public boolean exists() {
        return event != null;
    }

    public void create(Component name, BossEvent.BossBarColor color) {
        remove();
        event = new ServerBossEvent(name, color, BossEvent.BossBarOverlay.PROGRESS);
        event.setProgress(1.0F);
    }

    public void addPlayer(ServerPlayer player) {
        if (event != null) {
            event.addPlayer(player);
        }
    }

    public void removePlayer(ServerPlayer player) {
        if (event != null) {
            event.removePlayer(player);
        }
    }

    public void update(Component name, float progress, BossEvent.BossBarColor color) {
        if (event == null) {
            return;
        }
        event.setName(name);
        event.setProgress(Math.max(0.0F, Math.min(1.0F, progress)));
        event.setColor(color);
    }

    public void remove() {
        if (event != null) {
            event.removeAllPlayers();
            event = null;
        }
    }
}
