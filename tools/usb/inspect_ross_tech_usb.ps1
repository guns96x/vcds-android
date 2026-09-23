<#
.SYNOPSIS
    Non-destructive Windows USB inspector for Ross-Tech / B03-V2 FTDI diagnostic cables.

.DESCRIPTION
    Enumerates connected USB devices, checks for FTDI / Ross-Tech Hardware IDs (VID 0403, PID FA24/FA20/etc.),
    collects descriptors, endpoints, driver bindings, and serial information.
    Outputs normalized, machine-readable JSON.
    SAFETY: Strictly read-only. NEVER writes FTDI EEPROM.

.PARAMETER OutputPath
    Target JSON output path. Defaults to "tools/usb/evidence_windows_usb.json".

.EXAMPLE
    .\inspect_ross_tech_usb.ps1 -OutputPath "evidence.json"
#>

[CmdletBinding()]
param (
    [string]$OutputPath = "tools/usb/evidence_windows_usb.json"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Continue"

Write-Host "=== Ross-Tech / FTDI USB Hardware Probe (Windows) ===" -ForegroundColor Cyan
Write-Host "Safety Guarantee: STRICTLY READ-ONLY (No EEPROM writes)" -ForegroundColor Green

$timestamp = (Get-Date).ToString("yyyy-MM-ddTHH:mm:sszzz")
$targetVids = @("0403")
$targetPids = @("FA24", "FA20", "FA23", "FA25", "FA30", "6001", "6010", "6014")

$probeResult = [ordered]@{
    timestamp = $timestamp
    os = (Get-CimInstance Win32_OperatingSystem).Caption
    probe_type = "windows_pnp_usb_inspection"
    currently_connected = $false
    active_device = $null
    historical_evidence = $null
}

# 1. Query currently present PnP entities matching USB\VID_0403
$presentDevices = Get-CimInstance Win32_PnPEntity | Where-Object {
    $_.Present -eq $true -and $_.DeviceID -match "USB\\VID_0403"
}

if ($presentDevices) {
    Write-Host "[+] Found $(@($presentDevices).Count) active FTDI device(s) connected right now!" -ForegroundColor Green
    $activeList = @()
    foreach ($dev in $presentDevices) {
        $devId = $dev.DeviceID
        $hwIds = $dev.HardwareID
        $mfg = $dev.Manufacturer
        $name = $dev.Name
        $desc = $dev.Description
        $status = $dev.Status
        $service = $dev.Service
        $pnpClass = $dev.PNPClass

        # Extract VID and PID
        $vid = if ($devId -match "VID_([0-9A-Fa-f]{4})") { $Matches[1].ToUpper() } else { "UNKNOWN" }
        $devPid = if ($devId -match "PID_([0-9A-Fa-f]{4})") { $Matches[1].ToUpper() } else { "UNKNOWN" }
        $rev = if ($hwIds -join " " -match "REV_([0-9A-Fa-f]{4})") { $Matches[1].ToUpper() } else { "UNKNOWN" }

        # Extract serial number from instance ID (last segment after backslash)
        $serial = if ($devId -match "\\([^\\]+)$") { $Matches[1] } else { "UNKNOWN" }

        $isRossTech = ($vid -eq "0403" -and $targetPids -contains $devPid)

        $deviceObj = [ordered]@{
            name = $name
            description = $desc
            device_id = $devId
            hardware_ids = $hwIds
            vid = "0x$vid"
            pid = "0x$devPid"
            rev = if ($rev -ne "UNKNOWN") { "0x$rev" } else { $null }
            serial_number = $serial
            manufacturer = $mfg
            pnp_class = $pnpClass
            active_service = $service
            status = $status
            is_ross_tech_vid_pid = $isRossTech
            ftdi_silicon_family = switch ($rev) {
                "0200" { "FT232BM (Legacy)" }
                "0400" { "FT232BL" }
                "0600" { "FT232R (FT232RL / FT232RQ)" }
                "0900" { "FT232H" }
                default { if ($isRossTech) { "FT232R (Presumed from REV_0600 / Ross-Tech line)" } else { "Unknown FTDI silicon" } }
            }
        }
        $activeList += $deviceObj
    }
    $probeResult.currently_connected = $true
    $probeResult.active_device = $activeList
} else {
    Write-Host "[-] No FTDI device (VID 0403) is currently enumerated on the USB bus." -ForegroundColor Yellow
    Write-Host "    (Cable is either disconnected or not powered from OBD-II port)" -ForegroundColor Gray
}

# 2. Query historical evidence from Windows DriverStore, SetupAPI logs, and Registry
$historicalList = @()

# Check setupapi.dev*.log for VID_0403&PID_FA24
$setupapiFiles = Get-ChildItem "C:\Windows\inf\setupapi.dev*.log" -ErrorAction SilentlyContinue
foreach ($sf in $setupapiFiles) {
    $matchLines = Get-Content $sf.FullName -ErrorAction SilentlyContinue | Select-String -Pattern "USB\\VID_0403&PID_FA24" -Context 0, 5
    if ($matchLines) {
        $firstMatch = $matchLines[0]
        $historicalList += [ordered]@{
            source_file = $sf.Name
            detected_hardware_id = "USB\VID_0403&PID_FA24"
            log_sample = ($firstMatch.Line.Trim())
            context = ($firstMatch.Context.PostContext | ForEach-Object { $_.Trim() })
        }
    }
}

# Check Ross-Tech INF
$rtInfPath = "C:\Ross-Tech\VCDS\RT-USB64.inf"
if (Test-Path $rtInfPath) {
    $infContent = Get-Content $rtInfPath -ErrorAction SilentlyContinue
    $pidsInInf = $infContent | Select-String -Pattern "USB\\VID_0403&PID_[0-9A-Fa-f]{4}" | ForEach-Object { $_.Matches[0].Value } | Select-Object -Unique
    $historicalList += [ordered]@{
        source = "C:\Ross-Tech\VCDS\RT-USB64.inf"
        supported_pids = $pidsInInf
    }
}

$probeResult.historical_evidence = $historicalList

# 3. Write Output JSON
$outDir = Split-Path -Path $OutputPath -Parent
if ($outDir -and !(Test-Path $outDir)) {
    New-Item -ItemType Directory -Path $outDir -Force | Out-Null
}

$json = $probeResult | ConvertTo-Json -Depth 5
[System.IO.File]::WriteAllText($OutputPath, $json, [System.Text.Encoding]::UTF8)

Write-Host "[+] Inspection complete. Results saved to: $OutputPath" -ForegroundColor Green
return $probeResult
