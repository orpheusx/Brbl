package com.enoughisasgoodasafeast.operator;

/**
 * The record that forms the key for our script id mapping cache.
 * NB: Keywords are only relevant to MO initiated conversations not Push messages.
 * @param channel the short/long code or platform-specific session initiation code aka the channel.
 * @param keyword the language specific word (or phrase) that
 */
public record KeywordCacheKey(String channel, Platform platform, String keyword) {

    public static KeywordCacheKey newKey(SessionKey sessionKey) {
        return new KeywordCacheKey(sessionKey.to(), sessionKey.platform(), sessionKey.keyword().toLowerCase());
    }

}
