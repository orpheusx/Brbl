package com.enoughisasgoodasafeast.sndr;

import com.enoughisasgoodasafeast.RetryDelayRoutingKey;
import com.enoughisasgoodasafeast.operator.ProcessState;

public record ProcessStateRoutingKey(ProcessState processState, RetryDelayRoutingKey retryDelayRoutingKey) {

    public ProcessStateRoutingKey(ProcessState processState) {
        this(processState, null);
    }
}
