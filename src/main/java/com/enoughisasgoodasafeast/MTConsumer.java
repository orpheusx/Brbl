package com.enoughisasgoodasafeast;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An implementation of Rabbit's DefaultConsumer that processes messages from a queue using an MTHandler impl.
 */
public class MTConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(MTConsumer.class);

    private final MTHandler handler;
    private final ConcurrentHashMap<Integer, SessionState> states;

    /**
     * Constructs a new instance and records its association to the passed-in channel.
     *
     * @param channel the channel to which this consumer is attached
     * @param handler the handler that will process incoming messages in some fashion.
     */
    public MTConsumer(Channel channel, MTHandler handler) {
        super(channel);
        this.handler = handler;
        this.states = new ConcurrentHashMap<>(); // use time expired Caffeine cache instead
    }

    @Override
    public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
        // super.handleDelivery(consumerTag, envelope, properties, body);
        long deliveryTag = envelope.getDeliveryTag();
        String message = new String(body, StandardCharsets.UTF_8);
        LOG.info(" [x] Receiving on {}: '{}' (consumerTag: {})", envelope.getRoutingKey(), message, consumerTag);

        Integer sessionID = 1; // TODO figure out how we express session identifiers.

        /*
         * Each time we receive a message there are the following possibilities
         * 1) the message is the first message in the session sequence
         * 2) the message is not
         */

        String[] messageParts = message.split(SharedConstants.TEST_SPACE_TOKEN, 2);
        Integer seqId = Integer.valueOf(messageParts[0]); // 1
        SessionState latest = states.get(sessionID);
        if (latest != null) {
            if (latest.wasSent) {
                // then the new message should be next in sequence, not equal to latest
                if (1 + latest.seqNum == seqId) {
                    // okay we can send it
                    LOG.info("ORDER_CHECK: Message to be sent: Session 1: {}", seqId);
                } else {
                    LOG.info("ORDER_CHECK: Message {} does not follow the previously sent message ({})", seqId, latest.seqNum);
                    getChannel().basicReject(deliveryTag, true);
                    return;
                }
            } else {
                // sequence number must be the same as
                if (latest.seqNum != seqId) {
                    LOG.info("ORDER_CHECK: Expected to retry failed message Session 1: {} but got {}", latest.seqNum, seqId);
                    getChannel().basicReject(deliveryTag, true);
                    return;
                } else {
                    LOG.info("ORDER_CHECK: Retrying previously failed message Session 1: {}", latest.seqNum);
                }
            }
            // This is either not the first message in the session or it is first message being retried
//            if (latest.wasSent == false && latest.seqNum == seqId) {
//                // retry sending a previously failed message
//            }
//            if (latest.wasSent == false || seqId != (latest.seqNum+1)) {
//                LOG.info("Cannot send {} since previous is {} for session {}", seqId, latest.seqNum, sessionID);
//                getChannel().basicReject(deliveryTag, true);
//                return;
//            }
        } else { // this is the first message in the session
            LOG.info("ORDER_CHECK: Sending message {} for new session, Session 1", seqId);
            latest = new SessionState(seqId, false);
            states.put(sessionID, latest);
        }

        try {
            latest.seqNum = seqId;
            boolean ok = this.handler.handle(message);
            if (ok) {
                LOG.info("Processed message: {}", message);
                getChannel().basicAck(deliveryTag, false);
                latest.wasSent = true;
            } else {
                LOG.error("Delivery failed for message: {} (No exception)", message);
                getChannel().basicReject(deliveryTag, true); // Is this the thing to do?
                latest.wasSent = false;
            }

        } catch (Exception e) {
            LOG.error("Delivery failed for message: {} ({})", message, e.getMessage());
            getChannel().basicReject(deliveryTag, true); // Is this the thing to do?
            latest.wasSent = false;
        }
    }

    public class SessionState {
        public SessionState(int seqNum, boolean wasSent) {
            this.seqNum = seqNum;
            this.wasSent = wasSent;
        }

        int seqNum;
        boolean wasSent;
    }
}
