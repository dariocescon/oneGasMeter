package com.aton.proj.oneGasMeter.server;

import com.aton.proj.oneGasMeter.config.DlmsClientConfig;
import com.aton.proj.oneGasMeter.dlms.DlmsMeterClient;
import com.aton.proj.oneGasMeter.dlms.transport.IncomingTcpTransport;
import com.aton.proj.oneGasMeter.exception.DlmsCommunicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import java.io.IOException;
import java.net.Socket;
import java.time.Instant;

/**
 * Manages a single inbound DLMS meter session in its own virtual thread.
 * <p>
 * For each meter-initiated TCP connection:
 * <ol>
 *   <li>A {@link DlmsMeterClient} is created via the {@link SessionFactory}.</li>
 *   <li>{@link DlmsMeterClient#connect()} is called to perform the DLMS handshake
 *       (AARQ → AARE).</li>
 *   <li>A {@link MeterSessionEvent} is published <em>synchronously</em>, giving
 *       listeners an opportunity to interact with the meter.</li>
 *   <li>The client is disconnected in a {@code finally} block, regardless of
 *       whether the session succeeded or failed.</li>
 * </ol>
 * </p>
 *
 * <h3>Error handling</h3>
 * <p>
 * {@link DlmsCommunicationException} and {@link IOException} are caught and logged;
 * they do <em>not</em> propagate to the executor.  If the {@link SessionFactory}
 * throws before a client can be created, the raw socket is closed directly.
 * </p>
 */
public class MeterSessionHandler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(MeterSessionHandler.class);

    /**
     * Factory for creating a {@link DlmsMeterClient} over an accepted socket.
     * <p>
     * The default implementation ({@link #DEFAULT_FACTORY}) wraps the socket in an
     * {@link IncomingTcpTransport} and creates a {@link DlmsMeterClient}.  A custom
     * factory can be supplied via the package-private constructor to facilitate
     * unit testing.
     * </p>
     */
    @FunctionalInterface
    interface SessionFactory {
        /**
         * Creates a (not yet connected) {@link DlmsMeterClient} for the given socket.
         *
         * @param socket the accepted meter socket
         * @param config inbound server configuration
         * @return a new, unconnected {@link DlmsMeterClient}
         * @throws IOException if the transport cannot be initialised
         */
        DlmsMeterClient create(Socket socket, DlmsInboundServerConfig config) throws IOException;
    }

    /** Production factory: wraps socket in IncomingTcpTransport and creates a DlmsMeterClient. */
    static final SessionFactory DEFAULT_FACTORY = (socket, config) -> {
        String meterIp = socket.getInetAddress().getHostAddress();
        DlmsClientConfig sessionConfig = config.toSessionConfig(meterIp);
        IncomingTcpTransport transport = new IncomingTcpTransport(socket, config.getSessionTimeoutMs());
        return new DlmsMeterClient(sessionConfig, transport);
    };

    private final Socket socket;
    private final DlmsInboundServerConfig config;
    private final ApplicationEventPublisher eventPublisher;
    private final SessionFactory sessionFactory;

    /**
     * Production constructor: uses {@link #DEFAULT_FACTORY}.
     *
     * @param socket         accepted meter socket
     * @param config         inbound server configuration
     * @param eventPublisher Spring event publisher
     */
    public MeterSessionHandler(Socket socket,
                                DlmsInboundServerConfig config,
                                ApplicationEventPublisher eventPublisher) {
        this(socket, config, eventPublisher, DEFAULT_FACTORY);
    }

    /**
     * Test constructor: uses the supplied factory.
     *
     * @param socket         accepted meter socket
     * @param config         inbound server configuration
     * @param eventPublisher Spring event publisher
     * @param sessionFactory factory for creating meter clients (injectable for tests)
     */
    MeterSessionHandler(Socket socket,
                        DlmsInboundServerConfig config,
                        ApplicationEventPublisher eventPublisher,
                        SessionFactory sessionFactory) {
        this.socket = socket;
        this.config = config;
        this.eventPublisher = eventPublisher;
        this.sessionFactory = sessionFactory;
    }

    /**
     * Runs the meter session:
     * create client → connect → publish event → disconnect (in finally).
     */
    @Override
    public void run() {
        String meterIp = socket.getInetAddress().getHostAddress();
        DlmsMeterClient client = null;
        try {
            client = sessionFactory.create(socket, config);
            client.connect();
            eventPublisher.publishEvent(new MeterSessionEvent(meterIp, Instant.now(), client));
        } catch (DlmsCommunicationException e) {
            log.error("DLMS communication error during session with {}: {}", meterIp, e.getMessage(), e);
        } catch (IOException e) {
            log.error("I/O error during session with {}: {}", meterIp, e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error during session with {}: {}", meterIp, e.getMessage(), e);
        } finally {
            if (client != null) {
                try {
                    client.disconnect();
                } catch (DlmsCommunicationException e) {
                    log.warn("Error disconnecting DLMS client for {}: {}", meterIp, e.getMessage());
                }
            } else {
                // Factory failed before a client was created — close the raw socket
                try {
                    socket.close();
                } catch (IOException e) {
                    log.warn("Error closing socket for {}: {}", meterIp, e.getMessage());
                }
            }
        }
    }
}
