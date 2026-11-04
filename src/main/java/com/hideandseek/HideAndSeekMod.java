package com.hideandseek;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * Entry point. The mod is server-side only: it registers no blocks, items or network channels.
 * The only static state is the manager reference, which exists only while a server is running.
 */
@Mod(HideAndSeekMod.MOD_ID)
public class HideAndSeekMod {
    public static final String MOD_ID = "hideandseek";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static volatile HideAndSeekManager manager;

    private HideAndSeekEvents events;

    public HideAndSeekMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, HideAndSeekConfig.SPEC, "hideandseek-common.toml");
        MinecraftForge.EVENT_BUS.register(this);
    }

    /** Returns the manager of the running server, or null when no server is running. */
    public static HideAndSeekManager manager() {
        return manager;
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        HideAndSeekCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        shutdownCurrent();
        manager = new HideAndSeekManager(event.getServer());
        events = new HideAndSeekEvents(manager);
        MinecraftForge.EVENT_BUS.register(events);
        LOGGER.info("Hide And Seek ready.");
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        shutdownCurrent();
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        shutdownCurrent();
    }

    private void shutdownCurrent() {
        if (events != null) {
            MinecraftForge.EVENT_BUS.unregister(events);
            events = null;
        }
        if (manager != null) {
            manager.shutdown();
            manager = null;
        }
    }
}
