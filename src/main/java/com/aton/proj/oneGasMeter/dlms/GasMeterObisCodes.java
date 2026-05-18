package com.aton.proj.oneGasMeter.dlms;

/**
 * OBIS codes per contatore GAS DLMS/COSEM.
 * Strutturati per categoria.
 */
public final class GasMeterObisCodes {

    private GasMeterObisCodes() {
        // Utility class
    }

    /* =========================================================
     *  OGGETTI GENERICI DLMS (COMUNI A TUTTI I METER)
     * ========================================================= */

    /** Clock */
    public static final String CLOCK = "0.0.1.0.0.255";

    /** Logical Device Name */
    public static final String LOGICAL_DEVICE_NAME = "0.0.42.0.0.255";

    /** Association Logical Name */
    public static final String ASSOCIATION_LN = "0.0.40.0.0.255";

    /** SAP Assignment */
    public static final String SAP_ASSIGNMENT = "0.0.41.0.0.255";

    /** Security Setup */
    public static final String SECURITY_SETUP = "0.0.43.0.0.255";

    /** Image Transfer */
    public static final String IMAGE_TRANSFER = "0.0.44.0.0.255";

    /** Serial Number */
    public static final String SERIAL_NUMBER = "0.0.96.1.0.255";

    /** Production Date */
    public static final String PRODUCTION_DATE = "0.0.96.1.4.255";

    /** Firmware Version */
    public static final String FIRMWARE_VERSION = "1.0.0.2.0.255";

    /** IEC HDLC Setup */
    public static final String IEC_HDLC_SETUP = "0.0.22.0.0.255";


    /* =========================================================
     *  COMUNICAZIONE (SMART METER CON MODEM)
     * ========================================================= */

    /** TCP/UDP Setup */
    public static final String TCP_UDP_SETUP = "0.0.25.0.0.255";

    /** IPv4 Setup */
    public static final String IPV4_SETUP = "0.0.25.1.0.255";

    /** GPRS Setup */
    public static final String GPRS_SETUP = "0.0.25.4.0.255";

    /** Push Setup */
    public static final String PUSH_SETUP = "0.0.25.9.0.255";


    /* =========================================================
     *  PROFILI ED EVENTI
     * ========================================================= */

    /** Load Profile 1 */
    public static final String LOAD_PROFILE_1 = "1.0.99.1.0.255";

    /** Load Profile 2 */
    public static final String LOAD_PROFILE_2 = "1.0.99.2.0.255";

    /** Event Log */
    public static final String EVENT_LOG = "0.0.99.98.0.255";


    /* =========================================================
     *  MISURE GAS (CLASSE 24.x.x)
     * ========================================================= */

    /** Volume totale (m3) */
    public static final String GAS_VOLUME_TOTAL = "7.0.24.2.1.255";

    /** Volume corretto (conversione PTZ) */
    public static final String GAS_VOLUME_CORRECTED = "7.0.24.2.2.255";

    /** Volume istantaneo */
    public static final String GAS_VOLUME_INSTANTANEOUS = "7.0.24.2.3.255";

    /** Pressione */
    public static final String GAS_PRESSURE = "7.0.24.3.0.255";

    /** Temperatura */
    public static final String GAS_TEMPERATURE = "7.0.24.4.0.255";

    /** Fattore di conversione (PTZ) */
    public static final String GAS_CONVERSION_FACTOR = "7.0.24.5.0.255";


    /* =========================================================
     *  OGGETTI TIPICI PRESENTI NEI CONTATORI ELETTRICI
     * ========================================================= */

    // Energia attiva / reattiva
    public static final String ACTIVE_ENERGY_IMPORT_TOTAL = "1.0.1.8.0.255";
    public static final String ACTIVE_ENERGY_EXPORT_TOTAL = "1.0.2.8.0.255";
    public static final String REACTIVE_ENERGY_IMPORT_TOTAL = "1.0.3.8.0.255";
    public static final String REACTIVE_ENERGY_EXPORT_TOTAL = "1.0.4.8.0.255";

    // Potenza
    public static final String ACTIVE_POWER_IMPORT = "1.0.1.7.0.255";
    public static final String ACTIVE_POWER_EXPORT = "1.0.2.7.0.255";
    public static final String ACTIVE_POWER_TOTAL = "1.0.16.7.0.255";

    // Massima domanda
    public static final String MAXIMUM_DEMAND = "1.0.1.6.0.255";

    // Tensioni trifase
    public static final String VOLTAGE_L1 = "1.0.32.7.0.255";
    public static final String VOLTAGE_L2 = "1.0.52.7.0.255";
    public static final String VOLTAGE_L3 = "1.0.72.7.0.255";

    // Correnti trifase
    public static final String CURRENT_L1 = "1.0.31.7.0.255";
    public static final String CURRENT_L2 = "1.0.51.7.0.255";
    public static final String CURRENT_L3 = "1.0.71.7.0.255";

    // Trasformatori
    public static final String CT_RATIO = "1.0.0.4.2.255";


    /* =========================================================
     *  OPZIONALI / RARI IN GAS
     * ========================================================= */

    /** Disconnect Control (tipico elettrico) */
    public static final String DISCONNECT_CONTROL = "0.0.96.3.10.255";

    /** Limiter (tipico elettrico) */
    public static final String LIMITER = "0.0.17.0.0.255";

}
