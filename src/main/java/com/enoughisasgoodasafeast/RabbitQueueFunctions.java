package com.enoughisasgoodasafeast;

import static com.enoughisasgoodasafeast.RabbitQueueConsumer.*;

public class RabbitQueueFunctions {

    public static String exchangeForQueueName(String queueName) {
        return queueName + EXCHANGE_SUFFIX;
    }

    public static String retryExchangeForQueueName(String queueName) {
        return queueName + RETRY_QUEUE_SUFFIX + EXCHANGE_SUFFIX;
    }

    public static String failQueueForQueue(String queueName) {
        return queueName + FAILED_QUEUE_SUFFIX;
    }


    public static String delayQueueForRoutingKey(String queueName, RetryDelayRoutingKey routingKey) {
        return queueName + RETRY_QUEUE_SUFFIX + routingKey.suffix();
    }
}
