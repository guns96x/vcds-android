# M1 — стабільне S24 FE ⇄ FA24 ⇄ OBD

## Результат

30/30 стабільних cable-only handshake на конкретному `0403:FA24/RT000001`, без ECU-команд.

## Нові компоненти

Створити:

```kotlin
data class UsbDeviceKey(
    val vid: Int,
    val pid: Int,
    val serial: String?,
    val deviceName: String,
    val attachGeneration: Long,
)

sealed interface UsbLinkState {
    data object Absent : UsbLinkState
    data class AttachedNoPermission(val device: UsbDeviceKey) : UsbLinkState
    data class PermissionPending(val device: UsbDeviceKey) : UsbLinkState
    data class Opening(val device: UsbDeviceKey) : UsbLinkState
    data class Configuring(val step: Fa24InitStep) : UsbLinkState
    data object Probing02 : UsbLinkState
    data object Identifying04 : UsbLinkState
    data class CableReady(val report: Fa24ProbeReport) : UsbLinkState
    data class Fault(val fault: ConnectionFault) : UsbLinkState
}
```

```kotlin
class UsbAttachCoordinator {
    fun onAttached(device: UsbDevice)
    fun onDetached(device: UsbDevice)
    suspend fun connect(): Fa24ProbeOutcome
    suspend fun suspendLink(reason: SuspendReason)
}
```

```kotlin
enum class ConnectionFault {
    USB_NOT_ENUMERATED,
    USB_PERMISSION_DENIED,
    USB_PERMISSION_TIMEOUT,
    USB_OPEN_RETURNED_NULL,
    FTDI_CONFIGURATION_FAILED,
    BULK_WRITE_FAILED,
    CABLE_MCU_SILENT,
    INVALID_FRAME,
    IDENTIFY_NOT_ROSSTECH,
    DETACHED_DURING_OPERATION,
}
```

Рознести логіку з [MainActivity.kt](C:/Users/pavlo/vcds-android/app/src/main/java/com/vag/vcdsandroid/ui/MainActivity.kt:187) у `UsbAttachCoordinator` і `Fa24ConnectionService`.

## Permission lifecycle

Алгоритм:

1. На `ACTION_USB_DEVICE_ATTACHED` збільшити `attachGeneration`.
2. Побудувати `UsbDeviceKey`.
3. Якщо `hasPermission == true`, перейти до `Opening`.
4. Інакше викликати `requestPermission()` рівно один раз на `attachGeneration`.
5. Повторні натискання Connect у стані `PermissionPending` нічого не роблять.
6. Permission timeout 30 секунд лише змінює UI; він не означає автоматичну відмову користувача.
7. Після `EXTRA_PERMISSION_GRANTED=true` повторно знайти `UsbDevice` у `UsbManager.deviceList`, бо Samsung може змінити `/dev/bus/usb` path.
8. На detach:

```text
cancel probe → cancel reads → close port → close connection →
clear decoder → increment generation → state=Absent
```

9. Screen off не закриває порт. Активна сесія належить foreground service із partial wake lock.
10. Process death не виконує автоматичний reconnect у background. Після відновлення — нова read-only M1 probe.

## FTDI init profile

Зафіксувати окремою структурою:

```kotlin
data class Fa24InitProfile(
    val baudSequence: IntArray = intArrayOf(9_600, 19_200, 115_200),
    val latencyMs: Int = 1,
    val dataBits: Int = 8,
    val stopBits: Int = 1,
    val parity: Int = UsbSerialPort.PARITY_NONE,
    val dtr: Boolean = false,
    val rts: Boolean = false,
)
```

Порядок:

```text
open/reset
→ purge RX+TX
→ set latency 1 ms
→ set 8N1 at 9600
→ set 8N1 at 19200
→ set 8N1 at 115200
→ clear DTR
→ clear RTS
→ purge RX only
→ probe
```

Кожний крок повинен мати власний результат і timestamp. Заборонено ігнорувати exception від DTR/RTS або latency setter.

## Cable handshake

Кандидатні request bytes, уже реалізовані:

