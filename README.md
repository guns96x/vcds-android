# VCDS Mobile for Android

Android diagnostic/logger project for VW Golf 5 1.9 TDI BLS / Bosch EDC16U34.

## Transport modes

The application intentionally keeps acquisition paths separate:

1. **Turbo Fast — ELM327 Bluetooth / generic OBD-II**
   - Real-car validated path for 4th-gear WOT logging.
   - Core pair: RPM `010C` followed by MAP `010B`.
   - Auxiliary Mode 01 data: MAF, speed, load and slower temperature/voltage/BARO channels.
   - Uses `Turbo_Pair_*.csv` plus `Event_RAW_*.csv`.
   - WOT start is gated by its own pre-flight check.

2. **VAG OEM — ELM327 + VW TP 2.0 / KWP2000**
   - Proprietary measuring blocks.
   - Separate OEM pre-flight and RAW logger.
   - Does not depend on generic Mode 01 PID readiness.

3. **USB OEM — USB-OTG FTDI/CH340/CP210x/Prolific**
   - KWP2000/K-Line experimental hardware path.
   - Serial receive is accumulated until a complete checksum-valid ECU frame is available; tester echo is rejected.
   - Hardware validation on the target car is still required. Do not treat a successful unit test as proof that a particular cable can reach the Golf 5 ECU through the vehicle DLC.

4. **Simulator**
   - UI/protocol development without the car.

## Measuring blocks

Core OEM groups are **011 / 008 / 003**. The logger also rotates through context groups including 007, 010, 004, 015, 001, 009, 013, 023, 020, 062, 006 and 002.

Measuring-block values are decoded from the KWP scaler byte. Important supported scalers include RPM, pressure, temperature, duty/ratio, air/fuel mass and torque. **Unknown scaler IDs remain `raw`** instead of being converted with guessed formulas.

OEM `CHECK DATA` requires Groups 011/008/003 to return four decodable fields before enabling `START OEM RAW LOG`. Long-press `CHECK DATA` runs a 10-second Group 011 stress test and reports request count, valid rate, latency and RPM range.

## Logging

Logs are stored under:

`Android/data/com.vag.vcdsandroid/files/Documents/VCDS_Logs/`

- Turbo Fast: synchronized `Turbo_Pair_*.csv` is the primary road-pull artifact.
- OEM modes: `Event_RAW_*.csv` is the primary artifact.
- GitHub upload selects the meaningful file for the active pipeline instead of accidentally uploading an empty sibling CSV.
- The asynchronous writer tracks rows actually written, queue depth and dropped records.

## Validation status

**Validated on the real car:** Turbo Fast ELM path, phone BARO integration, synchronized RPM/MAP logging and existing road-pull analyzer.

**Not yet claimed as real-car validated:** USB KWP/KKL and direct Ross-Tech intelligent-interface probing. Those paths require a fresh stationary hardware test before road use.

CI runs `testDebugUnitTest` and `assembleDebug` for pull requests.
