package com.enoughisasgoodasafeast;

public class RabbitQueueFunctions {

    public static String exchangeForQueueName(String queueName) {
        return /*"exchange." + */queueName;
    }
}