```text
Probe:    53 04 02 55
Identify: 53 04 04 53
```

Вимоги:

- очікуваний RX marker `4D`;
- length `4..255`;
- XOR усіх попередніх байтів дорівнює останньому байту;
- response opcode дорівнює request opcode;
- identify payload містить ASCII `ROSSTECH`;
- сторонні/пошкоджені frames не приймаються як success;
- fragmented USB reads збираються stream decoder-ом.

Таймінги:

```text
FTDI control transfer: 500 ms кожний
Probe 0x02: 1000 ms
Identify 0x04: 1000 ms
Read slice: ≤100 ms
Повтор: 1 повний reopen після 250 ms
Загальний cable-probe deadline: 5 s
```

## Діагностика несправності

| Спостереження | Висновок |
|---|---|
| Пристрою немає в `deviceList` | OTG/кабель/USB-C проблема |
| Пристрій є, permission відхилено | Android permission |
| Permission є, `openDevice()==null` | USB host/driver/device handle |
| FTDI init падає | FTDI control path |
| FTDI init і TX успішні, RX повністю порожній | `CABLE_MCU_SILENT`; OBD power лише probable |
| Є байти, але marker/XOR/length хибні | firmware/framing mismatch |
| `0x02` відповів, `0x04` без ROSSTECH | інша версія firmware |
| `0x02` і `0x04` valid | Android⇄cable PASS; ECU ще NOT TESTED |

## Тести й DoD

- Unit: fragmented frame, concatenated frames, garbage prefix, bad XOR, bad length, wrong opcode.
- Instrumentation: permission coalescing, detach під час кожного init step, Activity recreation.
- S24 FE:

```text
30/30 cold attach+probe з OBD та ignition ON
10/10 screen-off 10 хв → wake → повторний probe
10/10 detach/re-attach без crash і stale UsbDevice
5/5 USB без OBD → правильний CABLE_MCU_SILENT, не “ECU timeout”
0 повторних permission dialog у межах одного attach
```

M1 PASS тільки після збереженого trace конкретного RT000001.

---

# M2 — 01-Engine через FA24 CAN і TP2.0

## Обов’язковий evidence gate

Перед кодом ECU-трафіку зняти тією самою машиною і кабелем:

1. VCDS Options → Test.
2. Select → 01-Engine.
3. 60 секунд idle.
4. Close Controller.
5. Ignition OFF під час відкритої сесії.

Потрібні D2XX `FT_Write/FT_Read`, baud/control changes і часові мітки.

Мета — встановити, що саме FA24 очікує від host:

- raw CAN frames;
- CAN configuration opcodes;
- чи лише KWP payload;
- хто виконує TP2.0 ACK і keepalive.

До цього `HexB03Adapter.transact()` лишається ZERO-TX.

## Інтерфейси

```kotlin
interface RawCanPort {
    suspend fun configure500k11bit(): Result<Unit>
    suspend fun send(id: Int, data: ByteArray, deadlineMs: Long): CanTxResult
    suspend fun receive(filter: CanFilter, deadlineMs: Long): CanFrame?
}
```

```kotlin
class Tp20Channel(private val can: RawCanPort) {
    suspend fun open(logicalAddress: UByte = 0x01u): Tp20OpenResult
    suspend fun request(kwpPayload: ByteArray): KwpResponse
    suspend fun close(reason: CloseReason)
}
```

ELM implementation залишається окремим `ElmRawCanPort`; FA24 отримує `HexB03CanPort` лише після trace.

## TP2.0 candidate transcript

Поточна ELM-гілка містить такий кандидат:

```text
CAN ID 0x200, DLC 7:
01 C0 00 10 00 03 01
```

Очікувана відповідь на `0x201`:

```text
00 D0 rxLo rxHi txLo txHi 01
```

ID обчислюються лише з відповіді:

```kotlin
rxId = rxLo or (rxHi shl 8)
txId = txLo or (txHi shl 8)
```

Заборонено підставляти `0x300/0x740`, якщо response закороткий або невалідний.

Parameter request-кандидат:

```text
A0 0F 8A FF 32 FF
```

