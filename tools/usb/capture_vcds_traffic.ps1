<#
.SYNOPSIS
    Automates USB traffic capture using USBPcapCMD for Ross-Tech / B03-V2 reverse engineering.

.DESCRIPTION
    Launches USBPcapCMD.exe to capture USB Request Blocks (URBs) from the USB root hub
    connecting the Ross-Tech / B03-V2 diagnostic cable. Produces a standard .pcap file
    suitable for analysis with tools/usb/parse_usb_pcap.py and Wireshark.

.PARAMETER CaptureName
    Base name for the output capture file (e.g. "capture_A_options_test").

.PARAMETER OutputDirectory
    Directory where the .pcap file will be written. Defaults to tools/usb/captures.

.PARAMETER DurationSeconds
    Capture duration in seconds. If 0 (default), capture runs until Enter is pressed.

.PARAMETER Device
    USBPcap control device name. Defaults to "\\.\USBPcap1".

.EXAMPLE
    .\tools\usb\capture_vcds_traffic.ps1 -CaptureName "capture_A_options_test" -DurationSeconds 30
#>

[CmdletBinding()]
param (
    [Parameter(Position = 0)]
    [string]$CaptureName = "vcds_usb_capture_" + (Get-Date -Format "yyyyMMdd_HHmmss"),

    [Parameter(Position = 1)]
    [string]$OutputDirectory = "$PSScriptRoot\captures",

    [Parameter(Position = 2)]
    [int]$DurationSeconds = 0,

    [Parameter(Position = 3)]
    [string]$Device = "\\.\USBPcap1"
)

$ErrorActionPreference = "Stop"

$usbpcapExe = "C:\Program Files\USBPcap\USBPcapCMD.exe"
if (-not (Test-Path $usbpcapExe)) {
    # Check alternate x86 path
    $usbpcapExeX86 = "C:\Program Files (x86)\USBPcap\USBPcapCMD.exe"
    if (Test-Path $usbpcapExeX86) {
        $usbpcapExe = $usbpcapExeX86
    } else {
        Write-Error "USBPcapCMD.exe not found. Please verify USBPcap installation."
        exit 1
    }
}

if (-not (Test-Path $OutputDirectory)) {
    New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
}

$outputFile = Join-Path $OutputDirectory "$CaptureName.pcap"

Write-Host "=========================================================" -ForegroundColor Cyan
Write-Host " VCDS Black-Box USB Capture Tool" -ForegroundColor Cyan
Write-Host "=========================================================" -ForegroundColor Cyan
Write-Host "USBPcap Executable : $usbpcapExe"
Write-Host "Control Device     : $Device"
Write-Host "Output File        : $outputFile"
Write-Host "Capture Duration   : $(if ($DurationSeconds -gt 0) { "$DurationSeconds seconds" } else { "Interactive (Enter to stop)" })"
Write-Host "---------------------------------------------------------"

$processArgs = "-d $Device -o `"$outputFile`" -A"
Write-Host "Starting USBPcap capture process..." -ForegroundColor Yellow

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $usbpcapExe
$psi.Arguments = $processArgs
$psi.UseShellExecute = $false
$psi.RedirectStandardInput = $true
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.CreateNoWindow = $true

$proc = [System.Diagnostics.Process]::Start($psi)

if ($null -eq $proc -or $proc.HasExited) {
    Write-Error "Failed to launch USBPcapCMD.exe or it exited immediately."
    exit 1
}

Write-Host "Capture ACTIVE! Perform your VCDS actions now..." -ForegroundColor Green

try {
    if ($DurationSeconds -gt 0) {
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        while ($sw.Elapsed.TotalSeconds -lt $DurationSeconds) {
            if ($proc.HasExited) {
                Write-Warning "USBPcapCMD exited unexpectedly early."
                break
            }
            $remaining = [math]::Max(0, [int]($DurationSeconds - $sw.Elapsed.TotalSeconds))
            Write-Progress -Activity "Capturing USB Traffic" -Status "$remaining s remaining" -PercentComplete (($sw.Elapsed.TotalSeconds / $DurationSeconds) * 100)
            Start-Sleep -Milliseconds 500
        }
        Write-Progress -Activity "Capturing USB Traffic" -Completed
    } else {
        Write-Host "Press [ENTER] to stop capture..." -ForegroundColor Yellow
        [Console]::ReadLine() | Out-Null
    }
} finally {
    Write-Host "Stopping capture..." -ForegroundColor Yellow
    try {
        # Send newline or Ctrl+C to USBPcapCMD stdin to trigger clean close
        $proc.StandardInput.WriteLine()
        $proc.StandardInput.Flush()
    } catch {
        # Ignore if pipe is already closed
    }

    $waitSuccess = $proc.WaitForExit(4000)
    if (-not $waitSuccess) {
        Write-Warning "Process did not terminate cleanly within 4s, forcing stop..."
        $proc.Kill()
    }
}

Write-Host "Capture stopped." -ForegroundColor Green

if (Test-Path $outputFile) {
    $fileItem = Get-Item $outputFile
    $sizeKb = [math]::Round($fileItem.Length / 1KB, 2)
    Write-Host "---------------------------------------------------------"
    Write-Host "Capture Saved Successfully!" -ForegroundColor Green
    Write-Host "File : $outputFile ($sizeKb KB)"
    Write-Host "Next : Parse with python tools/usb/parse_usb_pcap.py `"$outputFile`"" -ForegroundColor Cyan
    Write-Host "---------------------------------------------------------"
} else {
    Write-Warning "Capture file was not created or is 0 bytes."
}
