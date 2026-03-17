package com.aton.proj.oneGasMeter.cosem.model;

import java.time.Instant;

/**
 * Immutable value object per una lettura da Demand Register (classe COSEM 5).
 * <p>
 * Contiene la media corrente, l'ultima media, parametri di scaling, stato,
 * timestamp e configurazione dei periodi di integrazione.
 * </p>
 * <p>
 * Usa il {@link Builder} annidato per costruire le istanze.
 * </p>
 */
public class DemandReading {

    private final String obisCode;
    private final Object currentAverageValue;
    private final Object lastAverageValue;
    private final double scaler;
    private final String unit;
    private final Object status;
    private final Instant captureTime;
    private final Instant startTimeCurrent;
    private final long period;
    private final long numberOfPeriods;
    private final Instant readTimestamp;

    private DemandReading(Builder builder) {
        this.obisCode = builder.obisCode;
        this.currentAverageValue = builder.currentAverageValue;
        this.lastAverageValue = builder.lastAverageValue;
        this.scaler = builder.scaler;
        this.unit = builder.unit;
        this.status = builder.status;
        this.captureTime = builder.captureTime;
        this.startTimeCurrent = builder.startTimeCurrent;
        this.period = builder.period;
        this.numberOfPeriods = builder.numberOfPeriods;
        this.readTimestamp = builder.readTimestamp;
    }

    /** OBIS code del Demand Register. */
    public String getObisCode() {
        return obisCode;
    }

    /** Media corrente del periodo in corso (attributo 2). */
    public Object getCurrentAverageValue() {
        return currentAverageValue;
    }

    /** Ultima media completata (attributo 3). */
    public Object getLastAverageValue() {
        return lastAverageValue;
    }

    /** Fattore di scala (attributo 4). */
    public double getScaler() {
        return scaler;
    }

    /** Unita' di misura (attributo 4). */
    public String getUnit() {
        return unit;
    }

    /** Stato del registro (attributo 5). */
    public Object getStatus() {
        return status;
    }

    /** Timestamp di cattura dell'ultimo valore medio (attributo 6). */
    public Instant getCaptureTime() {
        return captureTime;
    }

    /** Inizio del periodo di integrazione corrente (attributo 7). */
    public Instant getStartTimeCurrent() {
        return startTimeCurrent;
    }

    /** Durata del periodo di integrazione in secondi (attributo 8). */
    public long getPeriod() {
        return period;
    }

    /** Numero di periodi per il calcolo della media (attributo 9). */
    public long getNumberOfPeriods() {
        return numberOfPeriods;
    }

    /** Timestamp UTC della lettura da parte dell'applicazione. */
    public Instant getReadTimestamp() {
        return readTimestamp;
    }

    @Override
    public String toString() {
        return "DemandReading{" +
                "obisCode='" + obisCode + '\'' +
                ", currentAverageValue=" + currentAverageValue +
                ", lastAverageValue=" + lastAverageValue +
                ", scaler=" + scaler +
                ", unit='" + unit + '\'' +
                ", status=" + status +
                ", captureTime=" + captureTime +
                ", startTimeCurrent=" + startTimeCurrent +
                ", period=" + period +
                ", numberOfPeriods=" + numberOfPeriods +
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
     * Builder per {@link DemandReading}.
     */
    public static class Builder {

        private String obisCode;
        private Object currentAverageValue;
        private Object lastAverageValue;
        private double scaler = 1.0;
        private String unit = "";
        private Object status;
        private Instant captureTime;
        private Instant startTimeCurrent;
        private long period;
        private long numberOfPeriods;
        private Instant readTimestamp = Instant.now();

        public Builder obisCode(String obisCode) {
            this.obisCode = obisCode;
            return this;
        }

        public Builder currentAverageValue(Object currentAverageValue) {
            this.currentAverageValue = currentAverageValue;
            return this;
        }

        public Builder lastAverageValue(Object lastAverageValue) {
            this.lastAverageValue = lastAverageValue;
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

        public Builder startTimeCurrent(Instant startTimeCurrent) {
            this.startTimeCurrent = startTimeCurrent;
            return this;
        }

        public Builder period(long period) {
            this.period = period;
            return this;
        }

        public Builder numberOfPeriods(long numberOfPeriods) {
            this.numberOfPeriods = numberOfPeriods;
            return this;
        }

        public Builder readTimestamp(Instant readTimestamp) {
            this.readTimestamp = readTimestamp;
            return this;
        }

        /**
         * Costruisce l'istanza di {@link DemandReading}.
         *
         * @return nuova istanza
         * @throws IllegalArgumentException se {@code obisCode} e' {@code null} o vuoto
         */
        public DemandReading build() {
            if (obisCode == null || obisCode.isBlank()) {
                throw new IllegalArgumentException("OBIS code must not be null or blank");
            }
            return new DemandReading(this);
        }
    }
}
