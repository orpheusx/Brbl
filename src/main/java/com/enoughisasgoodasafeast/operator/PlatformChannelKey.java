package com.enoughisasgoodasafeast.operator;

public record PlatformChannelKey(Platform platform, String channel) {

    public static PlatformChannelKey newPlatformChannel(KeywordCacheKey key) {
        return new PlatformChannelKey(key.platform(), key.channel());
    }

}
