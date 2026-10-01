param(
    [string]$VcdsDir = "C:\Ross-Tech\VCDS",
    [string]$OutDir = ""
)

# Captures, through the D2XX trace shim (RTUS64.dll -> RTUS64_orig.dll):
#   D = VCDS restoring the FA24 from dumb back to intelligent mode
#   E = VCDS opening 01-Engine in intelligent mode (wire proof for opcode 0x84)
#   F = Measuring Blocks group 011 (wire proof for the group read framing)
# Only opens controllers and reads blocks. No coding, adaptation, output tests or flashing.

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($OutDir)) {
    $stamp = Get-Date -Format "yyyyMMdd_HHmmss"
    $OutDir = Join-Path $PSScriptRoot "captures\engine_session_$stamp"
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
Write-Host "VCDS FA24 restore + 01-Engine capture" -ForegroundColor Cyan
Write-Host "Output: $OutDir"
Write-Host ""
Write-Host "IMPORTANT:" -ForegroundColor Yellow
Write-Host "  - Cable in PC USB + car OBD, ignition ON, engine OFF"
Write-Host "  - Plug order for this cable: PC USB first, then the car OBD port"
Write-Host "  - Only open and close controllers. No coding, adaptation, output tests, flashing"
Write-Host ""

Reset-Trace
Read-Host "Stage D. Start VCDS, Options -> TICK 'Boot in intelligent mode' -> Test -> Save, close VCDS, press Enter"
Save-Trace "D_restore_intelligent_test"
$testText = Read-Host "Type what the Test dialog said (e.g. 'Interface: Found! ... K1: OK')"

Reset-Trace
Read-Host "Stage E. Start VCDS, Select -> 01-Engine, wait until it opens or fails, Close Controller, close VCDS, press Enter"
Save-Trace "E_intelligent_01_engine"
$engineText = Read-Host "Did 01-Engine open? Type the result or the error text"

Reset-Trace
Read-Host "Stage F. Start VCDS, 01-Engine -> Measuring Blocks -> group 011, let it run ~10 s, Go Back, Close Controller, close VCDS, press Enter"
Save-Trace "F_intelligent_group_011"
$obdText = Read-Host "Did group 011 show values? Type RPM / boost seen"

$readme = @"
# VCDS FA24 restore + 01-Engine capture

D = Options: tick Boot in intelligent mode, Test, Save (dumb -> intelligent)
E = Select 01-Engine in intelligent mode (open, then close)
F = 01-Engine Measuring Blocks group 011 for ~10 s

User notes:
- Test dialog: $testText
- 01-Engine:   $engineText
- Group 011:   $obdText

Raw D2XX shim logs (FT_SetBreakOn/Off, FT_SetDtr/Rts, FT_SetBitMode,
FT_SetBaudRate, FT_Write/FT_Read with timestamps).
"@
Set-Content -Path (Join-Path $OutDir "README.txt") -Value $readme -Encoding UTF8

$zip = "$OutDir.zip"
if (Test-Path $zip) { Remove-Item $zip -Force }
Compress-Archive -Path (Join-Path $OutDir "*") -DestinationPath $zip -Force
Write-Host ""
Write-Host "DONE: $zip" -ForegroundColor Cyan
Write-Host "Send that ZIP."
