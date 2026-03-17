package com.aton.proj.oneGasMeter.cosem.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExtendedReadingTest {

    @Test
    void buildWithAllFields() {
        Instant captureTime = Instant.parse("2026-03-15T10:00:00Z");
        Instant readTimestamp = Instant.parse("2026-03-15T10:00:05Z");

        ExtendedReading reading = ExtendedReading.builder()
                .obisCode("1.0.1.8.0.255")
                .value(12345L)
                .scaler(0.001)
                .unit("kWh")
                .status(0)
                .captureTime(captureTime)
                .readTimestamp(readTimestamp)
                .build();

        assertThat(reading.getObisCode()).isEqualTo("1.0.1.8.0.255");
        assertThat(reading.getValue()).isEqualTo(12345L);
        assertThat(reading.getScaler()).isEqualTo(0.001);
        assertThat(reading.getUnit()).isEqualTo("kWh");
        assertThat(reading.getStatus()).isEqualTo(0);
        assertThat(reading.getCaptureTime()).isEqualTo(captureTime);
        assertThat(reading.getReadTimestamp()).isEqualTo(readTimestamp);
    }

    @Test
    void buildWithDefaultValues() {
        ExtendedReading reading = ExtendedReading.builder()
                .obisCode("1.0.1.8.0.255")
                .build();

        assertThat(reading.getScaler()).isEqualTo(1.0);
        assertThat(reading.getUnit()).isEmpty();
        assertThat(reading.getReadTimestamp()).isNotNull();
        assertThat(reading.getValue()).isNull();
        assertThat(reading.getStatus()).isNull();
        assertThat(reading.getCaptureTime()).isNull();
    }

    @Test
    void buildWithNullObisCodeThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> ExtendedReading.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OBIS code must not be null or blank");
    }

    @Test
    void buildWithBlankObisCodeThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> ExtendedReading.builder().obisCode("  ").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OBIS code must not be null or blank");
    }

    @Test
    void toStringContainsAllFields() {
        ExtendedReading reading = ExtendedReading.builder()
                .obisCode("1.0.1.8.0.255")
                .value(100)
                .unit("Wh")
                .build();

        String str = reading.toString();
        assertThat(str).contains("1.0.1.8.0.255");
        assertThat(str).contains("100");
        assertThat(str).contains("Wh");
    }
}
