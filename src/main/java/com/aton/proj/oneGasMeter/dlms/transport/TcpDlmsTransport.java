package com.aton.proj.oneGasMeter.dlms.transport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * DLMS WRAPPER (IEC 62056-47) transport over TCP/IP.
 * <p>
 * Connects to a meter's TCP endpoint, writes raw DLMS WRAPPER frames and
 * reads the corresponding responses.  The DLMS WRAPPER frame format is:
 * <pre>
 *   Byte 0-1 : version  (0x00 0x01)
 *   Byte 2-3 : source   WPORT (big-endian)
 *   Byte 4-5 : dest     WPORT (big-endian)
 *   Byte 6-7 : payload  length  (big-endian)
 *   Byte 8.. : payload  data
 * </pre>
 * </p>
 */
public class TcpDlmsTransport implements DlmsTransport {

    /** Minimum header size of a DLMS WRAPPER frame (bytes 0-7). */
    private static final int WRAPPER_HEADER_SIZE = 8;

    private final String host;
    private final int port;
    private final int timeoutMs;

    private Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;

    /**
     * Creates a new TCP transport.
     *
     * @param host      hostname or IP address of the meter
     * @param port      TCP port (DLMS default: 4059)
     * @param timeoutMs socket connect/read timeout in milliseconds
     */
    public TcpDlmsTransport(String host, int port, int timeoutMs) {
        this.host = host;
        this.port = port;
        this.timeoutMs = timeoutMs;
    }

    /**
     * Opens a TCP connection to the meter.
     *
     * @throws IOException if the connection cannot be established
     */
    @Override
    public void connect() throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setSoTimeout(timeoutMs);
        inputStream = socket.getInputStream();
        outputStream = socket.getOutputStream();
    }

    /**
     * Closes the TCP connection and releases all resources.
     *
     * @throws IOException if closing fails
     */
    @Override
    public void disconnect() throws IOException {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    /**
     * Sends all bytes of {@code data} over the TCP socket.
     *
     * @param data raw DLMS WRAPPER frame bytes
     * @throws IOException if the write fails
     */
    @Override
    public void write(byte[] data) throws IOException {
        outputStream.write(data);
        outputStream.flush();
    }

    /**
     * Reads one complete DLMS WRAPPER frame.
     * <p>
     * First reads the 8-byte header, extracts the payload length from bytes
     * 6-7, then reads exactly that many additional bytes.
     * </p>
     *
     * @return complete WRAPPER frame (header + payload)
     * @throws IOException if the socket is closed or a read error occurs
     */
    @Override
    public byte[] read() throws IOException {
        byte[] header = new byte[WRAPPER_HEADER_SIZE];
        readFully(header);

        int payloadLength = ((header[6] & 0xFF) << 8) | (header[7] & 0xFF);
        byte[] payload = new byte[payloadLength];
        if (payloadLength > 0) {
            readFully(payload);
        }

        byte[] frame = new byte[WRAPPER_HEADER_SIZE + payloadLength];
        System.arraycopy(header, 0, frame, 0, WRAPPER_HEADER_SIZE);
        System.arraycopy(payload, 0, frame, WRAPPER_HEADER_SIZE, payloadLength);
        return frame;
    }

    /**
     * Returns {@code true} when the TCP socket is open and connected.
     *
     * @return {@code true} if connected
     */
    @Override
    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    /** Reads exactly {@code buffer.length} bytes, blocking until all are available. */
    private void readFully(byte[] buffer) throws IOException {
        int offset = 0;
        while (offset < buffer.length) {
            int bytesRead = inputStream.read(buffer, offset, buffer.length - offset);
            if (bytesRead == -1) {
                throw new IOException("Connection closed unexpectedly while reading DLMS frame");
            }
            offset += bytesRead;
        }
    }

    // --- Visible for testing ---

    String getHost() {
        return host;
    }

    int getPort() {
        return port;
    }

    int getTimeoutMs() {
        return timeoutMs;
    }

    /**
     * Injects pre-built streams into an already-constructed transport without
     * opening a real TCP connection.  Intended for unit tests only.
     *
     * @param in  input stream to use
     * @param out output stream to use
     */
    void injectStreams(java.io.InputStream in, java.io.OutputStream out) {
        this.inputStream = in;
        this.outputStream = out;
    }

    /**
     * Injects a pre-built socket.  Intended for unit tests that need to mock
     * the socket's {@code close()} behaviour.
     *
     * @param s socket to inject
     */
    void injectSocket(java.net.Socket s) throws java.io.IOException {
        this.socket = s;
        this.inputStream = s.getInputStream();
        this.outputStream = s.getOutputStream();
    }
}
