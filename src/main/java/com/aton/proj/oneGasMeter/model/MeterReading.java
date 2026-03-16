package com.aton.proj.oneGasMeter.model;

import java.time.Instant;

/**
 * Immutable value object that holds a single DLMS meter reading.
 * <p>
 * Use the nested {@link Builder} to construct instances.
 * </p>
 */
public class MeterReading {

    private final String obisCode;
    private final Object value;
    private final String unit;
    private final double scaler;
    private final Instant timestamp;

    private MeterReading(Builder builder) {
        this.obisCode = builder.obisCode;
        this.value = builder.value;
        this.unit = builder.unit;
        this.scaler = builder.scaler;
        this.timestamp = builder.timestamp;
    }

    /**
     * OBIS code that identifies the measured quantity (e.g. {@code 1.0.1.8.0.255}).
     *
     * @return OBIS code string
     */
    public String getObisCode() {
        return obisCode;
    }

    /**
     * Raw value as returned by the meter.
     * <p>
     * The actual Java type depends on the DLMS data type of the object (e.g.
     * {@link Long}, {@link Double}, {@link String}, byte[]).  Apply
     * {@link #getScaler()} to convert to engineering units.
     * </p>
     *
     * @return raw meter value
     */
    public Object getValue() {
        return value;
    }

    /**
     * Physical unit string (e.g. {@code "Wh"}, {@code "m3"}, {@code "W"}).
     * May be empty if the object has no unit.
     *
     * @return unit string
     */
    public String getUnit() {
        return unit;
    }

    /**
     * Scaler factor; multiply {@link #getValue()} by this to obtain the value
     * in engineering units.  For most gas registers the scaler is 1.0 or a
     * power of 10 (e.g. 0.001 for kWh→MWh conversion).
     *
     * @return scaler (default 1.0)
     */
    public double getScaler() {
        return scaler;
    }

    /**
     * UTC timestamp at which the reading was captured by this application.
     *
     * @return capture timestamp
     */
    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "MeterReading{" +
                "obisCode='" + obisCode + '\'' +
                ", value=" + value +
                ", unit='" + unit + '\'' +
                ", scaler=" + scaler +
                ", timestamp=" + timestamp +
                '}';
    }

    /**
     * Creates a new builder for {@link MeterReading}.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link MeterReading}.
     */
    public static class Builder {

        private String obisCode;
        private Object value;
        private String unit = "";
        private double scaler = 1.0;
        private Instant timestamp = Instant.now();

        /** OBIS code identifying the measured object. */
        public Builder obisCode(String obisCode) {
            this.obisCode = obisCode;
            return this;
        }

        /** Raw value from the meter. */
        public Builder value(Object value) {
            this.value = value;
            return this;
        }

        /** Physical unit string. */
        public Builder unit(String unit) {
            this.unit = unit;
            return this;
        }

        /** Scaler factor (default: 1.0). */
        public Builder scaler(double scaler) {
            this.scaler = scaler;
            return this;
        }

        /** Capture timestamp (default: {@link Instant#now()}). */
        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        /**
         * Builds the {@link MeterReading}.
         *
         * @return new instance
         * @throws IllegalArgumentException if {@code obisCode} is null or blank
         */
        public MeterReading build() {
            if (obisCode == null || obisCode.isBlank()) {
                throw new IllegalArgumentException("OBIS code must not be null or blank");
            }
            return new MeterReading(this);
        }
    }
}
