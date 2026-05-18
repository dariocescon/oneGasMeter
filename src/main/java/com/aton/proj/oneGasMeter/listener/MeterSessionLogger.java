package com.aton.proj.oneGasMeter.listener;

import com.aton.proj.oneGasMeter.command.MeterCommand;
import com.aton.proj.oneGasMeter.command.MeterCommandRepository;
import com.aton.proj.oneGasMeter.dlms.DlmsMeterClient;
import com.aton.proj.oneGasMeter.exception.DlmsCommunicationException;
import com.aton.proj.oneGasMeter.model.MeterReading;
import com.aton.proj.oneGasMeter.server.MeterSessionEvent;
import gurux.dlms.objects.GXDLMSObject;
import gurux.dlms.objects.GXDLMSRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Ascoltatore unico per le sessioni meter in ingresso.
 * <p>
 * Ad ogni connessione:
 * <ol>
 *   <li>Identifica il meter (OBIS {@code 0.0.96.1.0.255}, fallback all'IP).</li>
 *   <li>Esegue i comandi {@code PENDING} accodati per quel meter.</li>
 *   <li>Scopre il modello COSEM e stampa su console il valore di ogni registro.</li>
 * </ol>
 * </p>
 */
@Component
public class MeterSessionLogger {

    private static final Logger log = LoggerFactory.getLogger(MeterSessionLogger.class);

    /** OBIS del Logical Device Name / serial number. */
    private static final String OBIS_DEVICE_ID = "0.0.96.1.0.255";

    private final MeterCommandRepository commandRepository;

    public MeterSessionLogger(MeterCommandRepository commandRepository) {
        this.commandRepository = commandRepository;
    }

    // -----------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------

    /**
     * Invocato da Spring (sincrono) mentre la sessione DLMS con il meter è attiva.
     * Il client viene disconnesso automaticamente al ritorno di questo metodo.
     *
     * @param event evento pubblicato da {@link com.aton.proj.oneGasMeter.server.MeterSessionHandler}
     */
    @EventListener
    public void onMeterConnected(MeterSessionEvent event) {
        DlmsMeterClient client = event.client();
        String meterId = readMeterId(client, event.meterIp());

        log.info("=== Meter connesso: {} (ID: {}) alle {} ===",
                event.meterIp(), meterId, event.connectedAt());

        executeCommands(client, meterId);
        logCosemModel(client, event.meterIp());

        log.info("=== Sessione con {} terminata ===", event.meterIp());
    }

    // -----------------------------------------------------------------------
    // Esecuzione comandi
    // -----------------------------------------------------------------------

    private void executeCommands(DlmsMeterClient client, String meterId) {
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
            case SYNC_CLOCK       -> client.syncClock();
            case SET_CLOCK        -> {
                String iso = cmd.getPayload().get("isoDateTime");
                if (iso == null) throw new IllegalArgumentException("Payload mancante: isoDateTime");
                client.setClock(Instant.parse(iso));
            }
            case DISCONNECT_VALVE -> client.disconnectValve();
            case RECONNECT_VALVE  -> client.reconnectValve();
            case CHANGE_PUSH_DESTINATION -> {
                String ip      = cmd.getPayload().get("ip");
                String portStr = cmd.getPayload().get("port");
                if (ip == null)      throw new IllegalArgumentException("Payload mancante: ip");
                if (portStr == null) throw new IllegalArgumentException("Payload mancante: port");
                int port;
                try {
                    port = Integer.parseInt(portStr);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            "Payload non valido: port deve essere un intero, ricevuto: " + portStr);
                }
                client.setPushDestination(ip, port);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Logging modello COSEM
    // -----------------------------------------------------------------------

    private void logCosemModel(DlmsMeterClient client, String meterIp) {
        try {
            List<GXDLMSObject> objects = client.getObjects();
            log.info("Modello COSEM: {} oggetti scoperti", objects.size());

            for (GXDLMSObject obj : objects) {
                log.info("  [{}] {} - {}",
                        obj.getClass().getSimpleName(),
                        obj.getLogicalName(),
                        obj.getDescription());

                if (obj instanceof GXDLMSRegister) {
                    try {
                        MeterReading reading = client.readRegister(obj.getLogicalName());
                        log.info("    → valore={} {} (scaler={})",
                                reading.getValue(), reading.getUnit(), reading.getScaler());
                    } catch (DlmsCommunicationException e) {
                        log.warn("    → lettura fallita: {}", e.getMessage());
                    }
                }
            }

        } catch (DlmsCommunicationException e) {
            log.error("Impossibile ottenere il modello COSEM da {}: {}", meterIp, e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Utility
    // -----------------------------------------------------------------------

    private String readMeterId(DlmsMeterClient client, String fallbackIp) {
        try {
            return client.readData(OBIS_DEVICE_ID).getValue().toString();
        } catch (DlmsCommunicationException e) {
            log.warn("Impossibile leggere device ID ({}), uso IP come fallback", e.getMessage());
            return fallbackIp;
        }
    }
}
