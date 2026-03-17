package com.aton.proj.oneGasMeter.cosem.model;

import java.time.Instant;

/**
 * Immutable value object per una lettura da Extended Register (classe COSEM 4).
 * <p>
 * Oltre ai campi di un registro standard (valore, scaler, unita'), include
 * lo stato e il timestamp di cattura registrati dal contatore.
 * </p>
 * <p>
 * Usa il {@link Builder} annidato per costruire le istanze.
 * </p>
 */
public class ExtendedReading {

    private final String obisCode;
    private final Object value;
    private final double scaler;
    private final String unit;
    private final Object status;
    private final Instant captureTime;
    private final Instant readTimestamp;

    private ExtendedReading(Builder builder) {
        this.obisCode = builder.obisCode;
        this.value = builder.value;
        this.scaler = builder.scaler;
        this.unit = builder.unit;
        this.status = builder.status;
        this.captureTime = builder.captureTime;
        this.readTimestamp = builder.readTimestamp;
    }

    /** OBIS code dell'Extended Register. */
    public String getObisCode() {
        return obisCode;
    }

    /** Valore grezzo dal contatore (attributo 2). */
    public Object getValue() {
        return value;
    }

    /** Fattore di scala (attributo 3). */
    public double getScaler() {
        return scaler;
    }

    /** Unita' di misura (attributo 3). */
    public String getUnit() {
        return unit;
    }

    /** Stato del registro (attributo 4). */
    public Object getStatus() {
        return status;
    }

    /** Timestamp di cattura del valore nel contatore (attributo 5). */
    public Instant getCaptureTime() {
        return captureTime;
    }

    /** Timestamp UTC della lettura da parte dell'applicazione. */
    public Instant getReadTimestamp() {
        return readTimestamp;
    }

    @Override
    public String toString() {
        return "ExtendedReading{" +
                "obisCode='" + obisCode + '\'' +
                ", value=" + value +
                ", scaler=" + scaler +
                ", unit='" + unit + '\'' +
                ", status=" + status +
                ", captureTime=" + captureTime +
                ", readTimestamp=" + readTimestamp +
                '}';
    }

    /**
     * Crea un nuovo {@link Builder}.
     *
     * @return nuovo builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder per {@link ExtendedReading}.
     */
    public static class Builder {

        private String obisCode;
        private Object value;
        private double scaler = 1.0;
        private String unit = "";
        private Object status;
        private Instant captureTime;
        private Instant readTimestamp = Instant.now();

        public Builder obisCode(String obisCode) {
            this.obisCode = obisCode;
            return this;
        }

        public Builder value(Object value) {
            this.value = value;
            return this;
        }

        public Builder scaler(double scaler) {
            this.scaler = scaler;
            return this;
        }

        public Builder unit(String unit) {
            this.unit = unit;
            return this;
        }

        public Builder status(Object status) {
            this.status = status;
            return this;
        }

        public Builder captureTime(Instant captureTime) {
            this.captureTime = captureTime;
            return this;
        }

        public Builder readTimestamp(Instant readTimestamp) {
            this.readTimestamp = readTimestamp;
            return this;
        }

        /**
         * Costruisce l'istanza di {@link ExtendedReading}.
         *
         * @return nuova istanza
         * @throws IllegalArgumentException se {@code obisCode} e' {@code null} o vuoto
         */
        public ExtendedReading build() {
            if (obisCode == null || obisCode.isBlank()) {
                throw new IllegalArgumentException("OBIS code must not be null or blank");
            }
            return new ExtendedReading(this);
        }
    }
}
