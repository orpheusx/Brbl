package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.QueueProducer;

import java.util.UUID;

public record PushSupport(
        UUID campaignId,
        Node startNode,
        QueueProducer queueProducer,
        PersistenceManager persistenceManager) {};
