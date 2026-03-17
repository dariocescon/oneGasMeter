package com.aton.proj.oneGasMeter.dlms;

import com.aton.proj.oneGasMeter.config.DlmsClientConfig;
import com.aton.proj.oneGasMeter.dlms.transport.DlmsTransport;
import com.aton.proj.oneGasMeter.exception.DlmsCommunicationException;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.enums.Authentication;
import gurux.dlms.enums.InterfaceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import gurux.dlms.enums.DataType;
import gurux.dlms.objects.GXDLMSData;
import gurux.dlms.objects.GXDLMSRegister;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DlmsMeterClientTest {

    @Mock
    private DlmsTransport transport;

    private DlmsClientConfig wrapperConfig;
    private DlmsClientConfig hdlcConfig;

    @BeforeEach
    void setUp() {
        wrapperConfig = DlmsClientConfig.builder()
                .host("192.168.1.100")
                .port(4059)
                .clientAddress(16)
                .serverAddress(1)
                .protocolType(DlmsProtocolType.WRAPPER)
                .authentication(Authentication.NONE)
                .build();

        hdlcConfig = DlmsClientConfig.builder()
                .protocolType(DlmsProtocolType.HDLC)
                .serialPort("/dev/ttyUSB0")
                .clientAddress(16)
                .serverAddress(1)
                .authentication(Authentication.NONE)
                .build();
    }

    // -----------------------------------------------------------------------
    // Constructor / GXDLMSClient configuration
    // -----------------------------------------------------------------------

    @Test
    void gxClientIsConfiguredWithCorrectAddresses() {
        DlmsMeterClient client = new DlmsMeterClient(wrapperConfig, transport);
        GXDLMSClient gxClient = client.getGxClient();

        assertThat(gxClient.getClientAddress()).isEqualTo(16);
        assertThat(gxClient.getServerAddress()).isEqualTo(1);
        assertThat(gxClient.getAuthentication()).isEqualTo(Authentication.NONE);
    }

    @Test
    void gxClientInterfaceTypeMatchesProtocolType() {
        DlmsMeterClient wrapperClient = new DlmsMeterClient(wrapperConfig, transport);
        assertThat(wrapperClient.getGxClient().getInterfaceType()).isEqualTo(InterfaceType.WRAPPER);

        DlmsMeterClient hdlcClient = new DlmsMeterClient(hdlcConfig, transport);
        assertThat(hdlcClient.getGxClient().getInterfaceType()).isEqualTo(InterfaceType.HDLC);
    }

    @Test
    void gxClientUsesLogicalNameReferencingByDefault() {
        DlmsMeterClient client = new DlmsMeterClient(wrapperConfig, transport);
        assertThat(client.getGxClient().getUseLogicalNameReferencing()).isTrue();
    }

    @Test
    void gxClientUsesShortNameReferencingWhenConfigured() {
        DlmsClientConfig snConfig = DlmsClientConfig.builder()
                .host("host")
                .useLogicalNameReferencing(false)
                .build();

        DlmsMeterClient client = new DlmsMeterClient(snConfig, transport);
        assertThat(client.getGxClient().getUseLogicalNameReferencing()).isFalse();
    }

    // -----------------------------------------------------------------------
    // isConnected
    // -----------------------------------------------------------------------

    @Test
    void isConnectedDelegatesToTransport() {
        DlmsMeterClient client = new DlmsMeterClient(wrapperConfig, transport);

        when(transport.isConnected()).thenReturn(true);
        assertThat(client.isConnected()).isTrue();

        when(transport.isConnected()).thenReturn(false);
        assertThat(client.isConnected()).isFalse();
    }

    // -----------------------------------------------------------------------
    // connect – transport failure
    // -----------------------------------------------------------------------

    @Test
    void connectThrowsDlmsCommunicationExceptionWhenTransportConnectFails() throws IOException {
        doThrow(new IOException("refused")).when(transport).connect();

        DlmsMeterClient client = new DlmsMeterClient(wrapperConfig, transport);

        assertThatThrownBy(client::connect)
                .isInstanceOf(DlmsCommunicationException.class)
                .hasMessageContaining("Failed to connect");
    }

    @Test
    void connectCallsTransportConnect() throws Exception {
        // Stub transport.read() to throw so the handshake fails fast after connect()
        when(transport.read()).thenThrow(new IOException("no meter"));

        DlmsMeterClient client = new DlmsMeterClient(wrapperConfig, transport);

        try {
            client.connect();
        } catch (DlmsCommunicationException ignored) {
            // expected – we only care that transport.connect() was invoked
        }

        verify(transport).connect();
    }

    @Test
    void connectSendsAarqFrameForWrapper() throws Exception {
        // First call to read() returns a dummy AARE – we won't actually parse it,
        // which causes a parse error, but we can verify write() was called.
        when(transport.read()).thenThrow(new IOException("no meter"));

        DlmsMeterClient client = new DlmsMeterClient(wrapperConfig, transport);

        try {
            client.connect();
        } catch (DlmsCommunicationException ignored) { }

        // At minimum the AARQ write must have been attempted
        verify(transport).write(org.mockito.ArgumentMatchers.any(byte[].class));
    }

    // -----------------------------------------------------------------------
    // disconnect
    // -----------------------------------------------------------------------

    @Test
    void disconnectCallsTransportDisconnect() throws Exception {
        DlmsMeterClient client = new DlmsMeterClient(wrapperConfig, transport);
        client.disconnect();

        verify(transport).disconnect();
    }

    @Test
    void disconnectThrowsDlmsCommunicationExceptionWhenTransportDisconnectFails()
            throws IOException {
        doThrow(new IOException("close error")).when(transport).disconnect();

        DlmsMeterClient client = new DlmsMeterClient(wrapperConfig, transport);

        assertThatThrownBy(client::disconnect)
                .isInstanceOf(DlmsCommunicationException.class)
                .hasMessageContaining("Failed to disconnect");
    }

    // -----------------------------------------------------------------------
    // getGxClient
    // -----------------------------------------------------------------------

    @Test
    void getGxClientReturnsNonNullClient() {
        DlmsMeterClient client = new DlmsMeterClient(wrapperConfig, transport);
        assertThat(client.getGxClient()).isNotNull();
    }

    // -----------------------------------------------------------------------
    // readAttribute – transport errors
    // -----------------------------------------------------------------------

    @Test
    void readAttributeThrowsDlmsCommunicationExceptionOnTransportError() throws IOException {
        when(transport.read()).thenThrow(new IOException("read error"));

        DlmsMeterClient client = new DlmsMeterClient(wrapperConfig, transport);
        GXDLMSData data = new GXDLMSData("0.0.96.1.0.255");

        assertThatThrownBy(() -> client.readAttribute(data, 2))
                .isInstanceOf(DlmsCommunicationException.class)
                .hasMessageContaining("0.0.96.1.0.255")
                .hasMessageContaining("attribute 2");
    }

    // -----------------------------------------------------------------------
    // writeAttribute – transport errors
    // -----------------------------------------------------------------------

    @Test
    void writeAttributeThrowsDlmsCommunicationExceptionOnTransportError() throws IOException {
        when(transport.read()).thenThrow(new IOException("write error"));

        DlmsMeterClient client = new DlmsMeterClient(wrapperConfig, transport);
        GXDLMSRegister register = new GXDLMSRegister("1.0.1.8.0.255");

        assertThatThrownBy(() -> client.writeAttribute(register, 2))
                .isInstanceOf(DlmsCommunicationException.class)
                .hasMessageContaining("1.0.1.8.0.255")
                .hasMessageContaining("attribute 2");
    }

    // -----------------------------------------------------------------------
    // invokeMethod – transport errors
    // -----------------------------------------------------------------------

    @Test
    void invokeMethodThrowsDlmsCommunicationExceptionOnTransportError() throws IOException {
        when(transport.read()).thenThrow(new IOException("method error"));

        DlmsMeterClient client = new DlmsMeterClient(wrapperConfig, transport);
        GXDLMSData data = new GXDLMSData("0.0.96.3.10.255");

        assertThatThrownBy(() -> client.invokeMethod(data, 1, 0, DataType.INT8))
                .isInstanceOf(DlmsCommunicationException.class)
                .hasMessageContaining("0.0.96.3.10.255")
                .hasMessageContaining("method 1");
    }

    @Test
    void readAttributeReThrowsDlmsCommunicationException() throws IOException {
        // Simulate a DLMS error that's already wrapped
        when(transport.read()).thenThrow(new IOException("timeout"));

        DlmsMeterClient client = new DlmsMeterClient(wrapperConfig, transport);
        GXDLMSData data = new GXDLMSData("0.0.96.1.0.255");

        assertThatThrownBy(() -> client.readAttribute(data, 2))
                .isInstanceOf(DlmsCommunicationException.class);
    }

    @Test
    void invokeMethodIncludesObisAndMethodInMessage() throws IOException {
        when(transport.read()).thenThrow(new IOException("fail"));

        DlmsMeterClient client = new DlmsMeterClient(wrapperConfig, transport);
        GXDLMSData data = new GXDLMSData("0.0.13.0.0.255");

        assertThatThrownBy(() -> client.invokeMethod(data, 2, 0, DataType.INT8))
                .isInstanceOf(DlmsCommunicationException.class)
                .hasMessageContaining("method 2")
                .hasMessageContaining("0.0.13.0.0.255");
    }
}
