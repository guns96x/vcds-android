param(
    [Parameter(Mandatory = $true)][string]$Iso,
    [string]$Disk = "$env:USERPROFILE\vcds-vm\win7x64.qcow2",
    [string]$ShareIso = "",
    [string]$Qemu = "C:\Program Files\qemu",
    [string]$Accel = "whpx,kernel-irqchip=off"
)

# Installs Windows + VCDS into a qcow2 on the PC (hardware-accelerated, minutes
# instead of hours on the phone). The virtual hardware matches common.sh, so
# the phone boots the same image without re-detecting devices.
# Accel: "whpx,kernel-irqchip=off" needs the Windows Hypervisor Platform feature;
# use -Accel tcg if QEMU refuses it (slower, still faster than the phone).
# Copy the finished qcow2 to the phone into ~/vcds-vm/ in Termux.

$ErrorActionPreference = "Stop"
$qemuExe = Join-Path $Qemu "qemu-system-x86_64.exe"
$qemuImg = Join-Path $Qemu "qemu-img.exe"
if (!(Test-Path $qemuExe)) { throw "QEMU not found in $Qemu (install from https://qemu.weilnetz.de/w64/)" }
if (!(Test-Path $Iso)) { throw "ISO not found: $Iso" }

if (!(Test-Path $Disk)) {
    New-Item -ItemType Directory -Force -Path (Split-Path $Disk) | Out-Null
    & $qemuImg create -f qcow2 $Disk 40G
}

$cpu = "qemu64,+cx16,+lahf_lm,+3dnowprefetch,+ssse3,+sse4.1,+sse4.2,+popcnt"
$qargs = @(
    "-M", "pc", "-accel", $Accel,
    "-cpu", $cpu, "-smp", "2", "-m", "2048",
    "-rtc", "base=localtime",
    "-vga", "std",
    "-usb", "-device", "usb-tablet",
    "-nic", "user,model=rtl8139",
    "-drive", "file=$Disk,if=ide,index=0,media=disk,cache=writeback",
    "-drive", "file=$Iso,if=ide,index=2,media=cdrom,readonly=on",
    "-boot", "order=dc"
)
if ($ShareIso) { $qargs += @("-drive", "file=$ShareIso,if=ide,index=3,media=cdrom,readonly=on") }

Write-Host "$qemuExe $($qargs -join ' ')"
& $qemuExe @qargs
