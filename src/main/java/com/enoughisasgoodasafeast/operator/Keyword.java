package com.enoughisasgoodasafeast.operator;

import java.util.UUID;

public record Keyword(UUID id, String wordPattern, Platform platform, UUID scriptId/*, Customer c*/, String shortCode) {}

