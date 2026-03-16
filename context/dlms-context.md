# oneGasMeter – Contesto Progetto per Copilot

## Descrizione

Applicazione Spring Boot (Java 17) per la raccolta di tele-letture da contatori del gas
tramite il protocollo **DLMS/COSEM** (IEC 62056), usando la libreria `gurux.dlms` v4.0.78.

## Stack tecnologico

- Java 17
- Spring Boot 4.0.3
- `org.gurux:gurux.dlms:4.0.78`
- JUnit 5, Mockito, AssertJ (via `spring-boot-starter-test`)
- Build: Maven (`./mvnw test`)

## Struttura dei pacchetti principali

```
com.aton.proj.oneGasMeter
├── config/          DlmsClientConfig (builder, immutable)
├── dlms/
│   ├── DlmsProtocolType        (enum: HDLC, HDLC_WITH_MODE_E, WRAPPER, PLC, PLC_HDLC)
│   ├── DlmsMeterClient         (GXDLMSClient + DlmsTransport)
│   └── DlmsConnectionFactory   (@Component Spring factory)
│   └── transport/
│       ├── DlmsTransport       (interface: connect/disconnect/write/read/isConnected)
│       ├── TcpDlmsTransport    (TCP socket, WRAPPER protocol, legge frame da 8-byte header)
│       └── HdlcSerialTransport (InputStream/OutputStream, frame delimitati da 0x7E)
├── exception/       DlmsCommunicationException (RuntimeException, + errorCode int)
└── model/           MeterReading (builder, obisCode/value/unit/scaler/timestamp)
```

## Protocolli implementati

| Enum | InterfaceType gurux | Trasporto |
|---|---|---|
| `HDLC` | `InterfaceType.HDLC` | `HdlcSerialTransport` |
| `HDLC_WITH_MODE_E` | `InterfaceType.HDLC_WITH_MODE_E` | `HdlcSerialTransport` |
| `WRAPPER` | `InterfaceType.WRAPPER` | `TcpDlmsTransport` |
| `PLC` | `InterfaceType.PLC` | custom |
| `PLC_HDLC` | `InterfaceType.PLC_HDLC` | custom |

## Metodi pubblici di DlmsMeterClient

```java
void connect()                                                      // SNRM→UA→AARQ→AARE (HDLC) / AARQ→AARE (WRAPPER)
void disconnect()                                                   // DISC + transport.disconnect()
List<GXDLMSObject> getObjects()                                     // lettura object list
MeterReading readRegister(String obisCode)                          // classe 3, attr 2+3
MeterReading readData(String obisCode)                              // classe 1, attr 2
Instant readClock()                                                 // classe 8, OBIS 0.0.1.0.0.255
List<Object[]> readProfileGeneric(String obis, Date from, Date to)  // classe 7, readRowsByRange
boolean isConnected()
GXDLMSClient getGxClient()                                          // accesso avanzato gurux
```

## Metodi di DlmsConnectionFactory (@Component)

```java
DlmsMeterClient createClient(DlmsClientConfig config)                                      // auto-transport (solo WRAPPER)
DlmsMeterClient createClient(DlmsClientConfig config, DlmsTransport transport)             // custom transport
DlmsMeterClient createHdlcClient(DlmsClientConfig config, InputStream in, OutputStream out) // HDLC + serial streams
```

## Convenzioni di codice

- Le eccezioni da gurux.dlms (crypto, IO) vengono incapsulate in `DlmsCommunicationException`
- Il multi-block DLMS è gestito automaticamente da `sendReceive()` tramite `receiverReady()`
- I test usano `@ExtendWith(MockitoExtension.class)` per i mock, `@SpringBootTest` per il context
- Pattern builder con validazione in `build()` per le classi di config/model

## OBIS code comuni per contatori gas

| OBIS | Descrizione |
|---|---|
| `0.0.1.0.0.255` | Orologio |
| `0.0.96.1.0.255` | Numero seriale |
| `7.0.3.0.1.255` | Volume gas (m³) |
| `7.0.3.1.0.255` | Volume gas convertito |
| `1.0.99.1.0.255` | Profilo di carico 1 |

## File di documentazione

- `documentation/doc.md` – documentazione completa (IT)
- `context/dlms-context.md` – questo file (contesto Copilot)
