package com.aton.proj.oneGasMeter.dlms;

import gurux.dlms.enums.InterfaceType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DlmsProtocolTypeTest {

    @Test
    void hdlcMapsToHdlcInterfaceType() {
        assertThat(DlmsProtocolType.HDLC.getInterfaceType()).isEqualTo(InterfaceType.HDLC);
    }

    @Test
    void wrapperMapsToWrapperInterfaceType() {
        assertThat(DlmsProtocolType.WRAPPER.getInterfaceType()).isEqualTo(InterfaceType.WRAPPER);
    }

    @Test
    void hdlcWithModeEMapsCorrectly() {
        assertThat(DlmsProtocolType.HDLC_WITH_MODE_E.getInterfaceType())
                .isEqualTo(InterfaceType.HDLC_WITH_MODE_E);
    }

    @Test
    void plcMapsToPlcInterfaceType() {
        assertThat(DlmsProtocolType.PLC.getInterfaceType()).isEqualTo(InterfaceType.PLC);
    }

    @Test
    void plcHdlcMapsToPlcHdlcInterfaceType() {
        assertThat(DlmsProtocolType.PLC_HDLC.getInterfaceType()).isEqualTo(InterfaceType.PLC_HDLC);
    }

    @Test
    void isHdlcBasedReturnsTrueForHdlc() {
        assertThat(DlmsProtocolType.HDLC.isHdlcBased()).isTrue();
    }

    @Test
    void isHdlcBasedReturnsTrueForHdlcWithModeE() {
        assertThat(DlmsProtocolType.HDLC_WITH_MODE_E.isHdlcBased()).isTrue();
    }

    @Test
    void isHdlcBasedReturnsFalseForWrapper() {
        assertThat(DlmsProtocolType.WRAPPER.isHdlcBased()).isFalse();
    }

    @Test
    void isHdlcBasedReturnsFalseForPlc() {
        assertThat(DlmsProtocolType.PLC.isHdlcBased()).isFalse();
    }

    @Test
    void isTcpBasedReturnsTrueForWrapper() {
        assertThat(DlmsProtocolType.WRAPPER.isTcpBased()).isTrue();
    }

    @Test
    void isTcpBasedReturnsFalseForHdlc() {
        assertThat(DlmsProtocolType.HDLC.isTcpBased()).isFalse();
    }

    @Test
    void isTcpBasedReturnsFalseForPlc() {
        assertThat(DlmsProtocolType.PLC.isTcpBased()).isFalse();
    }

    @Test
    void allValuesHaveNonNullInterfaceType() {
        for (DlmsProtocolType type : DlmsProtocolType.values()) {
            assertThat(type.getInterfaceType())
                    .as("InterfaceType for %s", type)
                    .isNotNull();
        }
    }
}
