package com.aton.proj.oneGasMeter.listener;

import com.aton.proj.oneGasMeter.command.InMemoryMeterCommandRepository;
import com.aton.proj.oneGasMeter.command.MeterCommand;
import com.aton.proj.oneGasMeter.command.MeterCommandStatus;
import com.aton.proj.oneGasMeter.command.MeterCommandType;
import com.aton.proj.oneGasMeter.dlms.DlmsMeterClient;
import com.aton.proj.oneGasMeter.exception.DlmsCommunicationException;
import com.aton.proj.oneGasMeter.model.MeterReading;
import com.aton.proj.oneGasMeter.server.MeterSessionEvent;
import gurux.dlms.objects.GXDLMSData;
import gurux.dlms.objects.GXDLMSRegister;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
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
class MeterSessionLoggerTest {

    private static final String METER_ID = "METER-42";
    private static final String METER_IP  = "10.0.0.5";

    @Mock private DlmsMeterClient client;

    private InMemoryMeterCommandRepository repo;
    private MeterSessionLogger logger;

    @BeforeEach
    void setUp() throws DlmsCommunicationException {
        repo   = new InMemoryMeterCommandRepository();
        logger = new MeterSessionLogger(repo);

        MeterReading idReading = MeterReading.builder()
                .obisCode("0.0.96.1.0.255").value(METER_ID).build();
        lenient().when(client.readData("0.0.96.1.0.255")).thenReturn(idReading);
        lenient().when(client.getObjects()).thenReturn(List.of());
    }

    private MeterSessionEvent event() {
        return new MeterSessionEvent(METER_IP, Instant.now(), client);
    }

    // -----------------------------------------------------------------------
    // Comandi — syncClock
    // -----------------------------------------------------------------------

    @Test
    void syncClock_executedAndMarkedDone() throws DlmsCommunicationException {
        MeterCommand cmd = repo.enqueue(METER_ID, MeterCommandType.SYNC_CLOCK);

        logger.onMeterConnected(event());

        verify(client).syncClock();
        assertThat(cmd.getStatus()).isEqualTo(MeterCommandStatus.DONE);
        assertThat(cmd.getExecutedAt()).isNotNull();
    }

    // -----------------------------------------------------------------------
    // Comandi — setClock
    // -----------------------------------------------------------------------

    @Test
    void setClock_withValidPayload_executedAndMarkedDone() throws DlmsCommunicationException {
        String iso = "2026-03-26T10:00:00Z";
        MeterCommand cmd = new MeterCommand(1, METER_ID, MeterCommandType.SET_CLOCK,
                Map.of("isoDateTime", iso));
        repo.save(cmd);

        logger.onMeterConnected(event());

        verify(client).setClock(Instant.parse(iso));
        assertThat(cmd.getStatus()).isEqualTo(MeterCommandStatus.DONE);
    }

    @Test
    void setClock_markedFailed_whenPayloadMissing() {
        MeterCommand cmd = new MeterCommand(1, METER_ID, MeterCommandType.SET_CLOCK, Map.of());
        repo.save(cmd);

        logger.onMeterConnected(event());

        assertThat(cmd.getStatus()).isEqualTo(MeterCommandStatus.FAILED);
        assertThat(cmd.getErrorMessage()).containsIgnoringCase("isoDateTime");
    }

    // -----------------------------------------------------------------------
    // Comandi — valvola
    // -----------------------------------------------------------------------

    @Test
    void disconnectValve_executedAndMarkedDone() throws DlmsCommunicationException {
        MeterCommand cmd = repo.enqueue(METER_ID, MeterCommandType.DISCONNECT_VALVE);

        logger.onMeterConnected(event());

        verify(client).disconnectValve();
        assertThat(cmd.getStatus()).isEqualTo(MeterCommandStatus.DONE);
    }

    @Test
    void reconnectValve_executedAndMarkedDone() throws DlmsCommunicationException {
        MeterCommand cmd = repo.enqueue(METER_ID, MeterCommandType.RECONNECT_VALVE);

        logger.onMeterConnected(event());

        verify(client).reconnectValve();
        assertThat(cmd.getStatus()).isEqualTo(MeterCommandStatus.DONE);
    }

    // -----------------------------------------------------------------------
    // Comandi — gestione errori
    // -----------------------------------------------------------------------

    @Test
    void command_markedFailed_whenClientThrows() throws DlmsCommunicationException {
        MeterCommand cmd = repo.enqueue(METER_ID, MeterCommandType.SYNC_CLOCK);
        doThrow(new DlmsCommunicationException("timeout")).when(client).syncClock();

        logger.onMeterConnected(event());

        assertThat(cmd.getStatus()).isEqualTo(MeterCommandStatus.FAILED);
        assertThat(cmd.getErrorMessage()).contains("timeout");
    }

    @Test
    void secondCommand_executedEvenIfFirstFails() throws DlmsCommunicationException {
        MeterCommand first  = repo.enqueue(METER_ID, MeterCommandType.SYNC_CLOCK);
        MeterCommand second = repo.enqueue(METER_ID, MeterCommandType.DISCONNECT_VALVE);
        doThrow(new DlmsCommunicationException("timeout")).when(client).syncClock();

        logger.onMeterConnected(event());

        assertThat(first.getStatus()).isEqualTo(MeterCommandStatus.FAILED);
        assertThat(second.getStatus()).isEqualTo(MeterCommandStatus.DONE);
        verify(client).disconnectValve();
    }

