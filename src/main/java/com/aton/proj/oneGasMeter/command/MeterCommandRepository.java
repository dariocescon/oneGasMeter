package com.aton.proj.oneGasMeter.command;

import java.util.List;

/**
 * Accesso ai comandi in attesa per un meter.
 * <p>
 * L'implementazione di default ({@link InMemoryMeterCommandRepository}) usa
 * una lista in memoria. Per produzione, sostituirla con un'implementazione
 * JPA o JDBC registrata come bean Spring.
 * </p>
 */
public interface MeterCommandRepository {

    /**
     * Restituisce i comandi con stato {@link MeterCommandStatus#PENDING}
     * per il meter dato, ordinati per data di creazione.
     *
     * @param meterId identificativo DLMS del meter
     * @return lista ordinata di comandi in attesa (può essere vuota)
     */
    List<MeterCommand> findPendingByMeterId(String meterId);

    /**
     * Aggiunge un nuovo comando alla coda.
     *
     * @param command comando da accodare (deve essere in stato PENDING)
     */
    void save(MeterCommand command);
}
