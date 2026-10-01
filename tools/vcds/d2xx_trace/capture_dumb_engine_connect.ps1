param(
    [string]$VcdsDir = "C:\Ross-Tech\VCDS",
    [string]$OutDir = ""
)

# Captures how Windows VCDS drives the FA24 in DUMB mode on the K-Line:
# break timing, DTR/RTS, bit mode, baud and the ECU reply bytes.
# Requires the D2XX trace shim (RTUS64.dll -> RTUS64_orig.dll) to be installed.
# The cable must already be in dumb mode; this script never switches modes.

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($OutDir)) {
    $stamp = Get-Date -Format "yyyyMMdd_HHmmss"
    $OutDir = Join-Path $PSScriptRoot "captures\dumb_engine_$stamp"
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$log = Join-Path $VcdsDir "vcds_d2xx_trace.log"
$shim = Join-Path $VcdsDir "RTUS64.dll"
$orig = Join-Path $VcdsDir "RTUS64_orig.dll"

if (!(Test-Path $shim)) { throw "RTUS64.dll not found in $VcdsDir" }
if (!(Test-Path $orig)) { throw "RTUS64_orig.dll not found: install the trace shim first" }

function Stop-Vcds {
    Get-Process | Where-Object { $_.ProcessName -like "VCDS*" } |
        Stop-Process -Force -ErrorAction SilentlyContinue
    Start-Sleep -Milliseconds 400
}

function Reset-Trace {
    Stop-Vcds
    if (Test-Path $log) { Clear-Content -Path $log } else { New-Item -ItemType File -Path $log | Out-Null }
}

function Save-Trace([string]$Name) {
    if (!(Test-Path $log)) { throw "Trace log not created: $log" }
    $dst = Join-Path $OutDir "$Name.log"
    Copy-Item $log $dst -Force
    Write-Host "Saved $Name ($((Get-Item $dst).Length) bytes)" -ForegroundColor Green
}

Write-Host ""
Write-Host "VCDS dumb-mode K-Line capture" -ForegroundColor Cyan
Write-Host "Output: $OutDir"
Write-Host ""
Write-Host "IMPORTANT:" -ForegroundColor Yellow
Write-Host "  - Cable in PC USB + car OBD, ignition ON, engine OFF"
Write-Host "  - Do NOT tick 'Boot in intelligent mode' (keep the cable dumb)"
Write-Host "  - Only open and close controllers. No coding, adaptation, output tests, flashing"
Write-Host ""

Reset-Trace
Read-Host "Stage D. Start VCDS, Options -> Test ONCE, write down what the dialog says, close VCDS, press Enter"
Save-Trace "D_dumb_options_test"
$testText = Read-Host "Type what the Test dialog said (e.g. 'Interface: Found! ... K1: OK')"

Reset-Trace
Read-Host "Stage E. Start VCDS, Select -> 01-Engine, wait until it connects or fails, press Close/Go Back, close VCDS, press Enter"
Save-Trace "E_dumb_01_engine"
$engineText = Read-Host "Did 01-Engine open? Type the result or the error text"

Reset-Trace
Read-Host "Stage F. Start VCDS, OBD-II -> 'Mode 1' (Generic scan tool), wait for result, close VCDS, press Enter"
Save-Trace "F_dumb_obd2_generic"
$obdText = Read-Host "Did generic OBD-II connect? Type the result"

$readme = @"
# VCDS dumb-mode K-Line capture

D = Options/Test with the cable already in dumb mode
E = Select 01-Engine (open, then close)
F = OBD-II generic scan tool, Mode 1

User notes:
- Test dialog: $testText
- 01-Engine:   $engineText
- OBD-II:      $obdText

Raw D2XX shim logs (FT_SetBreakOn/Off, FT_SetDtr/Rts, FT_SetBitMode,
FT_SetBaudRate, FT_Write/FT_Read with timestamps). No mode switch was made.
"@
Set-Content -Path (Join-Path $OutDir "README.txt") -Value $readme -Encoding UTF8

$zip = "$OutDir.zip"
if (Test-Path $zip) { Remove-Item $zip -Force }
Compress-Archive -Path (Join-Path $OutDir "*") -DestinationPath $zip -Force
Write-Host ""
Write-Host "DONE: $zip" -ForegroundColor Cyan
Write-Host "Send that ZIP."
