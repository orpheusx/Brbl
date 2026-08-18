package com.enoughisasgoodasafeast;

import io.helidon.http.Status;
import org.jspecify.annotations.Nullable;

/**
 * A tuple that encapsulates the result of an HttpMessageHandler.handle() call.
 * At least one of these parameters must be non-null.
 * @param status The HTTP status code returned by the attempted operation.
 * @param exception Any exception that might have been thrown by the attempted operation.
 */

public record StatusException(@Nullable Status status, @Nullable Exception exception) {
    public StatusException(@Nullable Status status, @Nullable Exception exception) {
        this.status = status;
        this.exception = exception;
        assert null != status || exception != null; // one must be non-null.
    }

    public boolean isSuccess() {
        if(status != null && status.family() == Status.Family.SUCCESSFUL) {
            return true;
        } else {
            return false;
        }
    }
}
