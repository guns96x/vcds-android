> [!IMPORTANT]
> **Priority override — 2026-09-22:** the current project goal is not feature growth and not an ELM fallback. Until the user's exact FA24 cable works end-to-end with the Samsung Galaxy S24 FE, the UI and feature set are frozen. The only active milestone is to make Android open this cable, perform the real cable-side handshake, receive a valid interface response, and do so reliably on the user's car. All ECU functions and all other cable families are deferred until that proof succeeds.

Отримав повний, глибокий та деталізований аналіз від **Claude Opus 5.5** (`effort = medium`). 

Opus 5.5 не просто сформував дорожню карту, а й знайшов критичні приховані дефекти в поточному коді (зокрема обхід ZERO-TX у `Kwp2000DiagnosticEngine` та баг парсингу VAG DTC-кодів).

Нижче наведено повний звіт: що саме треба реверснути, що реверсити НЕ потрібно, та покроковий інженерний план до повністю робочої версії додатку.

---

### 1. Що ще потрібно реверснути (і що реверсити НЕ потрібно)

#### 1.1. Що потрібно зняти через наш D2XX-шим (8 фаз замість 4)
Чотирьох простих кроків недостатньо. Щоб повністю покрити весь життєвий цикл без сліпих зон, потрібно записати **8 фаз** (кожну бажано по 2 рази для виявлення детермінізму):
1. **Фаза A (Options → Test)**: Ініціалізація FTDI (порядок `Reset/Purge/Latency/Baud/DTR/RTS`), затримки між викликами, службовий handshake кабелю. **Це єдина фаза, потрібна до M1; фази B–H відкладені до успішного Android↔кабель handshake.**
2. **Фаза B (01-Engine Connect)**: Визначення ролей: чи хост сам шле кадри TP2.0 (`0x200/0xC0`), чи кабель бере це на себе, а хост передає лише KWP-payload.
3. **Фаза C (Group 011, 003, 008)**: Цикли запитів. Еталонні вектори: сирі байти + точні значення з екрана VCDS.
4. **Фаза D (DTC Read)**: Формат запиту помилок `0x58`, розбір VAG-номерів та статус-байтів.
5. **Фаза E (Close Controller)**: Коректний розрив сесії (кадр TP2.0 `A8` та переведення адаптера в спокій).
6. **Фаза F (Відкритий контролер, 60 с простою)**: Визначення каденції **keepalive** на двох рівнях: хост ↔ кабель та сесія TP2.0/KWP.
7. **Фаза G (Вимкнення запалення під час сесії)**: Обробка помилок та втрати зв'язку.
8. **Фаза H (Тест на шифрування / Точка GO/NO-GO)**: Запит групи 011 двічі за однакових умов. **Це найважливіший тест**: чи дає однаковий діагностичний запит однаковий байтовий блок `0xB8`. Якщо блоки випадкові (nonce / session key / challenge) — у кабелі активне крипто-ліцензування Ross-Tech.

#### 1.2. Що реверсити НЕ потрібно
- **Прошивку ATmega162**: не потрібна, якщо хост спілкується через стандартний командний протокол.
- **Регістри SPI / MCP2515 всередині адаптера**: хост їх не бачить напряму, достатньо відтворити конфігураційні кадри, які надсилає VCDS.
- **Файли `.lbl` / `.clb` (Labels)**: не потрібні і не повинні включатися в додаток (нові `.clb` зашифровані). KWP2000 відповідь `0x61 <group>` **сама себе описує**: 4 поля × 3 байти `[formula_type, A, B]`. Формули стандартизовані VAG, а текстові назви для EDC16U34 зашиваються напряму в код.
- **`Codes.dat`**: не потрібен (пропрієтарний). VAG KWP2000 повертає 16-бітний номер несправності, який за стандартом переводиться в SAE P-код (`16384 <= N < 20000` → `P` + `(N - 16384)`).

---

### 2. Критичні дефекти коду, виявлені Opus 5.5

