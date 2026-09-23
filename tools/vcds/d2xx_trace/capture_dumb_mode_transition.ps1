param(
    [string]$VcdsDir = "C:\Ross-Tech\VCDS",
    [string]$OutDir = ""
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($OutDir)) {
    $stamp = Get-Date -Format "yyyyMMdd_HHmmss"
    $OutDir = Join-Path $PSScriptRoot "captures\dumb_mode_$stamp"
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$log = Join-Path $VcdsDir "vcds_d2xx_trace.log"
$shim = Join-Path $VcdsDir "RTUS64.dll"
$orig = Join-Path $VcdsDir "RTUS64_orig.dll"

Write-Host ""
Write-Host "VCDS HEX smart/dumb mode capture" -ForegroundColor Cyan
Write-Host "Output: $OutDir"
Write-Host ""

if (!(Test-Path $shim)) {
    throw "RTUS64.dll not found in $VcdsDir"
}
if (!(Test-Path $orig)) {
    Write-Warning "RTUS64_orig.dll not found. Continue only if the trace shim is already installed."
}

function Stop-Vcds {
    Get-Process | Where-Object { $_.ProcessName -like "VCDS*" } |
        Stop-Process -Force -ErrorAction SilentlyContinue
    Start-Sleep -Milliseconds 400
}

function Reset-Trace {
    Stop-Vcds
    if (Test-Path $log) {
        Clear-Content -Path $log -ErrorAction Stop
    } else {
        New-Item -ItemType File -Path $log | Out-Null
    }
}

function Save-Trace([string]$Name) {
    if (!(Test-Path $log)) {
        throw "Trace log not created: $log"
    }
    $dst = Join-Path $OutDir "$Name.log"
    Copy-Item $log $dst -Force
    $size = (Get-Item $dst).Length
    Write-Host "Saved $Name ($size bytes)" -ForegroundColor Green
}

Write-Host "IMPORTANT:" -ForegroundColor Yellow
Write-Host "  - Cable connected to PC USB + car OBD"
Write-Host "  - Ignition ON, engine OFF"
Write-Host "  - Do not enter any controller, coding, adaptation, output tests, or flashing"
Write-Host "  - We capture only Options -> Test"
Write-Host ""

Read-Host "Stage A. In VCDS Options make sure 'Boot in intelligent mode' is CHECKED. Press Enter here, then run ONE Test. After the Test dialog appears, close VCDS and press Enter again"
Reset-Trace
Read-Host "Run ONE Options -> Test with Boot in intelligent mode CHECKED, close VCDS, then press Enter"
Save-Trace "A_smart_on_test"

Reset-Trace
Read-Host "Stage B. Open VCDS Options, UNCHECK 'Boot in intelligent mode', run ONE Test, close VCDS, then press Enter"
Save-Trace "B_switch_to_dumb"

Reset-Trace
Read-Host "Stage C. Re-open VCDS. Leave 'Boot in intelligent mode' UNCHECKED. Run ONE Test, close VCDS, then press Enter"
Save-Trace "C_dumb_persistent_test"

$readme = @"
# VCDS smart -> dumb mode capture

A = Options/Test with Boot in intelligent mode checked
B = uncheck Boot in intelligent mode, then Options/Test
C = restart VCDS, keep it unchecked, then Options/Test

The cable is intentionally left in dumb mode after Stage C.

Files are raw D2XX shim logs. No ECU controller session was opened.
"@
Set-Content -Path (Join-Path $OutDir "README.txt") -Value $readme -Encoding UTF8

$python = Get-Command python -ErrorAction SilentlyContinue
if ($python) {
    $diff = Join-Path $PSScriptRoot "diff_mode_toggle.py"
    if (Test-Path $diff) {
        & python $diff `
            (Join-Path $OutDir "A_smart_on_test.log") `
            (Join-Path $OutDir "B_switch_to_dumb.log") `
            (Join-Path $OutDir "C_dumb_persistent_test.log") `
            -o (Join-Path $OutDir "mode_toggle_diff.json")
    }
}

$zip = "$OutDir.zip"
if (Test-Path $zip) { Remove-Item $zip -Force }
Compress-Archive -Path (Join-Path $OutDir "*") -DestinationPath $zip -Force
Write-Host ""
Write-Host "DONE: $zip" -ForegroundColor Cyan
Write-Host "Upload that ZIP. Do NOT switch the cable back to intelligent mode."
