package fr.sythm.tkfredis;
import com.google.gson.Gson;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisConnectionStateListener;
import io.lettuce.core.RedisURI;
import io.lettuce.core.pubsub.RedisPubSubListener;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands;
import io.lettuce.core.RedisChannelHandler;
import org.jetbrains.annotations.NotNull;
import java.net.SocketAddress;

/**
 * This class allows the user to interact with a Redis server as a Publisher/Subscriber.
 * More operations will be added in the future if needed.
 */
public class RedisAccess {

    private static RedisAccess instance;

    private final RedisClient redisClient;

    private final RedisPubSubAsyncCommands<String, String> asyncCommands;

    private final StatefulRedisPubSubConnection<String, String> connection;

    /**
     * Establish connection with the Redis server as a subscriber. {@link #connect} has to be called before anything else.
     * @param address Redis server IP or hostname
     * @param port Port used by the Redis server
     * @param password Password to access the Redis server
     * @param handler The action that needs to be performed following a message reception
     *
     * @param subscribedChannels Channels that TKF Game servers will receive messages from.
     */
    public static void connect(String address, int port, String password, @NotNull RedisMessageHandler handler, @NotNull String... subscribedChannels) {
        if(instance == null) {
            instance = new RedisAccess(address, port, password, handler, subscribedChannels);
        }
        else {
            throw new IllegalStateException("You can't connect twice to Redis.");
        }
    }

    /**
     * Establish connection with the Redis server. {@link #connect} has to be called before anything else.
     * @param address Redis server IP or hostname
     * @param port Port used by the Redis server
     * @param password Password to access the Redis server
     */
    public static void connect(String address, int port, String password) {
        connect(address, port, password, (s1, s2) -> {});
    }

    /**
     * Allows you to publish a Java object through the Redis server, which will redistribute it to all subscribers.
     * Throws an exception if you call this method without having used {@link #connect} first.
     * @param javaObject The Java object to send
     * @param channel The channel subscribers will receive the message from
     * @param <T> The type of the object to be sent
     */
    public static <T> void publish(T javaObject, String channel) {
        if (instance == null) {
            throw new IllegalStateException("RedisAccess is not connected. Call connect() first.");
        }
        instance.publish_(javaObject, channel);
    }

    /**
     * This needs to be called everytime the Redis connection has to be closed, for example on a server reload.
     */
    public static void shutdown() {
        if(instance == null)
            return;

        instance.connection.close();
        instance.redisClient.close();
        instance = null;
    }

    /* ---------------------------------------
      Private methods, internal to the class.
     */

    /**
     * Initializes the connection in Publish/Subscribe with the Redis server.
     * The user doesn't have to deal with this as it's called by the static method {@link #connect} itself.
     *
     * @param address Redis server IP or hostname
     * @param port Port used by the Redis server
     * @param password Password to access the Redis server
     * @param handler The action that needs to be performed following a message reception
     * @param subscribedChannels Channels that TKF Game servers will receive messages from.
     *
     */
    private RedisAccess(String address, int port, String password, @NotNull RedisMessageHandler handler, String @NotNull ... subscribedChannels) {
        RedisURI redisURI = RedisURI.Builder.redis(address)
                .withPort(port)
                .withPassword(password)
                .build();

        // Init connection, from the given address, port and password, and then tell Redis that all operations will be asynchronous.
        this.redisClient = RedisClient.create(redisURI);
        this.redisClient.setOptions(ClientOptions.builder()
                .autoReconnect(true)
                .build());

        this.redisClient.addListener(new RedisConnectionStateListener() {
            @Override
            public void onRedisConnected(RedisChannelHandler<?, ?> connection, SocketAddress socketAddress) {
                RedisConnectionStateListener.super.onRedisConnected(connection, socketAddress);
                System.out.println("Successfully established connection with Redis.");
            }

            @Override
            public void onRedisDisconnected(RedisChannelHandler<?, ?> connection) {
                RedisConnectionStateListener.super.onRedisDisconnected(connection);
                System.out.println("Redis has been disconnected.");
            }

            @Override
            public void onRedisExceptionCaught(RedisChannelHandler<?, ?> connection, Throwable cause) {
                RedisConnectionStateListener.super.onRedisExceptionCaught(connection, cause);
                System.err.println("An error has occurred with Redis : " + cause);
            }
        });

        this.connection = this.redisClient.connectPubSub();

        this.asyncCommands = connection.async();

        // As this API will be executed by both Lobby and TKF Game servers, we will need in the case of Game servers to be able to listen
        // to incoming messages from the Lobby server (for preparing the game or start the game for example)
        if(subscribedChannels.length > 0) {
            this.subscribeRedis_(handler, subscribedChannels);
        }
    }

    /**
     * Same as {@link #publish} but not callable by the user.
     *
     * @param javaObject The Java object to send
     * @param channel The channel subscribers will receive the message from
     * @param <T> The type of the object to be sent
     */
    private <T> void publish_(T javaObject, String channel) {
        //Transform the object into a readable JSON sequence, and then publish it via the redis server
        Gson gson = new Gson();
        String json = gson.toJson(javaObject);
        this.asyncCommands.publish(channel, json);
    }

    /**
     * Allows to listen for incoming messages. Not callable by the user.
     *
     * @param handler The action that needs to be performed
     * @param subscribedChannels The list of channels you need to subscribe to
     */
    private void subscribeRedis_(@NotNull RedisMessageHandler handler, String @NotNull ... subscribedChannels) {
        this.addMessageHandler_(handler);
        this.asyncCommands.subscribe(subscribedChannels);
    }

    /**
     * Specifies the action performed when a message is received by a subscriber. Not callable by the user.
     * @param redisMessageHandler The action that needs to be performed
     */
    private void addMessageHandler_(RedisMessageHandler redisMessageHandler) {
        RedisPubSubListener<String, String> redisListener = new RedisMessageListener(redisMessageHandler);
        this.connection.addListener(redisListener);
    }

}