package com.enoughisasgoodasafeast.operator;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;

public record ProcessStateNode(@NonNull ProcessState processState, @Nullable Node node) implements Serializable {
}
