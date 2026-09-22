<#
.SYNOPSIS
    One-command read-only USBPcap capture session for the B03-V2 / 0403:FA24 HEX cable.

.DESCRIPTION
    Records short isolated VCDS USB captures, parses them with the repo tools,
    hashes all evidence, and creates one ZIP ready for analysis.

    This script never writes to the adapter itself. During the session use
    READ-ONLY VCDS functions only.
#>

[CmdletBinding()]
param (
    [string]$Device = "\\.\USBPcap1",
    [string]$OutputRoot = "$PSScriptRoot\captures"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$captureScript = Join-Path $PSScriptRoot "capture_vcds_traffic.ps1"
$inspectScript = Join-Path $PSScriptRoot "inspect_ross_tech_usb.ps1"
$parserScript = Join-Path $PSScriptRoot "parse_usb_pcap.py"

foreach ($required in @($captureScript, $inspectScript, $parserScript)) {
    if (-not (Test-Path $required)) {
        throw "Required tool is missing: $required"
    }
}

$stamp = Get-Date -Format "yyyyMMdd_HHmmss"
$sessionDir = Join-Path $OutputRoot "B03_$stamp"
New-Item -ItemType Directory -Path $sessionDir -Force | Out-Null

Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " B03-V2 / 0403:FA24 READ-ONLY USB CAPTURE SESSION" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "Session directory : $sessionDir"
Write-Host "USBPcap device    : $Device"
Write-Host ""
Write-Host "Cable -> Windows USB + OBD-II, ignition ON." -ForegroundColor Yellow
Write-Host "Use normal VCDS. READ ONLY." -ForegroundColor Yellow
Write-Host "DO NOT clear DTCs, code, adapt, flash, run Basic Settings or Output Tests." -ForegroundColor Red
Write-Host ""

$evidencePath = Join-Path $sessionDir "windows_usb_evidence.json"
Write-Host "[1/3] Reading Windows USB identity..." -ForegroundColor Green
& $inspectScript -OutputPath $evidencePath | Out-Null

$stages = @(
    [ordered]@{
        Name = "A_options_test"
        Instruction = "VCDS -> Options -> Test. Click Test once and wait for the result."
    },
    [ordered]@{
        Name = "B_engine_connect"
        Instruction = "VCDS main screen -> Select -> 01-Engine. Wait until controller ID is shown."
    },
    [ordered]@{
        Name = "C_group_011"
        Instruction = "01-Engine -> Measuring Blocks -> Group 011. Let it refresh for 5-10 seconds."
    },
    [ordered]@{
        Name = "D_group_008"
        Instruction = "Switch to Measuring Blocks Group 008. Let it refresh for 5-10 seconds."
    },
    [ordered]@{
        Name = "E_group_003"
        Instruction = "Switch to Measuring Blocks Group 003. Let it refresh for 5-10 seconds."
    },
    [ordered]@{
        Name = "F_dtc_read"
        Instruction = "Open Fault Codes and READ stored DTCs only. DO NOT press Clear Codes."
    },
    [ordered]@{
        Name = "G_disconnect"
        Instruction = "Return/Close Controller so VCDS cleanly disconnects from 01-Engine."
    }
)

Write-Host "[2/3] Isolated captures." -ForegroundColor Green
Write-Host "For each stage: press ENTER to start, do exactly the requested VCDS action,"
Write-Host "then return to PowerShell and press ENTER to stop the capture."
Write-Host ""

foreach ($stage in $stages) {
    Write-Host "------------------------------------------------------------" -ForegroundColor DarkGray
    Write-Host ("STAGE: " + $stage.Name) -ForegroundColor Cyan
    Write-Host $stage.Instruction -ForegroundColor White
    [void](Read-Host "Press ENTER to START")

    & $captureScript -CaptureName $stage.Name -OutputDirectory $sessionDir -DurationSeconds 0 -Device $Device

    $pcap = Join-Path $sessionDir ($stage.Name + ".pcap")
    if (Test-Path $pcap) {
        $size = (Get-Item $pcap).Length
        Write-Host ("Saved: {0} ({1:N1} KB)" -f $pcap, ($size / 1KB)) -ForegroundColor Green
        if ($size -le 24) {
            Write-Warning "Capture looks empty."
        }
    } else {
        Write-Warning "Capture file was not created: $pcap"
    }
}

Write-Host ""
Write-Host "[3/3] Parsing and packaging..." -ForegroundColor Green
$pythonCmd = Get-Command python -ErrorAction SilentlyContinue
if (-not $pythonCmd) {
    $pythonCmd = Get-Command py -ErrorAction SilentlyContinue
}

if ($pythonCmd) {
    foreach ($pcap in Get-ChildItem $sessionDir -Filter "*.pcap" -File | Sort-Object Name) {
        Write-Host "Parsing $($pcap.Name)..."
        try {
            & $pythonCmd.Source $parserScript $pcap.FullName --export-csv
        } catch {
            Write-Warning "Parser failed for $($pcap.Name): $($_.Exception.Message)"
        }
    }
} else {
    Write-Warning "Python not found. PCAP files are still valid and can be parsed later."
}

$hashFile = Join-Path $sessionDir "SHA256SUMS.txt"
$hashLines = @()
Get-ChildItem $sessionDir -File |
    Where-Object { $_.Name -ne "SHA256SUMS.txt" } |
    Sort-Object Name |
    ForEach-Object {
        $h = Get-FileHash $_.FullName -Algorithm SHA256
        $hashLines += ("{0}  {1}" -f $h.Hash.ToLowerInvariant(), $_.Name)
    }
[System.IO.File]::WriteAllLines($hashFile, $hashLines, [System.Text.Encoding]::UTF8)

$readme = @"
B03-V2 USB capture session
==========================
Created: $(Get-Date -Format o)
USBPcap control device: $Device

Expected sample identity:
  VID 0403
  PID FA24
  Serial RT000001

Captures:
  A_options_test.pcap
  B_engine_connect.pcap
  C_group_011.pcap
  D_group_008.pcap
  E_group_003.pcap
  F_dtc_read.pcap
  G_disconnect.pcap

READ-ONLY session. No Clear DTC, Coding, Adaptation, Security Access,
Basic Settings, Output Tests, or flashing.
"@
[System.IO.File]::WriteAllText((Join-Path $sessionDir "README_CAPTURE.txt"), $readme, [System.Text.Encoding]::UTF8)

$zipPath = "$sessionDir.zip"
if (Test-Path $zipPath) {
    Remove-Item $zipPath -Force
}
Compress-Archive -Path (Join-Path $sessionDir "*") -DestinationPath $zipPath -CompressionLevel Optimal

Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
Write-Host " CAPTURE COMPLETE" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
Write-Host "ZIP: $zipPath" -ForegroundColor Cyan
Write-Host "Upload/send this ZIP unchanged."
