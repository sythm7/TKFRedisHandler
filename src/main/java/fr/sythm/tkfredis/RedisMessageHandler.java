package fr.sythm.tkfredis;

/**
 * This interface is used as a callback for the {@link RedisMessageListener} class.
 */
public interface RedisMessageHandler {

    /**
     * Allows you to specify an action to perform after receiving a message.
     *
     * @param channel The channel that the message comes from
     * @param message The message received
     */
    void processMessage(String channel, String message);

}
