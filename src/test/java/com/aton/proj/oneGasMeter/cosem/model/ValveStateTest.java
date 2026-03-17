package com.aton.proj.oneGasMeter.cosem.model;

import gurux.dlms.objects.enums.ControlState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValveStateTest {

    @Test
    void fromDisconnectedReturnsDisconnected() {
        assertThat(ValveState.from(ControlState.DISCONNECTED))
                .isEqualTo(ValveState.DISCONNECTED);
    }

    @Test
    void fromConnectedReturnsConnected() {
        assertThat(ValveState.from(ControlState.CONNECTED))
                .isEqualTo(ValveState.CONNECTED);
    }

    @Test
    void fromReadyForReconnectionReturnsReadyForReconnection() {
        assertThat(ValveState.from(ControlState.READY_FOR_RECONNECTION))
                .isEqualTo(ValveState.READY_FOR_RECONNECTION);
    }

    @Test
    void fromNullThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> ValveState.from(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null");
    }

    @Test
    void allControlStatesAreMapped() {
        // Verifica che ogni valore di ControlState abbia un mapping valido
        for (ControlState cs : ControlState.values()) {
            ValveState vs = ValveState.from(cs);
            assertThat(vs).isNotNull();
        }
    }
}
