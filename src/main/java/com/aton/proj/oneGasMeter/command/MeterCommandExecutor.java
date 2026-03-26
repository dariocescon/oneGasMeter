package com.aton.proj.oneGasMeter.command;

import com.aton.proj.oneGasMeter.dlms.DlmsMeterClient;
import com.aton.proj.oneGasMeter.exception.DlmsCommunicationException;
import com.aton.proj.oneGasMeter.server.MeterSessionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Esegue i comandi in attesa ogni volta che un meter si connette.
 * <p>
 * Al ricevimento di un {@link MeterSessionEvent}:
 * <ol>
 *   <li>Legge l'ID DLMS del meter (OBIS {@code 0.0.96.1.0.255}).</li>
 *   <li>Carica i comandi {@link MeterCommandStatus#PENDING} per quell'ID.</li>
 *   <li>Esegue ogni comando in ordine di creazione.</li>
 *   <li>Aggiorna lo stato ({@link MeterCommandStatus#DONE} /
 *       {@link MeterCommandStatus#FAILED}) per ciascuno.</li>
 * </ol>
 * </p>
 *
 * <h3>Robustezza</h3>
 * <p>
 * Un errore su un singolo comando non interrompe l'esecuzione degli altri.
 * I comandi falliti restano in stato {@link MeterCommandStatus#FAILED} con il
 * messaggio d'errore e possono essere reaccodati dall'operatore.
 * </p>
 */
@Component
public class MeterCommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(MeterCommandExecutor.class);

    /** OBIS del Logical Device Name / serial number. */
    private static final String OBIS_DEVICE_ID = "0.0.96.1.0.255";

    private final MeterCommandRepository commandRepository;

    public MeterCommandExecutor(MeterCommandRepository commandRepository) {
        this.commandRepository = commandRepository;
    }

    @EventListener
    public void onMeterConnected(MeterSessionEvent event) {
        DlmsMeterClient client = event.client();

        String meterId = readMeterId(client, event.meterIp());
        log.info("Sessione con meter {} (IP: {})", meterId, event.meterIp());

        List<MeterCommand> pending = commandRepository.findPendingByMeterId(meterId);
        if (pending.isEmpty()) {
            log.debug("Nessun comando in attesa per meter {}", meterId);
            return;
        }

        log.info("{} comando/i da eseguire per meter {}", pending.size(), meterId);
        for (MeterCommand cmd : pending) {
            executeCommand(client, cmd, meterId);
        }
    }

    // -----------------------------------------------------------------------

    private void executeCommand(DlmsMeterClient client, MeterCommand cmd, String meterId) {
        log.info("Esecuzione comando {} [id={}] per meter {}", cmd.getType(), cmd.getId(), meterId);
        cmd.markInProgress();
        try {
            dispatch(client, cmd);
            cmd.markDone();
            log.info("Comando {} [id={}] completato", cmd.getType(), cmd.getId());
        } catch (Exception e) {
            cmd.markFailed(e.getMessage());
            log.error("Comando {} [id={}] fallito: {}", cmd.getType(), cmd.getId(), e.getMessage());
        }
    }

    private void dispatch(DlmsMeterClient client, MeterCommand cmd)
            throws DlmsCommunicationException {
        switch (cmd.getType()) {
            case SYNC_CLOCK -> client.syncClock();
            case SET_CLOCK -> {
                String iso = cmd.getPayload().get("isoDateTime");
                if (iso == null) throw new IllegalArgumentException("Payload mancante: isoDateTime");
                client.setClock(Instant.parse(iso));
            }
            case DISCONNECT_VALVE -> client.disconnectValve();
            case RECONNECT_VALVE  -> client.reconnectValve();
        }
    }

    private String readMeterId(DlmsMeterClient client, String fallbackIp) {
        try {
            return client.readData(OBIS_DEVICE_ID).getValue().toString();
        } catch (DlmsCommunicationException e) {
            log.warn("Impossibile leggere device ID ({}), uso IP come fallback", e.getMessage());
            return fallbackIp;
        }
    }
}
