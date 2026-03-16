# oneGasMeter – DLMS/COSEM Communication Documentation

## Indice

1. [Panoramica del Progetto](#panoramica)
2. [Protocolli Supportati](#protocolli)
3. [Architettura](#architettura)
4. [Configurazione](#configurazione)
5. [Utilizzo](#utilizzo)
6. [Test Unitari](#test)
7. [Dipendenze](#dipendenze)
8. [Riferimenti](#riferimenti)

---

## 1. Panoramica del Progetto <a name="panoramica"></a>

**oneGasMeter** è un'applicazione Spring Boot per la raccolta di tele-letture da contatori del gas tramite il protocollo **DLMS/COSEM** (Device Language Message Specification / Companion Specification for Energy Metering), definito dagli standard IEC 62056.

La libreria [`gurux.dlms`](https://www.gurux.fi/gurux.dlms.java) (versione 4.0.78) viene utilizzata come motore di protocollo per la generazione e il parsing dei frame DLMS.

---

## 2. Protocolli Supportati <a name="protocolli"></a>

### 2.1 HDLC (IEC 62056-46)

Protocollo **High-Level Data Link Control** su porta seriale o testa ottica.

| Caratteristica | Dettaglio |
|---|---|
| Livello fisico | RS-232, RS-485, testa ottica IEC 62056-21 |
| Connessione | `HdlcSerialTransport` |
| Baud rate tipico | 300 bps (Mode-E) → 9600 / 19200 bps |
| Framing | Flag byte `0x7E` come delimitatori di frame |
| Handshake | SNRM → UA → AARQ → AARE |

**Modalità HDLC_WITH_MODE_E:** Variante che avvia la comunicazione a 300 bps (IEC 62056-21) per negoziare una velocità superiore tramite sequenza Mode-E, prima di passare al protocollo DLMS completo.

### 2.2 DLMS WRAPPER / TCP-IP (IEC 62056-47)

Protocollo **DLMS WRAPPER** su TCP/IP, usato per connessioni di rete (NB-IoT, GPRS, Ethernet, Wi-Fi).

| Caratteristica | Dettaglio |
|---|---|
| Livello fisico | Ethernet, NB-IoT, GPRS, LTE |
| Connessione | `TcpDlmsTransport` |
| Porta TCP default | 4059 (IANA) |
| Frame format | 8-byte header + payload |
| Handshake | AARQ → AARE (no SNRM) |

**Struttura del frame WRAPPER:**
```
Byte 0-1: Version  (0x00 0x01)
Byte 2-3: Source WPORT  (big-endian)
Byte 4-5: Dest WPORT    (big-endian)
Byte 6-7: Payload length (big-endian)
Byte 8.. : APDU payload
```

### 2.3 PLC (Power Line Communication)

Supporto per comunicazione tramite linea elettrica (G3-PLC, PRIME):

| Tipo | Descrizione |
|---|---|
| `PLC` | PLC puro |
| `PLC_HDLC` | PLC con framing HDLC |

---

## 3. Architettura <a name="architettura"></a>

```
com.aton.proj.oneGasMeter/
├── config/
│   └── DlmsClientConfig          # Configurazione immutabile (builder pattern)
├── dlms/
│   ├── DlmsProtocolType          # Enum: HDLC, HDLC_WITH_MODE_E, WRAPPER, PLC, PLC_HDLC
│   ├── DlmsMeterClient           # Client principale (GXDLMSClient + DlmsTransport)
│   └── DlmsConnectionFactory     # Spring @Component factory
│   └── transport/
│       ├── DlmsTransport         # Interfaccia canale fisico
│       ├── TcpDlmsTransport      # Implementazione TCP/IP (WRAPPER)
│       └── HdlcSerialTransport   # Implementazione seriale (HDLC)
├── exception/
│   └── DlmsCommunicationException # Unchecked exception DLMS
└── model/
    └── MeterReading              # Value object lettura contatore
```

### Flusso di comunicazione

```
DlmsConnectionFactory
        │
        ▼ crea
DlmsMeterClient
  ├── GXDLMSClient  (gurux.dlms) ← genera/parsa frame DLMS
  └── DlmsTransport              ← trasmette/riceve byte sul canale fisico
            ├── TcpDlmsTransport  (WRAPPER su TCP socket)
            └── HdlcSerialTransport (HDLC su InputStream/OutputStream)
```

---

## 4. Configurazione <a name="configurazione"></a>

### DlmsClientConfig – parametri

| Parametro | Default | Descrizione |
|---|---|---|
| `host` | — | Hostname/IP (richiesto per WRAPPER) |
| `port` | 4059 | Porta TCP |
| `serialPort` | — | Path porta seriale (richiesto per HDLC) |
| `baudRate` | 9600 | Baud rate seriale |
| `clientAddress` | 16 | Indirizzo client DLMS (SAP) |
| `serverAddress` | 1 | Indirizzo server DLMS (logical device) |
| `protocolType` | WRAPPER | Tipo di protocollo |
| `authentication` | NONE | Livello autenticazione |
| `password` | null | Password (per LOW/HIGH auth) |
| `useLogicalNameReferencing` | true | LN referencing (false = SN) |
| `connectionTimeoutMs` | 5000 | Timeout connessione/lettura (ms) |

### Livelli di autenticazione supportati

| Livello | Descrizione |
|---|---|
| `NONE` | Nessuna autenticazione |
| `LOW` | Password in chiaro |
| `HIGH` | Challenge-response (AAA) |
| `HIGH_MD5` | Challenge-response con MD5 |
| `HIGH_SHA1` | Challenge-response con SHA-1 |
| `HIGH_GMAC` | Autenticazione GMAC (AES-GCM) |

---

## 5. Utilizzo <a name="utilizzo"></a>

### 5.1 Connessione TCP/IP (WRAPPER)

```java
// Configurazione
DlmsClientConfig config = DlmsClientConfig.builder()
    .host("192.168.1.100")
    .port(4059)
    .clientAddress(16)
    .serverAddress(1)
    .protocolType(DlmsProtocolType.WRAPPER)
    .authentication(Authentication.LOW)
    .password("00000000")
    .build();

// Creazione client tramite factory Spring
@Autowired DlmsConnectionFactory factory;
DlmsMeterClient client = factory.createClient(config);

// Connessione
client.connect();
```

### 5.2 Connessione HDLC (seriale / testa ottica)

```java
DlmsClientConfig config = DlmsClientConfig.builder()
    .protocolType(DlmsProtocolType.HDLC)
    .serialPort("/dev/ttyUSB0")
    .baudRate(9600)
    .clientAddress(16)
    .serverAddress(1)
    .build();

// Aprire la porta seriale con una libreria a scelta (es. jSerialComm)
// SerialPort port = SerialPort.getCommPort("/dev/ttyUSB0");
// port.openPort();
// InputStream in = port.getInputStream();
// OutputStream out = port.getOutputStream();

DlmsMeterClient client = factory.createHdlcClient(config, in, out);
client.connect();
```

### 5.3 Lettura registro (GXDLMSRegister, classe 3)

```java
// Lettura energia attiva importata (OBIS 1.0.1.8.0.255)
MeterReading reading = client.readRegister("1.0.1.8.0.255");
System.out.println("Valore: " + reading.getValue());
System.out.println("Unità:  " + reading.getUnit());
System.out.println("Scaler: " + reading.getScaler());
```

### 5.4 Lettura dati generici (GXDLMSData, classe 1)

```java
// Lettura numero seriale (OBIS 0.0.96.1.0.255)
MeterReading serial = client.readData("0.0.96.1.0.255");
System.out.println("Seriale: " + serial.getValue());
```

### 5.5 Lettura orologio contatore

```java
Instant meterTime = client.readClock();
System.out.println("Ora contatore: " + meterTime);
```

### 5.6 Lettura profilo di carico (GXDLMSProfileGeneric, classe 7)

```java
Date from = Date.from(Instant.now().minus(7, ChronoUnit.DAYS));
Date to   = Date.from(Instant.now());

List<Object[]> rows = client.readProfileGeneric("1.0.99.1.0.255", from, to);
for (Object[] row : rows) {
    System.out.println(Arrays.toString(row));
}
```

### 5.7 Elenco tutti gli oggetti DLMS

```java
List<GXDLMSObject> objects = client.getObjects();
for (GXDLMSObject obj : objects) {
    System.out.printf("Classe %d  OBIS %s  %s%n",
        obj.getObjectType().getValue(),
        obj.getLogicalName(),
        obj.getDescription());
}
```

### 5.8 Disconnessione

```java
client.disconnect();
```

---

## 6. Test Unitari <a name="test"></a>

I test sono scritti con **JUnit 5** e **Mockito** (inclusi tramite `spring-boot-starter-test`).

| Classe di test | Cosa testa |
|---|---|
| `DlmsProtocolTypeTest` | Mapping enum → InterfaceType, metodi `isHdlcBased()`, `isTcpBased()` |
| `DlmsClientConfigTest` | Builder pattern, valori default, validazione input |
| `HdlcSerialTransportTest` | Lettura frame HDLC, gestione flag 0x7E, errori di stream |
| `TcpDlmsTransportTest` | Parsing frame WRAPPER, write/read, timeout, socket mock |
| `DlmsMeterClientTest` | Configurazione GXDLMSClient, gestione eccezioni, deleghe transport |
| `DlmsConnectionFactoryTest` | Creazione client da Spring context, UnsupportedOperationException per HDLC |
| `MeterReadingTest` | Builder, valori default, validazione OBIS code |
| `DlmsCommunicationExceptionTest` | Costruttori, error code, tipo eccezione |

Per eseguire tutti i test:
```bash
./mvnw test
```

---

## 7. Dipendenze <a name="dipendenze"></a>

| Artefatto | Versione | Scopo |
|---|---|---|
| `spring-boot-starter` | 4.0.3 | Framework Spring Boot |
| `gurux.dlms` | 4.0.78 | Motore protocollo DLMS/COSEM |
| `spring-boot-starter-test` | (gestita da BOM) | JUnit 5, Mockito, AssertJ |

**Nota:** Per le connessioni HDLC via porta seriale è necessario aggiungere una libreria a scelta:
- [jSerialComm](https://fazecast.github.io/jSerialComm/) – `com.fazecast:jSerialComm:2.10.4`
- [RXTXcomm](http://rxtx.qbang.org/) – dipendenza nativa, non raccomandata per nuovi progetti
- [PureJavaComm](https://github.com/nyholku/purejavacomm) – implementazione pura Java

---

## 8. Riferimenti <a name="riferimenti"></a>

- [DLMS/COSEM standard – DLMS UA](https://www.dlms.com/)
- [IEC 62056-46: HDLC data link layer](https://www.iec.ch/)
- [IEC 62056-47: TCP-UDP/IP wrapper](https://www.iec.ch/)
- [gurux.dlms Java library](https://www.gurux.fi/gurux.dlms.java)
- [gurux.dlms GitHub](https://github.com/Gurux/gurux.dlms.java)
- [OBIS code standard IEC 62056-61](https://www.iec.ch/)
