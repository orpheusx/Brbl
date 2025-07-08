package com.enoughisasgoodasafeast.operator;

import com.enoughisasgoodasafeast.Message;

/**
 *
 * @param code the short/long code or platform-specific session initiation code
 * @param keyword
 */
public record KeywordCacheKey(String code, String keyword) {

    public static KeywordCacheKey newKey(SessionKey sessionKey) {
        return new KeywordCacheKey(sessionKey.to(), sessionKey.keyword());
    }

}
