package com.enoughisasgoodasafeast;

public class RabbitQueueFunctions {

    public static String exchangeForQueueName(String queueName) {
        return "x." + queueName;
    }

    public static String retryForExchangeName(String exchangeName) {
        return "rt" + exchangeName;
    }
}