    @Test
    void noCommands_clientNotInvokedForCommands() throws DlmsCommunicationException {
        logger.onMeterConnected(event());

        verify(client, never()).syncClock();
        verify(client, never()).disconnectValve();
        verify(client, never()).reconnectValve();
        verify(client, never()).setClock(any());
    }

    // -----------------------------------------------------------------------
    // Comandi — fallback IP
    // -----------------------------------------------------------------------

    @Test
    void fallbackToIp_whenDeviceIdReadFails() throws DlmsCommunicationException {
        when(client.readData(anyString())).thenThrow(new DlmsCommunicationException("not supported"));
        MeterCommand cmd = repo.enqueue(METER_IP, MeterCommandType.SYNC_CLOCK);

        logger.onMeterConnected(event());

        verify(client).syncClock();
        assertThat(cmd.getStatus()).isEqualTo(MeterCommandStatus.DONE);
    }

    // -----------------------------------------------------------------------
    // Comandi — cambio destinazione push
    // -----------------------------------------------------------------------

    @Test
    void changePushDestination_executedAndMarkedDone() throws DlmsCommunicationException {
        MeterCommand cmd = new MeterCommand(1, METER_ID, MeterCommandType.CHANGE_PUSH_DESTINATION,
                Map.of("ip", "192.168.1.10", "port", "4059"));
        repo.save(cmd);

        logger.onMeterConnected(event());

        verify(client).setPushDestination("192.168.1.10", 4059);
        assertThat(cmd.getStatus()).isEqualTo(MeterCommandStatus.DONE);
        assertThat(cmd.getExecutedAt()).isNotNull();
    }

    @Test
    void changePushDestination_markedFailed_whenIpMissing() {
        MeterCommand cmd = new MeterCommand(1, METER_ID, MeterCommandType.CHANGE_PUSH_DESTINATION,
                Map.of("port", "4059"));
        repo.save(cmd);

        logger.onMeterConnected(event());

        assertThat(cmd.getStatus()).isEqualTo(MeterCommandStatus.FAILED);
        assertThat(cmd.getErrorMessage()).containsIgnoringCase("ip");
    }

    @Test
    void changePushDestination_markedFailed_whenPortMissing() {
        MeterCommand cmd = new MeterCommand(1, METER_ID, MeterCommandType.CHANGE_PUSH_DESTINATION,
                Map.of("ip", "192.168.1.10"));
        repo.save(cmd);

        logger.onMeterConnected(event());

        assertThat(cmd.getStatus()).isEqualTo(MeterCommandStatus.FAILED);
        assertThat(cmd.getErrorMessage()).containsIgnoringCase("port");
    }

    @Test
    void changePushDestination_markedFailed_whenPortNotNumeric() {
        MeterCommand cmd = new MeterCommand(1, METER_ID, MeterCommandType.CHANGE_PUSH_DESTINATION,
                Map.of("ip", "192.168.1.10", "port", "abc"));
        repo.save(cmd);

        logger.onMeterConnected(event());

        assertThat(cmd.getStatus()).isEqualTo(MeterCommandStatus.FAILED);
        assertThat(cmd.getErrorMessage()).containsIgnoringCase("port");
    }

    // -----------------------------------------------------------------------
    // Logging COSEM
    // -----------------------------------------------------------------------

    @Test
    void cosemObjects_queried_onConnection() throws DlmsCommunicationException {
        logger.onMeterConnected(event());

        verify(client).getObjects();
    }

    @Test
    void cosemRegister_isRead_whenPresentInObjectList() throws DlmsCommunicationException {
        GXDLMSRegister reg = new GXDLMSRegister("1.0.1.8.0.255");
        when(client.getObjects()).thenReturn(List.of(reg));
        MeterReading reading = MeterReading.builder()
                .obisCode("1.0.1.8.0.255").value("1234").unit("Wh").scaler(-1).build();
        when(client.readRegister("1.0.1.8.0.255")).thenReturn(reading);

        logger.onMeterConnected(event());

        verify(client).readRegister("1.0.1.8.0.255");
    }

    @Test
    void nonRegisterObject_isNotRead() throws DlmsCommunicationException {
        GXDLMSData data = new GXDLMSData("0.0.96.1.0.255");
        when(client.getObjects()).thenReturn(List.of(data));

        logger.onMeterConnected(event());

        verify(client, never()).readRegister(anyString());
    }

    @Test
    void registerReadFailure_doesNotAbortSession() throws DlmsCommunicationException {
        GXDLMSRegister reg = new GXDLMSRegister("1.0.1.8.0.255");
        when(client.getObjects()).thenReturn(List.of(reg));
        when(client.readRegister(anyString()))
                .thenThrow(new DlmsCommunicationException("attribute not supported"));

        // Non deve lanciare eccezioni
        logger.onMeterConnected(event());
    }

    @Test
    void getObjectsFailure_doesNotAbortSession() throws DlmsCommunicationException {
        when(client.getObjects()).thenThrow(new DlmsCommunicationException("association failed"));

        // Non deve lanciare eccezioni
        logger.onMeterConnected(event());
    }
}
