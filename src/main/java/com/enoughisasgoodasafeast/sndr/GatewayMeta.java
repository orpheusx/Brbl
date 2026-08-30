package com.enoughisasgoodasafeast.sndr;

import com.enoughisasgoodasafeast.Message;

/**
 * A marker interface used by classes providing the information needed by a given gateway provider.
 */
public interface GatewayMeta {
    public Object toGatewayMessage(Message message);
}
