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
    private static boolean isClientSide = false;

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing OllamaChat Mod");
        
        // Register server-side initialization
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            OllamaChatMod.server = server;
            OllamaChatMod.isClientSide = false;
        });
    }

    public static MinecraftServer getServer() {
        return server;
    }
    
    public static boolean isClientSide() {
        return isClientSide;
    }
    
    public static void setClientSide(boolean clientSide) {
        isClientSide = clientSide;
    }
} 