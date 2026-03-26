package com.aton.proj.oneGasMeter.command;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementazione in-memory di {@link MeterCommandRepository}.
 * <p>
 * Implementazione di default. Per produzione, sostituire con un bean
 * Spring di tipo {@link MeterCommandRepository} basato su JPA o JDBC,
 * e rimuovere questa classe (o il suo {@code @Component}).
 * I comandi non sopravvivono al riavvio dell'applicazione.
 * </p>
 */
@Component
public class InMemoryMeterCommandRepository implements MeterCommandRepository {

    private final List<MeterCommand> store = new CopyOnWriteArrayList<>();
    private final AtomicLong idSequence = new AtomicLong(1);

    @Override
    public List<MeterCommand> findPendingByMeterId(String meterId) {
        return store.stream()
                .filter(c -> c.getMeterId().equals(meterId))
                .filter(c -> c.getStatus() == MeterCommandStatus.PENDING)
                .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .toList();
    }

    @Override
    public void save(MeterCommand command) {
        store.add(command);
    }

    /**
     * Crea e accoda un nuovo comando con ID auto-generato.
     *
     * @param meterId identificativo DLMS del meter destinatario
     * @param type    tipo di comando
     * @return comando creato
     */
    public MeterCommand enqueue(String meterId, MeterCommandType type) {
        MeterCommand cmd = new MeterCommand(idSequence.getAndIncrement(), meterId, type);
        save(cmd);
        return cmd;
    }
}