Відповідь повинна починатися з `A1`. Після D0 відправлення A0 має бути пріоритетним і без UI/logging між ними; поточний код вважає setup window приблизно 100 ms.

Початок KWP-сесії:

```text
TP seq 0 + length 2 + KWP:
10 00 02 10 89
```

Позитивний KWP payload:

```text
50 89
```

Ці байти є `CANDIDATE_FROM_EXISTING_ELM_CODE`, а не доведеним FA24 wire protocol.

## Автомат станів

```text
CLOSED
→ SETUP_SENT
→ CHANNEL_IDS_RECEIVED
→ PARAMS_SENT
→ TP_OPEN
→ KWP_SESSION_STARTING
→ READY
→ CLOSING
→ CLOSED
```

Помилки ведуть у `RECOVERING`, але:

- максимум 2 reconnect;
- backoff 250 ms / 1000 ms;
- жодного паралельного запиту під час reconnect;
- `A8` переводить канал у CLOSED негайно;
- stale response з попереднього generation відкидається.

## ACK і keepalive

Actor priorities:

```text
1. TP ACK / peer timing response
2. disconnect
3. keepalive
4. diagnostic request
5. UI refresh
```

Не називати `A0/A1` keepalive. Реалізувати control-frame dispatcher:

```kotlin
when (opcode) {
    0xA0 -> onParameterRequest()
    0xA1 -> onParameterAck()
    0xA3 -> onKeepAliveCandidate()
    0xA8 -> onPeerDisconnect()
    in 0xB0..0xBF -> onTransportAck()
}
```

Реальні ролі `A3/A1` та інтервал keepalive мають бути підтверджені Phase-F trace. До цього timer не hardcode-иться як protocol fact.

## DoD

- Replay tests із golden trace.
- Відкриття/закриття каналу 20/20 разів.
- 30 хвилин idle без `A8`, sequence desync або reconnect.
- Ignition OFF дає контрольований disconnect, не нескінченний retry.
- FA24 trace дорівнює Windows VCDS trace на рівні семантичних команд.
- Жодного write/security/reset SID.

---

# M3 — Group 011

## Запит і відповідь

```text
KWP request:  21 0B
KWP response: 61 0B
              [scaler1 A1 B1]
              [scaler2 A2 B2]
              [scaler3 A3 B3]
              [scaler4 A4 B4]
```

Відповідь повинна мати щонайменше 14 байтів.

## Нова модель

```kotlin
data class VagRawCell(
    val scaler: UByte,
    val a: UByte,
    val b: UByte,
)

sealed interface DecodedCell {
    data class EngineeringValue(
        val value: Double,
        val unit: String,
        val formulaId: UByte,
    ) : DecodedCell
    data class Unsupported(val raw16: Int, val formulaId: UByte) : DecodedCell
}
```

`MeasuringGroup.decode()` розділити на:

```kotlin
fun parseRaw(payload: ByteArray): VagMeasuringBlock
fun decodeCell(cell: VagRawCell): DecodedCell
fun label(group: Int, field: Int): String?
```

Ніколи не вибирати формулу лише за `group/field`; формулу визначає scaler byte у відповіді.

## Golden-vector acceptance

Зняти одночасно:

- raw `61 0B ...`;
- екран VCDS Group 011;
- тахометр;
- barometric plausibility з engine OFF.

Допуски:

```text
RPM: ±1 displayed RPM
Boost specified/actual: ±2 mbar після однакового округлення
N75: ±0.2 percentage point
```

Plausibility gates:

```text
RPM: 0..5500
Pressure: 500..3500 mbar absolute
N75: 0..100 %
```

Out-of-range — `INVALID_PHYSICAL_VALUE`, а не success.

## DoD

- 1000 послідовних Group 011 responses.
- ≥99.5% valid decode.
- 0 невідомих scaler-ів у прийнятому профілі конкретного ECU.
- Unknown scaler залишається RAW.
- Engine-off actual boost приблизно відповідає атмосферному тиску.
- VCDS і Android показують однакові значення.

---

# M4 — тригруповий WOT logger

