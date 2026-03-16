package com.aton.proj.oneGasMeter.dlms.transport;

import java.io.IOException;

/**
 * Abstraction over the physical communication channel used to exchange raw
 * DLMS bytes with a meter.
 * <p>
 * Two built-in implementations are provided:
 * <ul>
 *   <li>{@link TcpDlmsTransport} – TCP/IP socket for the WRAPPER interface</li>
 *   <li>{@link HdlcSerialTransport} – serial/optical interface for HDLC</li>
 * </ul>
 * Custom transports (e.g. UDP, SMS, CoAP) can be added by implementing this
 * interface and injecting them into {@link com.aton.proj.oneGasMeter.dlms.DlmsMeterClient}.
 * </p>
 */
public interface DlmsTransport {

    /**
     * Opens the physical channel (TCP connection, serial port open, etc.).
     *
     * @throws IOException if the channel cannot be opened
     */
    void connect() throws IOException;

    /**
     * Closes the physical channel and releases all resources.
     *
     * @throws IOException if closing fails
     */
    void disconnect() throws IOException;

    /**
     * Sends raw bytes over the channel.
     *
     * @param data bytes to send (a complete DLMS frame or frame fragment)
     * @throws IOException if the write fails
     */
    void write(byte[] data) throws IOException;

    /**
     * Reads one complete DLMS frame from the channel.
     * <p>
     * The method blocks until a full frame has been received or the configured
     * timeout expires.
     * </p>
     *
     * @return raw bytes of one complete DLMS frame
     * @throws IOException if the read fails or the channel is closed
     */
    byte[] read() throws IOException;

    /**
     * Returns {@code true} if the channel is currently open and ready.
     *
     * @return connection state
     */
    boolean isConnected();
}
