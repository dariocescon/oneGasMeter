package com.aton.proj.oneGasMeter.dlms.transport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * DLMS WRAPPER (IEC 62056-47) transport over an <em>already-accepted</em> TCP socket.
 * <p>
 * Unlike {@link TcpDlmsTransport}, which opens its own connection, this class
 * wraps a {@link Socket} that has already been accepted by a server-side
 * {@link java.net.ServerSocket}.  The intended use-case is <em>meter-initiated</em>
 * communication: the meter dials in, the application accepts the socket, and then
 * acts as DLMS client over that socket.
 * </p>
 * <p>
 * The DLMS WRAPPER frame format is:
 * <pre>
 *   Byte 0-1 : version  (0x00 0x01)
 *   Byte 2-3 : source   WPORT (big-endian)
 *   Byte 4-5 : dest     WPORT (big-endian)
 *   Byte 6-7 : payload  length  (big-endian)
 *   Byte 8.. : payload  data
 * </pre>
 * </p>
 *
 * <h3>Lifecycle</h3>
 * <p>
 * {@link #connect()} is a no-op because the socket is already connected.
 * {@link #disconnect()} closes the socket and releases all resources.
 * </p>
 */
public class IncomingTcpTransport implements DlmsTransport {

    /** Minimum header size of a DLMS WRAPPER frame (bytes 0-7). */
    private static final int WRAPPER_HEADER_SIZE = 8;

    private final Socket socket;
    private final InputStream inputStream;
    private final OutputStream outputStream;

    /**
     * Creates a transport wrapping an already-accepted socket.
     *
     * @param socket    the accepted TCP socket (must be connected and open)
     * @param timeoutMs socket read timeout in milliseconds
     * @throws IOException if the socket's streams cannot be obtained or the
     *                     timeout cannot be set
     */
    public IncomingTcpTransport(Socket socket, int timeoutMs) throws IOException {
        this.socket = socket;
        this.socket.setSoTimeout(timeoutMs);
        this.inputStream = socket.getInputStream();
        this.outputStream = socket.getOutputStream();
    }

    /**
     * No-op: the socket is already connected by the time this transport is created.
     */
    @Override
    public void connect() {
        // already connected — nothing to do
    }

    /**
     * Closes the underlying TCP socket and releases all resources.
     *
     * @throws IOException if closing fails
     */
    @Override
    public void disconnect() throws IOException {
        if (!socket.isClosed()) {
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
        return socket.isConnected() && !socket.isClosed();
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

    /**
     * Returns the underlying socket.  Intended for unit tests only.
     *
     * @return the socket
     */
    Socket getSocket() {
        return socket;
    }
}
