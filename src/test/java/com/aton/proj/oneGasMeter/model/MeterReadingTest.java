package com.aton.proj.oneGasMeter.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MeterReadingTest {

    @Test
    void buildWithRequiredFields() {
        MeterReading reading = MeterReading.builder()
                .obisCode("1.0.1.8.0.255")
                .value(12345L)
                .build();

        assertThat(reading.getObisCode()).isEqualTo("1.0.1.8.0.255");
        assertThat(reading.getValue()).isEqualTo(12345L);
        assertThat(reading.getUnit()).isEmpty();
        assertThat(reading.getScaler()).isEqualTo(1.0);
        assertThat(reading.getTimestamp()).isNotNull();
    }

    @Test
    void buildWithAllFields() {
        Instant ts = Instant.parse("2025-01-15T10:00:00Z");

        MeterReading reading = MeterReading.builder()
                .obisCode("1.0.1.8.0.255")
                .value(987.65)
                .unit("Wh")
                .scaler(0.001)
                .timestamp(ts)
                .build();

        assertThat(reading.getObisCode()).isEqualTo("1.0.1.8.0.255");
        assertThat(reading.getValue()).isEqualTo(987.65);
        assertThat(reading.getUnit()).isEqualTo("Wh");
        assertThat(reading.getScaler()).isEqualTo(0.001);
        assertThat(reading.getTimestamp()).isEqualTo(ts);
    }

    @Test
    void buildWithStringValue() {
        MeterReading reading = MeterReading.builder()
                .obisCode("0.0.96.1.0.255")
                .value("SERIAL-001")
                .build();

        assertThat(reading.getValue()).isEqualTo("SERIAL-001");
    }

    @Test
    void buildWithNullObisCodeThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> MeterReading.builder().value(0).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OBIS code");
    }

    @Test
    void buildWithBlankObisCodeThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> MeterReading.builder().obisCode("  ").value(0).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OBIS code");
    }

    @Test
    void defaultTimestampIsCloseToNow() {
        Instant before = Instant.now().minusSeconds(1);
        MeterReading reading = MeterReading.builder()
                .obisCode("1.0.1.8.0.255")
                .value(0)
                .build();
        Instant after = Instant.now().plusSeconds(1);

        assertThat(reading.getTimestamp()).isAfterOrEqualTo(before);
        assertThat(reading.getTimestamp()).isBeforeOrEqualTo(after);
    }

    @Test
    void defaultScalerIsOne() {
        MeterReading reading = MeterReading.builder()
                .obisCode("1.0.1.8.0.255")
                .value(100)
                .build();

        assertThat(reading.getScaler()).isEqualTo(1.0);
    }

    @Test
    void toStringContainsObisCodeAndValue() {
        MeterReading reading = MeterReading.builder()
                .obisCode("1.0.1.8.0.255")
                .value(42)
                .build();

        String str = reading.toString();
        assertThat(str).contains("1.0.1.8.0.255");
        assertThat(str).contains("42");
    }
}
