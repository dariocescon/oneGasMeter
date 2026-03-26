package com.aton.proj.oneGasMeter.command;

/** Stato di un {@link MeterCommand} nel suo ciclo di vita. */
public enum MeterCommandStatus {

    /** In attesa di essere eseguito alla prossima connessione del meter. */
    PENDING,

    /** Esecuzione in corso nella sessione corrente. */
    IN_PROGRESS,

    /** Eseguito con successo. */
    DONE,

    /** Esecuzione fallita — il messaggio di errore è in {@link MeterCommand#getErrorMessage()}. */
    FAILED
}
