package com.enoughisasgoodasafeast;

import com.enoughisasgoodasafeast.operator.ProcessState;
import com.enoughisasgoodasafeast.operator.Session;
import com.enoughisasgoodasafeast.operator.UserStatus;
import org.jspecify.annotations.Nullable;

/**
 * A Golang-style ("comma-ok") tuple that encapsulates the result of an Operator.process() call.
 * @param processState the enum that defines how the OperatorConsumer should handle the Message (e.g. ack it, reject it, etc.)
 * @param session the Session associated with the processed Message.
 * @param isNewUser true if processing identified a brand-new user requiring database persistence.
 * @param updatedUserStatus updated status for the user if changed during processing, null otherwise.
 */
public record ProcessStateSession(
        ProcessState processState,
        Session session,
        boolean isNewUser,
        @Nullable UserStatus updatedUserStatus
) {
    public ProcessStateSession(ProcessState processState, Session session) {
        this(processState, session, false, null);
    }
}

