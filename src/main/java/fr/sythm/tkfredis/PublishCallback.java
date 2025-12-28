package fr.sythm.tkfredis;

/**
 * Allows to specify an action that will be executed as soon as a Redis message publication is completed
 */
public interface PublishCallback {

    /**
     * Specifies the action that will be executed as soon as a Redis message publication is completed
     */
    void execute();

}
