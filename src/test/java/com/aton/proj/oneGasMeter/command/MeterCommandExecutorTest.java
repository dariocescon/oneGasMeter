package com.aton.proj.oneGasMeter.command;

import com.aton.proj.oneGasMeter.dlms.DlmsMeterClient;
import com.aton.proj.oneGasMeter.exception.DlmsCommunicationException;
import com.aton.proj.oneGasMeter.model.MeterReading;
import com.aton.proj.oneGasMeter.server.MeterSessionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeterCommandExecutorTest {

    private static final String METER_ID = "METER-42";
    private static final String METER_IP  = "10.0.0.5";

    @Mock private DlmsMeterClient client;

    private InMemoryMeterCommandRepository repo;
    private MeterCommandExecutor executor;

    @BeforeEach
    void setUp() throws DlmsCommunicationException {
        repo = new InMemoryMeterCommandRepository();
        executor = new MeterCommandExecutor(repo);

        MeterReading idReading = MeterReading.builder()
                .obisCode("0.0.96.1.0.255").value(METER_ID).build();
        lenient().when(client.readData("0.0.96.1.0.255")).thenReturn(idReading);
    }

    private MeterSessionEvent event() {
        return new MeterSessionEvent(METER_IP, Instant.now(), client);
    }

    // --- syncClock ---

    @Test
    void syncClock_executedAndMarkedDone() throws DlmsCommunicationException {
        MeterCommand cmd = repo.enqueue(METER_ID, MeterCommandType.SYNC_CLOCK);

        executor.onMeterConnected(event());

        verify(client).syncClock();
        assertThat(cmd.getStatus()).isEqualTo(MeterCommandStatus.DONE);
        assertThat(cmd.getExecutedAt()).isNotNull();
    }

    // --- setClock ---

    @Test
    void setClock_withValidPayload_executedAndMarkedDone() throws DlmsCommunicationException {
        String iso = "2026-03-26T10:00:00Z";
        MeterCommand cmd = new MeterCommand(1, METER_ID, MeterCommandType.SET_CLOCK,
                Map.of("isoDateTime", iso));
        repo.save(cmd);

        executor.onMeterConnected(event());

        verify(client).setClock(Instant.parse(iso));
        assertThat(cmd.getStatus()).isEqualTo(MeterCommandStatus.DONE);
    }

    @Test
    void setClock_markedFailed_whenPayloadMissing() {
        MeterCommand cmd = new MeterCommand(1, METER_ID, MeterCommandType.SET_CLOCK, Map.of());
        repo.save(cmd);

        executor.onMeterConnected(event());

        assertThat(cmd.getStatus()).isEqualTo(MeterCommandStatus.FAILED);
        assertThat(cmd.getErrorMessage()).containsIgnoringCase("isoDateTime");
    }

    // --- valvola ---

    @Test
    void disconnectValve_executedAndMarkedDone() throws DlmsCommunicationException {
        MeterCommand cmd = repo.enqueue(METER_ID, MeterCommandType.DISCONNECT_VALVE);

        executor.onMeterConnected(event());

        verify(client).disconnectValve();
        assertThat(cmd.getStatus()).isEqualTo(MeterCommandStatus.DONE);
    }

    @Test
    void reconnectValve_executedAndMarkedDone() throws DlmsCommunicationException {
        MeterCommand cmd = repo.enqueue(METER_ID, MeterCommandType.RECONNECT_VALVE);

        executor.onMeterConnected(event());

        verify(client).reconnectValve();
        assertThat(cmd.getStatus()).isEqualTo(MeterCommandStatus.DONE);
    }

    // --- gestione errori ---

    @Test
    void command_markedFailed_whenClientThrows() throws DlmsCommunicationException {
        MeterCommand cmd = repo.enqueue(METER_ID, MeterCommandType.SYNC_CLOCK);
        doThrow(new DlmsCommunicationException("timeout")).when(client).syncClock();

        executor.onMeterConnected(event());

        assertThat(cmd.getStatus()).isEqualTo(MeterCommandStatus.FAILED);
        assertThat(cmd.getErrorMessage()).contains("timeout");
    }

    @Test
    void secondCommand_executedEvenIfFirstFails() throws DlmsCommunicationException {
        MeterCommand first  = repo.enqueue(METER_ID, MeterCommandType.SYNC_CLOCK);
        MeterCommand second = repo.enqueue(METER_ID, MeterCommandType.DISCONNECT_VALVE);
        doThrow(new DlmsCommunicationException("timeout")).when(client).syncClock();

        executor.onMeterConnected(event());

        assertThat(first.getStatus()).isEqualTo(MeterCommandStatus.FAILED);
        assertThat(second.getStatus()).isEqualTo(MeterCommandStatus.DONE);
        verify(client).disconnectValve();
    }

    // --- nessun comando ---

    @Test
    void noCommands_clientNotInvoked() throws DlmsCommunicationException {
        executor.onMeterConnected(event());

        verify(client, never()).syncClock();
        verify(client, never()).disconnectValve();
        verify(client, never()).reconnectValve();
        verify(client, never()).setClock(any());
    }

    // --- fallback IP quando device-ID non è leggibile ---

    @Test
    void fallbackToIp_whenDeviceIdReadFails() throws DlmsCommunicationException {
        when(client.readData(anyString())).thenThrow(new DlmsCommunicationException("not supported"));
        MeterCommand cmd = repo.enqueue(METER_IP, MeterCommandType.SYNC_CLOCK);

        executor.onMeterConnected(event());

        verify(client).syncClock();
        assertThat(cmd.getStatus()).isEqualTo(MeterCommandStatus.DONE);
    }
}
