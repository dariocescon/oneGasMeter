package com.aton.proj.oneGasMeter.dlms;

import com.aton.proj.oneGasMeter.config.DlmsClientConfig;
import com.aton.proj.oneGasMeter.cosem.model.DemandReading;
import com.aton.proj.oneGasMeter.cosem.model.ExtendedReading;
import com.aton.proj.oneGasMeter.cosem.model.ValveState;
import com.aton.proj.oneGasMeter.dlms.transport.DlmsTransport;
import com.aton.proj.oneGasMeter.exception.DlmsCommunicationException;
import com.aton.proj.oneGasMeter.model.MeterReading;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.GXDateTime;
import gurux.dlms.GXReplyData;
import gurux.dlms.enums.DataType;
import gurux.dlms.enums.InterfaceType;
import gurux.dlms.objects.GXDLMSActivityCalendar;
import gurux.dlms.objects.GXDLMSClock;
import gurux.dlms.objects.GXDLMSData;
import gurux.dlms.objects.GXDLMSDemandRegister;
import gurux.dlms.objects.GXDLMSDisconnectControl;
import gurux.dlms.objects.GXDLMSExtendedRegister;
import gurux.dlms.objects.GXDLMSObject;
import gurux.dlms.objects.GXDLMSObjectCollection;
import gurux.dlms.objects.GXDLMSProfileGeneric;
import gurux.dlms.objects.GXDLMSRegister;
import gurux.dlms.objects.GXDLMSScriptTable;
import gurux.dlms.objects.GXDLMSSecuritySetup;
import gurux.dlms.objects.enums.ClockBase;
import gurux.dlms.objects.enums.ControlMode;
import gurux.dlms.objects.enums.SecurityPolicy;
import gurux.dlms.objects.enums.SecuritySuite;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * High-level DLMS/COSEM meter client.
 * <p>
 * Combines a {@link DlmsTransport} (physical channel) with the gurux.dlms
 * {@link GXDLMSClient} (protocol engine) to provide a unified API for:
 * <ul>
 *   <li>Connection management ({@link #connect()} / {@link #disconnect()})</li>
 *   <li>Object discovery ({@link #getObjects()})</li>
 *   <li>Reading DLMS registers ({@link #readRegister(String)})</li>
 *   <li>Reading generic data objects ({@link #readData(String)})</li>
 *   <li>Reading the meter clock ({@link #readClock()})</li>
 *   <li>Reading profile-generic (load profile) data ({@link #readProfileGeneric(String, Date, Date)})</li>
 *   <li>COSEM Clock operations ({@link #syncClock()}, {@link #setClock(Instant)},
 *       {@link #setTimeZone(int)}, {@link #setDaylightSavings(boolean, GXDateTime, GXDateTime, int)},
 *       {@link #setClockBase(ClockBase)})</li>
 *   <li>Disconnect Control ({@link #disconnectValve()}, {@link #reconnectValve()},
 *       {@link #getValveState()}, {@link #setControlMode(ControlMode)})</li>
 *   <li>Extended Register ({@link #readExtendedRegister(String)})</li>
 *   <li>Demand Register ({@link #readDemandRegister(String)}, {@link #resetDemandRegister(String)})</li>
 *   <li>Activity Calendar ({@link #readActivityCalendar()}, {@link #writePassiveCalendar(GXDLMSActivityCalendar)},
 *       {@link #activatePassiveCalendar()})</li>
 *   <li>Script Table ({@link #executeScript(String, int)})</li>
 *   <li>Security Setup ({@link #readSecurityPolicy()}, {@link #readSecuritySuite()},
 *       {@link #setSecurityPolicy(Set)}, {@link #setSecuritySuite(SecuritySuite)})</li>
 *   <li>Profile Generic config ({@link #setCapturePeriod(String, long)})</li>
 *   <li>Data write ({@link #writeData(String, Object)})</li>
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

    // -----------------------------------------------------------------------
    // OBIS codes standard
    // -----------------------------------------------------------------------

    /** OBIS code standard per il clock del contatore. */
    private static final String CLOCK_OBIS = "0.0.1.0.0.255";

    /** OBIS code standard per il Disconnect Control. */
    private static final String DISCONNECT_CONTROL_OBIS = "0.0.96.3.10.255";

    /** OBIS code standard per l'Activity Calendar. */
    private static final String ACTIVITY_CALENDAR_OBIS = "0.0.13.0.0.255";

    /** OBIS code standard per il Security Setup. */
    private static final String SECURITY_SETUP_OBIS = "0.0.43.0.0.255";

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

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

    // -----------------------------------------------------------------------
    // Connection management
    // -----------------------------------------------------------------------

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

    // -----------------------------------------------------------------------
    // DLMS object discovery
    // -----------------------------------------------------------------------

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

    // -----------------------------------------------------------------------
    // DLMS register reading
    // -----------------------------------------------------------------------

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
            GXDLMSClock clock = new GXDLMSClock(CLOCK_OBIS);
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

    // -----------------------------------------------------------------------
    // COSEM: Clock (classe 8, OBIS 0.0.1.0.0.255)
    // -----------------------------------------------------------------------

    /**
     * Sincronizza l'orologio del contatore con l'ora corrente del sistema.
     *
     * @throws DlmsCommunicationException se la scrittura fallisce
     */
    public void syncClock() throws DlmsCommunicationException {
        setClock(Instant.now());
    }

    /**
     * Imposta l'orologio del contatore a un istante specifico.
     *
     * @param time l'ora da impostare
     * @throws DlmsCommunicationException se la scrittura fallisce
     * @throws IllegalArgumentException se {@code time} e' {@code null}
     */
    public void setClock(Instant time) throws DlmsCommunicationException {
        if (time == null) {
            throw new IllegalArgumentException("Time must not be null");
        }
        GXDLMSClock clock = new GXDLMSClock(CLOCK_OBIS);
        clock.setTime(Date.from(time));
        writeAttribute(clock, 2);
    }

    /**
     * Imposta il fuso orario dell'orologio del contatore (attributo 3).
     *
     * @param offsetMinutes scostamento UTC in minuti (es. 60 = UTC+1, -300 = UTC-5)
     * @throws DlmsCommunicationException se la scrittura fallisce
     */
    public void setTimeZone(int offsetMinutes) throws DlmsCommunicationException {
        GXDLMSClock clock = new GXDLMSClock(CLOCK_OBIS);
        clock.setTimeZone(offsetMinutes);
        writeAttribute(clock, 3);
    }

    /**
     * Configura il passaggio all'ora legale (DST) del contatore (attributi 5–8).
     * <p>
     * Scrive i quattro attributi DST in sequenza:
     * <ul>
     *   <li>Attr 5 — {@code dstBegin}: istante di inizio ora legale</li>
     *   <li>Attr 6 — {@code dstEnd}: istante di fine ora legale</li>
     *   <li>Attr 7 — {@code deviation}: scostamento DST in minuti (tipicamente 60)</li>
     *   <li>Attr 8 — {@code enabled}: abilitazione del DST</li>
     * </ul>
     * </p>
     *
     * @param enabled         {@code true} per abilitare il DST
     * @param begin           istante di inizio ora legale
     * @param end             istante di fine ora legale
     * @param deviationMinutes scostamento DST in minuti
     * @throws DlmsCommunicationException se la scrittura fallisce
     */
    public void setDaylightSavings(boolean enabled, GXDateTime begin, GXDateTime end,
                                   int deviationMinutes)
            throws DlmsCommunicationException {
        GXDLMSClock clock = new GXDLMSClock(CLOCK_OBIS);
        clock.setBegin(begin);
        clock.setEnd(end);
        clock.setDeviation(deviationMinutes);
        clock.setEnabled(enabled);
        writeAttribute(clock, 5);   // DST begin
        writeAttribute(clock, 6);   // DST end
        writeAttribute(clock, 7);   // DST deviation
        writeAttribute(clock, 8);   // DST enabled
    }

    /**
     * Imposta la sorgente di riferimento dell'orologio (attributo 9).
     * <p>
     * I valori tipici sono:
     * <ul>
     *   <li>{@link ClockBase#NONE} — nessuna sorgente esterna</li>
     *   <li>{@link ClockBase#CRYSTAL} — oscillatore al quarzo interno</li>
     *   <li>{@link ClockBase#GPS} — sincronizzazione GPS</li>
     *   <li>{@link ClockBase#RADIO} — segnale radio orario</li>
     * </ul>
     * </p>
     *
     * @param clockBase sorgente di riferimento da impostare
     * @throws DlmsCommunicationException se la scrittura fallisce
     * @throws IllegalArgumentException   se {@code clockBase} e' {@code null}
     */
    public void setClockBase(ClockBase clockBase) throws DlmsCommunicationException {
        if (clockBase == null) {
            throw new IllegalArgumentException("ClockBase must not be null");
        }
        GXDLMSClock clock = new GXDLMSClock(CLOCK_OBIS);
        clock.setClockBase(clockBase);
        writeAttribute(clock, 9);
    }

    // -----------------------------------------------------------------------
    // COSEM: Disconnect Control (classe 70, OBIS 0.0.96.3.10.255)
    // -----------------------------------------------------------------------

    /**
     * Chiude la valvola del gas (remote disconnect) usando l'OBIS standard.
     *
     * @throws DlmsCommunicationException se l'operazione fallisce
     */
    public void disconnectValve() throws DlmsCommunicationException {
        disconnectValve(DISCONNECT_CONTROL_OBIS);
    }

    /**
     * Chiude la valvola del gas (remote disconnect) usando un OBIS specifico.
     *
     * @param obisCode OBIS code del Disconnect Control
     * @throws DlmsCommunicationException se l'operazione fallisce
     */
    public void disconnectValve(String obisCode) throws DlmsCommunicationException {
        GXDLMSDisconnectControl dc = new GXDLMSDisconnectControl(obisCode);
        invokeMethod(dc, 1, 0, DataType.INT8);
    }

    /**
     * Riapre la valvola del gas (remote reconnect) usando l'OBIS standard.
     *
     * @throws DlmsCommunicationException se l'operazione fallisce
     */
    public void reconnectValve() throws DlmsCommunicationException {
        reconnectValve(DISCONNECT_CONTROL_OBIS);
    }

    /**
     * Riapre la valvola del gas (remote reconnect) usando un OBIS specifico.
     *
     * @param obisCode OBIS code del Disconnect Control
     * @throws DlmsCommunicationException se l'operazione fallisce
     */
    public void reconnectValve(String obisCode) throws DlmsCommunicationException {
        GXDLMSDisconnectControl dc = new GXDLMSDisconnectControl(obisCode);
        invokeMethod(dc, 2, 0, DataType.INT8);
    }

    /**
     * Legge lo stato corrente della valvola usando l'OBIS standard.
     *
     * @return stato della valvola
     * @throws DlmsCommunicationException se la lettura fallisce
     */
    public ValveState getValveState() throws DlmsCommunicationException {
        return getValveState(DISCONNECT_CONTROL_OBIS);
    }

    /**
     * Legge lo stato corrente della valvola usando un OBIS specifico.
     *
     * @param obisCode OBIS code del Disconnect Control
     * @return stato della valvola
     * @throws DlmsCommunicationException se la lettura fallisce
     */
    public ValveState getValveState(String obisCode) throws DlmsCommunicationException {
        GXDLMSDisconnectControl dc = new GXDLMSDisconnectControl(obisCode);
        readAttribute(dc, 4);
        return ValveState.from(dc.getControlState());
    }

    /**
     * Imposta la modalità di controllo del Disconnect Control usando l'OBIS standard.
     * <p>
     * La control mode determina se il dispositivo può essere comandato da remoto
     * e come gestisce le condizioni di riconnessione automatica:
     * <ul>
     *   <li>{@link ControlMode#NONE} — sempre connesso, nessun controllo remoto</li>
     *   <li>{@link ControlMode#MODE_1} — controllo remoto con verifica dello stato prima della riconnessione</li>
     *   <li>{@link ControlMode#MODE_2} — controllo remoto con override manuale permesso</li>
     *   <li>{@link ControlMode#MODE_3} — controllo remoto, riconnessione solo via comando</li>
     *   <li>{@link ControlMode#MODE_4} — controllo remoto con riconnessione automatica</li>
     * </ul>
     * </p>
     *
     * @param controlMode modalità di controllo da impostare
     * @throws DlmsCommunicationException se la scrittura fallisce
     * @throws IllegalArgumentException   se {@code controlMode} e' {@code null}
     */
    public void setControlMode(ControlMode controlMode) throws DlmsCommunicationException {
        setControlMode(DISCONNECT_CONTROL_OBIS, controlMode);
    }

    /**
     * Imposta la modalità di controllo del Disconnect Control usando un OBIS specifico.
     *
     * @param obisCode    OBIS code del Disconnect Control
     * @param controlMode modalità di controllo da impostare
     * @throws DlmsCommunicationException se la scrittura fallisce
     * @throws IllegalArgumentException   se {@code controlMode} e' {@code null}
     */
    public void setControlMode(String obisCode, ControlMode controlMode)
            throws DlmsCommunicationException {
        if (controlMode == null) {
            throw new IllegalArgumentException("ControlMode must not be null");
        }
        GXDLMSDisconnectControl dc = new GXDLMSDisconnectControl(obisCode);
        dc.setControlMode(controlMode);
        writeAttribute(dc, 3);
    }

    // -----------------------------------------------------------------------
    // COSEM: Extended Register (classe 4)
    // -----------------------------------------------------------------------

    /**
     * Legge un Extended Register (classe COSEM 4) completo.
     * <p>
     * Legge gli attributi 2 (value), 3 (scaler+unit), 4 (status) e 5 (captureTime)
     * e restituisce un {@link ExtendedReading} immutabile.
     * </p>
     *
     * @param obisCode OBIS code del registro esteso
     * @return lettura completa con value, scaler, unit, status e captureTime
     * @throws DlmsCommunicationException se la lettura fallisce
     */
    public ExtendedReading readExtendedRegister(String obisCode) throws DlmsCommunicationException {
        GXDLMSExtendedRegister reg = new GXDLMSExtendedRegister(obisCode);

        // value (attr 2)
        readAttribute(reg, 2);
        // scaler + unit (attr 3)
        readAttribute(reg, 3);
        // status (attr 4)
        readAttribute(reg, 4);
        // captureTime (attr 5)
        readAttribute(reg, 5);

        Instant captureInstant = null;
        GXDateTime captureDateTime = reg.getCaptureTime();
        if (captureDateTime != null && captureDateTime.getMeterCalendar() != null) {
            captureInstant = captureDateTime.getMeterCalendar().toInstant();
        }

        return ExtendedReading.builder()
                .obisCode(obisCode)
                .value(reg.getValue())
                .scaler(reg.getScaler())
                .unit(reg.getUnit() != null ? reg.getUnit().toString() : "")
                .status(reg.getStatus())
                .captureTime(captureInstant)
                .readTimestamp(Instant.now())
                .build();
    }

    // -----------------------------------------------------------------------
    // COSEM: Demand Register (classe 5)
    // -----------------------------------------------------------------------

    /**
     * Legge un Demand Register (classe COSEM 5) completo.
     * <p>
     * Legge gli attributi da 2 a 9 e restituisce un {@link DemandReading} immutabile.
     * </p>
     *
     * @param obisCode OBIS code del registro di domanda
     * @return lettura completa con medie, scaler, unit, status, tempi e configurazione periodi
     * @throws DlmsCommunicationException se la lettura fallisce
     */
    public DemandReading readDemandRegister(String obisCode) throws DlmsCommunicationException {
        GXDLMSDemandRegister reg = new GXDLMSDemandRegister(obisCode);

        // currentAverageValue (attr 2)
        readAttribute(reg, 2);
        // lastAverageValue (attr 3)
        readAttribute(reg, 3);
        // scaler + unit (attr 4)
        readAttribute(reg, 4);
        // status (attr 5)
        readAttribute(reg, 5);
        // captureTime (attr 6)
        readAttribute(reg, 6);
        // startTimeCurrent (attr 7)
        readAttribute(reg, 7);
        // period (attr 8)
        readAttribute(reg, 8);
        // numberOfPeriods (attr 9)
        readAttribute(reg, 9);

        return DemandReading.builder()
                .obisCode(obisCode)
                .currentAverageValue(reg.getCurrentAverageValue())
                .lastAverageValue(reg.getLastAverageValue())
                .scaler(reg.getScaler())
                .unit(reg.getUnit() != null ? reg.getUnit().toString() : "")
                .status(reg.getStatus())
                .captureTime(toInstant(reg.getCaptureTime()))
                .startTimeCurrent(toInstant(reg.getStartTimeCurrent()))
                .period(reg.getPeriod())
                .numberOfPeriods(reg.getNumberOfPeriods())
                .readTimestamp(Instant.now())
                .build();
    }

    /**
     * Resetta un Demand Register (metodo 1).
     *
     * @param obisCode OBIS code del registro
     * @throws DlmsCommunicationException se l'operazione fallisce
     */
    public void resetDemandRegister(String obisCode) throws DlmsCommunicationException {
        GXDLMSDemandRegister reg = new GXDLMSDemandRegister(obisCode);
        invokeMethod(reg, 1, 0, DataType.INT8);
    }

    /**
     * Avanza al periodo successivo di un Demand Register (metodo 2).
     *
     * @param obisCode OBIS code del registro
     * @throws DlmsCommunicationException se l'operazione fallisce
     */
    public void nextPeriodDemandRegister(String obisCode) throws DlmsCommunicationException {
        GXDLMSDemandRegister reg = new GXDLMSDemandRegister(obisCode);
        invokeMethod(reg, 2, 0, DataType.INT8);
    }

    // -----------------------------------------------------------------------
    // COSEM: Activity Calendar (classe 20, OBIS 0.0.13.0.0.255)
    // -----------------------------------------------------------------------

    /**
     * Legge l'Activity Calendar completo dal contatore.
     * <p>
     * Legge gli attributi da 2 a 10. Restituisce l'oggetto gurux
     * {@link GXDLMSActivityCalendar} direttamente, dato che il modello dati
     * del calendario (season/week/day profiles) e' complesso.
     * </p>
     *
     * @return l'oggetto Activity Calendar popolato
     * @throws DlmsCommunicationException se la lettura fallisce
     */
    public GXDLMSActivityCalendar readActivityCalendar() throws DlmsCommunicationException {
        GXDLMSActivityCalendar cal = new GXDLMSActivityCalendar(ACTIVITY_CALENDAR_OBIS);

        // calendarNameActive (attr 2)
        readAttribute(cal, 2);
        // seasonProfileActive (attr 3)
        readAttribute(cal, 3);
        // weekProfileTableActive (attr 4)
        readAttribute(cal, 4);
        // dayProfileTableActive (attr 5)
        readAttribute(cal, 5);
        // calendarNamePassive (attr 6)
        readAttribute(cal, 6);
        // seasonProfilePassive (attr 7)
        readAttribute(cal, 7);
        // weekProfileTablePassive (attr 8)
        readAttribute(cal, 8);
        // dayProfileTablePassive (attr 9)
        readAttribute(cal, 9);
        // activatePassiveCalendarTime (attr 10)
        readAttribute(cal, 10);

        return cal;
    }

    /**
     * Attiva il calendario passivo (metodo 1 dell'Activity Calendar).
     *
     * @throws DlmsCommunicationException se l'operazione fallisce
     */
    public void activatePassiveCalendar() throws DlmsCommunicationException {
        GXDLMSActivityCalendar cal = new GXDLMSActivityCalendar(ACTIVITY_CALENDAR_OBIS);
        invokeMethod(cal, 1, 0, DataType.INT8);
    }

    /**
     * Scrive il profilo passivo dell'Activity Calendar sul contatore.
     * <p>
     * Scrive gli attributi 6–10 dall'oggetto {@link GXDLMSActivityCalendar} fornito:
     * <ul>
     *   <li>Attr 6 — {@code calendarNamePassive}: nome del calendario passivo</li>
     *   <li>Attr 7 — {@code seasonProfilePassive}: profili di stagione passivi</li>
     *   <li>Attr 8 — {@code weekProfileTablePassive}: profili settimanali passivi</li>
     *   <li>Attr 9 — {@code dayProfileTablePassive}: profili giornalieri passivi</li>
     *   <li>Attr 10 — {@code activatePassiveCalendarTime}: ora di attivazione automatica</li>
     * </ul>
     * </p>
     * <p>
     * Per attivare il calendario scritto, chiamare successivamente
     * {@link #activatePassiveCalendar()}.
     * </p>
     *
     * @param calendar oggetto Activity Calendar con il profilo passivo gia' configurato
     * @throws DlmsCommunicationException se la scrittura fallisce
     * @throws IllegalArgumentException   se {@code calendar} e' {@code null}
     */
    public void writePassiveCalendar(GXDLMSActivityCalendar calendar)
            throws DlmsCommunicationException {
        if (calendar == null) {
            throw new IllegalArgumentException("ActivityCalendar must not be null");
        }
        // calendarNamePassive (attr 6)
        writeAttribute(calendar, 6);
        // seasonProfilePassive (attr 7)
        writeAttribute(calendar, 7);
        // weekProfileTablePassive (attr 8)
        writeAttribute(calendar, 8);
        // dayProfileTablePassive (attr 9)
        writeAttribute(calendar, 9);
        // activatePassiveCalendarTime (attr 10)
        writeAttribute(calendar, 10);
    }

    // -----------------------------------------------------------------------
    // COSEM: Script Table (classe 9)
    // -----------------------------------------------------------------------

    /**
     * Esegue uno script dalla Script Table.
     *
     * @param obisCode OBIS code della Script Table
     * @param scriptId ID dello script da eseguire
     * @throws DlmsCommunicationException se l'esecuzione fallisce
     */
    public void executeScript(String obisCode, int scriptId) throws DlmsCommunicationException {
        GXDLMSScriptTable table = new GXDLMSScriptTable(obisCode);
        invokeMethod(table, 1, scriptId, DataType.UINT16);
    }

    // -----------------------------------------------------------------------
    // COSEM: Security Setup (classe 64, OBIS 0.0.43.0.0.255)
    // -----------------------------------------------------------------------

    /**
     * Legge la security policy corrente del contatore (attributo 2).
     *
     * @return set di {@link SecurityPolicy} attive
     * @throws DlmsCommunicationException se la lettura fallisce
     */
    public Set<SecurityPolicy> readSecurityPolicy() throws DlmsCommunicationException {
        GXDLMSSecuritySetup sec = new GXDLMSSecuritySetup(SECURITY_SETUP_OBIS);
        readAttribute(sec, 2);
        return sec.getSecurityPolicy();
    }

    /**
     * Legge la security suite corrente del contatore (attributo 3).
     *
     * @return la {@link SecuritySuite} attiva
     * @throws DlmsCommunicationException se la lettura fallisce
     */
    public SecuritySuite readSecuritySuite() throws DlmsCommunicationException {
        GXDLMSSecuritySetup sec = new GXDLMSSecuritySetup(SECURITY_SETUP_OBIS);
        readAttribute(sec, 3);
        return sec.getSecuritySuite();
    }

    /**
     * Imposta la security policy del contatore (attributo 2).
     * <p>
     * <strong>Attenzione:</strong> la modifica della security policy è un'operazione
     * critica che può rendere il contatore irraggiungibile se non configurata correttamente.
     * </p>
     *
     * @param policy set di {@link SecurityPolicy} da impostare
     * @throws DlmsCommunicationException se la scrittura fallisce
     * @throws IllegalArgumentException   se {@code policy} e' {@code null}
     */
    public void setSecurityPolicy(Set<SecurityPolicy> policy) throws DlmsCommunicationException {
        if (policy == null) {
            throw new IllegalArgumentException("SecurityPolicy must not be null");
        }
        GXDLMSSecuritySetup sec = new GXDLMSSecuritySetup(SECURITY_SETUP_OBIS);
        sec.setSecurityPolicy(policy);
        writeAttribute(sec, 2);
    }

    /**
     * Imposta la security suite del contatore (attributo 3).
     * <p>
     * <strong>Attenzione:</strong> la modifica della security suite è un'operazione
     * critica. Assicurarsi che client e server supportino la suite selezionata.
     * </p>
     *
     * @param suite la {@link SecuritySuite} da impostare
     * @throws DlmsCommunicationException se la scrittura fallisce
     * @throws IllegalArgumentException   se {@code suite} e' {@code null}
     */
    public void setSecuritySuite(SecuritySuite suite) throws DlmsCommunicationException {
        if (suite == null) {
            throw new IllegalArgumentException("SecuritySuite must not be null");
        }
        GXDLMSSecuritySetup sec = new GXDLMSSecuritySetup(SECURITY_SETUP_OBIS);
        sec.setSecuritySuite(suite);
        writeAttribute(sec, 3);
    }

    // -----------------------------------------------------------------------
    // COSEM: Profile Generic (classe 7) — configurazione
    // -----------------------------------------------------------------------

    /**
     * Imposta il periodo di acquisizione del Profile Generic (attributo 4).
     * <p>
     * Il periodo è espresso in secondi (0 = acquisizione su trigger, non periodica).
     * </p>
     *
     * @param obisCode      OBIS code del Profile Generic
     * @param periodSeconds periodo di acquisizione in secondi
     * @throws DlmsCommunicationException se la scrittura fallisce
     */
    public void setCapturePeriod(String obisCode, long periodSeconds)
            throws DlmsCommunicationException {
        GXDLMSProfileGeneric profile = new GXDLMSProfileGeneric(obisCode);
        profile.setCapturePeriod(periodSeconds);
        writeAttribute(profile, 4);
    }

    /**
     * Configura la lista degli oggetti da acquisire nel Profile Generic (attributo 3).
     * <p>
     * <strong>Non ancora implementato.</strong> Richiede la conoscenza della lista di
     * oggetti OBIS specifici del contatore. Da completare una volta note le specifiche
     * del dispositivo.
     * </p>
     *
     * @param obisCode OBIS code del Profile Generic
     * @throws UnsupportedOperationException sempre — metodo non ancora implementato
     */
    public void setCaptureObjects(String obisCode) throws DlmsCommunicationException {
        throw new UnsupportedOperationException(
                "setCaptureObjects non ancora implementato: richiede la lista OBIS degli oggetti"
                        + " da acquisire, specifica del contatore");
    }

    // -----------------------------------------------------------------------
    // COSEM: Data (classe 1) — scrittura
    // -----------------------------------------------------------------------

    /**
     * Scrive il valore di un oggetto Data (classe COSEM 1) specifico del contatore.
     * <p>
     * Questo metodo è generico e può scrivere qualsiasi oggetto Data identificato
     * dall'OBIS code. Gli OBIS code specifici del contatore (es. parametri di
     * configurazione, soglie di allarme, impostazioni tariffarie) dovranno essere
     * determinati a partire dalle specifiche del dispositivo.
     * </p>
     *
     * @param obisCode OBIS code dell'oggetto Data
     * @param value    valore da scrivere (il tipo deve essere compatibile con
     *                 il tipo atteso dall'oggetto sul contatore)
     * @throws DlmsCommunicationException se la scrittura fallisce
     */
    public void writeData(String obisCode, Object value) throws DlmsCommunicationException {
        GXDLMSData data = new GXDLMSData(obisCode);
        data.setValue(value);
        writeAttribute(data, 2);
    }

    // -----------------------------------------------------------------------
    // Status
    // -----------------------------------------------------------------------

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
    // Generic DLMS operations (package-private, visible for testing)
    // -----------------------------------------------------------------------

    /**
     * Reads a single attribute from a DLMS/COSEM object (GET).
     * <p>
     * Sends a read request for the given attribute index and populates the
     * object using {@link GXDLMSClient#updateValue}.
     * </p>
     * <p><strong>Package-private:</strong> non fa parte dell'API pubblica.
     * Visibilita' limitata per consentire il testing con Mockito Spy.</p>
     *
     * @param object    the target DLMS object (must have the OBIS code set)
     * @param attribute the attribute index to read (1-based)
     * @return the raw value returned by the meter
     * @throws DlmsCommunicationException if the read fails
     */
    Object readAttribute(GXDLMSObject object, int attribute)
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
     * invoking this method.
     * </p>
     * <p><strong>Package-private:</strong> non fa parte dell'API pubblica.
     * Visibilita' limitata per consentire il testing con Mockito Spy.</p>
     *
     * @param object    the target DLMS object with the attribute already set
     * @param attribute the attribute index to write (1-based)
     * @throws DlmsCommunicationException if the write fails
     */
    void writeAttribute(GXDLMSObject object, int attribute)
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
     * </p>
     * <p><strong>Package-private:</strong> non fa parte dell'API pubblica.
     * Visibilita' limitata per consentire il testing con Mockito Spy.</p>
     *
     * @param object      the target DLMS object
     * @param methodIndex the method index (1-based)
     * @param param       parameter to pass (e.g. {@code 0} for no-arg methods)
     * @param dataType    DLMS data type of {@code param}
     * @return the value returned by the meter (may be {@code null})
     * @throws DlmsCommunicationException if the method invocation fails
     */
    Object invokeMethod(GXDLMSObject object, int methodIndex,
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

    // -----------------------------------------------------------------------
    // Private helpers
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

    /**
     * Converte un {@link GXDateTime} in {@link Instant}, gestendo i casi null.
     */
    private static Instant toInstant(GXDateTime dateTime) {
        if (dateTime != null && dateTime.getMeterCalendar() != null) {
            return dateTime.getMeterCalendar().toInstant();
        }
        return null;
    }
}
