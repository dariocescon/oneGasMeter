package com.aton.proj.oneGasMeter.dlms;

import com.aton.proj.oneGasMeter.config.DlmsClientConfig;
import com.aton.proj.oneGasMeter.dlms.transport.DlmsTransport;
import com.aton.proj.oneGasMeter.exception.DlmsCommunicationException;
import com.aton.proj.oneGasMeter.model.MeterReading;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.GXReplyData;
import gurux.dlms.enums.DataType;
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
 *   <li>Generic attribute reading ({@link #readAttribute(GXDLMSObject, int)})</li>
 *   <li>Attribute writing / SET ({@link #writeAttribute(GXDLMSObject, int)})</li>
 *   <li>Method invocation / ACTION ({@link #invokeMethod(GXDLMSObject, int, Object, DataType)})</li>
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
     *                     HDLC (seriale)              WRAPPER (TCP/IP)
     *                    ──────────────              ────────────────
     *Livello fisico      Apri porta seriale          Apri socket TCP
     *                         │                           │
     *Livello 2 (DLL)    SNRM ──────► meter          (non necessario)
     *                   meter ──────► UA              TCP fa già tutto
     *                         │                           │
     *Livello 7 (App)    AARQ ──────► meter          AARQ ──────► meter
     *                   meter ──────► AARE          meter ──────► AARE
     *                         │                           │
     *(se HIGH auth)     challenge ──► meter         challenge ──► meter
     *                   meter ──────► response      meter ──────► response
     *                         │                           │
     *                    ✅ Connesso!                ✅ Connesso!
     *
     * @throws DlmsCommunicationException if the connection or handshake fails
     */
    public void connect() throws DlmsCommunicationException {
        try {
            transport.connect();

            InterfaceType ifType = config.getProtocolType().getInterfaceType();
            if (ifType == InterfaceType.HDLC || ifType == InterfaceType.HDLC_WITH_MODE_E) {
                // HDLC handshake: SNRM → UA
                GXReplyData uaReply = new GXReplyData();
                byte[] snrm = gxClient.snrmRequest();
                readDlmsPacket(snrm, uaReply);
                gxClient.parseUAResponse(uaReply.getData());
            }

            // Application association: AARQ → AARE
            GXReplyData aareReply = new GXReplyData();
            byte[][] aarqFrames = gxClient.aarqRequest();
            for (byte[] frame : aarqFrames) {
                aareReply.clear();
                readDlmsPacket(frame, aareReply);
            }
            gxClient.parseAareResponse(aareReply.getData());

            // High-level security: challenge-response
            if (gxClient.getIsAuthenticationRequired()) {
                GXReplyData challengeReply = new GXReplyData();
                byte[][] challengeFrames = gxClient.getApplicationAssociationRequest();
                for (byte[] frame : challengeFrames) {
                    challengeReply.clear();
                    readDlmsPacket(frame, challengeReply);
                }
                gxClient.parseApplicationAssociationResponse(challengeReply.getData());
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
            gxClient.updateValue(profile, 2, reply.getValue());

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
     * Reads a single attribute from a DLMS/COSEM object.
     * <p>
     * This is the generic GET operation: it sends a read request for the given
     * attribute index and populates the object using
     * {@link GXDLMSClient#updateValue}.
     * </p>
     *
     * @param object    the target DLMS object (must have the OBIS code set)
     * @param attribute the attribute index to read (1-based)
     * @return the raw value returned by the meter
     * @throws DlmsCommunicationException if the read fails
     */
    public Object readAttribute(GXDLMSObject object, int attribute)
            throws DlmsCommunicationException {
        try {
            GXReplyData reply = sendReceive(gxClient.read(object, attribute));
            gxClient.updateValue(object, attribute, reply.getValue());
            return reply.getValue();
        } catch (DlmsCommunicationException e) {
            throw e;
        } catch (Exception e) {
            throw new DlmsCommunicationException(
                    "Failed to read attribute " + attribute
                            + " of " + object.getLogicalName(), e);
        }
    }

    /**
     * Writes (SET) a single attribute to a DLMS/COSEM object.
     * <p>
     * The caller must populate the attribute value on the object <em>before</em>
     * invoking this method (e.g. {@code clock.setTime(...)}).
     * Internally calls {@link GXDLMSClient#write(GXDLMSObject, int)}.
     * </p>
     *
     * @param object    the target DLMS object with the attribute already set
     * @param attribute the attribute index to write (1-based)
     * @throws DlmsCommunicationException if the write fails
     */
    public void writeAttribute(GXDLMSObject object, int attribute)
            throws DlmsCommunicationException {
        try {
            sendReceive(gxClient.write(object, attribute));
        } catch (DlmsCommunicationException e) {
            throw e;
        } catch (Exception e) {
            throw new DlmsCommunicationException(
                    "Failed to write attribute " + attribute
                            + " of " + object.getLogicalName(), e);
        }
    }

    /**
     * Invokes a method (ACTION) on a DLMS/COSEM object.
     * <p>
     * Used for operations such as valve disconnect/reconnect, demand register
     * reset, script execution, etc.
     * Internally calls {@link GXDLMSClient#method(GXDLMSObject, int, Object, DataType)}.
     * </p>
     *
     * @param object      the target DLMS object
     * @param methodIndex the method index (1-based)
     * @param param       parameter to pass (e.g. {@code 0} for no-arg methods)
     * @param dataType    DLMS data type of {@code param}
     * @return the value returned by the meter (may be {@code null})
     * @throws DlmsCommunicationException if the method invocation fails
     */
    public Object invokeMethod(GXDLMSObject object, int methodIndex,
                               Object param, DataType dataType)
            throws DlmsCommunicationException {
        try {
            byte[][] request = gxClient.method(object, methodIndex, param, dataType);
            GXReplyData reply = sendReceive(request);
            return reply.getValue();
        } catch (DlmsCommunicationException e) {
            throw e;
        } catch (Exception e) {
            throw new DlmsCommunicationException(
                    "Failed to invoke method " + methodIndex
                            + " on " + object.getLogicalName(), e);
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
     * Sends a single request frame and reads the complete response,
     * stripping transport-level framing via {@link GXDLMSClient#getData}.
     * <p>
     * {@code transport.read()} guarantees a complete transport frame
     * (HDLC flag-to-flag or WRAPPER header+payload), so a single
     * {@code getData()} call is sufficient to extract the APDU.
     * </p>
     */
    private void readDlmsPacket(byte[] data, GXReplyData reply) throws Exception {
        if (data == null || data.length == 0) {
            return;
        }
        transport.write(data);
        byte[] response = transport.read();
        gxClient.getData(response, reply);
    }

    /**
     * Sends all request frames and reads replies until the full response is assembled.
     * Each frame is sent individually and its response is read before proceeding
     * to the next frame (required for HDLC frame-level acknowledgment).
     * Handles DLMS multi-block transfers automatically by sending receiver-ready frames.
     */
    private GXReplyData sendReceive(byte[][] request) throws Exception {
        GXReplyData reply = new GXReplyData();

        for (byte[] frame : request) {
            reply.clear();
            readDlmsPacket(frame, reply);

            while (reply.isMoreData()) {
                byte[] nextFrame = gxClient.receiverReady(reply);
                readDlmsPacket(nextFrame, reply);
            }
        }

        if (reply.getError() != 0) {
            throw new DlmsCommunicationException(
                    "DLMS error: " + reply.getErrorMessage(), reply.getError());
        }

        return reply;
    }
}
