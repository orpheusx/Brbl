package com.enoughisasgoodasafeast;

import com.enoughisasgoodasafeast.operator.ProcessState;
import com.enoughisasgoodasafeast.operator.Session;

/**
 * A Golang-style ("comma-ok") tuple that encapsulates the result of an Operator.process() call.
 * @param processState the enum that defines how the OperatorConsumer should handle the Message (e.g. ack it, reject it, etc.)
 * @param session the Session associated with the processed Message.
 */
public record ProcessStateSession(ProcessState processState, Session session) {
}
