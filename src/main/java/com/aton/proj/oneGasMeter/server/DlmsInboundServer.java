package com.aton.proj.oneGasMeter.server;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TCP server that accepts inbound DLMS connections from meters.
 * <p>
 * This bean is <em>not</em> annotated with {@code @Component} and must be created
 * by {@link DlmsInboundAutoConfiguration} (which is only activated when
 * {@code dlms.inbound.enabled=true}).  This prevents the server from binding a
 * TCP port during integration tests that do not set that property.
 * </p>
 *
 * <h3>Lifecycle</h3>
 * <ul>
 *   <li>{@link #start()} — called by Spring after construction ({@code @PostConstruct});
 *       starts the accept loop in a dedicated virtual thread.</li>
 *   <li>{@link #stop()} — called by Spring on shutdown ({@code @PreDestroy});
 *       closes the server socket and shuts down the session executor.</li>
 * </ul>
 *
 * <h3>Concurrency</h3>
 * <p>
 * Each accepted connection is handled in a separate Java 21 virtual thread via
 * {@link Executors#newVirtualThreadPerTaskExecutor()}.  Virtual threads are
 * ideal for blocking I/O (DLMS frame read/write) and allow thousands of
 * concurrent meter sessions with minimal overhead.
 * </p>
 */
public class DlmsInboundServer {

    private static final Logger log = LoggerFactory.getLogger(DlmsInboundServer.class);

    /**
     * Factory for creating a {@link ServerSocket} bound to a given port.
     * <p>
     * The production factory is simply {@code ServerSocket::new} (method reference
     * to {@code new ServerSocket(int)}).  A mock factory can be supplied via the
     * package-private constructor to facilitate unit testing without real ports.
     * </p>
     */
    @FunctionalInterface
    interface ServerSocketFactory {
        /**
         * Creates and binds a {@link ServerSocket} to the given port.
         *
         * @param port TCP port to listen on
         * @return a bound, listening {@link ServerSocket}
         * @throws IOException if binding fails (e.g. port already in use)
         */
        ServerSocket create(int port) throws IOException;
    }

    private final DlmsInboundServerConfig config;
    private final ApplicationEventPublisher eventPublisher;
    private final ServerSocketFactory serverSocketFactory;

    private volatile ServerSocket serverSocket;
    private ExecutorService sessionExecutor;

    /**
     * Production constructor: binds a real {@link ServerSocket}.
     *
     * @param config         inbound server configuration
     * @param eventPublisher Spring event publisher
     */
    public DlmsInboundServer(DlmsInboundServerConfig config, ApplicationEventPublisher eventPublisher) {
        this(config, eventPublisher, ServerSocket::new);
    }

    /**
     * Test constructor: uses a custom {@link ServerSocketFactory}.
     *
     * @param config              inbound server configuration
     * @param eventPublisher      Spring event publisher
     * @param serverSocketFactory custom factory (injectable for unit tests)
     */
    DlmsInboundServer(DlmsInboundServerConfig config,
                      ApplicationEventPublisher eventPublisher,
                      ServerSocketFactory serverSocketFactory) {
        this.config = config;
        this.eventPublisher = eventPublisher;
        this.serverSocketFactory = serverSocketFactory;
    }

    /**
     * Starts the accept loop in a virtual thread.
     * <p>
     * Called by Spring after bean construction ({@code @PostConstruct}).
     * </p>
     */
    @PostConstruct
    public void start() {
        sessionExecutor = Executors.newVirtualThreadPerTaskExecutor();
        Thread.ofVirtual()
              .name("dlms-inbound-server")
              .start(this::acceptLoop);
        log.info("DLMS inbound server starting on port {}", config.getListenPort());
    }

    /**
     * Stops the server by closing the server socket and shutting down the executor.
     * <p>
     * Called by Spring on application shutdown ({@code @PreDestroy}).
     * Closing the server socket causes the accept loop's {@code accept()} call to
     * throw a {@link java.net.SocketException}, which terminates the loop.
     * </p>
     */
    @PreDestroy
    public void stop() {
        log.info("Stopping DLMS inbound server");
        ServerSocket ss = this.serverSocket;
        if (ss != null && !ss.isClosed()) {
            try {
                ss.close();
            } catch (IOException e) {
                log.warn("Error closing DLMS inbound server socket: {}", e.getMessage());
            }
        }
        if (sessionExecutor != null) {
            sessionExecutor.shutdownNow();
        }
    }

    // -----------------------------------------------------------------------

    private void acceptLoop() {
        ServerSocket ss = null;
        try {
            ss = serverSocketFactory.create(config.getListenPort());
            this.serverSocket = ss;
            log.info("DLMS inbound server listening on port {}", config.getListenPort());

            while (!ss.isClosed()) {
                try {
                    Socket meterSocket = ss.accept();
                    sessionExecutor.submit(
                            new MeterSessionHandler(meterSocket, config, eventPublisher));
                } catch (IOException e) {
                    if (!ss.isClosed()) {
                        log.error("Error accepting inbound DLMS connection: {}", e.getMessage(), e);
                    }
                    // If ss is closed, while condition will be false → loop exits
                }
            }
        } catch (IOException e) {
            log.error("Failed to bind DLMS inbound server to port {}: {}",
                    config.getListenPort(), e.getMessage(), e);
        } finally {
            if (ss != null && !ss.isClosed()) {
                try {
                    ss.close();
                } catch (IOException ignored) {
                }
            }
        }
        log.info("DLMS inbound server stopped");
    }
}
