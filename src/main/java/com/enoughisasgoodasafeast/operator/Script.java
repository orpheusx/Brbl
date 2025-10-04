package com.enoughisasgoodasafeast.operator;

import java.time.Instant;
import java.util.UUID;

public record Script(
        UUID id,
        String name,
        String description,
        UUID customer_id,
        UUID node_id,
        ScriptStatus status,
        //LanguageCode language,
        Instant created_at,
        Instant updated_at
) {}
