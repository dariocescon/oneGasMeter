package com.aton.proj.oneGasMeter.dlms.transport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TcpDlmsTransportTest {

    @Test
    void constructorStoresHostPortAndTimeout() {
        TcpDlmsTransport transport = new TcpDlmsTransport("192.168.1.1", 4059, 3000);

        assertThat(transport.getHost()).isEqualTo("192.168.1.1");
        assertThat(transport.getPort()).isEqualTo(4059);
        assertThat(transport.getTimeoutMs()).isEqualTo(3000);
    }

    @Test
    void isConnectedReturnsFalseBeforeConnect() {
        TcpDlmsTransport transport = new TcpDlmsTransport("host", 4059, 1000);
        assertThat(transport.isConnected()).isFalse();
    }

    @Test
    void writeFlushesDataToOutputStream() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TcpDlmsTransport transport = buildTransportWithStreams(
                new ByteArrayInputStream(new byte[0]), out);

        byte[] frame = {0x00, 0x01, 0x00, 0x01, 0x00, 0x01, 0x00, 0x03, 0x01, 0x02, 0x03};
        transport.write(frame);

        assertThat(out.toByteArray()).isEqualTo(frame);
    }

    @Test
    void readParsesWrapperFrameCorrectly() throws IOException {
        // Build WRAPPER frame: version(2) + src(2) + dst(2) + length(2) + payload
        byte[] payload = {0x01, 0x02, 0x03};
        byte[] frame = buildWrapperFrame(payload);

        TcpDlmsTransport transport = buildTransportWithStreams(
                new ByteArrayInputStream(frame), new ByteArrayOutputStream());

        byte[] result = transport.read();
        assertThat(result).isEqualTo(frame);
    }

    @Test
    void readHandlesZeroLengthPayload() throws IOException {
        byte[] frame = buildWrapperFrame(new byte[0]);

        TcpDlmsTransport transport = buildTransportWithStreams(
                new ByteArrayInputStream(frame), new ByteArrayOutputStream());

        byte[] result = transport.read();
        assertThat(result).isEqualTo(frame);
    }

    @Test
    void readThrowsIOExceptionWhenConnectionClosedDuringHeader() {
        // Provide only 4 bytes instead of the 8-byte header
        TcpDlmsTransport transport = buildTransportWithStreams(
                new ByteArrayInputStream(new byte[]{0x00, 0x01, 0x00, 0x01}),
                new ByteArrayOutputStream());

        assertThatThrownBy(transport::read)
                .isInstanceOf(IOException.class)
                .hasMessageContaining("closed unexpectedly");
    }

    @Test
    void disconnectDoesNotThrowWhenSocketIsNull() {
        TcpDlmsTransport transport = new TcpDlmsTransport("host", 4059, 1000);
        // Should complete without exception even before connect() was called
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(transport::disconnect);
    }

    @Test
    void disconnectClosesSocket() throws IOException {
        Socket mockSocket = mock(Socket.class);
        when(mockSocket.isClosed()).thenReturn(false);
        when(mockSocket.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(mockSocket.getOutputStream()).thenReturn(new ByteArrayOutputStream());

        TcpDlmsTransport transport = new TcpDlmsTransport("host", 4059, 1000);
        transport.injectSocket(mockSocket);

        transport.disconnect();
        verify(mockSocket).close();
    }

    @Test
    void writeThrowsIOExceptionWhenStreamFails() throws IOException {
        OutputStream failingOut = mock(OutputStream.class);
        doThrow(new IOException("write error")).when(failingOut).write((byte[]) org.mockito.ArgumentMatchers.any());

        TcpDlmsTransport transport = buildTransportWithStreams(
                new ByteArrayInputStream(new byte[0]), failingOut);

        assertThatThrownBy(() -> transport.write(new byte[]{0x01}))
                .isInstanceOf(IOException.class);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Builds a DLMS WRAPPER frame: 8-byte header + payload. */
    private static byte[] buildWrapperFrame(byte[] payload) {
        byte[] frame = new byte[8 + payload.length];
        frame[0] = 0x00;
        frame[1] = 0x01; // version
        frame[2] = 0x00;
        frame[3] = 0x01; // source WPORT
        frame[4] = 0x00;
        frame[5] = 0x01; // dest WPORT
        frame[6] = (byte) ((payload.length >> 8) & 0xFF);
        frame[7] = (byte) (payload.length & 0xFF);
        System.arraycopy(payload, 0, frame, 8, payload.length);
        return frame;
    }

    /**
     * Creates a TcpDlmsTransport that uses the supplied in-memory streams
     * instead of a real socket.
     */
    private static TcpDlmsTransport buildTransportWithStreams(
            InputStream in, OutputStream out) {
        TcpDlmsTransport transport = new TcpDlmsTransport("localhost", 4059, 1000);
        transport.injectStreams(in, out);
        return transport;
    }
}
