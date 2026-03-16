package com.aton.proj.oneGasMeter.dlms;

import com.aton.proj.oneGasMeter.config.DlmsClientConfig;
import com.aton.proj.oneGasMeter.dlms.transport.DlmsTransport;
import com.aton.proj.oneGasMeter.dlms.transport.HdlcSerialTransport;
import com.aton.proj.oneGasMeter.dlms.transport.TcpDlmsTransport;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Spring-managed factory for creating {@link DlmsMeterClient} instances.
 * <p>
 * Automatically selects the correct {@link DlmsTransport} implementation based
 * on the {@link DlmsClientConfig#getProtocolType()} value:
 * <ul>
 *   <li>{@link DlmsProtocolType#WRAPPER} → {@link TcpDlmsTransport}</li>
 *   <li>{@link DlmsProtocolType#HDLC} or {@link DlmsProtocolType#HDLC_WITH_MODE_E}
 *       → {@link HdlcSerialTransport} (streams must be supplied externally)</li>
 * </ul>
 * </p>
 *
 * <h3>HDLC / serial usage</h3>
 * <p>
 * Because the Java SE standard library does not include serial-port APIs, HDLC
 * clients require the caller to open the serial port using their preferred
 * library (e.g. jSerialComm, RXTXcomm) and pass the resulting
 * {@link InputStream}/{@link OutputStream} via
 * {@link #createHdlcClient(DlmsClientConfig, InputStream, OutputStream)}.
 * </p>
 */
@Component
public class DlmsConnectionFactory {

    /**
     * Creates a {@link DlmsMeterClient} for the given configuration.
     * <p>
     * For TCP-based protocols (WRAPPER) the transport is created automatically.
     * For HDLC-based protocols use
     * {@link #createHdlcClient(DlmsClientConfig, InputStream, OutputStream)} instead.
     * </p>
     *
     * @param config connection configuration
     * @return a ready-to-use (but not yet connected) {@link DlmsMeterClient}
     * @throws UnsupportedOperationException if the protocol requires serial streams
     *                                       and none have been provided
     */
    public DlmsMeterClient createClient(DlmsClientConfig config) {
        DlmsTransport transport = buildTransport(config);
        return new DlmsMeterClient(config, transport);
    }

    /**
     * Creates a {@link DlmsMeterClient} with an explicitly supplied transport.
     * <p>
     * Use this overload when you need a custom transport (e.g. for testing or
     * for serial-port libraries that do not expose plain {@link InputStream}/
     * {@link OutputStream}).
     * </p>
     *
     * @param config    connection configuration
     * @param transport custom transport implementation
     * @return a ready-to-use (but not yet connected) {@link DlmsMeterClient}
     */
    public DlmsMeterClient createClient(DlmsClientConfig config, DlmsTransport transport) {
        return new DlmsMeterClient(config, transport);
    }

    /**
     * Creates an HDLC {@link DlmsMeterClient} backed by the supplied serial streams.
     *
     * @param config       HDLC connection configuration
     * @param inputStream  byte stream from the serial/optical port
     * @param outputStream byte stream to the serial/optical port
     * @return a ready-to-use (but not yet connected) {@link DlmsMeterClient}
     */
    public DlmsMeterClient createHdlcClient(DlmsClientConfig config,
                                             InputStream inputStream,
                                             OutputStream outputStream) {
        HdlcSerialTransport transport = new HdlcSerialTransport(inputStream, outputStream);
        return new DlmsMeterClient(config, transport);
    }

    // -----------------------------------------------------------------------

    private DlmsTransport buildTransport(DlmsClientConfig config) {
        DlmsProtocolType type = config.getProtocolType();
        if (type.isTcpBased()) {
            return new TcpDlmsTransport(
                    config.getHost(),
                    config.getPort(),
                    config.getConnectionTimeoutMs());
        }
        if (type.isHdlcBased()) {
            throw new UnsupportedOperationException(
                    "HDLC transport requires serial streams. "
                    + "Use createHdlcClient(config, inputStream, outputStream) instead.");
        }
        throw new UnsupportedOperationException(
                "Protocol type not supported by the automatic factory: " + type
                + ". Use createClient(config, customTransport) instead.");
    }
}
