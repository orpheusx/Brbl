package com.enoughisasgoodasafeast;

import org.slf4j.MDC;
import java.util.UUID;
import static com.enoughisasgoodasafeast.SharedConstants.INSTANCE_ID;

/**
 * Common functionality can live here. If we only provide the MDC setup then it may not be worth it.
 */
public abstract class WebService {

    static {
        MDC.put(INSTANCE_ID, UUID.randomUUID().toString());
    }

}
