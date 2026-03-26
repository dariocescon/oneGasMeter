package com.aton.proj.oneGasMeter.listener;

import com.aton.proj.oneGasMeter.exception.DlmsCommunicationException;
import com.aton.proj.oneGasMeter.model.MeterReading;
import com.aton.proj.oneGasMeter.server.MeterSessionEvent;
import gurux.dlms.objects.GXDLMSObject;
import gurux.dlms.objects.GXDLMSRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Ascoltatore di sessioni meter in ingresso.
 * <p>
 * Quando un meter si connette, scopre il suo modello COSEM (elenco oggetti)
 * e stampa su console il valore di ogni registro disponibile.
 * </p>
 */
@Component
public class MeterSessionLogger {

    private static final Logger log = LoggerFactory.getLogger(MeterSessionLogger.class);

    /**
     * Invocato da Spring (sincrono) mentre la sessione DLMS con il meter è attiva.
     * Il client viene disconnesso automaticamente al ritorno di questo metodo.
     *
     * @param event evento pubblicato da {@link com.aton.proj.oneGasMeter.server.MeterSessionHandler}
     */
    @EventListener
    public void onMeterConnected(MeterSessionEvent event) {
        log.info("=== Meter connesso: {} alle {} ===", event.meterIp(), event.connectedAt());

        try {
            List<GXDLMSObject> objects = event.client().getObjects();
            log.info("Modello COSEM: {} oggetti scoperti", objects.size());

            for (GXDLMSObject obj : objects) {
                log.info("  [{}] {} - {}",
                        obj.getClass().getSimpleName(),
                        obj.getLogicalName(),
                        obj.getDescription());

                if (obj instanceof GXDLMSRegister) {
                    try {
                        MeterReading reading = event.client().readRegister(obj.getLogicalName());
                        log.info("    → valore={} {} (scaler={})",
                                reading.getValue(), reading.getUnit(), reading.getScaler());
                    } catch (DlmsCommunicationException e) {
                        log.warn("    → lettura fallita: {}", e.getMessage());
                    }
                }
            }

        } catch (DlmsCommunicationException e) {
            log.error("Impossibile ottenere il modello COSEM da {}: {}", event.meterIp(), e.getMessage());
        }

        log.info("=== Sessione con {} terminata ===", event.meterIp());
    }
}
