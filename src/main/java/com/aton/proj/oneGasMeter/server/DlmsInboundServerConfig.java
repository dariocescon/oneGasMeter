package com.aton.proj.oneGasMeter.server;

import com.aton.proj.oneGasMeter.config.DlmsClientConfig;
import gurux.dlms.enums.Authentication;

/**
 * Immutable configuration for the DLMS inbound (server-side) TCP listener.
 * <p>
 * When meters initiate connections to the application, each accepted socket
 * is wrapped in an {@link com.aton.proj.oneGasMeter.dlms.transport.IncomingTcpTransport}
 * and a {@link com.aton.proj.oneGasMeter.dlms.DlmsMeterClient} is created using
 * the DLMS session parameters defined here.
 * </p>
 * <p>
 * Use the nested {@link Builder} to construct instances. Default values:
 * <ul>
 *   <li>Listen port: 4059 (IANA assigned DLMS port)</li>
 *   <li>Session timeout: 30 000 ms</li>
 *   <li>Client address: 16 (public client)</li>
 *   <li>Server address: 1</li>
 *   <li>Authentication: {@link Authentication#NONE}</li>
 *   <li>Logical-name referencing: {@code true}</li>
 * </ul>
 * </p>
 */
public class DlmsInboundServerConfig {

    private final int listenPort;
    private final int sessionTimeoutMs;
    private final int clientAddress;
    private final int serverAddress;
    private final Authentication authentication;
    private final String password;
    private final boolean useLogicalNameReferencing;

    private DlmsInboundServerConfig(Builder builder) {
        this.listenPort = builder.listenPort;
        this.sessionTimeoutMs = builder.sessionTimeoutMs;
        this.clientAddress = builder.clientAddress;
        this.serverAddress = builder.serverAddress;
        this.authentication = builder.authentication;
        this.password = builder.password;
        this.useLogicalNameReferencing = builder.useLogicalNameReferencing;
    }

    /** TCP port the server listens on (default: 4059). */
    public int getListenPort() {
        return listenPort;
    }

    /** Read/write timeout in milliseconds for each meter session (default: 30 000). */
    public int getSessionTimeoutMs() {
        return sessionTimeoutMs;
    }

    /** DLMS client (SAP) address used for meter sessions (default: 16 – public client). */
    public int getClientAddress() {
        return clientAddress;
    }

    /** DLMS server (logical device) address used for meter sessions (default: 1). */
    public int getServerAddress() {
        return serverAddress;
    }

    /** Authentication level used for meter sessions (default: {@link Authentication#NONE}). */
    public Authentication getAuthentication() {
        return authentication;
    }

    /** Password for LOW / HIGH authentication; {@code null} if not required. */
    public String getPassword() {
        return password;
    }

    /** Whether to use logical-name (LN) referencing; {@code false} = short-name (default: {@code true}). */
    public boolean isUseLogicalNameReferencing() {
        return useLogicalNameReferencing;
    }

    /**
     * Builds a {@link DlmsClientConfig} suitable for a single meter session.
     * <p>
     * The meter's IP address is used as the {@code host} field so that the
     * {@link DlmsClientConfig} validation passes (TCP-based protocols require a
     * non-blank host).
     * </p>
     *
     * @param meterIp IP address of the connected meter
     * @return a {@link DlmsClientConfig} configured for this session
     */
    public DlmsClientConfig toSessionConfig(String meterIp) {
        return DlmsClientConfig.builder()
                .host(meterIp)
                .port(listenPort)
                .clientAddress(clientAddress)
                .serverAddress(serverAddress)
                .authentication(authentication)
                .password(password)
                .useLogicalNameReferencing(useLogicalNameReferencing)
                .connectionTimeoutMs(sessionTimeoutMs)
                .build();
    }

    /**
     * Creates a new builder for {@link DlmsInboundServerConfig}.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link DlmsInboundServerConfig}.
     */
    public static class Builder {

        private int listenPort = 4059;
        private int sessionTimeoutMs = 30_000;
        private int clientAddress = 16;
        private int serverAddress = 1;
        private Authentication authentication = Authentication.NONE;
        private String password;
        private boolean useLogicalNameReferencing = true;

        /** TCP port to listen on (default: 4059). */
        public Builder listenPort(int listenPort) {
            this.listenPort = listenPort;
            return this;
        }

        /** Read/write timeout in milliseconds for each session (default: 30 000). */
        public Builder sessionTimeoutMs(int sessionTimeoutMs) {
            this.sessionTimeoutMs = sessionTimeoutMs;
            return this;
        }

        /** DLMS client (SAP) address (default: 16 – public client). */
        public Builder clientAddress(int clientAddress) {
            this.clientAddress = clientAddress;
            return this;
        }

        /** DLMS server (logical device) address (default: 1). */
        public Builder serverAddress(int serverAddress) {
            this.serverAddress = serverAddress;
            return this;
        }

        /** Authentication level (default: {@link Authentication#NONE}). */
        public Builder authentication(Authentication authentication) {
            this.authentication = authentication;
            return this;
        }

        /** Password for LOW / HIGH authentication. */
        public Builder password(String password) {
            this.password = password;
            return this;
        }

        /** Use logical-name (LN) referencing (default: {@code true}). */
        public Builder useLogicalNameReferencing(boolean useLogicalNameReferencing) {
            this.useLogicalNameReferencing = useLogicalNameReferencing;
            return this;
        }

        /**
         * Validates and builds the {@link DlmsInboundServerConfig}.
         *
         * @return immutable config instance
         * @throws IllegalArgumentException if required fields are invalid
         */
        public DlmsInboundServerConfig build() {
            validate();
            return new DlmsInboundServerConfig(this);
        }

        private void validate() {
            if (listenPort < 1 || listenPort > 65535) {
                throw new IllegalArgumentException("Listen port must be between 1 and 65535, got: " + listenPort);
            }
            if (sessionTimeoutMs <= 0) {
                throw new IllegalArgumentException("Session timeout must be positive, got: " + sessionTimeoutMs);
            }
            if (authentication == null) {
                throw new IllegalArgumentException("Authentication must not be null");
            }
        }
    }
}