## Важливе обмеження

Тільки Groups 011/008/003 недостатньо для достовірного автоматичного визначення четвертої передачі:

- потрібна швидкість автомобіля;
- потрібне доведене положення педалі;
- Group 008 на цьому exact SW може віддавати лімітери в Nm, а не pedal %.

Тому M4 має два етапи.

### M4a — scheduler/logger

```kotlin
class OemPollScheduler {
    suspend fun run(groups: IntArray = intArrayOf(11, 8, 3))
}
```

Правила:

- один request in flight;
- порядок `011 → 008 → 003 → repeat`;
- жодних `delay()` між успішними транзакціями;
- ACK/keepalive мають вищий пріоритет;
- після timeout — наступна група, але sample позначається invalid;
- після 2 послідовних protocol failures — контрольований reconnect.

10 Hz означає aggregate:

```text
≥10 completed group requests/s
кожна група ≥3.0 samples/s
p95 transaction latency ≤100 ms
```

Якщо hardware не досягає цього, milestone FAIL; UI показує виміряну частоту, а не заявляє 10 Hz.

### M4b — trigger signals

```kotlin
interface TriggerSignalProvider {
    val pedalPct: Double?
    val vehicleSpeedKph: Double?
    val engineRpm: Double?
}
```

Джерела pedal/speed допускаються лише після capture-grounded mapping. Якщо speed недоступний, дозволений лише режим `MANUAL_4TH_GEAR_ARM`; він не називається автоматичним.

`GearEstimator` використовує `speed/rpm` ratio та калібрується на стабільній їзді:

```kotlin
fun estimateGear(rpm: Double, speedKph: Double): GearEstimate
```

Перед AUTO режимом треба отримати не менше 10 секунд steady-state зразків кожної передачі та зберегти median ratio.

## WOT state machine

```text
DISARMED
→ ARMED
→ CANDIDATE
→ RECORDING
→ COMPLETE
```

Умови start:

```text
gear == 4
pedal >= 95%
rpm crosses 1500 upward
all trigger values fresh ≤200 ms
TP2 channel READY
```

Умови stop:

```text
rpm >= 4000
OR pedal < 90% for 300 ms
OR gear != 4
OR ECU data stale >500 ms
OR TP channel lost
OR user STOP
```

Зберігати 2 секунди pre-roll і 1 секунду post-roll.

## CSV

Один рядок на ECU response:

```csv
session_id,row_seq,utc_ms,mono_ns,
group,request_seq,tp_tx_seq,tp_rx_seq,
latency_ms,status,
cell1_scaler,cell1_a,cell1_b,cell1_value,cell1_unit,
cell2_scaler,cell2_a,cell2_b,cell2_value,cell2_unit,
cell3_scaler,cell3_a,cell3_b,cell3_value,cell3_unit,
cell4_scaler,cell4_a,cell4_b,cell4_value,cell4_unit,
rpm,pedal_pct,speed_kph,estimated_gear,
trigger_state,dropped_rows
```

Поточний `DROP_OLDEST` у [AsyncCsvLogger.kt](C:/Users/pavlo/vcds-android/app/src/main/java/com/vag/vcdsandroid/logging/AsyncCsvLogger.kt:181) неприйнятний для acceptance-log. Якщо queue переповнена:

- інкрементувати `dropped_rows`;
- позначити run `DEGRADED`;
- не використовувати run для calibration decisions.

## DoD

- 30 хвилин stationary logging: 0 silent drops.
- 10 хвилин screen-off: polling і CSV продовжуються.
- CSV парситься після force-close.
- Aggregate ≥10 Hz на FA24 або milestone FAIL.
- AUTO trigger не доступний, доки pedal/speed/gear не мають golden trace.
- Road test дозволено лише після stationary DTC/sensor preflight; наявні невирішені критичні DTC блокують WOT.

---

# M5 — DTC read і clear

## Read

Кандидатний запит:

```text
18 02 FF 00
```

Позитивна відповідь:

```text
58 [dtc_hi dtc_lo status]...
```

