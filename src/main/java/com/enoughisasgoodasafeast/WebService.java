package com.enoughisasgoodasafeast;

import org.slf4j.MDC;

import static com.enoughisasgoodasafeast.Functions.randomUUID;
import static com.enoughisasgoodasafeast.SharedConstants.INSTANCE_ID;

/**
 * Common functionality can live here. If we only provide the MDC setup then it may not be worth it.
 */
public abstract class WebService {

    static {
        MDC.put(INSTANCE_ID, randomUUID().toString());
    }

}
