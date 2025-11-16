package com.enoughisasgoodasafeast.operator;

import java.util.UUID;

/**
 * The encapsulation of the properties used to find the right script given a User's input text, typically when initiating
 * a new Session. The wordPattern can be a word or a phrase or even a set of emojis.
 * @param id
 * @param wordPattern the word, phrase or set of characters that should be mapped to a particular script.
 * @param platform the platform where the keyword is defined.
 * @param scriptId the script mapped to this keyword.
 * @param channel the channel where the keyword is defined.
 */
public record Keyword(UUID id, String wordPattern, Platform platform, UUID scriptId/*, Customer c*/, String channel) {}

