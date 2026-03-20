package com.aton.proj.oneGasMeter.config;

import com.aton.proj.oneGasMeter.server.DlmsInboundServer;
import com.aton.proj.oneGasMeter.server.DlmsInboundServerConfig;
import gurux.dlms.enums.Authentication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring auto-configuration for the DLMS inbound (meter-initiated) TCP server.
 * <p>
 * Activated only when {@code dlms.inbound.enabled=true} is present in the
 * application properties.  This prevents the server from binding a port during
 * integration tests or when running in outbound-only mode.
 * </p>
 *
 * <h3>Configuration properties</h3>
 * <pre>
 *   dlms.inbound.enabled          = true          # required to activate
 *   dlms.inbound.port             = 4059           # TCP listen port (default: 4059)
 *   dlms.inbound.session-timeout-ms = 30000        # per-session timeout (default: 30 000 ms)
 *   dlms.inbound.client-address   = 16             # DLMS client address (default: 16)
 *   dlms.inbound.server-address   = 1              # DLMS server address (default: 1)
 *   dlms.inbound.authentication   = NONE           # Authentication level (default: NONE)
 *   dlms.inbound.password         =                # Password (optional)
 *   dlms.inbound.max-connections    = 10000          # Max concurrent sessions (default: 10 000)
 *   dlms.inbound.backlog            = 1024           # TCP accept backlog (default: 1 024)
 * </pre>
 */
@Configuration
@ConditionalOnProperty(name = "dlms.inbound.enabled", havingValue = "true", matchIfMissing = false)
public class DlmsInboundAutoConfiguration {

    @Value("${dlms.inbound.port:4059}")
    private int listenPort;

    @Value("${dlms.inbound.session-timeout-ms:30000}")
    private int sessionTimeoutMs;

    @Value("${dlms.inbound.client-address:16}")
    private int clientAddress;

    @Value("${dlms.inbound.server-address:1}")
    private int serverAddress;

    @Value("${dlms.inbound.authentication:NONE}")
    private Authentication authentication;

    @Value("${dlms.inbound.password:#{null}}")
    private String password;

    @Value("${dlms.inbound.max-connections:10000}")
    private int maxConnections;

    @Value("${dlms.inbound.backlog:1024}")
    private int backlog;

    /**
     * Creates the {@link DlmsInboundServerConfig} bean from application properties.
     *
     * @return configured inbound server config
     */
    @Bean
    public DlmsInboundServerConfig dlmsInboundServerConfig() {
        return DlmsInboundServerConfig.builder()
                .listenPort(listenPort)
                .sessionTimeoutMs(sessionTimeoutMs)
                .clientAddress(clientAddress)
                .serverAddress(serverAddress)
                .authentication(authentication)
                .password(password)
                .maxConnections(maxConnections)
                .backlog(backlog)
                .build();
    }

    /**
     * Creates the {@link DlmsInboundServer} bean.
     * <p>
     * The server implements {@link org.springframework.boot.CommandLineRunner}:
     * Spring Boot calls {@code run()} after context initialization, which blocks
     * the main thread while the server is listening.
     * </p>
     *
     * @param serverConfig   inbound server configuration
     * @param eventPublisher Spring application event publisher
     * @return the configured inbound server
     */
    @Bean
    public DlmsInboundServer dlmsInboundServer(DlmsInboundServerConfig serverConfig,
                                                ApplicationEventPublisher eventPublisher) {
        return new DlmsInboundServer(serverConfig, eventPublisher);
    }
}
