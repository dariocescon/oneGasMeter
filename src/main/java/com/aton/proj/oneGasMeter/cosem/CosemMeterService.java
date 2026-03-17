package com.aton.proj.oneGasMeter.cosem;

import com.aton.proj.oneGasMeter.cosem.model.DemandReading;
import com.aton.proj.oneGasMeter.cosem.model.ExtendedReading;
import com.aton.proj.oneGasMeter.cosem.model.ValveState;
import com.aton.proj.oneGasMeter.dlms.DlmsMeterClient;
import com.aton.proj.oneGasMeter.exception.DlmsCommunicationException;
import gurux.dlms.GXDateTime;
import gurux.dlms.enums.DataType;
import gurux.dlms.objects.GXDLMSActivityCalendar;
import gurux.dlms.objects.GXDLMSClock;
import gurux.dlms.objects.GXDLMSDemandRegister;
import gurux.dlms.objects.GXDLMSDisconnectControl;
import gurux.dlms.objects.GXDLMSExtendedRegister;
import gurux.dlms.objects.GXDLMSScriptTable;
import gurux.dlms.objects.GXDLMSSecuritySetup;
import gurux.dlms.objects.enums.SecurityPolicy;
import gurux.dlms.objects.enums.SecuritySuite;

import java.time.Instant;
import java.util.Date;
import java.util.Set;

/**
 * Service layer COSEM per operazioni specifiche su contatori gas.
 * <p>
 * Utilizza le operazioni generiche di {@link DlmsMeterClient} (GET, SET, ACTION)
 * per implementare funzionalita' di alto livello sugli oggetti COSEM del contatore:
 * <ul>
 *   <li><strong>Clock</strong> (classe 8): lettura e sincronizzazione orologio</li>
 *   <li><strong>Disconnect Control</strong> (classe 70): apertura/chiusura valvola gas</li>
 *   <li><strong>Extended Register</strong> (classe 4): lettura registri estesi</li>
 *   <li><strong>Demand Register</strong> (classe 5): lettura registri di domanda, reset, avanzamento periodo</li>
 *   <li><strong>Activity Calendar</strong> (classe 20): lettura e attivazione calendario tariffario</li>
 *   <li><strong>Script Table</strong> (classe 9): esecuzione script</li>
 *   <li><strong>Security Setup</strong> (classe 64): lettura parametri di sicurezza</li>
 * </ul>
 * </p>
 * <p>
 * Questa classe <strong>non</strong> e' un componente Spring. Viene creata a mano:
 * {@code new CosemMeterService(client)}.
 * </p>
 *
 * <h3>Thread safety</h3>
 * <p>Non thread-safe, come il {@link DlmsMeterClient} sottostante.</p>
 */
public class CosemMeterService {

    /** OBIS code standard per il clock del contatore. */
    private static final String CLOCK_OBIS = "0.0.1.0.0.255";

    /** OBIS code standard per il Disconnect Control. */
    private static final String DISCONNECT_CONTROL_OBIS = "0.0.96.3.10.255";

    /** OBIS code standard per l'Activity Calendar. */
    private static final String ACTIVITY_CALENDAR_OBIS = "0.0.13.0.0.255";

    /** OBIS code standard per il Security Setup. */
    private static final String SECURITY_SETUP_OBIS = "0.0.43.0.0.255";

    private final DlmsMeterClient client;

    /**
     * Crea un nuovo service COSEM.
     *
     * @param client il {@link DlmsMeterClient} gia' connesso al contatore
     * @throws IllegalArgumentException se {@code client} e' {@code null}
     */
    public CosemMeterService(DlmsMeterClient client) {
        if (client == null) {
            throw new IllegalArgumentException("DlmsMeterClient must not be null");
        }
        this.client = client;
    }

    // -----------------------------------------------------------------------
    // Clock (classe 8, OBIS 0.0.1.0.0.255)
    // -----------------------------------------------------------------------

