package com.enoughisasgoodasafeast;

import com.enoughisasgoodasafeast.operator.MessageProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.TimeoutException;

public class Sndr implements MessageProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(Sndr.class);

    public QueueConsumer queueConsumer;
    HttpMTHandler httpMtHandler;

    //    public void init() {
            // This is the JDK provided client
            //try (HttpClient client = HttpClient.newBuilder()
            //        .version(HttpClient.Version.HTTP_2)
            //        .connectTimeout(Duration.ofSeconds(1))
            //        .build()) {
            //    this.client = client;
            //}
    //    }

    public void init() {
        LOG.info("Initializing SNDR");

        try {
            final Properties properties = ConfigLoader.readConfig("sndr.properties");
            httpMtHandler = (HttpMTHandler) HttpMTHandler.newHandler(properties);
            queueConsumer = RabbitQueueConsumer.createQueueConsumer(
                    properties, this);
        } catch (IOException | TimeoutException e) {
            throw new RuntimeException(e);
        }

        //        client = WebClient.builder()
        //                //.addService(WebClientTracing.create())
        //                .baseUri(uri)
        //                .build();
    }

    @Override
    public boolean process(Message message) {
        return httpMtHandler.handle(message.text());
    }

    public static void main(String[] args) {
        Sndr sndr = new Sndr();
        sndr.init();

        //        HttpClientRequest fooReq = sndr.client.post().path("/mtReceive");//.peek(System.out::println).await();
        //        ClientResponseTyped<String> fooRes = fooReq.request(String.class);
        //        LOG.info("/foo response status: {}", fooRes.status());
        //        LOG.info("/foo response content: {}", fooRes.entity());
        //
        //        HttpClientRequest enqueueReq = sndr.client.post(ENQUEUE_ENDPOINT);
        //        HttpClientResponse enqueueRes = enqueueReq.
        //                submit("{\"id\": \"123\",\"msg\": \"this is message 0\"}");
        //        LOG.info("/enqueue response status: {}",enqueueRes.status());
        //        LOG.info("/enqueue response content: {}", enqueueRes.entity().as(String.class));
        //
        //        HttpClientRequest healthReq = sndr.client.get().path(HEALTH_ENDPOINT);
        //        ClientResponseTyped<String> healthRes = healthReq.request(String.class);
        //        LOG.info("/health response status: {}", healthRes.status());
        //        LOG.info("No response content expected.");
    }
}