Парсер приймає лише payload, кратний трьом після SID/header. Точний golden vector для VAG `16683 → P0299`:

```text
41 2B A2
0x412B = 16683
16683 - 16384 = 0299
```

Додати тест саме для `41 2B`, а не synthetic `02 99`.

## Clear

```text
14 FF 00
positive: 54 ...
```

Clear DTC не входить до read allowlist. Створити окрему команду:

```kotlin
sealed interface MutatingDiagnosticCommand {
    data object ClearAllDtc : MutatingDiagnosticCommand
}
```

Gate:

- engine RPM == 0;
- ignition ON;
- TP2.0 READY;
- DTC snapshot збережений;
- explicit confirmation;
- команда не запускається з logger;
- після `0x54` — повторний scan;
- NRC або timeout не трактується як success.

## DoD

- Android DTC list збігається з VCDS за code/status.
- `16683 → P0299`.
- Release build не може clear DTC через raw/debug API.
- Before/after snapshots збережені.
- Clear без confirmation фізично не передає байтів.

---

# M6 — доведення physical flasher protocol

M6 починається в `C:\Users\pavlo\golf5-android-flasher`, не у `vcds-android`.

## Мета

Перетворити наявний flasher із emulator-proven у tool-specific, fail-closed architecture.

## Обов’язкові зміни дизайну

1. `termux/edc16_flasher.py` стає лише research reference, не oracle.
2. Не переносити його generic KWP flow у production Kotlin.
3. Виділити:

```kotlin
interface ProgrammingTransport {
    val identity: ToolIdentity
    val capabilities: Set<ProgrammingCapability>
    suspend fun authenticateTool(): ToolAuthResult
    suspend fun identifyEcu(): EcuIdentityResult
    suspend fun readCalibration(): ReadResult
    suspend fun writeCalibration(data: ByteArray): WriteResult
}
```

4. Окремі реалізації:

```text
OfflineEmulatorTransport
MppsMicrocodeTransport
KklProgrammingTransport
```

`KklProgrammingTransport` має порожню write capability, доки окремий physical trace не доведе її.

5. Generic candidate sequence:

```text
10 85
27 01
27 02 <key>
35 <address/size>
34 <address/size>
36 <seq/data>
37
14 FF 00
11 01
```

зберігається як `UNVERIFIED_KWP_CANDIDATE`, а не як physical MPPS profile.

## Evidence acquisition

Для exact MPPS adapter потрібні Windows golden traces:

- adapter open/auth;
- ECU identify;
- voltage read;
- calibration read;
- transfer exit;
- clean close.

Destructive write trace — лише на bench ECU або з уже відомої безпечної процедури. Не здобувати його експериментом на автомобілі.

## DoD

- Unknown MPPS challenge повертає `UnknownChallenge`.
- `LegacyBlsSecurityAlgorithm.verified == false` блокує physical write.
- Відсутній trace-backed microcode opcode блокує capability.
- Release UI показує `PHYSICAL READ ONLY`, доки всі gates не доведені.
- Жодного fallback із MPPS на звичайний FTDI/KKL.

---

# M7 — physical identify і обов’язковий backup

## Backup-модель

Поточний boolean `backupCompleted` замінити на:

```kotlin
data class BackupEvidence(
    val ecuIdentity: EcuIdentity,
    val adapterIdentity: ToolIdentity,
    val addressStart: Int,
    val byteCount: Int,
    val coverage: BackupCoverage,
    val sha256: String,
    val secondReadSha256: String,
    val createdUtcMs: Long,
    val sessionId: UUID,
)
```

`BackupCoverage`:

```text
CALIBRATION_512_KIB
FULL_FLASH_2_MIB
EEPROM_4_KIB
VIRTUAL_CONTAINER
```

512 KiB calibration read не можна називати full backup. Контейнер із `0xFF` поза calibration region — `VIRTUAL_CONTAINER`.

## Процедура

1. Authenticate adapter.
2. Read ECU ID.
3. Require exact:

```text
HW: 03G906021QJ
SW: 1037391847 / diagnostic display 391847
ECU: EDC16U34-3.42
```

