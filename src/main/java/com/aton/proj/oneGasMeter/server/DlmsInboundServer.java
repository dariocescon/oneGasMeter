package com.aton.proj.oneGasMeter.server;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationEventPublisher;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * TCP server that accepts inbound DLMS connections from meters.
 * <p>
 * This bean is <em>not</em> annotated with {@code @Component} and must be created
 * by {@link com.aton.proj.oneGasMeter.config.DlmsInboundAutoConfiguration} (which is
 * only activated when {@code dlms.inbound.enabled=true}).  This prevents the server
 * from binding a TCP port during integration tests that do not set that property.
 * </p>
 *
 * <h3>Lifecycle</h3>
 * <ul>
 *   <li>{@link #run(String...)} — called by Spring Boot's {@link CommandLineRunner}
 *       infrastructure after context initialization; blocks the main thread while the
 *       server is listening (keeping the JVM alive).</li>
 *   <li>{@link #shutdown()} — called by Spring on application shutdown
 *       ({@code @PreDestroy}); sets the running flag, closes the server socket
 *       (which unblocks {@code accept()}), and awaits graceful session termination.</li>
 * </ul>
 *
 * <h3>Concurrency</h3>
 * <p>
 * Each accepted connection is handled in a separate Java 21 virtual thread via
 * {@link Executors#newVirtualThreadPerTaskExecutor()}.  A {@link Semaphore} limits
 * the number of concurrent sessions to {@link DlmsInboundServerConfig#getMaxConnections()}.
 * </p>
 */
public class DlmsInboundServer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DlmsInboundServer.class);

    /**
     * Factory for creating a {@link ServerSocket} bound to a given port with a
     * specified accept backlog.
     * <p>
     * The production factory is simply {@code ServerSocket::new} (method reference
     * to {@code new ServerSocket(int, int)}).  A mock factory can be supplied via the
     * package-private constructor to facilitate unit testing without real ports.
     * </p>
     */
    @FunctionalInterface
    interface ServerSocketFactory {
        /**
         * Creates and binds a {@link ServerSocket} to the given port.
         *
         * @param port    TCP port to listen on
         * @param backlog maximum number of pending connections in the OS queue
         * @return a bound, listening {@link ServerSocket}
         * @throws IOException if binding fails (e.g. port already in use)
         */
        ServerSocket create(int port, int backlog) throws IOException;
    }

    private final DlmsInboundServerConfig config;
    private final ApplicationEventPublisher eventPublisher;
    private final ServerSocketFactory serverSocketFactory;
    private final ExecutorService executorService;
    private final Semaphore connectionLimiter;

    private volatile boolean running = false;
    private volatile ServerSocket serverSocket;

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
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
        this.connectionLimiter = new Semaphore(config.getMaxConnections(), true);
    }

    /**
     * Starts the TCP server and blocks until {@link #shutdown()} is called.
     * <p>
     * Called by Spring Boot's {@link CommandLineRunner} infrastructure.
     * Blocking the main thread keeps the JVM alive without requiring
     * {@code spring.main.keep-alive=true}.
     * </p>
     *
     * @param args command-line arguments (not used)
     * @throws RuntimeException if the server socket cannot be bound
     */
    @Override
    public void run(String... args) {
        startServer();
    }

    // -----------------------------------------------------------------------

    private void startServer() {
        try {
            serverSocket = serverSocketFactory.create(config.getListenPort(), config.getBacklog());
            running = true;
            log.info("DLMS inbound server started on port {}", config.getListenPort());
            log.info("Max concurrent connections: {}", config.getMaxConnections());
            acceptConnections();
        } catch (IOException e) {
            log.error("Failed to start DLMS inbound server on port {}: {}",
                    config.getListenPort(), e.getMessage(), e);
            throw new RuntimeException("Cannot start DLMS inbound server", e);
        }
    }

    private void acceptConnections() {
        while (running) {
            try {
                connectionLimiter.acquire();
                int available = connectionLimiter.availablePermits();
                if (available < config.getMaxConnections() * 0.1) {
                    log.warn("Connection limiter running low: {}/{} permits available",
                            available, config.getMaxConnections());
                }
                Socket meterSocket = serverSocket.accept();
                executorService.submit(() -> {
                    try {
                        new MeterSessionHandler(meterSocket, config, eventPublisher).run();
                    } finally {
                        connectionLimiter.release();
                    }
                });
            } catch (InterruptedException e) {
                if (running) {
                    log.warn("Connection limiter interrupted unexpectedly");
                    Thread.currentThread().interrupt();
                }
            } catch (IOException e) {
                if (running) {
                    log.error("Error accepting inbound DLMS connection: {}", e.getMessage(), e);
                }
                connectionLimiter.release();
            }
        }
    }

    /**
     * Stops the server gracefully.
     * <p>
     * Called by Spring on application shutdown ({@code @PreDestroy}).
     * Sets the running flag to {@code false}, closes the server socket (which
     * causes {@code accept()} to throw, exiting the accept loop), then waits up
     * to 5 seconds for active sessions to complete before forcing shutdown.
     * </p>
     */
    @PreDestroy
    public void shutdown() {
        log.info("Stopping DLMS inbound server");
        running = false;
        try {
            ServerSocket ss = this.serverSocket;
            if (ss != null && !ss.isClosed()) {
                ss.close();
            }
            executorService.shutdown();
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            log.info("DLMS inbound server stopped. Connection limiter: {}/{} permits available",
                    connectionLimiter.availablePermits(), config.getMaxConnections());
        } catch (IOException | InterruptedException e) {
            log.error("Error during DLMS inbound server shutdown: {}", e.getMessage());
        }
    }
}
