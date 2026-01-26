package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.QueueProducer;

public record PushSupport(
        Node startNode,
        QueueProducer queueProducer,
        PersistenceManager persistenceManager) {};
