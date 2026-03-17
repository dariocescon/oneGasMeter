package com.aton.proj.oneGasMeter.cosem.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DemandReadingTest {

    @Test
    void buildWithAllFields() {
        Instant captureTime = Instant.parse("2026-03-15T10:00:00Z");
        Instant startTime = Instant.parse("2026-03-15T09:45:00Z");
        Instant readTimestamp = Instant.parse("2026-03-15T10:00:05Z");

        DemandReading reading = DemandReading.builder()
                .obisCode("1.0.1.4.0.255")
                .currentAverageValue(500L)
                .lastAverageValue(480L)
                .scaler(0.01)
                .unit("W")
                .status(0)
                .captureTime(captureTime)
                .startTimeCurrent(startTime)
                .period(900)
                .numberOfPeriods(4)
                .readTimestamp(readTimestamp)
                .build();

        assertThat(reading.getObisCode()).isEqualTo("1.0.1.4.0.255");
        assertThat(reading.getCurrentAverageValue()).isEqualTo(500L);
        assertThat(reading.getLastAverageValue()).isEqualTo(480L);
        assertThat(reading.getScaler()).isEqualTo(0.01);
        assertThat(reading.getUnit()).isEqualTo("W");
        assertThat(reading.getStatus()).isEqualTo(0);
        assertThat(reading.getCaptureTime()).isEqualTo(captureTime);
        assertThat(reading.getStartTimeCurrent()).isEqualTo(startTime);
        assertThat(reading.getPeriod()).isEqualTo(900);
        assertThat(reading.getNumberOfPeriods()).isEqualTo(4);
        assertThat(reading.getReadTimestamp()).isEqualTo(readTimestamp);
    }

    @Test
    void buildWithDefaultValues() {
        DemandReading reading = DemandReading.builder()
                .obisCode("1.0.1.4.0.255")
                .build();

        assertThat(reading.getScaler()).isEqualTo(1.0);
        assertThat(reading.getUnit()).isEmpty();
        assertThat(reading.getReadTimestamp()).isNotNull();
        assertThat(reading.getCurrentAverageValue()).isNull();
        assertThat(reading.getLastAverageValue()).isNull();
        assertThat(reading.getStatus()).isNull();
        assertThat(reading.getCaptureTime()).isNull();
        assertThat(reading.getStartTimeCurrent()).isNull();
        assertThat(reading.getPeriod()).isZero();
        assertThat(reading.getNumberOfPeriods()).isZero();
    }

    @Test
    void buildWithNullObisCodeThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> DemandReading.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OBIS code must not be null or blank");
    }

    @Test
    void buildWithBlankObisCodeThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> DemandReading.builder().obisCode("  ").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OBIS code must not be null or blank");
    }

    @Test
    void toStringContainsAllFields() {
        DemandReading reading = DemandReading.builder()
                .obisCode("1.0.1.4.0.255")
                .currentAverageValue(500L)
                .lastAverageValue(480L)
                .period(900)
                .numberOfPeriods(4)
                .build();

        String str = reading.toString();
        assertThat(str).contains("1.0.1.4.0.255");
        assertThat(str).contains("500");
        assertThat(str).contains("480");
        assertThat(str).contains("900");
    }
}
