package fun.xingwangzhe.ollamachat.chat;

import fun.xingwangzhe.ollamachat.config.ModConfig;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MessageCooldownManager {
    private final ModConfig config;
    private final Map<UUID, Long> lastMessageTimes;
    private long lastCleanupTime;
    private static final long CLEANUP_INTERVAL = 900000; // 15 minutes in milliseconds
    private static final long INACTIVE_THRESHOLD = 3600000; // 1 hour in milliseconds

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
        return (System.currentTimeMillis() / 1000 - lastTime) >= config.messageCooldown;
    }

    public int getRemainingCooldown(UUID playerId) {
        cleanupInactivePlayers();
        Long lastTime = lastMessageTimes.get(playerId);
        if (lastTime == null) {
            return 0;
        }
        return Math.max(0, (int) (config.messageCooldown - (System.currentTimeMillis() / 1000 - lastTime)));
    }

    public void updateLastMessageTime(UUID playerId) {
        cleanupInactivePlayers();
        lastMessageTimes.put(playerId, System.currentTimeMillis() / 1000);
    }

    public String getCooldownMessage(UUID playerId) {
        return String.format(config.cooldownMessage, getRemainingCooldown(playerId));
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