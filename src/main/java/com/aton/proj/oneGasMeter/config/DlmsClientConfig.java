package com.aton.proj.oneGasMeter.config;

import com.aton.proj.oneGasMeter.dlms.DlmsProtocolType;
import gurux.dlms.enums.Authentication;

/**
 * Immutable configuration for a DLMS/COSEM meter client connection.
 * <p>
 * Use the nested {@link Builder} to construct instances. Default values follow
 * the DLMS/COSEM standard recommendations:
 * <ul>
 *   <li>Protocol: {@link DlmsProtocolType#WRAPPER}</li>
 *   <li>TCP port: 4059 (IANA assigned DLMS port)</li>
 *   <li>Client address: 16 (public client)</li>
 *   <li>Server address: 1</li>
 *   <li>Authentication: {@link Authentication#NONE}</li>
 *   <li>Logical-name referencing: {@code true}</li>
 *   <li>Connection timeout: 5 000 ms</li>
 * </ul>
 * </p>
 */
public class DlmsClientConfig {

    private final String host;
    private final int port;
    private final String serialPort;
    private final int baudRate;
    private final int clientAddress;
    private final int serverAddress;
    private final DlmsProtocolType protocolType;
    private final Authentication authentication;
    private final String password;
    private final boolean useLogicalNameReferencing;
    private final int connectionTimeoutMs;

    private DlmsClientConfig(Builder builder) {
        this.host = builder.host;
        this.port = builder.port;
        this.serialPort = builder.serialPort;
        this.baudRate = builder.baudRate;
        this.clientAddress = builder.clientAddress;
        this.serverAddress = builder.serverAddress;
        this.protocolType = builder.protocolType;
        this.authentication = builder.authentication;
        this.password = builder.password;
        this.useLogicalNameReferencing = builder.useLogicalNameReferencing;
        this.connectionTimeoutMs = builder.connectionTimeoutMs;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getSerialPort() {
        return serialPort;
    }

    public int getBaudRate() {
        return baudRate;
    }

    public int getClientAddress() {
        return clientAddress;
    }

    public int getServerAddress() {
        return serverAddress;
    }

    public DlmsProtocolType getProtocolType() {
        return protocolType;
    }

    public Authentication getAuthentication() {
        return authentication;
    }

    public String getPassword() {
        return password;
    }

    public boolean isUseLogicalNameReferencing() {
        return useLogicalNameReferencing;
    }

    public int getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    /**
     * Creates a new builder for {@link DlmsClientConfig}.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link DlmsClientConfig}.
     */
    public static class Builder {

        private String host;
        private int port = 4059;
        private String serialPort;
        private int baudRate = 9600;
        private int clientAddress = 16;
        private int serverAddress = 1;
        private DlmsProtocolType protocolType = DlmsProtocolType.WRAPPER;
        private Authentication authentication = Authentication.NONE;
        private String password;
        private boolean useLogicalNameReferencing = true;
        private int connectionTimeoutMs = 5000;

        /** TCP/IP hostname or IP address (required for WRAPPER protocol). */
        public Builder host(String host) {
            this.host = host;
            return this;
        }

        /** TCP port number (default: 4059). */
        public Builder port(int port) {
            this.port = port;
            return this;
        }

        /** Serial port name, e.g. {@code /dev/ttyUSB0} or {@code COM3} (required for HDLC). */
        public Builder serialPort(String serialPort) {
            this.serialPort = serialPort;
            return this;
        }

        /** Serial baud rate (default: 9600). */
        public Builder baudRate(int baudRate) {
            this.baudRate = baudRate;
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

        /** Communication protocol (default: {@link DlmsProtocolType#WRAPPER}). */
        public Builder protocolType(DlmsProtocolType protocolType) {
            this.protocolType = protocolType;
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

        /** Use logical-name (LN) referencing; {@code false} = short-name (SN) referencing (default: {@code true}). */
        public Builder useLogicalNameReferencing(boolean useLogicalNameReferencing) {
            this.useLogicalNameReferencing = useLogicalNameReferencing;
            return this;
        }

        /** TCP socket / serial read timeout in milliseconds (default: 5 000). */
        public Builder connectionTimeoutMs(int connectionTimeoutMs) {
            this.connectionTimeoutMs = connectionTimeoutMs;
            return this;
        }

        /**
         * Validates and builds the {@link DlmsClientConfig}.
         *
         * @return immutable config instance
         * @throws IllegalArgumentException if required fields are missing
         */
        public DlmsClientConfig build() {
            validate();
            return new DlmsClientConfig(this);
        }

        private void validate() {
            if (protocolType == null) {
                throw new IllegalArgumentException("Protocol type must not be null");
            }
            if (protocolType.isTcpBased() && (host == null || host.isBlank())) {
                throw new IllegalArgumentException("Host is required for " + protocolType + " protocol");
            }
            if (protocolType.isHdlcBased() && (serialPort == null || serialPort.isBlank())) {
                throw new IllegalArgumentException("Serial port is required for " + protocolType + " protocol");
            }
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Port must be between 1 and 65535");
            }
            if (connectionTimeoutMs <= 0) {
                throw new IllegalArgumentException("Connection timeout must be positive");
            }
        }
    }
}
