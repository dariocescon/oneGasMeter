package com.aton.proj.oneGasMeter.cosem.model;

import gurux.dlms.objects.enums.ControlState;

/**
 * Stato della valvola di un contatore gas DLMS/COSEM.
 * <p>
 * Mappa i valori dell'enum gurux {@link ControlState} (Disconnect Control,
 * classe COSEM 70, attributo 4) in un enum di dominio dell'applicazione.
 * </p>
 */
public enum ValveState {

    /** Valvola chiusa (gas interrotto). */
    DISCONNECTED,

    /** Valvola aperta (gas erogato). */
    CONNECTED,

    /** Valvola pronta per la riconnessione (attesa di comando manuale o remoto). */
    READY_FOR_RECONNECTION;

    /**
     * Converte un {@link ControlState} gurux nel corrispondente {@link ValveState}.
     *
     * @param controlState stato restituito dal meter
     * @return il {@link ValveState} equivalente
     * @throws IllegalArgumentException se {@code controlState} e' {@code null}
     *         o non ha una mappatura valida
     */
    public static ValveState from(ControlState controlState) {
        if (controlState == null) {
            throw new IllegalArgumentException("ControlState must not be null");
        }
        return switch (controlState) {
            case DISCONNECTED -> DISCONNECTED;
            case CONNECTED -> CONNECTED;
            case READY_FOR_RECONNECTION -> READY_FOR_RECONNECTION;
        };
    }
}
