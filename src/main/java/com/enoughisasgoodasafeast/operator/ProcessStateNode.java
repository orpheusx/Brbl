package com.enoughisasgoodasafeast.operator;

import org.jspecify.annotations.Nullable;

import java.io.Serializable;

public record ProcessStateNode(ProcessState processState, @Nullable Node node) implements Serializable {
}
