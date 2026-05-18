package com.aton.proj.oneGasMeter.command;

/**
 * Tipi di comando inviabili a un meter DLMS durante una sessione inbound.
 * <p>
 * Ogni tipo indica quale metodo di {@link com.aton.proj.oneGasMeter.dlms.DlmsMeterClient}
 * viene invocato da {@link MeterCommandExecutor}.
 * </p>
 *
 * <h3>Payload atteso per tipo</h3>
 * <ul>
 *   <li>{@link #SYNC_CLOCK} — nessun payload richiesto</li>
 *   <li>{@link #SET_CLOCK} — chiave {@code "isoDateTime"} (ISO-8601, es. {@code 2026-03-26T10:00:00Z})</li>
 *   <li>{@link #DISCONNECT_VALVE} — nessun payload richiesto</li>
 *   <li>{@link #RECONNECT_VALVE} — nessun payload richiesto</li>
 *   <li>{@link #CHANGE_PUSH_DESTINATION} — chiavi {@code "ip"} (es. {@code 192.168.1.10}) e
 *       {@code "port"} (es. {@code 4059})</li>
 * </ul>
 */
public enum MeterCommandType {

    /** Sincronizza l'orologio del meter con l'ora corrente del server. */
    SYNC_CLOCK,

    /** Imposta l'orologio del meter a un istante specifico (payload: {@code isoDateTime}). */
    SET_CLOCK,

    /** Apre la valvola del meter (remote disconnect → reconnect). */
    RECONNECT_VALVE,

    /** Chiude la valvola del meter (remote disconnect). */
    DISCONNECT_VALVE,

    /**
     * Cambia la destinazione push del meter (COSEM Push Setup, OBIS {@code 0.0.25.9.0.255}).
     * <p>Payload richiesto: {@code "ip"} (indirizzo IP) e {@code "port"} (porta TCP intera).</p>
     */
    CHANGE_PUSH_DESTINATION
}