4. Read `0x180000..0x1FFFFF`.
5. Перевірити рівно `0x80000` bytes.
6. Зберегти immutable file.
7. Обчислити SHA-256.
8. Повторити повний read.
9. Вимагати рівність двох hash.
10. Записати manifest.
11. Лише після цього `BackupEvidence` може відкрити наступний gate.

## Voltage gate

Безпечніші пороги:

```text
READY_TO_START: ≥12.5 V стабільно 60 секунд
HARD REFUSE: <12.2 V
CRITICAL EVENT DURING WRITE: <12.0 V
```

Живлення — charger/regulated supply ≥5 A. Напруга з непідтвердженого measuring group не є достатньою для physical write.

## DoD

- Два послідовні physical reads byte-identical.
- Backup відкривається після перезапуску й проходить hash verification.
- Backup exact ECU і candidate image не плутаються.
- `backupCompleted=true` без artifact більше неможливий.
- Write button disabled, якщо backup із іншої ECU/session/coverage.

---

# M8 — image integrity, preflight та offline transaction

## Firmware profile

```kotlin
data class Edc16U34Profile(
    val fullImageSize: Int = 0x200000,
    val calibrationStart: Int = 0x180000,
    val calibrationSize: Int = 0x080000,
)
```

Checksum blocks exact SW:

```text
Block 1: 0x180000..0x1BFFFF
patch:   0x1BFFFC
residue: 0xD01FE500

Block 2: 0x1C0000..0x1FDFFF
patch:   0x1FDFFC
residue: 0xD01FE500
```

Не узагальнювати їх на інші EDC16.

## Preflight evidence

```kotlin
data class FlashPreflightEvidence(
    val exactIdentityMatch: Boolean,
    val toolProtocolVerified: Boolean,
    val toolAuthVerified: Boolean,
    val securityAlgorithmVerified: Boolean,
    val voltageStable: Boolean,
    val backup: BackupEvidence?,
    val inputSha256: String,
    val byteDiffManifestSha256: String,
    val checksumVerification: ChecksumVerification,
    val calibrationAuditVerdict: AuditVerdict,
    val recoveryRouteVerified: Boolean,
)
```

`Eligible` можливий лише при всіх true і `AuditVerdict.PASS`.

Поточний Stage 1 із `HARD_FAIL` не може пройти цей gate навіть із валідними двома checksum.

## Transaction state machine

```text
IDLE
→ PREFLIGHT
→ BACKUP_VERIFIED
→ TOOL_AUTH
→ ECU_IDENTIFIED
→ PROGRAM_SESSION
→ SECURITY_GRANTED
→ DOWNLOAD_ACCEPTED
→ TRANSFERRING
→ TRANSFER_EXIT
→ READBACK
→ VERIFIED
→ RESETTING
→ COMPLETE
```

Будь-який збій після destructive start переходить у `RECOVERY_REQUIRED`, не назад у `IDLE`.

## Offline fault tests

Emulator повинен інжектувати:

- NRC `0x78`;
- NRC `0x35`;
- timeout кожного stage;
- disconnect на block 1, 2, 255, 256, last;
- wrong ACK sequence;
- duplicate ACK;
- partial readback;
- one-byte readback corruption;
- voltage gate before start;
- process cancellation;
- corrupted backup;
- unknown security seed/challenge.

## DoD

- 100% fault matrix проходить offline.
- Жодна невідома умова не дає `Success`.
- Journal відновлює останній підтверджений stage після process death.
- Emulator success не змінює physical capability.
- Canonical ECU gateway повертає PASS для конкретного output image.

---

# M9 — bench write і physical readback

## Умови входу

M9 заборонено починати, доки:

- M6 physical protocol PASS;
- M7 double-read backup PASS;
- M8 artifact audit PASS;
- verified recovery equipment фізично доступне;
- stable supply ≥12.5 V;
- exact bench pinout повторно перевірений;
- є spare/test ECU або явно авторизований bench target.

## Write loop

Для кожного блока:

```text
check session ownership
→ check latest voltage sample
→ send one block
→ await exact positive response
→ persist journal checkpoint
→ advance sequence
```

