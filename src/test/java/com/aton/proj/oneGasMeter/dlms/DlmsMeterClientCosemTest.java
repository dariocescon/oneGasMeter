package com.aton.proj.oneGasMeter.dlms;

import com.aton.proj.oneGasMeter.config.DlmsClientConfig;
import com.aton.proj.oneGasMeter.cosem.model.DemandReading;
import com.aton.proj.oneGasMeter.cosem.model.ExtendedReading;
import com.aton.proj.oneGasMeter.cosem.model.ValveState;
import com.aton.proj.oneGasMeter.dlms.transport.DlmsTransport;
import com.aton.proj.oneGasMeter.exception.DlmsCommunicationException;
import gurux.dlms.GXDateTime;
import gurux.dlms.enums.Authentication;
import gurux.dlms.enums.DataType;
import gurux.dlms.enums.Unit;
import gurux.dlms.objects.GXDLMSActivityCalendar;
import gurux.dlms.objects.GXDLMSClock;
import gurux.dlms.objects.GXDLMSData;
import gurux.dlms.objects.GXDLMSDemandRegister;
import gurux.dlms.objects.GXDLMSDisconnectControl;
import gurux.dlms.objects.GXDLMSExtendedRegister;
import gurux.dlms.objects.GXDLMSObject;
import gurux.dlms.objects.GXDLMSProfileGeneric;
import gurux.dlms.objects.GXDLMSScriptTable;
import gurux.dlms.objects.GXDLMSSecuritySetup;
import gurux.dlms.objects.enums.ClockBase;
import gurux.dlms.objects.enums.ControlMode;
import gurux.dlms.objects.enums.ControlState;
import gurux.dlms.objects.enums.SecurityPolicy;
import gurux.dlms.objects.enums.SecuritySuite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Date;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Test delle operazioni COSEM integrate in {@link DlmsMeterClient}.
 * <p>
 * Utilizza {@link Mockito#spy(Object)} per intercettare le chiamate ai metodi
 * package-private {@code readAttribute}, {@code writeAttribute} e {@code invokeMethod},
 * verificando che i metodi COSEM di alto livello usino correttamente gli OBIS code,
 * gli attributi e gli indici di metodo previsti dal protocollo.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class DlmsMeterClientCosemTest {

    @Mock
    private DlmsTransport transport;

    private DlmsMeterClient client;

    @BeforeEach
    void setUp() {
        DlmsClientConfig config = DlmsClientConfig.builder()
                .host("192.168.1.100")
                .port(4059)
                .clientAddress(16)
                .serverAddress(1)
                .protocolType(DlmsProtocolType.WRAPPER)
                .authentication(Authentication.NONE)
                .build();
        client = Mockito.spy(new DlmsMeterClient(config, transport));
    }

    // -----------------------------------------------------------------------
    // Clock
    // -----------------------------------------------------------------------

    @Nested
    class ClockTests {

        @Test
        void setClockWritesAttributeWithCorrectObis() throws DlmsCommunicationException {
            doNothing().when(client).writeAttribute(any(GXDLMSClock.class), eq(2));

            client.setClock(Instant.parse("2026-03-15T10:00:00Z"));

            ArgumentCaptor<GXDLMSObject> captor = ArgumentCaptor.forClass(GXDLMSObject.class);
            verify(client).writeAttribute(captor.capture(), eq(2));

            GXDLMSClock clock = (GXDLMSClock) captor.getValue();
            assertThat(clock.getLogicalName()).isEqualTo("0.0.1.0.0.255");
        }

        @Test
        void setClockWithNullTimeThrowsIllegalArgumentException() {
            assertThatThrownBy(() -> client.setClock(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be null");
        }

        @Test
        void syncClockWritesClock() throws DlmsCommunicationException {
            doNothing().when(client).writeAttribute(any(GXDLMSClock.class), eq(2));

            client.syncClock();

            ArgumentCaptor<GXDLMSObject> captor = ArgumentCaptor.forClass(GXDLMSObject.class);
            verify(client).writeAttribute(captor.capture(), eq(2));
            assertThat(captor.getValue()).isInstanceOf(GXDLMSClock.class);
        }

        @Test
        void setTimeZoneWritesAttribute3WithCorrectOffset() throws DlmsCommunicationException {
            doNothing().when(client).writeAttribute(any(GXDLMSClock.class), eq(3));

            client.setTimeZone(60);

            ArgumentCaptor<GXDLMSObject> captor = ArgumentCaptor.forClass(GXDLMSObject.class);
            verify(client).writeAttribute(captor.capture(), eq(3));

            GXDLMSClock clock = (GXDLMSClock) captor.getValue();
            assertThat(clock.getLogicalName()).isEqualTo("0.0.1.0.0.255");
            assertThat(clock.getTimeZone()).isEqualTo(60);
        }

        @Test
        void setDaylightSavingsWritesAttributes5to8() throws DlmsCommunicationException {
            doNothing().when(client).writeAttribute(any(GXDLMSClock.class), anyInt());

            GXDateTime begin = new GXDateTime(Date.from(Instant.parse("2026-03-29T01:00:00Z")));
            GXDateTime end   = new GXDateTime(Date.from(Instant.parse("2026-10-25T01:00:00Z")));

            client.setDaylightSavings(true, begin, end, 60);

            verify(client).writeAttribute(any(GXDLMSClock.class), eq(5));
            verify(client).writeAttribute(any(GXDLMSClock.class), eq(6));
            verify(client).writeAttribute(any(GXDLMSClock.class), eq(7));
            verify(client).writeAttribute(any(GXDLMSClock.class), eq(8));
        }

        @Test
        void setDaylightSavingsSetsCorrectValuesOnClockObject() throws DlmsCommunicationException {
            doNothing().when(client).writeAttribute(any(GXDLMSClock.class), anyInt());

            GXDateTime begin = new GXDateTime(Date.from(Instant.parse("2026-03-29T01:00:00Z")));
            GXDateTime end   = new GXDateTime(Date.from(Instant.parse("2026-10-25T01:00:00Z")));

            client.setDaylightSavings(true, begin, end, 60);

            // Capture the clock from the first write call (attr 5) and verify DST fields
            ArgumentCaptor<GXDLMSObject> captor = ArgumentCaptor.forClass(GXDLMSObject.class);
            verify(client).writeAttribute(captor.capture(), eq(5));
            GXDLMSClock clock = (GXDLMSClock) captor.getValue();
            assertThat(clock.getEnabled()).isTrue();
            assertThat(clock.getDeviation()).isEqualTo(60);
        }

        @Test
        void setClockBaseWritesAttribute9() throws DlmsCommunicationException {
            doNothing().when(client).writeAttribute(any(GXDLMSClock.class), eq(9));

            client.setClockBase(ClockBase.CRYSTAL);

            ArgumentCaptor<GXDLMSObject> captor = ArgumentCaptor.forClass(GXDLMSObject.class);
            verify(client).writeAttribute(captor.capture(), eq(9));

            GXDLMSClock clock = (GXDLMSClock) captor.getValue();
            assertThat(clock.getLogicalName()).isEqualTo("0.0.1.0.0.255");
            assertThat(clock.getClockBase()).isEqualTo(ClockBase.CRYSTAL);
        }

        @Test
        void setClockBaseWithNullThrowsIllegalArgumentException() {
            assertThatThrownBy(() -> client.setClockBase(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be null");
        }
    }

    // -----------------------------------------------------------------------
    // Disconnect Control
    // -----------------------------------------------------------------------

    @Nested
    class DisconnectControlTests {

        @Test
        void disconnectValveInvokesMethod1WithDefaultObis() throws DlmsCommunicationException {
            doReturn(null).when(client).invokeMethod(
                    any(GXDLMSDisconnectControl.class), eq(1), eq(0), eq(DataType.INT8));

            client.disconnectValve();

            ArgumentCaptor<GXDLMSObject> captor = ArgumentCaptor.forClass(GXDLMSObject.class);
            verify(client).invokeMethod(captor.capture(), eq(1), eq(0), eq(DataType.INT8));

            assertThat(captor.getValue()).isInstanceOf(GXDLMSDisconnectControl.class);
            assertThat(captor.getValue().getLogicalName()).isEqualTo("0.0.96.3.10.255");
        }

        @Test
        void disconnectValveWithCustomObis() throws DlmsCommunicationException {
            doReturn(null).when(client).invokeMethod(
                    any(GXDLMSDisconnectControl.class), eq(1), eq(0), eq(DataType.INT8));

            client.disconnectValve("0.0.96.3.11.255");

            ArgumentCaptor<GXDLMSObject> captor = ArgumentCaptor.forClass(GXDLMSObject.class);
            verify(client).invokeMethod(captor.capture(), eq(1), eq(0), eq(DataType.INT8));

            assertThat(captor.getValue().getLogicalName()).isEqualTo("0.0.96.3.11.255");
        }

        @Test
        void reconnectValveInvokesMethod2WithDefaultObis() throws DlmsCommunicationException {
            doReturn(null).when(client).invokeMethod(
                    any(GXDLMSDisconnectControl.class), eq(2), eq(0), eq(DataType.INT8));

            client.reconnectValve();

            ArgumentCaptor<GXDLMSObject> captor = ArgumentCaptor.forClass(GXDLMSObject.class);
            verify(client).invokeMethod(captor.capture(), eq(2), eq(0), eq(DataType.INT8));

            assertThat(captor.getValue().getLogicalName()).isEqualTo("0.0.96.3.10.255");
        }

        @Test
        void reconnectValveWithCustomObis() throws DlmsCommunicationException {
            doReturn(null).when(client).invokeMethod(
                    any(GXDLMSDisconnectControl.class), eq(2), eq(0), eq(DataType.INT8));

            client.reconnectValve("0.0.96.3.11.255");

            ArgumentCaptor<GXDLMSObject> captor = ArgumentCaptor.forClass(GXDLMSObject.class);
            verify(client).invokeMethod(captor.capture(), eq(2), eq(0), eq(DataType.INT8));

            assertThat(captor.getValue().getLogicalName()).isEqualTo("0.0.96.3.11.255");
        }

        @Test
        void getValveStateReadsAttribute4() throws DlmsCommunicationException {
            doAnswer(invocation -> {
                GXDLMSDisconnectControl dc = (GXDLMSDisconnectControl) invocation.getArgument(0);
                dc.setControlState(ControlState.CONNECTED);
                return null;
            }).when(client).readAttribute(any(GXDLMSDisconnectControl.class), eq(4));

            ValveState state = client.getValveState();

            assertThat(state).isEqualTo(ValveState.CONNECTED);
        }

        @Test
        void getValveStateWithCustomObis() throws DlmsCommunicationException {
            doAnswer(invocation -> {
                GXDLMSDisconnectControl dc = (GXDLMSDisconnectControl) invocation.getArgument(0);
                dc.setControlState(ControlState.DISCONNECTED);
                return null;
            }).when(client).readAttribute(any(GXDLMSDisconnectControl.class), eq(4));

            ValveState state = client.getValveState("0.0.96.3.11.255");

            ArgumentCaptor<GXDLMSObject> captor = ArgumentCaptor.forClass(GXDLMSObject.class);
            verify(client).readAttribute(captor.capture(), eq(4));
            assertThat(captor.getValue().getLogicalName()).isEqualTo("0.0.96.3.11.255");
            assertThat(state).isEqualTo(ValveState.DISCONNECTED);
        }

        @Test
        void setControlModeWritesAttribute3WithDefaultObis() throws DlmsCommunicationException {
            doNothing().when(client).writeAttribute(any(GXDLMSDisconnectControl.class), eq(3));

            client.setControlMode(ControlMode.MODE_3);

            ArgumentCaptor<GXDLMSObject> captor = ArgumentCaptor.forClass(GXDLMSObject.class);
            verify(client).writeAttribute(captor.capture(), eq(3));

            GXDLMSDisconnectControl dc = (GXDLMSDisconnectControl) captor.getValue();
            assertThat(dc.getLogicalName()).isEqualTo("0.0.96.3.10.255");
            assertThat(dc.getControlMode()).isEqualTo(ControlMode.MODE_3);
        }

        @Test
        void setControlModeWithCustomObis() throws DlmsCommunicationException {
            doNothing().when(client).writeAttribute(any(GXDLMSDisconnectControl.class), eq(3));

            client.setControlMode("0.0.96.3.11.255", ControlMode.MODE_1);

            ArgumentCaptor<GXDLMSObject> captor = ArgumentCaptor.forClass(GXDLMSObject.class);
            verify(client).writeAttribute(captor.capture(), eq(3));

            assertThat(captor.getValue().getLogicalName()).isEqualTo("0.0.96.3.11.255");
        }

        @Test
        void setControlModeWithNullThrowsIllegalArgumentException() {
            assertThatThrownBy(() -> client.setControlMode(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be null");
        }
    }

    // -----------------------------------------------------------------------
    // Extended Register
    // -----------------------------------------------------------------------

    @Nested
    class ExtendedRegisterTests {

        @Test
        void readExtendedRegisterReadsAttributes2To5() throws DlmsCommunicationException {
            String obis = "1.0.1.8.0.255";

            doAnswer(invocation -> {
                GXDLMSExtendedRegister reg = (GXDLMSExtendedRegister) invocation.getArgument(0);
                int attr = invocation.getArgument(1);
                switch (attr) {
                    case 2 -> reg.setValue(12345L);
                    case 3 -> { reg.setScaler(0.001); reg.setUnit(Unit.ACTIVE_ENERGY); }
                    case 4 -> reg.setStatus(0);
                    case 5 -> reg.setCaptureTime(new GXDateTime(Date.from(
                            Instant.parse("2026-03-15T10:00:00Z"))));
                }
                return null;
            }).when(client).readAttribute(any(GXDLMSExtendedRegister.class), anyInt());

            ExtendedReading reading = client.readExtendedRegister(obis);

            assertThat(reading.getObisCode()).isEqualTo(obis);
            assertThat(reading.getValue()).isEqualTo(12345L);
            assertThat(reading.getScaler()).isEqualTo(0.001);
            assertThat(reading.getStatus()).isEqualTo(0);
            assertThat(reading.getReadTimestamp()).isNotNull();

            // Verify all 4 attributes were read
            verify(client).readAttribute(any(GXDLMSExtendedRegister.class), eq(2));
            verify(client).readAttribute(any(GXDLMSExtendedRegister.class), eq(3));
            verify(client).readAttribute(any(GXDLMSExtendedRegister.class), eq(4));
            verify(client).readAttribute(any(GXDLMSExtendedRegister.class), eq(5));
        }

        @Test
        void readExtendedRegisterDefaultUnitIsNone() throws DlmsCommunicationException {
            // Without setting the unit in the stub, gurux defaults to Unit.NONE → "None"
            doReturn(null).when(client).readAttribute(
                    any(GXDLMSExtendedRegister.class), anyInt());

            ExtendedReading reading = client.readExtendedRegister("1.0.1.8.0.255");

            assertThat(reading.getUnit()).isEqualTo("None");
        }
    }

    // -----------------------------------------------------------------------
    // Demand Register
    // -----------------------------------------------------------------------

    @Nested
    class DemandRegisterTests {

        @Test
        void readDemandRegisterReadsAttributes2To9() throws DlmsCommunicationException {
            String obis = "1.0.1.4.0.255";

            doAnswer(invocation -> {
                GXDLMSDemandRegister reg = (GXDLMSDemandRegister) invocation.getArgument(0);
                int attr = invocation.getArgument(1);
                switch (attr) {
                    case 2 -> reg.setCurrentAverageValue(500L);
                    case 3 -> reg.setLastAverageValue(480L);
                    case 4 -> { reg.setScaler(0.01); reg.setUnit(Unit.ACTIVE_POWER); }
                    case 5 -> reg.setStatus(0);
                    case 6 -> reg.setCaptureTime(new GXDateTime(Date.from(
                            Instant.parse("2026-03-15T10:00:00Z"))));
                    case 7 -> reg.setStartTimeCurrent(new GXDateTime(Date.from(
                            Instant.parse("2026-03-15T09:45:00Z"))));
                    case 8 -> reg.setPeriod(900);
                    case 9 -> reg.setNumberOfPeriods(4);
                }
                return null;
            }).when(client).readAttribute(any(GXDLMSDemandRegister.class), anyInt());

            DemandReading reading = client.readDemandRegister(obis);

            assertThat(reading.getObisCode()).isEqualTo(obis);
            assertThat(reading.getCurrentAverageValue()).isEqualTo(500L);
            assertThat(reading.getLastAverageValue()).isEqualTo(480L);
            assertThat(reading.getScaler()).isEqualTo(0.01);
            assertThat(reading.getStatus()).isEqualTo(0);
            assertThat(reading.getPeriod()).isEqualTo(900);
            assertThat(reading.getNumberOfPeriods()).isEqualTo(4);

            // Verify all 8 attributes were read
            for (int attr = 2; attr <= 9; attr++) {
                verify(client).readAttribute(any(GXDLMSDemandRegister.class), eq(attr));
            }
        }

        @Test
        void resetDemandRegisterInvokesMethod1() throws DlmsCommunicationException {
            doReturn(null).when(client).invokeMethod(
                    any(GXDLMSDemandRegister.class), eq(1), eq(0), eq(DataType.INT8));

            client.resetDemandRegister("1.0.1.4.0.255");

            ArgumentCaptor<GXDLMSObject> captor = ArgumentCaptor.forClass(GXDLMSObject.class);
            verify(client).invokeMethod(captor.capture(), eq(1), eq(0), eq(DataType.INT8));

            assertThat(captor.getValue()).isInstanceOf(GXDLMSDemandRegister.class);
            assertThat(captor.getValue().getLogicalName()).isEqualTo("1.0.1.4.0.255");
        }

        @Test
        void nextPeriodDemandRegisterInvokesMethod2() throws DlmsCommunicationException {
            doReturn(null).when(client).invokeMethod(
                    any(GXDLMSDemandRegister.class), eq(2), eq(0), eq(DataType.INT8));

            client.nextPeriodDemandRegister("1.0.1.4.0.255");

            ArgumentCaptor<GXDLMSObject> captor = ArgumentCaptor.forClass(GXDLMSObject.class);
            verify(client).invokeMethod(captor.capture(), eq(2), eq(0), eq(DataType.INT8));

            assertThat(captor.getValue()).isInstanceOf(GXDLMSDemandRegister.class);
        }
    }

    // -----------------------------------------------------------------------
    // Activity Calendar
    // -----------------------------------------------------------------------

    @Nested
    class ActivityCalendarTests {

        @Test
        void readActivityCalendarReadsAttributes2To10() throws DlmsCommunicationException {
            doAnswer(invocation -> {
                GXDLMSActivityCalendar cal = (GXDLMSActivityCalendar) invocation.getArgument(0);
                int attr = invocation.getArgument(1);
                if (attr == 2) {
                    cal.setCalendarNameActive("TariffA");
                }
                return null;
            }).when(client).readAttribute(any(GXDLMSActivityCalendar.class), anyInt());

            GXDLMSActivityCalendar result = client.readActivityCalendar();

            assertThat(result.getLogicalName()).isEqualTo("0.0.13.0.0.255");
            assertThat(result.getCalendarNameActive()).isEqualTo("TariffA");

            // Verify all 9 attributes (2-10) were read
            for (int attr = 2; attr <= 10; attr++) {
                verify(client).readAttribute(any(GXDLMSActivityCalendar.class), eq(attr));
            }
        }

        @Test
        void activatePassiveCalendarInvokesMethod1() throws DlmsCommunicationException {
            doReturn(null).when(client).invokeMethod(
                    any(GXDLMSActivityCalendar.class), eq(1), eq(0), eq(DataType.INT8));

            client.activatePassiveCalendar();

            ArgumentCaptor<GXDLMSObject> captor = ArgumentCaptor.forClass(GXDLMSObject.class);
            verify(client).invokeMethod(captor.capture(), eq(1), eq(0), eq(DataType.INT8));

            assertThat(captor.getValue()).isInstanceOf(GXDLMSActivityCalendar.class);
            assertThat(captor.getValue().getLogicalName()).isEqualTo("0.0.13.0.0.255");
        }

        @Test
        void writePassiveCalendarWritesAttributes6To10() throws DlmsCommunicationException {
            doNothing().when(client).writeAttribute(any(GXDLMSActivityCalendar.class), anyInt());

            GXDLMSActivityCalendar cal = new GXDLMSActivityCalendar("0.0.13.0.0.255");
            cal.setCalendarNamePassive("Tariffa2027");

            client.writePassiveCalendar(cal);

            // Verify all 5 passive attributes were written
            for (int attr = 6; attr <= 10; attr++) {
                verify(client).writeAttribute(any(GXDLMSActivityCalendar.class), eq(attr));
            }
        }

        @Test
        void writePassiveCalendarWithNullThrowsIllegalArgumentException() {
            assertThatThrownBy(() -> client.writePassiveCalendar(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be null");
        }
    }

    // -----------------------------------------------------------------------
    // Script Table
    // -----------------------------------------------------------------------

    @Nested
    class ScriptTableTests {

        @Test
        void executeScriptInvokesMethod1WithScriptId() throws DlmsCommunicationException {
            doReturn(null).when(client).invokeMethod(
                    any(GXDLMSScriptTable.class), eq(1), eq(42), eq(DataType.UINT16));

            client.executeScript("0.0.10.0.0.255", 42);

            ArgumentCaptor<GXDLMSObject> captor = ArgumentCaptor.forClass(GXDLMSObject.class);
            verify(client).invokeMethod(captor.capture(), eq(1), eq(42), eq(DataType.UINT16));

            assertThat(captor.getValue()).isInstanceOf(GXDLMSScriptTable.class);
            assertThat(captor.getValue().getLogicalName()).isEqualTo("0.0.10.0.0.255");
        }
    }

    // -----------------------------------------------------------------------
    // Security Setup
    // -----------------------------------------------------------------------

    @Nested
    class SecuritySetupTests {

        @Test
        void readSecurityPolicyReadsAttribute2() throws DlmsCommunicationException {
            doAnswer(invocation -> {
                GXDLMSSecuritySetup sec = (GXDLMSSecuritySetup) invocation.getArgument(0);
                sec.setSecurityPolicy(Set.of(SecurityPolicy.AUTHENTICATED, SecurityPolicy.ENCRYPTED));
                return null;
            }).when(client).readAttribute(any(GXDLMSSecuritySetup.class), eq(2));

            Set<SecurityPolicy> result = client.readSecurityPolicy();

            assertThat(result).containsExactlyInAnyOrder(
                    SecurityPolicy.AUTHENTICATED, SecurityPolicy.ENCRYPTED);

            ArgumentCaptor<GXDLMSObject> captor = ArgumentCaptor.forClass(GXDLMSObject.class);
            verify(client).readAttribute(captor.capture(), eq(2));
            assertThat(captor.getValue().getLogicalName()).isEqualTo("0.0.43.0.0.255");
        }

        @Test
        void readSecuritySuiteReadsAttribute3() throws DlmsCommunicationException {
            doAnswer(invocation -> {
                GXDLMSSecuritySetup sec = (GXDLMSSecuritySetup) invocation.getArgument(0);
                sec.setSecuritySuite(SecuritySuite.SUITE_0);
                return null;
            }).when(client).readAttribute(any(GXDLMSSecuritySetup.class), eq(3));

            SecuritySuite result = client.readSecuritySuite();

            assertThat(result).isEqualTo(SecuritySuite.SUITE_0);
        }

        @Test
        void setSecurityPolicyWritesAttribute2WithCorrectObis() throws DlmsCommunicationException {
            doNothing().when(client).writeAttribute(any(GXDLMSSecuritySetup.class), eq(2));

            Set<SecurityPolicy> policy = Set.of(SecurityPolicy.AUTHENTICATED);
            client.setSecurityPolicy(policy);

            ArgumentCaptor<GXDLMSObject> captor = ArgumentCaptor.forClass(GXDLMSObject.class);
            verify(client).writeAttribute(captor.capture(), eq(2));

            GXDLMSSecuritySetup sec = (GXDLMSSecuritySetup) captor.getValue();
            assertThat(sec.getLogicalName()).isEqualTo("0.0.43.0.0.255");
            assertThat(sec.getSecurityPolicy()).containsExactly(SecurityPolicy.AUTHENTICATED);
        }

        @Test
        void setSecurityPolicyWithNullThrowsIllegalArgumentException() {
            assertThatThrownBy(() -> client.setSecurityPolicy(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be null");
        }

        @Test
        void setSecuritySuiteWritesAttribute3() throws DlmsCommunicationException {
            doNothing().when(client).writeAttribute(any(GXDLMSSecuritySetup.class), eq(3));

            client.setSecuritySuite(SecuritySuite.SUITE_0);

            ArgumentCaptor<GXDLMSObject> captor = ArgumentCaptor.forClass(GXDLMSObject.class);
            verify(client).writeAttribute(captor.capture(), eq(3));

            GXDLMSSecuritySetup sec = (GXDLMSSecuritySetup) captor.getValue();
            assertThat(sec.getLogicalName()).isEqualTo("0.0.43.0.0.255");
            assertThat(sec.getSecuritySuite()).isEqualTo(SecuritySuite.SUITE_0);
        }

        @Test
        void setSecuritySuiteWithNullThrowsIllegalArgumentException() {
            assertThatThrownBy(() -> client.setSecuritySuite(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be null");
        }
    }

    // -----------------------------------------------------------------------
    // Profile Generic — configurazione
    // -----------------------------------------------------------------------

    @Nested
    class ProfileGenericTests {

        @Test
        void setCapturePeriodWritesAttribute4WithCorrectObisAndPeriod()
                throws DlmsCommunicationException {
            doNothing().when(client).writeAttribute(any(GXDLMSProfileGeneric.class), eq(4));

            client.setCapturePeriod("1.0.99.1.0.255", 3600L);

            ArgumentCaptor<GXDLMSObject> captor = ArgumentCaptor.forClass(GXDLMSObject.class);
            verify(client).writeAttribute(captor.capture(), eq(4));

            GXDLMSProfileGeneric profile = (GXDLMSProfileGeneric) captor.getValue();
            assertThat(profile.getLogicalName()).isEqualTo("1.0.99.1.0.255");
            assertThat(profile.getCapturePeriod()).isEqualTo(3600L);
        }

        @Test
        void setCaptureObjectsThrowsUnsupportedOperationException() {
            assertThatThrownBy(() -> client.setCaptureObjects("1.0.99.1.0.255"))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("non ancora implementato");
        }
    }

    // -----------------------------------------------------------------------
    // Data — scrittura
    // -----------------------------------------------------------------------

    @Nested
    class DataTests {

        @Test
        void writeDataWritesAttribute2WithCorrectObisAndValue() throws DlmsCommunicationException {
            doNothing().when(client).writeAttribute(any(GXDLMSData.class), eq(2));

            client.writeData("0.0.96.5.4.255", 42);

            ArgumentCaptor<GXDLMSObject> captor = ArgumentCaptor.forClass(GXDLMSObject.class);
            verify(client).writeAttribute(captor.capture(), eq(2));

            GXDLMSData data = (GXDLMSData) captor.getValue();
            assertThat(data.getLogicalName()).isEqualTo("0.0.96.5.4.255");
            assertThat(data.getValue()).isEqualTo(42);
        }
    }

    // -----------------------------------------------------------------------
    // Error propagation
    // -----------------------------------------------------------------------

    @Nested
    class ErrorPropagationTests {

        @Test
        void disconnectValvePropagatesDlmsCommunicationException() throws DlmsCommunicationException {
            doThrow(new DlmsCommunicationException("valve error", 1))
                    .when(client).invokeMethod(any(), eq(1), eq(0), eq(DataType.INT8));

            assertThatThrownBy(() -> client.disconnectValve())
                    .isInstanceOf(DlmsCommunicationException.class)
                    .hasMessageContaining("valve error");
        }

        @Test
        void setClockPropagatesDlmsCommunicationException() throws DlmsCommunicationException {
            doThrow(new DlmsCommunicationException("write error", 2))
                    .when(client).writeAttribute(any(GXDLMSClock.class), eq(2));

            assertThatThrownBy(() -> client.setClock(Instant.now()))
                    .isInstanceOf(DlmsCommunicationException.class)
                    .hasMessageContaining("write error");
        }

        @Test
        void readExtendedRegisterPropagatesDlmsCommunicationException()
                throws DlmsCommunicationException {
            doThrow(new DlmsCommunicationException("read error", 3))
                    .when(client).readAttribute(any(GXDLMSExtendedRegister.class), eq(2));

            assertThatThrownBy(() -> client.readExtendedRegister("1.0.1.8.0.255"))
                    .isInstanceOf(DlmsCommunicationException.class)
                    .hasMessageContaining("read error");
        }

        @Test
        void setControlModePropagatesDlmsCommunicationException() throws DlmsCommunicationException {
            doThrow(new DlmsCommunicationException("write error", 4))
                    .when(client).writeAttribute(any(GXDLMSDisconnectControl.class), eq(3));

            assertThatThrownBy(() -> client.setControlMode(ControlMode.MODE_3))
                    .isInstanceOf(DlmsCommunicationException.class)
                    .hasMessageContaining("write error");
        }

        @Test
        void writePassiveCalendarPropagatesDlmsCommunicationException()
                throws DlmsCommunicationException {
            doThrow(new DlmsCommunicationException("write error", 5))
                    .when(client).writeAttribute(any(GXDLMSActivityCalendar.class), eq(6));

            GXDLMSActivityCalendar cal = new GXDLMSActivityCalendar("0.0.13.0.0.255");

            assertThatThrownBy(() -> client.writePassiveCalendar(cal))
                    .isInstanceOf(DlmsCommunicationException.class)
                    .hasMessageContaining("write error");
        }

        @Test
        void setTimeZonePropagatesDlmsCommunicationException() throws DlmsCommunicationException {
            doThrow(new DlmsCommunicationException("timezone error", 6))
                    .when(client).writeAttribute(any(GXDLMSClock.class), eq(3));

            assertThatThrownBy(() -> client.setTimeZone(60))
                    .isInstanceOf(DlmsCommunicationException.class)
                    .hasMessageContaining("timezone error");
        }

        @Test
        void setCapturePeriodPropagatesDlmsCommunicationException()
                throws DlmsCommunicationException {
            doThrow(new DlmsCommunicationException("profile error", 7))
                    .when(client).writeAttribute(any(GXDLMSProfileGeneric.class), eq(4));

            assertThatThrownBy(() -> client.setCapturePeriod("1.0.99.1.0.255", 900L))
                    .isInstanceOf(DlmsCommunicationException.class)
                    .hasMessageContaining("profile error");
        }
    }

}
