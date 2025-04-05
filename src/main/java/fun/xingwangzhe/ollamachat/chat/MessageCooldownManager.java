package fun.xingwangzhe.ollamachat.chat;

import fun.xingwangzhe.ollamachat.config.ModConfig;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MessageCooldownManager {
    private final ModConfig config;
    private final Map<UUID, Long> lastMessageTimes;

    public MessageCooldownManager(ModConfig config) {
        this.config = config;
        this.lastMessageTimes = new ConcurrentHashMap<>();
    }

    public boolean canSendMessage(UUID playerId) {
        Long lastTime = lastMessageTimes.get(playerId);
        if (lastTime == null) {
            return true;
        }

        long currentTime = System.currentTimeMillis() / 1000;
        return (currentTime - lastTime) >= config.messageCooldown;
    }

    public int getRemainingCooldown(UUID playerId) {
        Long lastTime = lastMessageTimes.get(playerId);
        if (lastTime == null) {
            return 0;
        }

        long currentTime = System.currentTimeMillis() / 1000;
        int remaining = (int) (config.messageCooldown - (currentTime - lastTime));
        return Math.max(0, remaining);
    }

    public void updateLastMessageTime(UUID playerId) {
        lastMessageTimes.put(playerId, System.currentTimeMillis() / 1000);
    }

    public String getCooldownMessage(UUID playerId) {
        int remaining = getRemainingCooldown(playerId);
        return String.format(config.cooldownMessage, remaining);
    }
} 