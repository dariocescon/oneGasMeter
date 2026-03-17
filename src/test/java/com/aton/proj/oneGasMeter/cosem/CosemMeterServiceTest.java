package com.aton.proj.oneGasMeter.cosem;

import com.aton.proj.oneGasMeter.cosem.model.DemandReading;
import com.aton.proj.oneGasMeter.cosem.model.ExtendedReading;
import com.aton.proj.oneGasMeter.cosem.model.ValveState;
import com.aton.proj.oneGasMeter.dlms.DlmsMeterClient;
import com.aton.proj.oneGasMeter.exception.DlmsCommunicationException;
import gurux.dlms.GXDateTime;
import gurux.dlms.enums.DataType;
import gurux.dlms.enums.Unit;
import gurux.dlms.objects.GXDLMSActivityCalendar;
import gurux.dlms.objects.GXDLMSClock;
import gurux.dlms.objects.GXDLMSDemandRegister;
import gurux.dlms.objects.GXDLMSDisconnectControl;
import gurux.dlms.objects.GXDLMSExtendedRegister;
import gurux.dlms.objects.GXDLMSObject;
import gurux.dlms.objects.GXDLMSScriptTable;
import gurux.dlms.objects.GXDLMSSecuritySetup;
import gurux.dlms.objects.enums.ControlState;
import gurux.dlms.objects.enums.SecurityPolicy;
import gurux.dlms.objects.enums.SecuritySuite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Calendar;
import java.util.Date;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CosemMeterServiceTest {

    @Mock
    private DlmsMeterClient client;

    private CosemMeterService service;

    @BeforeEach
    void setUp() {
        service = new CosemMeterService(client);
    }

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    @Test
    void constructorWithNullClientThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> new CosemMeterService(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null");
    }

    // -----------------------------------------------------------------------
    // Clock
    // -----------------------------------------------------------------------

    @Nested
    class ClockTests {

        @Test
        void readClockDelegatesToClient() throws DlmsCommunicationException {
            Instant expected = Instant.parse("2026-03-15T10:00:00Z");
            org.mockito.Mockito.when(client.readClock()).thenReturn(expected);

            Instant result = service.readClock();

            assertThat(result).isEqualTo(expected);
            verify(client).readClock();
        }

        @Test
        void setClockWritesAttributeWithCorrectObis() throws DlmsCommunicationException {
            Instant time = Instant.parse("2026-03-15T10:00:00Z");
            service.setClock(time);

            ArgumentCaptor<GXDLMSObject> captor = ArgumentCaptor.forClass(GXDLMSObject.class);
            verify(client).writeAttribute(captor.capture(), eq(2));

            GXDLMSClock clock = (GXDLMSClock) captor.getValue();
            assertThat(clock.getLogicalName()).isEqualTo("0.0.1.0.0.255");
        }

        @Test
        void setClockWithNullTimeThrowsIllegalArgumentException() {
            assertThatThrownBy(() -> service.setClock(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be null");
        }

        @Test
        void syncClockWritesClock() throws DlmsCommunicationException {
            service.syncClock();

            ArgumentCaptor<GXDLMSObject> captor = ArgumentCaptor.forClass(GXDLMSObject.class);
            verify(client).writeAttribute(captor.capture(), eq(2));
            assertThat(captor.getValue()).isInstanceOf(GXDLMSClock.class);
        }
    }

    // -----------------------------------------------------------------------
    // Disconnect Control
    // -----------------------------------------------------------------------

    @Nested
    class DisconnectControlTests {

        @Test
        void disconnectValveInvokesMethod1WithDefaultObis() throws DlmsCommunicationException {
            service.disconnectValve();

            ArgumentCaptor<GXDLMSObject> captor = ArgumentCaptor.forClass(GXDLMSObject.class);
            verify(client).invokeMethod(captor.capture(), eq(1), eq(0), eq(DataType.INT8));

            assertThat(captor.getValue()).isInstanceOf(GXDLMSDisconnectControl.class);
            assertThat(captor.getValue().getLogicalName()).isEqualTo("0.0.96.3.10.255");
        }

        @Test
        void disconnectValveWithCustomObis() throws DlmsCommunicationException {
            service.disconnectValve("0.0.96.3.11.255");

            ArgumentCaptor<GXDLMSObject> captor = ArgumentCaptor.forClass(GXDLMSObject.class);
            verify(client).invokeMethod(captor.capture(), eq(1), eq(0), eq(DataType.INT8));

            assertThat(captor.getValue().getLogicalName()).isEqualTo("0.0.96.3.11.255");
        }

        @Test
        void reconnectValveInvokesMethod2WithDefaultObis() throws DlmsCommunicationException {
            service.reconnectValve();

            ArgumentCaptor<GXDLMSObject> captor = ArgumentCaptor.forClass(GXDLMSObject.class);
            verify(client).invokeMethod(captor.capture(), eq(2), eq(0), eq(DataType.INT8));

            assertThat(captor.getValue().getLogicalName()).isEqualTo("0.0.96.3.10.255");
        }

        @Test
        void reconnectValveWithCustomObis() throws DlmsCommunicationException {
            service.reconnectValve("0.0.96.3.11.255");

            ArgumentCaptor<GXDLMSObject> captor = ArgumentCaptor.forClass(GXDLMSObject.class);
            verify(client).invokeMethod(captor.capture(), eq(2), eq(0), eq(DataType.INT8));

            assertThat(captor.getValue().getLogicalName()).isEqualTo("0.0.96.3.11.255");
        }

        @Test
        void getValveStateReadsAttribute4() throws DlmsCommunicationException {
            // Simulate readAttribute populating the ControlState
            doAnswer(invocation -> {
                GXDLMSDisconnectControl dc = (GXDLMSDisconnectControl) invocation.getArgument(0);
                dc.setControlState(ControlState.CONNECTED);
                return null;
            }).when(client).readAttribute(any(GXDLMSDisconnectControl.class), eq(4));

            ValveState state = service.getValveState();

            assertThat(state).isEqualTo(ValveState.CONNECTED);
        }

        @Test
        void getValveStateWithCustomObis() throws DlmsCommunicationException {
            doAnswer(invocation -> {
                GXDLMSDisconnectControl dc = (GXDLMSDisconnectControl) invocation.getArgument(0);
                dc.setControlState(ControlState.DISCONNECTED);
                return null;
            }).when(client).readAttribute(any(GXDLMSDisconnectControl.class), eq(4));

            ValveState state = service.getValveState("0.0.96.3.11.255");

            ArgumentCaptor<GXDLMSObject> captor = ArgumentCaptor.forClass(GXDLMSObject.class);
            verify(client).readAttribute(captor.capture(), eq(4));
            assertThat(captor.getValue().getLogicalName()).isEqualTo("0.0.96.3.11.255");
            assertThat(state).isEqualTo(ValveState.DISCONNECTED);
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

            ExtendedReading reading = service.readExtendedRegister(obis);

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
            // Without setting the unit in the mock, gurux defaults to Unit.NONE → "None"
            ExtendedReading reading = service.readExtendedRegister("1.0.1.8.0.255");

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

            DemandReading reading = service.readDemandRegister(obis);

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
            service.resetDemandRegister("1.0.1.4.0.255");

            ArgumentCaptor<GXDLMSObject> captor = ArgumentCaptor.forClass(GXDLMSObject.class);
            verify(client).invokeMethod(captor.capture(), eq(1), eq(0), eq(DataType.INT8));

            assertThat(captor.getValue()).isInstanceOf(GXDLMSDemandRegister.class);
            assertThat(captor.getValue().getLogicalName()).isEqualTo("1.0.1.4.0.255");
        }

        @Test
        void nextPeriodDemandRegisterInvokesMethod2() throws DlmsCommunicationException {
            service.nextPeriodDemandRegister("1.0.1.4.0.255");

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

            GXDLMSActivityCalendar result = service.readActivityCalendar();

            assertThat(result.getLogicalName()).isEqualTo("0.0.13.0.0.255");
            assertThat(result.getCalendarNameActive()).isEqualTo("TariffA");

            // Verify all 9 attributes (2-10) were read
            for (int attr = 2; attr <= 10; attr++) {
                verify(client).readAttribute(any(GXDLMSActivityCalendar.class), eq(attr));
            }
        }

        @Test
        void activatePassiveCalendarInvokesMethod1() throws DlmsCommunicationException {
            service.activatePassiveCalendar();

            ArgumentCaptor<GXDLMSObject> captor = ArgumentCaptor.forClass(GXDLMSObject.class);
            verify(client).invokeMethod(captor.capture(), eq(1), eq(0), eq(DataType.INT8));

            assertThat(captor.getValue()).isInstanceOf(GXDLMSActivityCalendar.class);
            assertThat(captor.getValue().getLogicalName()).isEqualTo("0.0.13.0.0.255");
        }
    }

    // -----------------------------------------------------------------------
    // Script Table
    // -----------------------------------------------------------------------

    @Nested
    class ScriptTableTests {

        @Test
        void executeScriptInvokesMethod1WithScriptId() throws DlmsCommunicationException {
            service.executeScript("0.0.10.0.0.255", 42);

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

            Set<SecurityPolicy> result = service.readSecurityPolicy();

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

            SecuritySuite result = service.readSecuritySuite();

            assertThat(result).isEqualTo(SecuritySuite.SUITE_0);
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

            assertThatThrownBy(() -> service.disconnectValve())
                    .isInstanceOf(DlmsCommunicationException.class)
                    .hasMessageContaining("valve error");
        }

        @Test
        void setClockPropagatesDlmsCommunicationException() throws DlmsCommunicationException {
            doThrow(new DlmsCommunicationException("write error", 2))
                    .when(client).writeAttribute(any(GXDLMSClock.class), eq(2));

            assertThatThrownBy(() -> service.setClock(Instant.now()))
                    .isInstanceOf(DlmsCommunicationException.class)
                    .hasMessageContaining("write error");
        }

        @Test
        void readExtendedRegisterPropagatesDlmsCommunicationException()
                throws DlmsCommunicationException {
            doThrow(new DlmsCommunicationException("read error", 3))
                    .when(client).readAttribute(any(GXDLMSExtendedRegister.class), eq(2));

            assertThatThrownBy(() -> service.readExtendedRegister("1.0.1.8.0.255"))
                    .isInstanceOf(DlmsCommunicationException.class)
                    .hasMessageContaining("read error");
        }
    }
}