Block size береться з доведеної відповіді tool/ECU; `128` не можна мовчки підставляти після malformed negotiation.

Після останнього блока:

```text
TransferExit
→ повний 512 KiB readback
→ SHA-256 compare
→ checksum verify readback
→ optional Clear DTC
→ ECU reset
```

Clear DTC не є умовою успішного запису. Reset timeout не можна приховувати, але він також не скасовує вже доведений readback.

## Поведінка при low voltage під час write

Не можна просто “негайно обірвати USB”, бо це теж може пошкодити сектор.

Алгоритм визначається тільки підтвердженою процедурою tool:

- зафіксувати `POWER_CRITICAL`;
- не запускати нову erase/session;
- завершити або припинити поточний block лише відповідно до tool protocol;
- не надсилати speculative reset;
- зберегти всі логи;
- перейти у `RECOVERY_REQUIRED`.

## DoD

- Один повний bench write PASS.
- 512 KiB readback byte-identical до intended calibration.
- Повторний identify після power cycle PASS.
- ECU залишається доступним для діагностики.
- Programming, readback і engine/bench acceptance звітуються окремо.

---

# M10 — recovery, vehicle acceptance і release

## Recovery matrix

| Стан | Дія |
|---|---|
| Application відповідає | normal programming recovery |
| Application dead, loader відповідає | documented OBD/bench recovery |
| Loader dead | BDM/COP recovery |
| Backup coverage insufficient | recovery blocked/UNKNOWN |
| Tool protocol unknown | жодних повторних write attempts |
| Readback mismatch | не reset/power-cycle без recovery decision |

Recovery mode може послабити лише звичайний application-ID handshake, але не може обходити:

- voltage;
- tool authentication;
- image/profile identity;
- checksum profile;
- backup provenance;
- recovery confirmation;
- protected range;
- verified protocol.

## Vehicle acceptance

Окремі результати:

```text
Static image validation: PASS/FAIL
Checksum verification: PASS/FAIL/UNKNOWN
Programming: PASS/FAIL/NOT RUN
Readback verification: PASS/FAIL/NOT RUN
Power-cycle identify: PASS/FAIL/NOT RUN
Engine start/idle: PASS/FAIL/NOT RUN
DTC scan: PASS/FAIL/NOT RUN
Controlled road validation: PASS/FAIL/NOT RUN
```

Послідовність:

1. Ignition OFF/ON за documented procedure.
2. ECU identify.
3. Повний DTC scan.
4. Engine start.
5. 10 хвилин idle.
6. Перевірка температур, boost plausibility, G507/T3 та rail-independent PD параметрів.
7. Low-load drive.
8. Лише після цього контрольований logging pull.
9. Порівняти pre/post DTC.
10. Архівувати backup, intended image, actual readback, manifests і session log.

## Release criteria

- Release APK не містить debug raw-TX.
- Physical write не компілюється/не активується без verified profile.
- Усі refusal reasons видно користувачу.
- Foreground service володіє USB і wake lock.
- Відсутні hardcoded “100% verified” твердження про emulator-only поведінку.
- На екрані підтвердження показано ECU ID, tool ID, voltage, backup hash, input hash, checksum result і recovery route.
- Один тап не може почати write: потрібне explicit long confirmation із точним hash suffix.
- APK build PASS, install PASS і physical E2E PASS звітуються окремо.

---

## Негайна черга виконання

Найближчий виконавець повинен отримати лише M1:

1. Винести USB lifecycle з Activity.
2. Зробити single-owner foreground connection service.
3. Додати typed states/faults.
4. Зберегти точний FTDI init trace.
5. Перевірити `53 04 02 55` / `53 04 04 53` на RT000001.
6. Виконати 30-cycle hardware acceptance.
7. Не торкатися ECU opcodes.
8. Лише після M1 PASS зняти Phase-B Windows trace і планувати M2 implementation.

Планування завершено; реалізація й тести в цьому turn не виконувались, файли не змінювались. Через read-only режим план не був записаний у `docs/superpowers/plans/`.