    /**
     * Legge l'orologio del contatore.
     * <p>Delega a {@link DlmsMeterClient#readClock()}.</p>
     *
     * @return ora del contatore come {@link Instant}, o {@code null} se non disponibile
     * @throws DlmsCommunicationException se la lettura fallisce
     */
    public Instant readClock() throws DlmsCommunicationException {
        return client.readClock();
    }

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
        client.writeAttribute(clock, 2);
    }

    // -----------------------------------------------------------------------
    // Disconnect Control (classe 70, OBIS 0.0.96.3.10.255)
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
        client.invokeMethod(dc, 1, 0, DataType.INT8);
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
        client.invokeMethod(dc, 2, 0, DataType.INT8);
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
        client.readAttribute(dc, 4);
        return ValveState.from(dc.getControlState());
    }

    // -----------------------------------------------------------------------
    // Extended Register (classe 4)
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
        client.readAttribute(reg, 2);
        // scaler + unit (attr 3)
        client.readAttribute(reg, 3);
        // status (attr 4)
        client.readAttribute(reg, 4);
        // captureTime (attr 5)
        client.readAttribute(reg, 5);

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
    // Demand Register (classe 5)
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
        client.readAttribute(reg, 2);
        // lastAverageValue (attr 3)
        client.readAttribute(reg, 3);
        // scaler + unit (attr 4)
        client.readAttribute(reg, 4);
        // status (attr 5)
        client.readAttribute(reg, 5);
        // captureTime (attr 6)
        client.readAttribute(reg, 6);
        // startTimeCurrent (attr 7)
        client.readAttribute(reg, 7);
        // period (attr 8)
        client.readAttribute(reg, 8);
        // numberOfPeriods (attr 9)
        client.readAttribute(reg, 9);

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
        client.invokeMethod(reg, 1, 0, DataType.INT8);
    }

    /**
     * Avanza al periodo successivo di un Demand Register (metodo 2).
     *
     * @param obisCode OBIS code del registro
     * @throws DlmsCommunicationException se l'operazione fallisce
     */
    public void nextPeriodDemandRegister(String obisCode) throws DlmsCommunicationException {
        GXDLMSDemandRegister reg = new GXDLMSDemandRegister(obisCode);
        client.invokeMethod(reg, 2, 0, DataType.INT8);
    }

    // -----------------------------------------------------------------------
    // Activity Calendar (classe 20, OBIS 0.0.13.0.0.255)
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
        client.readAttribute(cal, 2);
        // seasonProfileActive (attr 3)
        client.readAttribute(cal, 3);
        // weekProfileTableActive (attr 4)
        client.readAttribute(cal, 4);
        // dayProfileTableActive (attr 5)
        client.readAttribute(cal, 5);
        // calendarNamePassive (attr 6)
        client.readAttribute(cal, 6);
        // seasonProfilePassive (attr 7)
        client.readAttribute(cal, 7);
        // weekProfileTablePassive (attr 8)
        client.readAttribute(cal, 8);
        // dayProfileTablePassive (attr 9)
        client.readAttribute(cal, 9);
        // activatePassiveCalendarTime (attr 10)
        client.readAttribute(cal, 10);

        return cal;
    }

    /**
     * Attiva il calendario passivo (metodo 1 dell'Activity Calendar).
     *
     * @throws DlmsCommunicationException se l'operazione fallisce
     */
    public void activatePassiveCalendar() throws DlmsCommunicationException {
        GXDLMSActivityCalendar cal = new GXDLMSActivityCalendar(ACTIVITY_CALENDAR_OBIS);
        client.invokeMethod(cal, 1, 0, DataType.INT8);
    }

    // -----------------------------------------------------------------------
    // Script Table (classe 9)
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
        client.invokeMethod(table, 1, scriptId, DataType.UINT16);
    }

    // -----------------------------------------------------------------------
    // Security Setup (classe 64, OBIS 0.0.43.0.0.255) – solo lettura
    // -----------------------------------------------------------------------

    /**
     * Legge la security policy corrente del contatore.
     *
     * @return set di {@link SecurityPolicy} attive
     * @throws DlmsCommunicationException se la lettura fallisce
     */
    public Set<SecurityPolicy> readSecurityPolicy() throws DlmsCommunicationException {
        GXDLMSSecuritySetup sec = new GXDLMSSecuritySetup(SECURITY_SETUP_OBIS);
        client.readAttribute(sec, 2);
        return sec.getSecurityPolicy();
    }

    /**
     * Legge la security suite corrente del contatore.
     *
     * @return la {@link SecuritySuite} attiva
     * @throws DlmsCommunicationException se la lettura fallisce
     */
    public SecuritySuite readSecuritySuite() throws DlmsCommunicationException {
        GXDLMSSecuritySetup sec = new GXDLMSSecuritySetup(SECURITY_SETUP_OBIS);
        client.readAttribute(sec, 3);
        return sec.getSecuritySuite();
    }

    // -----------------------------------------------------------------------
    // Utility
    // -----------------------------------------------------------------------

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
