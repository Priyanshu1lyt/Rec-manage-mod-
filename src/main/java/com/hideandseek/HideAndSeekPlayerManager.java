package com.hideandseek;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/** Per-match player bookkeeping. Everything is keyed by UUID and wiped by {@link #clear()}. */
public final class HideAndSeekPlayerManager {
    private UUID seeker;
    private final Set<UUID> hiders = new LinkedHashSet<>();
    private final Set<UUID> caught = new LinkedHashSet<>();
    private final Set<UUID> departed = new HashSet<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final Map<UUID, GameType> previousModes = new HashMap<>();

    public void clear() {
        seeker = null;
        hiders.clear();
        caught.clear();
        departed.clear();
        names.clear();
        previousModes.clear();
    }

    private void remember(ServerPlayer p) {
        names.put(p.getUUID(), p.getGameProfile().getName());
    }

    public void setSeeker(ServerPlayer p) {
        seeker = p.getUUID();
        remember(p);
    }

    public void addHider(ServerPlayer p) {
        hiders.add(p.getUUID());
        departed.remove(p.getUUID());
        remember(p);
    }

    public UUID getSeeker() { return seeker; }
    public boolean isSeeker(UUID id) { return id != null && id.equals(seeker); }
    public boolean isActiveHider(UUID id) { return id != null && hiders.contains(id); }
    public boolean isCaught(UUID id) { return id != null && caught.contains(id); }
    public boolean isParticipant(UUID id) { return isSeeker(id) || isActiveHider(id); }

    public boolean removeHider(UUID id) { return hiders.remove(id); }

    /** Moves an active hider to the caught list. Returns false if already caught / not a hider. */
    public boolean markCaught(UUID id) {
        if (!hiders.remove(id)) {
            return false;
        }
        caught.add(id);
        return true;
    }

    public boolean unCatch(UUID id) {
        if (!caught.remove(id)) {
            return false;
        }
        hiders.add(id);
        departed.remove(id);
        return true;
    }

    public List<UUID> activeHiders() { return new ArrayList<>(hiders); }
    public List<UUID> caught() { return new ArrayList<>(caught); }

    public void markDeparted(UUID id) { departed.add(id); }
    public boolean isDeparted(UUID id) { return departed.contains(id); }

    public String nameOf(UUID id) {
        String n = names.get(id);
        return n == null ? "Unknown" : n;
    }

    public void rememberMode(UUID id, GameType type) { previousModes.putIfAbsent(id, type); }
    public GameType takeMode(UUID id) { return previousModes.remove(id); }
    public void forgetMode(UUID id) { previousModes.remove(id); }
    public List<UUID> modeHolders() { return new ArrayList<>(previousModes.keySet()); }
}
