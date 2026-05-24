package com.enoughisasgoodasafeast.operator;

import java.util.UUID;

/**
 * The encapsulation of the properties used to find the right node graph given a User's input text, typically when initiating
 * a new Session. The wordPattern can be a word or a phrase or even a set of emojis.
 *
 * NB: This is another instance where the runtime model is not a one-to-one match of the db table, "keywords."
 * The nodeId property is provided by the query that runs through "scripts" table to find the associated entry point in "nodes."
 *
 * @param id
 * @param wordPattern the word, phrase or set of characters that should be mapped to a particular script.
 * @param platform the platform where the keyword is defined.
 * @param nodeId the node mapped to this keyword by way of the scripts table.
 * @param channel the channel where the keyword is defined.
 */
public record Keyword(UUID id, String wordPattern, Platform platform, UUID nodeId, String channel) {}

