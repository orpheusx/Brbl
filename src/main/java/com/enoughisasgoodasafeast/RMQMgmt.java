package com.enoughisasgoodasafeast;

import com.rabbitmq.http.client.Client;
import com.rabbitmq.http.client.domain.ExchangeInfo;
import com.rabbitmq.http.client.domain.QueueInfo;
import com.rabbitmq.http.client.domain.VhostInfo;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.List;

public class RMQMgmt {
    static void main() throws MalformedURLException, URISyntaxException {
        // Example using the RabbitMQ HTTP Client API
        Client client = new Client("http://localhost:15672");

        // Get details on active virtual hosts
        List<VhostInfo> vhosts = client.getVhosts();

        // List all queues and their configurations in a specific vhost
        List<QueueInfo> queues = client.getQueues("/");
        for (QueueInfo q : queues) {
            System.out.println("Queue: " + q.getName() + " | Durable: " + q.isDurable());
        }

        final List<ExchangeInfo> exchanges = client.getExchanges();
        for (ExchangeInfo exchange : exchanges) {
            System.out.println("Exchange: " + exchange.getName());
        }

    }
}
