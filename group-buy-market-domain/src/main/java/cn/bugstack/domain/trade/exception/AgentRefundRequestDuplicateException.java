package cn.bugstack.domain.trade.exception;

/** Signals that the idempotency key is already owned by an Agent refund request. */
public class AgentRefundRequestDuplicateException extends RuntimeException {
    public AgentRefundRequestDuplicateException() {
        super();
    }
}
