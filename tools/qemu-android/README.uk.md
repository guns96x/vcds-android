# Windows x64 у QEMU на телефоні + проброс кабелю FA24

Мета: запустити справжній VCDS («Вася») на Samsung Galaxy S24 FE без ПК.
Головна причина: одноразово повернути кабель `0403:FA24` в intelligent mode
(VCDS → Options → Test, «Boot in intelligent mode»). Телефон сам цього зробити не може
(див. `AI_CONTEXT.md`, 2026-10-01).

Ланцюжок:

```
Android (ARM64, без root)
  └─ termux-usb  → дозвіл Android + fd кабелю
      └─ QEMU x86_64 (TCG, повна емуляція)  usb-host,hostdevice=/dev/fdset/1
          └─ Windows 7 SP1 x64 → драйвер Ross-Tech → VCDS 26.3
```

## Статус (2026-10-06)

| Що | Статус |
|---|---|
| QEMU у Termux має `usb-host` і `usb-redir` (пакет `qemu-system-x86-64-headless` 11.0.3, `--enable-libusb`) | PROVEN_STATIC (рецепт збірки termux-packages) |
| `-add-fd` → `hostdevice=/dev/fdset/1` → `libusb_wrap_sys_device()` | PROVEN на Linux x86_64, QEMU 8.2: доходить до ioctl на fd |
| shim `libusb_nodiscovery.so` вимикає сканування шини в libusb | PROVEN на Linux (libusb 1.0.27: `no device discovery will be performed`) |
| Android реально віддає FA24 у QEMU | **UNVERIFIED** — перевіряє `usb-smoke-test.sh` |
| VCDS 26.3 запускається на Windows 7 x64 | **UNVERIFIED** |
| Таймінги VCDS ↔ кабель через емульований UHCI | **UNVERIFIED** |

## Чому x64, а не Windows 7 x86

У `tools/vcds/d2xx_trace/pe_manifest.json` твоя установка VCDS 26.3:
`VCDS.exeL` — **x64**, `RTUS64.dll` — **x64**, лише `VCDSLoader.exe` x86.
Тобто на 32-бітній Windows цей VCDS не запуститься. Потрібна Windows x64.
Windows 7 SP1 x64 — найлегша; Windows 10 x64 під TCG на телефоні буде дуже повільною.
Бонус x64: наш trace-shim `RTUS64.dll` працюватиме і у VM.

Windows 11 ARM64 не підходить: x64-драйвер USB там не емулюється.

## Крок 1. Termux (10 хв)

Termux і Termux:API ставити з одного джерела (F-Droid або GitHub, не Play).

```bash
termux-setup-storage
git clone -b claude/qemu-windows-x86-android-wtxovs https://github.com/guns96x/vcds-android ~/vcds-android
bash ~/vcds-android/tools/qemu-android/setup-termux.sh
```

Android 12+: «Параметри розробника → Disable child process restrictions» (інакше Android може вбити QEMU).

## Крок 2. Smoke-тест USB (10 с, без Windows) — головна перевірка

Кабель через OTG у телефон. У машину поки не треба (FTDI живиться від USB).

```bash
bash ~/vcds-android/tools/qemu-android/usb-smoke-test.sh
```

- Android спитає дозвіл USB для Termux:API — дозволити.
- `SMOKE PASS` → Android віддав кабель у QEMU. Найризикованіша частина пройдена.
- `SMOKE FAIL` → пришли мені лог з `~/vcds-vm/logs/smoke-*.log` повністю. Далі Windows ставити немає сенсу.

## Крок 3. Образ Windows

**Рекомендовано: підготувати на ПК** (з апаратним прискоренням — хвилини замість годин):

```powershell
powershell -ExecutionPolicy Bypass -File prepare-image-pc.ps1 -Iso D:\iso\win7_sp1_x64.iso
```

Усередині VM на ПК:
1. Windows 7 SP1 x64.
2. Оновлення SHA-2: **KB4474419** і **KB4490628** — без них Win7 може не прийняти сучасний підписаний драйвер.
3. VCDS («Вася») тим самим інсталятором, що на ПК, з драйвером USB.
4. Вимкнути Windows Update, індексацію, теми — на телефоні кожен відсоток CPU на рахунку.

Потім `win7x64.qcow2` скопіювати на телефон у `~/vcds-vm/` (у домашню папку Termux, не на `/sdcard` — там повільно).

Альтернатива — ставити прямо на телефоні (кілька годин):

```bash
bash run-windows.sh --install ~/storage/downloads/win7_sp1_x64.iso
```

Файли в VM: `make-share-iso.sh папка` → `run-windows.sh --cd ~/vcds-vm/share.iso`.

## Крок 4. Запуск з кабелем

1. Кабель у телефон, потім (коли Windows завантажиться) — в OBD машини, запалювання ON.
   Порядок «спочатку телефон» — вже відоме з `AI_CONTEXT.md`.
2. ```bash
   bash ~/vcds-android/tools/qemu-android/run-windows.sh
   ```
3. VNC-клієнт (наприклад AVNC) → `127.0.0.1:5900`.
4. Лог: друга сесія Termux, `tail -f ~/vcds-vm/logs/run-*.log`.
   Монітор QEMU: `telnet 127.0.0.1 4444` → `info usb` має показати `ID: cable`.
5. У Windows: Device Manager → кабель має стати з драйвером Ross-Tech. VCDS → Options → Test.

Тільки читання/Test. Жодного кодування, адаптацій чи прошивки через VM: емуляція повільна, а обрив USB посеред запису — це ризик для блоку.

## Що може не спрацювати і що тоді

| Симптом | Імовірна причина | Що робити |
|---|---|---|
| `SMOKE FAIL ... connectinfo failed` | Android/SELinux не дає ioctl на fd | лог мені; план Б — ПК |
| `usb-host: немає` у setup | Termux змінив пакет | лог `qemu-system-x86_64 -device help` |
| Кабель «зникає» з VM під час Test | кабель переенумерувався (fd вже недійсний) | перезапустити `run-windows.sh` |
| VCDS: «interface not found» при `SMOKE PASS` | драйвер у Win7 або таймінг UHCI | скрін Device Manager + лог VCDS |
| Windows вантажиться > 15 хв | TCG на телефоні | `VM_SMP=4`, вимкнути служби; Win7, не Win10 |

Налаштування через змінні середовища: `VM_DISK`, `VM_RAM`, `VM_SMP`, `VM_CPU`, `VM_VNC`, `VM_CDROM` (див. `common.sh`).

## Файли

- `setup-termux.sh` — пакети + збірка shim.
- `libusb_nodiscovery.c` — LD_PRELOAD shim: `LIBUSB_OPTION_NO_DEVICE_DISCOVERY` перед `libusb_init()` QEMU.
- `usb-smoke-test.sh` / `usb-launch.sh` / `usb-callback.sh` — termux-usb → перевірка VID:PID з дескриптора → QEMU з fd.
- `run-windows.sh` — запуск VM (з кабелем, без, інсталяція).
- `make-share-iso.sh` — папка → ISO для VM.
- `prepare-image-pc.ps1` — та сама віртуальна апаратура на ПК для швидкої інсталяції.
