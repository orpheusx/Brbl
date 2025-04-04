package com.enoughisasgoodasafeast;

public class FakeQueueConsumer implements QueueConsumer {
    @Override
    public void shutdown() {
        // no op
    }
}
