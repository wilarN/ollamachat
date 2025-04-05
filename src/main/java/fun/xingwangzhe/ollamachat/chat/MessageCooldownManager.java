package fun.xingwangzhe.ollamachat.chat;

import fun.xingwangzhe.ollamachat.config.ModConfig;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MessageCooldownManager {
    private final ModConfig config;
    private final Map<UUID, Long> lastMessageTimes;
    private long lastCleanupTime;
    private static final long CLEANUP_INTERVAL = 3600000; // 1 hour in milliseconds
    private static final long INACTIVE_THRESHOLD = 86400000; // 24 hours in milliseconds

    public MessageCooldownManager(ModConfig config) {
        this.config = config;
        this.lastMessageTimes = new ConcurrentHashMap<>();
        this.lastCleanupTime = System.currentTimeMillis();
    }

    public boolean canSendMessage(UUID playerId) {
        cleanupInactivePlayers();
        Long lastTime = lastMessageTimes.get(playerId);
        if (lastTime == null) {
            return true;
        }

        long currentTime = System.currentTimeMillis() / 1000;
        return (currentTime - lastTime) >= config.messageCooldown;
    }

    public int getRemainingCooldown(UUID playerId) {
        cleanupInactivePlayers();
        Long lastTime = lastMessageTimes.get(playerId);
        if (lastTime == null) {
            return 0;
        }

        long currentTime = System.currentTimeMillis() / 1000;
        int remaining = (int) (config.messageCooldown - (currentTime - lastTime));
        return Math.max(0, remaining);
    }

    public void updateLastMessageTime(UUID playerId) {
        cleanupInactivePlayers();
        lastMessageTimes.put(playerId, System.currentTimeMillis() / 1000);
    }

    public String getCooldownMessage(UUID playerId) {
        int remaining = getRemainingCooldown(playerId);
        return String.format(config.cooldownMessage, remaining);
    }
    
    /**
     * Removes inactive players from the cooldown map to prevent memory leaks
     */
    private void cleanupInactivePlayers() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCleanupTime > CLEANUP_INTERVAL) {
            lastCleanupTime = currentTime;
            
            long threshold = currentTime / 1000 - INACTIVE_THRESHOLD / 1000;
            lastMessageTimes.entrySet().removeIf(entry -> entry.getValue() < threshold);
        }
    }
} 