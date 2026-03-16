package com.aton.proj.oneGasMeter.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DlmsCommunicationExceptionTest {

    @Test
    void constructWithMessageOnly() {
        DlmsCommunicationException ex = new DlmsCommunicationException("test error");

        assertThat(ex.getMessage()).isEqualTo("test error");
        assertThat(ex.getErrorCode()).isEqualTo(0);
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void constructWithMessageAndCause() {
        RuntimeException cause = new RuntimeException("root cause");
        DlmsCommunicationException ex = new DlmsCommunicationException("wrapper", cause);

        assertThat(ex.getMessage()).isEqualTo("wrapper");
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.getErrorCode()).isEqualTo(0);
    }

    @Test
    void constructWithMessageAndErrorCode() {
        DlmsCommunicationException ex = new DlmsCommunicationException("dlms error", 3);

        assertThat(ex.getMessage()).isEqualTo("dlms error");
        assertThat(ex.getErrorCode()).isEqualTo(3);
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void isRuntimeException() {
        assertThat(new DlmsCommunicationException("x"))
                .isInstanceOf(RuntimeException.class);
    }
}
