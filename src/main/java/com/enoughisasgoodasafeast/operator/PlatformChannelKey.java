package com.enoughisasgoodasafeast.operator;

public record PlatformChannelKey(Platform platform, String channel) {
    public static PlatformChannelKey newPlatformChannel(SessionKey key) {
        return new PlatformChannelKey(key.platform(), key.to());
    }
}
