Gemini execution directive:

Open `REVIEW_D2CBE72_BEFORE_CAR.md` and execute it literally in priority order.

Do not reinterpret the task and do not add unrelated features.

The most important P0 findings are:
1. Clear `latestSamples` and all session telemetry/counters on reconnect/disconnect; stale `0133` can currently leak across sessions.
2. Mode A connection must require BOTH valid `010C` and `010B` in the same connection stage. `0100` alone must never mark Turbo Fast ready.
3. Replace the current BARO resolution with a session cache that survives transient `0133` failures but resets on new connection.
4. `START LOG` must require fresh RPM + MAP + real BARO source, not only `DiagState.CONNECTED`.
5. Pre-flight must classify required/recommended/optional PIDs and use ATRV / engine-off BARO fallback.
6. CHECK DATA / RPM stress must be blocked during recording; stress job must be cancellable.
7. Reset log-button state on disconnect/mode switch.
8. While recording, use a core-priority RPM/MAP scheduler and suspend slow PIDs.
9. RAW debug must identify the actual transmitted command (including ATRV fallback).
10. Save connection trace on success as well as failure.
11. Add PC unit tests for PidDecoder, Generic OBD handshake, session BARO, and scheduler before another APK is taken to the car.
12. Mode B must not show fake OEM zeros after TP2.0 failure; DTC buttons must route to the correct engine.

Do all PC-side work first. Required PC gate before car:
`gradlew.bat clean testDebugUnitTest assembleDebug`

Only after all offline tests pass should the user do the single short stationary validation sequence described in `REVIEW_D2CBE72_BEFORE_CAR.md`.

Do not claim `verified on real car` from build/unit test success.