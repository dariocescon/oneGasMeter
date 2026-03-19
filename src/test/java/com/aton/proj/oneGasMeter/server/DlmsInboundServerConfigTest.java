package com.aton.proj.oneGasMeter.server;

import com.aton.proj.oneGasMeter.config.DlmsClientConfig;
import gurux.dlms.enums.Authentication;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DlmsInboundServerConfigTest {

    @Test
    void buildWithDefaults() {
        DlmsInboundServerConfig config = DlmsInboundServerConfig.builder().build();

        assertThat(config.getListenPort()).isEqualTo(4059);
        assertThat(config.getSessionTimeoutMs()).isEqualTo(30_000);
        assertThat(config.getClientAddress()).isEqualTo(16);
        assertThat(config.getServerAddress()).isEqualTo(1);
        assertThat(config.getAuthentication()).isEqualTo(Authentication.NONE);
        assertThat(config.getPassword()).isNull();
        assertThat(config.isUseLogicalNameReferencing()).isTrue();
    }

    @Test
    void buildWithCustomValues() {
        DlmsInboundServerConfig config = DlmsInboundServerConfig.builder()
                .listenPort(5000)
                .sessionTimeoutMs(60_000)
                .clientAddress(32)
                .serverAddress(2)
                .authentication(Authentication.LOW)
                .password("s3cr3t")
                .useLogicalNameReferencing(false)
                .build();

        assertThat(config.getListenPort()).isEqualTo(5000);
        assertThat(config.getSessionTimeoutMs()).isEqualTo(60_000);
        assertThat(config.getClientAddress()).isEqualTo(32);
        assertThat(config.getServerAddress()).isEqualTo(2);
        assertThat(config.getAuthentication()).isEqualTo(Authentication.LOW);
        assertThat(config.getPassword()).isEqualTo("s3cr3t");
        assertThat(config.isUseLogicalNameReferencing()).isFalse();
    }

    @Test
    void build_throwsOnInvalidPort_zero() {
        assertThatThrownBy(() -> DlmsInboundServerConfig.builder().listenPort(0).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Listen port");
    }

    @Test
    void build_throwsOnInvalidPort_tooHigh() {
        assertThatThrownBy(() -> DlmsInboundServerConfig.builder().listenPort(65536).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Listen port");
    }

    @Test
    void build_throwsOnNonPositiveSessionTimeout() {
        assertThatThrownBy(() -> DlmsInboundServerConfig.builder().sessionTimeoutMs(0).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Session timeout");
    }

    @Test
    void build_throwsOnNullAuthentication() {
        assertThatThrownBy(() -> DlmsInboundServerConfig.builder().authentication(null).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Authentication");
    }

    @Test
    void toSessionConfig_buildsCorrectDlmsClientConfig() {
        DlmsInboundServerConfig config = DlmsInboundServerConfig.builder()
                .listenPort(4059)
                .sessionTimeoutMs(15_000)
                .clientAddress(32)
                .serverAddress(3)
                .authentication(Authentication.LOW)
                .password("pass")
                .useLogicalNameReferencing(false)
                .build();

        DlmsClientConfig sessionConfig = config.toSessionConfig("10.0.0.5");

        assertThat(sessionConfig.getHost()).isEqualTo("10.0.0.5");
        assertThat(sessionConfig.getPort()).isEqualTo(4059);
        assertThat(sessionConfig.getClientAddress()).isEqualTo(32);
        assertThat(sessionConfig.getServerAddress()).isEqualTo(3);
        assertThat(sessionConfig.getAuthentication()).isEqualTo(Authentication.LOW);
        assertThat(sessionConfig.getPassword()).isEqualTo("pass");
        assertThat(sessionConfig.isUseLogicalNameReferencing()).isFalse();
        assertThat(sessionConfig.getConnectionTimeoutMs()).isEqualTo(15_000);
    }
}