Opus 5.5 виявив проблеми, які необхідно усунути разом із закриттям блокуючого рев'ю PR #4:
1. **Прихований обхід ZERO-TX**:
   У [`Kwp2000DiagnosticEngine.kt`](file:///C:/Users/pavlo/vcds-android/app/src/main/java/com/vag/vcdsandroid/protocol/Kwp2000DiagnosticEngine.kt#L220) методи `tryRossTechCanInit` та `bruteForceSweep` надсилали в порт `0403:FA24` сирі вгадані байти (`0x55`, `01 C0 ...`) на швидкостях 500k/250k/115200 в обхід `HexB03Adapter`! Цей код необхідно повністю відключити для FA24.
2. **Помилка парсингу VAG DTC у SAE**:
   У [`FaultCode.kt`](file:///C:/Users/pavlo/vcds-android/app/src/main/java/com/vag/vcdsandroid/model/FaultCode.kt#L45) метод `deriveSaeCode` неправильно маскував 16-бітний код VAG: помилка недодуву `P0299` (VAG 16683 = `0x412B`) перетворювалася на `C012B`.
3. **Blacklist замість Allowlist у перевірці безпеки**:
   `assertReadOnlyGuardrails` перевіряв лише кілька байтів, пропускаючи потенційно небезпечні сервіси (`0x11` ECUReset, `0x14` ClearDTC, `0x27` SecurityAccess, `0x30` IOControl). Потрібен строгий типізований **Allowlist** читання (`sealed class KwpReadRequest`).
4. **FTDI Latency Timer у Android**:
   У `mik3y usb-serial-for-android` за замовчуванням виставлено latency = 16 мс. Необхідно явно викликати `setLatencyTimer(1)` одразу після `open()`, інакше кожен пакет затримується на 16 мс.

---

### 3. Дорожня карта (Milestones M0 – M10)

```
 [M0: Гігієна доказів PR #4]
            │
 [M1: Samsung S24 FE ↔ FA24 cable: real interface handshake — DONE]
            │
 [M2: 01-Engine connect over legacy dumb K-Line — ACTIVE]
            │
 [M3: Group 011 read]
            │
 [M4: Group 003 / 008]
            │
 [M5: DTC read]
            │
 [M6+: Інші кабелі та подальший функціонал]
```

#### Деталізація етапів:
- **M0 (Поточний крок — закриття PR #4)**: 
  - Пониження B03 констант до `HYPOTHESIS`;
  - Повний ZERO-TX у `executeCandidateCommand()`;
  - Видалення `tryRossTechCanInit`/`bruteForceSweep` для FA24;
  - Виправлення `FaultCode.kt` та впровадження `KwpReadRequest` allowlist;
  - Додавання Windows CI для Rust DLL та тестів Python.
- **M1 (Єдиний активний milestone — довести кабель)**:
  - Samsung Galaxy S24 FE бачить `0403:FA24 / RT000001`, отримує USB permission і стабільно відкриває інтерфейс при підключеному OBD.
  - Відтворити тільки підтверджену кабельну ініціалізацію та plaintext handshake.
  - Мінімальний критерій успіху: реальна відповідь від інтерфейсу (`INTERFACE RESPONDED` / identity/version, якщо протокол це дає).
  - Прогнати 5 послідовних підключень без перепідключення USB до телефона.
  - **ELM/Bluetooth не є частиною M1 і не повинен відволікати роботу.**
- **M2 (Після M1)**:
  - Підключення до `01-Engine`.
- **M3 (Після стабільного M2)**:
  - Читання Group 011.
- **M4+**:
  - Group 003/008, DTC read, логування та підтримка інших кабелів — тільки після стабільної роботи цього FA24 кабелю на телефоні.

---

План повністю структурований і готовий до виконання.
Пропоную розпочати з **Етапу M0**: внести всі виправлення зауважень рев'ю PR #4 та виявлені дефекти безпеки, прогнати тести та запушити оновлення на GitHub. Переходимо?

### M2 connection ground truth

The current M2 is not a blind experiment anymore:

- exact cable-side M1 is proven on `RT000001`;
- the user's own ELM traces show `ISO 14230-4 / KWP 5BAUD`, `TP2.0 Active: false`;
- Ross-Tech documents legacy HEX-USB+CAN dumb mode for third-party serial use;
- NefMoto's current open implementation documents the critical ISO 14230 slow-init windows (W1/W2/W3/W4), including the hard 25–50 ms W4 complement window;
- current Android code implements that slow-init state machine, verifies a checksum-valid ECU frame from source `0x01`, retries with a quiet gap, keeps the KWP session alive, and now detects stale USB handles after Samsung OTG re-enumeration.

Do not enable M3 until the real car returns the first verified ECU 01 KWP frame.
