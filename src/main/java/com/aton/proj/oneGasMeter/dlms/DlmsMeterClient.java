package com.aton.proj.oneGasMeter.dlms;

import com.aton.proj.oneGasMeter.config.DlmsClientConfig;
import com.aton.proj.oneGasMeter.dlms.transport.DlmsTransport;
import com.aton.proj.oneGasMeter.exception.DlmsCommunicationException;
import com.aton.proj.oneGasMeter.model.MeterReading;
import gurux.dlms.GXByteBuffer;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.GXReplyData;
import gurux.dlms.enums.InterfaceType;
import gurux.dlms.objects.GXDLMSClock;
import gurux.dlms.objects.GXDLMSData;
import gurux.dlms.objects.GXDLMSObject;
import gurux.dlms.objects.GXDLMSObjectCollection;
import gurux.dlms.objects.GXDLMSProfileGeneric;
import gurux.dlms.objects.GXDLMSRegister;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * High-level DLMS/COSEM meter client.
 * <p>
 * Combines a {@link DlmsTransport} (physical channel) with the gurux.dlms
 * {@link GXDLMSClient} (protocol engine) to provide a simple API for:
 * <ul>
 *   <li>Connection management ({@link #connect()} / {@link #disconnect()})</li>
 *   <li>Object discovery ({@link #getObjects()})</li>
 *   <li>Reading DLMS registers ({@link #readRegister(String)})</li>
 *   <li>Reading generic data objects ({@link #readData(String)})</li>
 *   <li>Reading the meter clock ({@link #readClock()})</li>
 *   <li>Reading profile-generic (load profile) data ({@link #readProfileGeneric(String, Date, Date)})</li>
 * </ul>
 * </p>
 *
 * <h3>Thread safety</h3>
 * <p>
 * Instances of this class are <em>not</em> thread-safe.  Use one instance per
 * connection and protect concurrent access externally.
 * </p>
 */
public class DlmsMeterClient {

    private final DlmsClientConfig config;
    private final DlmsTransport transport;
    private final GXDLMSClient gxClient;

    /**
     * Creates a new meter client.
     *
     * @param config    connection configuration
     * @param transport physical transport channel
     */
    public DlmsMeterClient(DlmsClientConfig config, DlmsTransport transport) {
        this.config = config;
        this.transport = transport;
        this.gxClient = new GXDLMSClient(
                config.isUseLogicalNameReferencing(),
                config.getClientAddress(),
                config.getServerAddress(),
                config.getAuthentication(),
                config.getPassword(),
                config.getProtocolType().getInterfaceType());
    }

    /**
     * Establishes a DLMS/COSEM association with the meter.
     * <p>
     * For HDLC-based protocols the handshake is: SNRM → UA → AARQ → AARE.
     * For TCP/IP WRAPPER the handshake is: AARQ → AARE.
     * High-level authentication (challenge-response) is handled automatically
     * when required by the server.
     * </p>
     *
     * @throws DlmsCommunicationException if the connection or handshake fails
     */
    public void connect() throws DlmsCommunicationException {
        try {
            transport.connect();

            InterfaceType ifType = config.getProtocolType().getInterfaceType();
            if (ifType == InterfaceType.HDLC || ifType == InterfaceType.HDLC_WITH_MODE_E) {
                // HDLC handshake: SNRM → UA
                byte[] snrm = gxClient.snrmRequest();
                transport.write(snrm);
                byte[] uaReply = transport.read();
                gxClient.parseUAResponse(uaReply);
            }

            // Application association: AARQ → AARE
            byte[][] aarqFrames = gxClient.aarqRequest();
            for (byte[] frame : aarqFrames) {
                transport.write(frame);
            }
            byte[] aareReply = transport.read();
            gxClient.parseAareResponse(new GXByteBuffer(aareReply));

            // High-level security: challenge-response
            if (gxClient.getIsAuthenticationRequired()) {
                byte[][] challengeFrames = gxClient.getApplicationAssociationRequest();
                for (byte[] frame : challengeFrames) {
                    transport.write(frame);
                }
                byte[] challengeReply = transport.read();
                gxClient.parseApplicationAssociationResponse(new GXByteBuffer(challengeReply));
            }

        } catch (DlmsCommunicationException e) {
            throw e;
        } catch (Exception e) {
            throw new DlmsCommunicationException("Failed to connect to meter at "
                    + config.getHost() + ":" + config.getPort(), e);
        }
    }

    /**
     * Releases the DLMS association and closes the transport.
     * <p>
     * Any error during the DISC/RLRQ exchange is silently ignored so that the
     * transport is always closed.
     * </p>
     *
     * @throws DlmsCommunicationException if closing the transport fails
     */
    public void disconnect() throws DlmsCommunicationException {
        try {
            try {
                byte[] discFrame = gxClient.disconnectRequest();
                if (discFrame != null && discFrame.length > 0) {
                    transport.write(discFrame);
                    // Read and discard the disconnect response
                    transport.read();
                }
            } catch (Exception ignored) {
                // Disconnect response errors are non-fatal
            }
            transport.disconnect();
        } catch (IOException e) {
            throw new DlmsCommunicationException("Failed to disconnect from meter", e);
        }
    }

    /**
     * Retrieves the list of all DLMS objects supported by the meter.
     * <p>
     * Reads the Association Logical Name object (class 15, OBIS 0.0.40.0.0.255)
     * and returns the parsed object collection.
     * </p>
     *
     * @return list of {@link GXDLMSObject} instances reported by the meter
     * @throws DlmsCommunicationException if the read fails
     */
    public List<GXDLMSObject> getObjects() throws DlmsCommunicationException {
        try {
            byte[][] request = gxClient.getObjectsRequest();
            GXReplyData reply = sendReceive(request);
            GXDLMSObjectCollection collection = gxClient.parseObjects(reply.getData(), true);
            return new ArrayList<>(collection);
        } catch (DlmsCommunicationException e) {
            throw e;
        } catch (Exception e) {
            throw new DlmsCommunicationException("Failed to read object list", e);
        }
    }

    /**
     * Reads a DLMS Register object (class 3) by its OBIS code.
     * <p>
     * Attribute 2 (value) and attribute 3 (scaler + unit) are read.
     * </p>
     *
     * @param obisCode OBIS code string (e.g. {@code "1.0.1.8.0.255"})
     * @return {@link MeterReading} containing value, unit and scaler
     * @throws DlmsCommunicationException if the read fails
     */
    public MeterReading readRegister(String obisCode) throws DlmsCommunicationException {
        try {
            GXDLMSRegister register = new GXDLMSRegister(obisCode);

            // Read value (attribute 2)
            GXReplyData valueReply = sendReceive(gxClient.read(register, 2));
            gxClient.updateValue(register, 2, valueReply.getValue());

            // Read scaler + unit (attribute 3)
            GXReplyData scalerReply = sendReceive(gxClient.read(register, 3));
            gxClient.updateValue(register, 3, scalerReply.getValue());

            return MeterReading.builder()
                    .obisCode(obisCode)
                    .value(register.getValue())
                    .unit(register.getUnit() != null ? register.getUnit().toString() : "")
                    .scaler(register.getScaler())
                    .timestamp(Instant.now())
                    .build();

        } catch (DlmsCommunicationException e) {
            throw e;
        } catch (Exception e) {
            throw new DlmsCommunicationException("Failed to read register: " + obisCode, e);
        }
    }

    /**
     * Reads a DLMS Data object (class 1) by its OBIS code.
     *
     * @param obisCode OBIS code string (e.g. {@code "0.0.96.1.0.255"} for meter serial number)
     * @return {@link MeterReading} containing the raw value
     * @throws DlmsCommunicationException if the read fails
     */
    public MeterReading readData(String obisCode) throws DlmsCommunicationException {
        try {
            GXDLMSData data = new GXDLMSData(obisCode);
            GXReplyData reply = sendReceive(gxClient.read(data, 2));
            gxClient.updateValue(data, 2, reply.getValue());

            return MeterReading.builder()
                    .obisCode(obisCode)
                    .value(data.getValue())
                    .unit("")
                    .scaler(1.0)
                    .timestamp(Instant.now())
                    .build();

        } catch (DlmsCommunicationException e) {
            throw e;
        } catch (Exception e) {
            throw new DlmsCommunicationException("Failed to read data object: " + obisCode, e);
        }
    }

    /**
     * Reads the meter's real-time clock (class 8, OBIS {@code 0.0.1.0.0.255}).
     *
     * @return current meter time as a UTC {@link Instant}, or {@code null} if the
     *         clock is not available
     * @throws DlmsCommunicationException if the read fails
     */
    public Instant readClock() throws DlmsCommunicationException {
        try {
            GXDLMSClock clock = new GXDLMSClock("0.0.1.0.0.255");
            GXReplyData reply = sendReceive(gxClient.read(clock, 2));
            gxClient.updateValue(clock, 2, reply.getValue());

            if (clock.getTime() != null && clock.getTime().getMeterCalendar() != null) {
                return clock.getTime().getMeterCalendar().toInstant();
            }
            return null;

        } catch (DlmsCommunicationException e) {
            throw e;
        } catch (Exception e) {
            throw new DlmsCommunicationException("Failed to read meter clock", e);
        }
    }

    /**
     * Reads rows from a Profile Generic object (class 7) filtered by a date/time range.
     * <p>
     * Each element of the returned list represents one captured row; inner
     * array elements correspond to the profile's capture objects in order.
     * </p>
     *
     * @param obisCode OBIS code of the profile (e.g. {@code "1.0.99.1.0.255"})
     * @param from     start of the date/time range (inclusive)
     * @param to       end of the date/time range (inclusive)
     * @return list of row arrays; each row is an {@code Object[]} of captured values
     * @throws DlmsCommunicationException if the read fails
     */
    public List<Object[]> readProfileGeneric(String obisCode, Date from, Date to)
            throws DlmsCommunicationException {
        try {
            GXDLMSProfileGeneric profile = new GXDLMSProfileGeneric(obisCode);
            byte[][] request = gxClient.readRowsByRange(profile, from, to);
            GXReplyData reply = sendReceive(request);
            gxClient.updateValue(profile, 3, reply.getValue());

            List<Object[]> rows = new ArrayList<>();
            Object[] buffer = profile.getBuffer();
            if (buffer != null) {
                for (Object row : buffer) {
                    if (row instanceof Object[]) {
                        rows.add((Object[]) row);
                    }
                }
            }
            return rows;

        } catch (DlmsCommunicationException e) {
            throw e;
        } catch (Exception e) {
            throw new DlmsCommunicationException("Failed to read profile: " + obisCode, e);
        }
    }

    /**
     * Returns {@code true} if the underlying transport is currently connected.
     *
     * @return transport connection state
     */
    public boolean isConnected() {
        return transport.isConnected();
    }

    /**
     * Provides access to the underlying gurux {@link GXDLMSClient} for advanced usage.
     *
     * @return the configured {@link GXDLMSClient}
     */
    public GXDLMSClient getGxClient() {
        return gxClient;
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    /**
     * Sends all request frames and reads replies until the full response is assembled.
     * Handles DLMS multi-block transfers automatically by sending receiver-ready frames.
     */
    private GXReplyData sendReceive(byte[][] request) throws Exception {
        GXReplyData reply = new GXReplyData();

        for (byte[] frame : request) {
            transport.write(frame);
        }

        do {
            byte[] data = transport.read();
            gxClient.getData(data, reply);

            if (reply.isMoreData()) {
                // Request the next data block
                byte[] nextFrame = gxClient.receiverReady(reply);
                transport.write(nextFrame);
            }
        } while (reply.isMoreData());

        if (reply.getError() != 0) {
            throw new DlmsCommunicationException(
                    "DLMS error: " + reply.getErrorMessage(), reply.getError());
        }

        return reply;
    }
}
