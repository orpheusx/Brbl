package com.enoughisasgoodasafeast;

import static com.enoughisasgoodasafeast.RabbitQueueConsumer.FAILED_QUEUE_SUFFIX;

public class RabbitQueueFunctions {

    public static String exchangeForQueueName(String queueName) {
        return queueName + "-x";
    }

    public static String retryForExchangeName(String exchangeName) {
        return exchangeName + "r";
    }

    public static String failQueueForQueue(String queueName) {
        return queueName + FAILED_QUEUE_SUFFIX;
    }

    public static String delayQueueForRoutingKey(String baseName, RetryDelayRoutingKey routingKey) {
        return baseName + routingKey.suffix();
    }
}
