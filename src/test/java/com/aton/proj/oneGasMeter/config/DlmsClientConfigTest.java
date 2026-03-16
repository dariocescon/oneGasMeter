package com.aton.proj.oneGasMeter.config;

import com.aton.proj.oneGasMeter.dlms.DlmsProtocolType;
import gurux.dlms.enums.Authentication;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DlmsClientConfigTest {

    @Test
    void buildWrapperConfigWithDefaults() {
        DlmsClientConfig config = DlmsClientConfig.builder()
                .host("192.168.1.100")
                .build();

        assertThat(config.getHost()).isEqualTo("192.168.1.100");
        assertThat(config.getPort()).isEqualTo(4059);
        assertThat(config.getProtocolType()).isEqualTo(DlmsProtocolType.WRAPPER);
        assertThat(config.getClientAddress()).isEqualTo(16);
        assertThat(config.getServerAddress()).isEqualTo(1);
        assertThat(config.getAuthentication()).isEqualTo(Authentication.NONE);
        assertThat(config.getPassword()).isNull();
        assertThat(config.isUseLogicalNameReferencing()).isTrue();
        assertThat(config.getConnectionTimeoutMs()).isEqualTo(5000);
    }

    @Test
    void buildWrapperConfigWithCustomValues() {
        DlmsClientConfig config = DlmsClientConfig.builder()
                .host("10.0.0.1")
                .port(4060)
                .clientAddress(32)
                .serverAddress(2)
                .authentication(Authentication.LOW)
                .password("secret")
                .useLogicalNameReferencing(false)
                .connectionTimeoutMs(10000)
                .build();

        assertThat(config.getHost()).isEqualTo("10.0.0.1");
        assertThat(config.getPort()).isEqualTo(4060);
        assertThat(config.getClientAddress()).isEqualTo(32);
        assertThat(config.getServerAddress()).isEqualTo(2);
        assertThat(config.getAuthentication()).isEqualTo(Authentication.LOW);
        assertThat(config.getPassword()).isEqualTo("secret");
        assertThat(config.isUseLogicalNameReferencing()).isFalse();
        assertThat(config.getConnectionTimeoutMs()).isEqualTo(10000);
    }

    @Test
    void buildHdlcConfig() {
        DlmsClientConfig config = DlmsClientConfig.builder()
                .protocolType(DlmsProtocolType.HDLC)
                .serialPort("/dev/ttyUSB0")
                .baudRate(19200)
                .build();

        assertThat(config.getProtocolType()).isEqualTo(DlmsProtocolType.HDLC);
        assertThat(config.getSerialPort()).isEqualTo("/dev/ttyUSB0");
        assertThat(config.getBaudRate()).isEqualTo(19200);
    }

    @Test
    void buildHdlcWithModeEConfig() {
        DlmsClientConfig config = DlmsClientConfig.builder()
                .protocolType(DlmsProtocolType.HDLC_WITH_MODE_E)
                .serialPort("COM1")
                .build();

        assertThat(config.getProtocolType()).isEqualTo(DlmsProtocolType.HDLC_WITH_MODE_E);
        assertThat(config.getSerialPort()).isEqualTo("COM1");
        assertThat(config.getBaudRate()).isEqualTo(9600); // default
    }

    @Test
    void wrapperWithoutHostThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> DlmsClientConfig.builder()
                .protocolType(DlmsProtocolType.WRAPPER)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Host is required");
    }

    @Test
    void wrapperWithBlankHostThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> DlmsClientConfig.builder()
                .host("  ")
                .protocolType(DlmsProtocolType.WRAPPER)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Host is required");
    }

    @Test
    void hdlcWithoutSerialPortThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> DlmsClientConfig.builder()
                .protocolType(DlmsProtocolType.HDLC)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Serial port is required");
    }

    @Test
    void nullProtocolTypeThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> DlmsClientConfig.builder()
                .host("host")
                .protocolType(null)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Protocol type must not be null");
    }

    @Test
    void invalidPortThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> DlmsClientConfig.builder()
                .host("host")
                .port(0)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Port must be between 1 and 65535");
    }

    @Test
    void portAbove65535ThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> DlmsClientConfig.builder()
                .host("host")
                .port(65536)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Port must be between 1 and 65535");
    }

    @Test
    void zeroTimeoutThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> DlmsClientConfig.builder()
                .host("host")
                .connectionTimeoutMs(0)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Connection timeout must be positive");
    }
}
