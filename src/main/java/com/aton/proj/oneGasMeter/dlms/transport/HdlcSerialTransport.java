package com.aton.proj.oneGasMeter.dlms.transport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * DLMS HDLC transport over a serial (or optical) interface.
 * <p>
 * This class is agnostic of the underlying serial library: it accepts a
 * plain {@link InputStream} and {@link OutputStream} so that any serial
 * library (e.g. jSerialComm, RXTXcomm, purejavacomm) can be used to obtain
 * the streams and inject them here.
 * </p>
 *
 * <h3>HDLC frame framing</h3>
 * <p>
 * HDLC frames are delimited by a flag byte ({@code 0x7E}) at both ends.
 * Any occurrence of {@code 0x7E} or {@code 0x7D} inside the data is
 * byte-stuffed by the gurux.dlms library.  This class only deals with the
 * raw byte stream and locates frame boundaries by the {@code 0x7E} flags.
 * </p>
 */
public class HdlcSerialTransport implements DlmsTransport {

    /** HDLC frame delimiter byte. */
    static final byte HDLC_FLAG = 0x7E;

    private final InputStream inputStream;
    private final OutputStream outputStream;

    private volatile boolean connected;

    /**
     * Creates an HDLC transport backed by the supplied streams.
     *
     * @param inputStream  stream from which HDLC frame bytes are read
     * @param outputStream stream to which HDLC frame bytes are written
     */
    public HdlcSerialTransport(InputStream inputStream, OutputStream outputStream) {
        this.inputStream = inputStream;
        this.outputStream = outputStream;
    }

    /**
     * Marks the transport as connected.
     * <p>
     * The physical serial port must already be open before calling this method.
     * Callers are responsible for opening the serial port (e.g. via jSerialComm)
     * and supplying the resulting streams to the constructor.
     * </p>
     */
    @Override
    public void connect() throws IOException {
        connected = true;
    }

    /**
     * Marks the transport as disconnected and closes both streams.
     *
     * @throws IOException if closing fails
     */
    @Override
    public void disconnect() throws IOException {
        connected = false;
        try {
            inputStream.close();
        } finally {
            outputStream.close();
        }
    }

    /**
     * Writes {@code data} to the output stream and flushes it.
     *
     * @param data complete HDLC frame bytes as produced by gurux.dlms
     * @throws IOException if the write fails
     */
    @Override
    public void write(byte[] data) throws IOException {
        outputStream.write(data);
        outputStream.flush();
    }

    /**
     * Reads one complete HDLC frame from the input stream.
     * <p>
     * The method scans for the opening {@code 0x7E} flag, accumulates bytes
     * until the closing {@code 0x7E} flag is found, and returns the full
     * frame including both flags.
     * </p>
     *
     * @return complete raw HDLC frame bytes
     * @throws IOException if the stream ends before a complete frame is received
     */
    @Override
    public byte[] read() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        boolean frameStarted = false;
        int b;

        while ((b = inputStream.read()) != -1) {
            if ((byte) b == HDLC_FLAG) {
                if (!frameStarted) {
                    frameStarted = true;
                    buffer.write(b);
                } else if (buffer.size() > 1) {
                    buffer.write(b);
                    return buffer.toByteArray();
                }
                // A lone flag with nothing between the two flags is an inter-frame
                // fill; reset and look for the next real frame start.
            } else if (frameStarted) {
                buffer.write(b);
            }
        }
        throw new IOException("Connection closed before a complete HDLC frame was received");
    }

    /**
     * Returns {@code true} if {@link #connect()} has been called and
     * {@link #disconnect()} has not.
     *
     * @return connection state
     */
    @Override
    public boolean isConnected() {
        return connected;
    }
}
