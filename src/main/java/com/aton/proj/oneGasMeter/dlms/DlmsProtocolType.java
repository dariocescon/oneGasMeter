package com.aton.proj.oneGasMeter.dlms;

import gurux.dlms.enums.InterfaceType;

/**
 * Supported DLMS/COSEM communication protocols.
 * <p>
 * Maps each protocol type to the corresponding {@link InterfaceType} used by the
 * gurux.dlms library.
 * </p>
 */
public enum DlmsProtocolType {

    /**
     * HDLC over serial/optical interface (IEC 62056-46).
     * Used with serial ports, optical probes, or RS-485 connections.
     */
    HDLC(InterfaceType.HDLC),

    /**
     * HDLC with Mode-E negotiation (IEC 62056-21).
     * Used for optical head connections that start at 300 baud and negotiate a higher speed.
     */
    HDLC_WITH_MODE_E(InterfaceType.HDLC_WITH_MODE_E),

    /**
     * DLMS WRAPPER over TCP/IP (IEC 62056-47).
     * Used for network connections (Ethernet, NB-IoT, GPRS, etc.).
     */
    WRAPPER(InterfaceType.WRAPPER),

    /**
     * Power Line Communication using PRIME or G3-PLC.
     */
    PLC(InterfaceType.PLC),

    /**
     * Power Line Communication with HDLC framing.
     */
    PLC_HDLC(InterfaceType.PLC_HDLC);

    private final InterfaceType interfaceType;

    DlmsProtocolType(InterfaceType interfaceType) {
        this.interfaceType = interfaceType;
    }

    /**
     * Returns the corresponding gurux.dlms {@link InterfaceType}.
     *
     * @return the gurux InterfaceType for this protocol
     */
    public InterfaceType getInterfaceType() {
        return interfaceType;
    }

    /**
     * Returns {@code true} if this protocol uses HDLC framing
     * (requires a serial/optical transport).
     *
     * @return {@code true} for HDLC and HDLC_WITH_MODE_E
     */
    public boolean isHdlcBased() {
        return this == HDLC || this == HDLC_WITH_MODE_E;
    }

    /**
     * Returns {@code true} if this protocol uses TCP/IP transport.
     *
     * @return {@code true} for WRAPPER
     */
    public boolean isTcpBased() {
        return this == WRAPPER;
    }
}
