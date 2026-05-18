package com.aton.proj.oneGasMeter.command;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Comando da eseguire su un meter DLMS durante la prossima sessione inbound.
 * <p>
 * Un comando viene creato con stato {@link MeterCommandStatus#PENDING} e
 * aggiornato da {@link com.aton.proj.oneGasMeter.listener.MeterSessionLogger}
 * durante l'esecuzione.
 * </p>
 */
public class MeterCommand {

    private final long id;
    private final String meterId;
    private final MeterCommandType type;
    private final Map<String, String> payload;
    private final Instant createdAt;

    private MeterCommandStatus status;
    private Instant executedAt;
    private String errorMessage;

    public MeterCommand(long id, String meterId, MeterCommandType type, Map<String, String> payload) {
        this.id = id;
        this.meterId = meterId;
        this.type = type;
        this.payload = Collections.unmodifiableMap(new HashMap<>(payload));
        this.createdAt = Instant.now();
        this.status = MeterCommandStatus.PENDING;
    }

    public MeterCommand(long id, String meterId, MeterCommandType type) {
        this(id, meterId, type, Map.of());
    }

    public long getId()                  { return id; }
    public String getMeterId()           { return meterId; }
    public MeterCommandType getType()    { return type; }
    public Map<String, String> getPayload() { return payload; }
    public Instant getCreatedAt()        { return createdAt; }
    public MeterCommandStatus getStatus() { return status; }
    public Instant getExecutedAt()       { return executedAt; }
    public String getErrorMessage()      { return errorMessage; }

    public void markInProgress() {
        this.status = MeterCommandStatus.IN_PROGRESS;
    }

    public void markDone() {
        this.status = MeterCommandStatus.DONE;
        this.executedAt = Instant.now();
    }

    public void markFailed(String errorMessage) {
        this.status = MeterCommandStatus.FAILED;
        this.executedAt = Instant.now();
        this.errorMessage = errorMessage;
    }
}
