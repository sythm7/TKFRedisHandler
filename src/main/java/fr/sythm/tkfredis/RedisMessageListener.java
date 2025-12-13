package fr.sythm.tkfredis;

import io.lettuce.core.pubsub.RedisPubSubAdapter;

/**
 * This class allows the user to create a listener that will be triggered by a subscriber as long as he receives a message.
 */
public class RedisMessageListener extends RedisPubSubAdapter<String, String> {

    private final RedisMessageHandler redisMessageHandler;

    /**
     * Allows you to specify an action to be performed when a message is received.
     * @param redisMessageHandler The action to be performed
     */
    public RedisMessageListener(RedisMessageHandler redisMessageHandler) {
        this.redisMessageHandler = redisMessageHandler;
    }

    /**
     * Performs the action you specified in {@link #redisMessageHandler}.
     * @param channel The channel that the message comes from
     * @param message The message received
     */
    @Override
    public void message(String channel, String message) {
        this.redisMessageHandler.processMessage(channel, message);
    }
}
