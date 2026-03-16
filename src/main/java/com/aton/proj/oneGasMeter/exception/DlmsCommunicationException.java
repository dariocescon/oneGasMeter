package com.aton.proj.oneGasMeter.exception;

/**
 * Unchecked exception thrown when a DLMS communication error occurs.
 * <p>
 * The optional {@code errorCode} field contains the DLMS/COSEM error code
 * returned by the meter (0 means no DLMS-level error).
 * </p>
 */
public class DlmsCommunicationException extends RuntimeException {

    private final int errorCode;

    /**
     * Constructs a new exception with the specified message.
     *
     * @param message human-readable error description
     */
    public DlmsCommunicationException(String message) {
        super(message);
        this.errorCode = 0;
    }

    /**
     * Constructs a new exception with the specified message and cause.
     *
     * @param message human-readable error description
     * @param cause   the underlying exception
     */
    public DlmsCommunicationException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = 0;
    }

    /**
     * Constructs a new exception with a DLMS error code.
     *
     * @param message   human-readable error description
     * @param errorCode DLMS/COSEM error code from the meter
     */
    public DlmsCommunicationException(String message, int errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Returns the DLMS/COSEM error code, or {@code 0} if no DLMS-level error occurred.
     *
     * @return DLMS error code
     */
    public int getErrorCode() {
        return errorCode;
    }
}
