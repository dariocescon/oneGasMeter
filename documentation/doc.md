# oneGasMeter – DLMS/COSEM Communication Documentation

## Indice

1. [Panoramica del Progetto](#panoramica)
2. [Protocolli Supportati](#protocolli)
3. [Architettura](#architettura)
4. [Configurazione](#configurazione)
5. [Utilizzo DLMS](#utilizzo)
6. [Utilizzo COSEM](#utilizzo-cosem)
7. [Test Unitari](#test)
8. [Dipendenze](#dipendenze)
9. [Riferimenti](#riferimenti)
10. [Changelog](#changelog)

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
├── cosem/
│   └── model/
│       ├── ValveState            # Enum stato valvola (DISCONNECTED, CONNECTED, READY_FOR_RECONNECTION)
│       ├── ExtendedReading       # Value object Extended Register (classe 4)
│       └── DemandReading         # Value object Demand Register (classe 5)
├── dlms/
│   ├── DlmsProtocolType          # Enum: HDLC, HDLC_WITH_MODE_E, WRAPPER, PLC, PLC_HDLC
│   ├── DlmsMeterClient           # Client unificato DLMS+COSEM (registri, clock, valvola, calendar, security...)
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
DlmsMeterClient (DLMS + COSEM unificato)
  ├── GXDLMSClient  (gurux.dlms) ← genera/parsa frame DLMS
  └── DlmsTransport              ← trasmette/riceve byte sul canale fisico
            ├── TcpDlmsTransport  (WRAPPER su TCP socket)
            └── HdlcSerialTransport (HDLC su InputStream/OutputStream)
```

### Operazioni DLMS supportate (package-private, uso interno)

| Operazione | Metodo DlmsMeterClient | Descrizione |
|---|---|---|
| **GET** | `readAttribute(object, attr)` | Lettura generica di qualsiasi attributo |
| **SET** | `writeAttribute(object, attr)` | Scrittura di un attributo (valore gia' impostato sull'oggetto) |
| **ACTION** | `invokeMethod(object, method, param, type)` | Invocazione di un metodo COSEM |

> **Nota:** Questi tre metodi sono **package-private** (non fanno parte dell'API pubblica). Sono usati internamente dai metodi COSEM di alto livello e hanno visibilita' limitata per consentire il testing con Mockito Spy.

### Classi COSEM supportate (metodi pubblici di DlmsMeterClient)

| Classe COSEM | ID | Operazioni |
|---|---|---|
| Clock | 8 | readClock, setClock, syncClock |
| Disconnect Control | 70 | disconnectValve, reconnectValve, getValveState |
| Extended Register | 4 | readExtendedRegister |
| Demand Register | 5 | readDemandRegister, resetDemandRegister, nextPeriodDemandRegister |
| Activity Calendar | 20 | readActivityCalendar, activatePassiveCalendar |
| Script Table | 9 | executeScript |
| Security Setup | 64 | readSecurityPolicy, readSecuritySuite |

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

## 6. Utilizzo COSEM <a name="utilizzo-cosem"></a>

Le operazioni COSEM di alto livello sono metodi pubblici di `DlmsMeterClient`. Non serve creare oggetti aggiuntivi: basta usare il client gia' connesso.

### 6.1 Sincronizzazione orologio

```java
// Sincronizza con l'ora di sistema
client.syncClock();

// Imposta un'ora specifica
client.setClock(Instant.parse("2026-03-15T10:00:00Z"));

// Leggi l'ora del contatore
Instant meterTime = client.readClock();
```

### 6.2 Controllo valvola gas

```java
// Chiudi la valvola (interrompi erogazione)
client.disconnectValve();

// Riapri la valvola
client.reconnectValve();

// Leggi lo stato corrente
ValveState state = client.getValveState();
// state: CONNECTED, DISCONNECTED, READY_FOR_RECONNECTION

// Con OBIS personalizzato
client.disconnectValve("0.0.96.3.11.255");
```

### 6.3 Extended Register (classe 4)

```java
ExtendedReading reading = client.readExtendedRegister("1.0.1.8.0.255");
System.out.println("Valore:    " + reading.getValue());
System.out.println("Scaler:    " + reading.getScaler());
System.out.println("Unita':    " + reading.getUnit());
System.out.println("Stato:     " + reading.getStatus());
System.out.println("Catturato: " + reading.getCaptureTime());
```

### 6.4 Demand Register (classe 5)

```java
DemandReading demand = client.readDemandRegister("1.0.1.4.0.255");
System.out.println("Media corrente: " + demand.getCurrentAverageValue());
System.out.println("Ultima media:   " + demand.getLastAverageValue());
System.out.println("Periodo:        " + demand.getPeriod() + " s");
System.out.println("N. periodi:     " + demand.getNumberOfPeriods());

// Reset del registro
client.resetDemandRegister("1.0.1.4.0.255");

// Avanzamento al periodo successivo
client.nextPeriodDemandRegister("1.0.1.4.0.255");
```

### 6.5 Activity Calendar (classe 20)

```java
// Lettura calendario completo
GXDLMSActivityCalendar cal = client.readActivityCalendar();
System.out.println("Calendario attivo: " + cal.getCalendarNameActive());

// Attivazione del calendario passivo
client.activatePassiveCalendar();
```

### 6.6 Script Table (classe 9)

```java
// Esecuzione di uno script
client.executeScript("0.0.10.0.0.255", 1);
```

### 6.7 Security Setup (classe 64)

```java
Set<SecurityPolicy> policies = client.readSecurityPolicy();
SecuritySuite suite = client.readSecuritySuite();
```

---

## 7. Test Unitari <a name="test"></a>

I test sono scritti con **JUnit 5** e **Mockito** (inclusi tramite `spring-boot-starter-test`).

| Classe di test | Cosa testa |
|---|---|
| `DlmsProtocolTypeTest` | Mapping enum → InterfaceType, metodi `isHdlcBased()`, `isTcpBased()` |
| `DlmsClientConfigTest` | Builder pattern, valori default, validazione input |
| `HdlcSerialTransportTest` | Lettura frame HDLC, gestione flag 0x7E, errori di stream |
| `TcpDlmsTransportTest` | Parsing frame WRAPPER, write/read, timeout, socket mock |
| `DlmsMeterClientTest` | Configurazione GXDLMSClient, readAttribute/writeAttribute/invokeMethod, eccezioni |
| `DlmsMeterClientCosemTest` | Operazioni COSEM: clock, valvola, registri, calendar, script, security (Mockito Spy) |
| `DlmsConnectionFactoryTest` | Creazione client da Spring context, UnsupportedOperationException per HDLC |
| `MeterReadingTest` | Builder, valori default, validazione OBIS code |
| `DlmsCommunicationExceptionTest` | Costruttori, error code, tipo eccezione |
| `ValveStateTest` | Mapping da ControlState, gestione null |
| `ExtendedReadingTest` | Builder, default values, validazione OBIS |
| `DemandReadingTest` | Builder, tutti i campi, validazione OBIS |

Per eseguire tutti i test:
```bash
./mvnw test
```

---

## 8. Dipendenze <a name="dipendenze"></a>

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

## 9. Riferimenti <a name="riferimenti"></a>

- [DLMS/COSEM standard – DLMS UA](https://www.dlms.com/)
- [IEC 62056-46: HDLC data link layer](https://www.iec.ch/)
- [IEC 62056-47: TCP-UDP/IP wrapper](https://www.iec.ch/)
- [gurux.dlms Java library](https://www.gurux.fi/gurux.dlms.java)
- [gurux.dlms GitHub](https://github.com/Gurux/gurux.dlms.java)
- [OBIS code standard IEC 62056-61](https://www.iec.ch/)

---

## 10. Changelog <a name="changelog"></a>

### 2026-03-17 – Refactoring: merge CosemMeterService in DlmsMeterClient

Unificato il service layer COSEM dentro `DlmsMeterClient`. Eliminata la classe `CosemMeterService`.

#### Motivazione

Evitare la dispersione dei metodi tra due classi. Tutti i 17 metodi COSEM di alto livello sono ora metodi pubblici di `DlmsMeterClient`. I tre metodi generici (`readAttribute`, `writeAttribute`, `invokeMethod`) sono stati resi **package-private** (non fanno parte dell'API pubblica, visibili solo per il testing con Mockito Spy).

#### File eliminati

| File | Descrizione |
|---|---|
| `cosem/CosemMeterService.java` | Service layer separato (contenuto migrato in DlmsMeterClient) |
| `cosem/CosemMeterServiceTest.java` | Test del service layer (sostituiti da DlmsMeterClientCosemTest) |

#### File modificati

| File | Modifica |
|---|---|
| `DlmsMeterClient.java` | +17 metodi COSEM pubblici, +4 costanti OBIS, +metodo toInstant(); readAttribute/writeAttribute/invokeMethod da public a package-private |

#### Nuovi test

| File | Test |
|---|---|
| `DlmsMeterClientCosemTest.java` | 22 test COSEM con Mockito Spy (clock, valvola, registri, calendar, script, security, errori) |

Test totali progetto: da 116 a 114 (rimossi 25 test CosemMeterServiceTest, aggiunti 22 in DlmsMeterClientCosemTest, test DlmsMeterClientTest invariati).

---

### 2026-03-17 – Implementazione protocollo COSEM (CosemMeterService)

Aggiunto il service layer COSEM con operazioni specifiche per contatori gas.

#### Nuovi file

| File | Descrizione |
|---|---|
| `cosem/CosemMeterService.java` | Service layer con 17 metodi pubblici per operazioni COSEM |
| `cosem/model/ValveState.java` | Enum stato valvola (mapping da gurux `ControlState`) |
| `cosem/model/ExtendedReading.java` | Value object immutabile per Extended Register (classe 4) |
| `cosem/model/DemandReading.java` | Value object immutabile per Demand Register (classe 5) |

#### Modifiche a file esistenti

| File | Modifica |
|---|---|
| `DlmsMeterClient.java` | +3 metodi pubblici: `readAttribute()`, `writeAttribute()`, `invokeMethod()` |
| `DlmsMeterClient.java` | +1 import: `gurux.dlms.enums.DataType` |

#### Nuovi test

| File | Test |
|---|---|
| `CosemMeterServiceTest.java` | 25 test (clock, valvola, registri, calendar, script, security, errori) |
| `ValveStateTest.java` | 5 test (mapping, null handling) |
| `ExtendedReadingTest.java` | 5 test (builder, default, validazione) |
| `DemandReadingTest.java` | 5 test (builder, default, validazione) |
| `DlmsMeterClientTest.java` | +5 test (readAttribute, writeAttribute, invokeMethod errori) |

Test totali progetto: da 72 a 116.

---

### 2026-03-17 – Fix protocollo DLMS in DlmsMeterClient

Corretti 4 bug nell'implementazione del protocollo DLMS nel file `DlmsMeterClient.java`.

#### Bug 1 (Critico): `sendReceive()` – Write-all-before-read

**Problema:** Il metodo scriveva tutti i frame di richiesta sul transport prima di leggere qualsiasi risposta. In HDLC, ogni frame richiede un ACK individuale dal meter prima di poter inviare il successivo.

**Fix:** Riscritto `sendReceive()` per inviare un frame alla volta e leggere la risposta prima di procedere al frame successivo. Aggiunto helper `readDlmsPacket()` che esegue write → read → `getData()` per singolo frame. Poiché `transport.read()` garantisce un frame completo, una singola chiamata a `getData()` è sufficiente per estrarre l'APDU.

#### Bug 2 (Critico): `connect()` – AARE non processato tramite `getData()`

**Problema:** Dopo la scrittura dei frame AARQ, i byte raw del transport (che includono il framing HDLC/WRAPPER) venivano passati direttamente a `parseAareResponse()`. Questo metodo si aspetta solo l'APDU AARE, non il frame completo. Mancava la chiamata a `GXDLMSClient.getData()` per strippare il framing del livello transport.

**Fix:** La risposta AARE viene ora processata tramite `readDlmsPacket()` → `getData()`, e il risultato `reply.getData()` (solo APDU) viene passato a `parseAareResponse()`. Lo stesso fix è stato applicato al parsing della risposta SNRM/UA e al challenge-response.

#### Bug 3 (Critico): `connect()` – Challenge-response stessa problematica

**Problema:** I frame del challenge-response venivano tutti scritti prima di leggere le risposte, e i byte raw venivano passati a `parseApplicationAssociationResponse()` senza il processing tramite `getData()`.

**Fix:** Applicata la stessa correzione dei Bug 1 e 2.

#### Bug 4 (Medio): `readProfileGeneric()` – Attributo sbagliato

**Problema:** `gxClient.updateValue(profile, 3, ...)` usava l'attributo 3 (`captureObjects`) invece dell'attributo 2 (`buffer`) per i dati letti dal profile generic.

**Fix:** Corretto in `gxClient.updateValue(profile, 2, reply.getValue())`.

#### Riepilogo modifiche

| File | Modifica |
|---|---|
| `DlmsMeterClient.java` | Rimosso import `GXByteBuffer` (non più necessario) |
| `DlmsMeterClient.java` | `connect()`: SNRM/UA usa `readDlmsPacket()` + `parseUAResponse(reply.getData())` |
| `DlmsMeterClient.java` | `connect()`: AARQ/AARE usa `readDlmsPacket()` + `parseAareResponse(reply.getData())` |
| `DlmsMeterClient.java` | `connect()`: challenge usa `readDlmsPacket()` + `parseApplicationAssociationResponse(reply.getData())` |
| `DlmsMeterClient.java` | Nuovo metodo `readDlmsPacket(byte[], GXReplyData)` per I/O singolo frame |
| `DlmsMeterClient.java` | `sendReceive()`: riscritto con pattern frame-by-frame + multi-block |
| `DlmsMeterClient.java` | `readProfileGeneric()`: attributo corretto da 3 a 2 |
