package fun.xingwangzhe.ollamachat;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OllamaChatMod implements ModInitializer {
    public static final String MOD_ID = "ollamachat";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static MinecraftServer server;

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing OllamaChat Mod");
        
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            OllamaChatMod.server = server;
        });
    }

    public static MinecraftServer getServer() {
        return server;
    }
} 