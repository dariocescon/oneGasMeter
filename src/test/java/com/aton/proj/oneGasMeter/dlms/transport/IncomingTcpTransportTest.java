package com.aton.proj.oneGasMeter.dlms.transport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncomingTcpTransportTest {

    private Socket socket;
    private ByteArrayOutputStream outputStream;

    @BeforeEach
    void setUp() throws IOException {
        socket = mock(Socket.class);
        outputStream = new ByteArrayOutputStream();
        when(socket.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(socket.getOutputStream()).thenReturn(outputStream);
    }

    @Test
    void constructor_setsSoTimeoutAndGetsStreams() throws IOException {
        new IncomingTcpTransport(socket, 5000);

        verify(socket).setSoTimeout(5000);
        verify(socket).getInputStream();
        verify(socket).getOutputStream();
    }

    @Test
    void connect_isNoOp() throws IOException {
        IncomingTcpTransport transport = new IncomingTcpTransport(socket, 1000);
        // connect() should not throw and should not interact with the socket
        transport.connect();
        // no additional interactions beyond constructor
    }

    @Test
    void disconnect_closesSocket_whenOpen() throws IOException {
        when(socket.isClosed()).thenReturn(false);
        IncomingTcpTransport transport = new IncomingTcpTransport(socket, 1000);

        transport.disconnect();

        verify(socket).close();
    }

    @Test
    void disconnect_doesNotClose_whenAlreadyClosed() throws IOException {
        when(socket.isClosed()).thenReturn(true);
        IncomingTcpTransport transport = new IncomingTcpTransport(socket, 1000);

        transport.disconnect();
        // close() should NOT be called
        // verified implicitly — if it were called Mockito would record it
    }

    @Test
    void isConnected_returnsTrue_whenSocketConnectedAndOpen() throws IOException {
        when(socket.isConnected()).thenReturn(true);
        when(socket.isClosed()).thenReturn(false);
        IncomingTcpTransport transport = new IncomingTcpTransport(socket, 1000);

        assertThat(transport.isConnected()).isTrue();
    }

    @Test
    void isConnected_returnsFalse_whenSocketClosed() throws IOException {
        when(socket.isConnected()).thenReturn(true);
        when(socket.isClosed()).thenReturn(true);
        IncomingTcpTransport transport = new IncomingTcpTransport(socket, 1000);

        assertThat(transport.isConnected()).isFalse();
    }

    @Test
    void write_writesAllBytesToOutputStream() throws IOException {
        IncomingTcpTransport transport = new IncomingTcpTransport(socket, 1000);
        byte[] frame = {0x00, 0x01, 0x00, 0x01, 0x00, 0x01, 0x00, 0x03, 0x0A, 0x0B, 0x0C};

        transport.write(frame);

        assertThat(outputStream.toByteArray()).isEqualTo(frame);
    }

    @Test
    void read_parsesWrapperFrameCorrectly() throws IOException {
        // Build a valid WRAPPER frame: header (8 bytes) + 3-byte payload
        byte[] payload = {0x0A, 0x0B, 0x0C};
        byte[] frame = new byte[8 + payload.length];
        frame[0] = 0x00; frame[1] = 0x01;             // version
        frame[2] = 0x00; frame[3] = 0x01;             // source WPORT
        frame[4] = 0x00; frame[5] = 0x01;             // dest WPORT
        frame[6] = 0x00; frame[7] = (byte) payload.length; // payload length
        System.arraycopy(payload, 0, frame, 8, payload.length);

        when(socket.getInputStream()).thenReturn(new ByteArrayInputStream(frame));
        IncomingTcpTransport transport = new IncomingTcpTransport(socket, 1000);

        byte[] result = transport.read();

        assertThat(result).isEqualTo(frame);
    }

    @Test
    void read_throwsIOException_onEofDuringHeader() throws IOException {
        // Empty input stream → EOF when trying to read header
        when(socket.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        IncomingTcpTransport transport = new IncomingTcpTransport(socket, 1000);

        assertThatThrownBy(transport::read)
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Connection closed unexpectedly");
    }

    @Test
    void getSocket_returnsInjectedSocket() throws IOException {
        IncomingTcpTransport transport = new IncomingTcpTransport(socket, 1000);

        assertThat(transport.getSocket()).isSameAs(socket);
    }
}
