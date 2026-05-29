package com.enoughisasgoodasafeast;

import com.enoughisasgoodasafeast.operator.Session;
import org.jspecify.annotations.Nullable;

public record BooleanSession(boolean ok, @Nullable Session session) {
}
