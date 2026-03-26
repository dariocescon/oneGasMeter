package com.aton.proj.oneGasMeter.dlms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.aton.proj.oneGasMeter.config.DlmsClientConfig;
import com.aton.proj.oneGasMeter.dlms.transport.DlmsTransport;

@SpringBootTest(properties = "dlms.inbound.enabled=false")
class DlmsConnectionFactoryTest {

    @Autowired
    private DlmsConnectionFactory factory;

    @Test
    void factoryIsPresentInApplicationContext() {
        assertThat(factory).isNotNull();
    }

    @Test
    void createClientReturnsNonNullForWrapperConfig() {
        DlmsClientConfig config = DlmsClientConfig.builder()
                .host("192.168.1.100")
                .protocolType(DlmsProtocolType.WRAPPER)
                .build();

        DlmsMeterClient client = factory.createClient(config);
        assertThat(client).isNotNull();
    }

    @Test
    void createClientWithCustomTransportUsesSuppliedTransport() {
        DlmsClientConfig config = DlmsClientConfig.builder()
                .host("host")
                .build();

        DlmsTransport customTransport = mock(DlmsTransport.class);
        DlmsMeterClient client = factory.createClient(config, customTransport);

        assertThat(client).isNotNull();
        assertThat(client.isConnected()).isFalse(); // mock returns false by default
    }

    @Test
    void createClientForHdlcThrowsUnsupportedOperationException() {
        DlmsClientConfig config = DlmsClientConfig.builder()
                .protocolType(DlmsProtocolType.HDLC)
                .serialPort("/dev/ttyUSB0")
                .build();

        assertThatThrownBy(() -> factory.createClient(config))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("HDLC");
    }

    @Test
    void createHdlcClientReturnsNonNullClient() {
        DlmsClientConfig config = DlmsClientConfig.builder()
                .protocolType(DlmsProtocolType.HDLC)
                .serialPort("/dev/ttyUSB0")
                .build();

        DlmsMeterClient client = factory.createHdlcClient(
                config,
                new ByteArrayInputStream(new byte[0]),
                new ByteArrayOutputStream());

        assertThat(client).isNotNull();
    }

    @Test
    void createWrapperClientHasCorrectGxConfiguration() {
        DlmsClientConfig config = DlmsClientConfig.builder()
                .host("10.0.0.1")
                .port(4059)
                .clientAddress(32)
                .serverAddress(3)
                .build();

        DlmsMeterClient client = factory.createClient(config);
        assertThat(client.getGxClient().getClientAddress()).isEqualTo(32);
        assertThat(client.getGxClient().getServerAddress()).isEqualTo(3);
    }
}
