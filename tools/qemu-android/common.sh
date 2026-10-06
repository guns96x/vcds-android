# Shared settings for the Windows-in-QEMU-on-Android tools. Sourced, not executed.
# Every value can be overridden from the environment before calling a script.

QA_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

VM_DIR="${VM_DIR:-$HOME/vcds-vm}"
VM_DISK="${VM_DISK:-$VM_DIR/win7x64.qcow2}"
VM_RAM="${VM_RAM:-2048}"
VM_SMP="${VM_SMP:-2}"
# qemu64 alone is enough for Windows 7 x64; the extra flags also satisfy Windows 10 x64.
VM_CPU="${VM_CPU:-qemu64,+cx16,+lahf_lm,+3dnowprefetch,+ssse3,+sse4.1,+sse4.2,+popcnt}"
# VNC display :0 = TCP port 5900, localhost only.
VM_VNC="${VM_VNC:-127.0.0.1:0}"
VM_LOG_DIR="${VM_LOG_DIR:-$VM_DIR/logs}"
# Optional ISO in the virtual CD drive (Windows installer or make-share-iso.sh output).
VM_CDROM="${VM_CDROM:-}"

# Ross-Tech FTDI vendor id; FA20/FA23/FA24 are the HEX-USB(+CAN) family.
CABLE_VID="${CABLE_VID:-0403}"
CABLE_PIDS="${CABLE_PIDS:-fa20 fa23 fa24}"

SHIM_SO="${SHIM_SO:-$HOME/.local/lib/libusb_nodiscovery.so}"

qa_die() { echo "ПОМИЛКА: $*" >&2; exit 1; }

# Virtual hardware Windows 7 has in-box drivers for: i440FX + PIIX3 UHCI (USB 1.1,
# enough for the 12 Mb/s FTDI), IDE disk, std VGA, rtl8139. The PC-side image
# preparation script uses the same set so Windows does not re-detect hardware.
qa_base_args() {
    QA_ARGS=(
        -M pc -accel "tcg,tb-size=256"
        -cpu "$VM_CPU" -smp "$VM_SMP" -m "$VM_RAM"
        -rtc base=localtime
        -vga std -vnc "$VM_VNC"
        -usb -device usb-tablet
        -nic "user,model=rtl8139"
        -monitor "telnet:127.0.0.1:4444,server=on,wait=off"
    )
    if [ -f "$VM_DISK" ]; then
        QA_ARGS+=(-drive "file=$VM_DISK,if=ide,index=0,media=disk,cache=writeback")
    fi
    if [ -n "${VM_CDROM:-}" ]; then
        QA_ARGS+=(-drive "file=$VM_CDROM,if=ide,index=2,media=cdrom,readonly=on")
    fi
}

qa_qemu() {
    command -v qemu-system-x86_64 >/dev/null || qa_die "немає qemu-system-x86_64, запусти setup-termux.sh"
    echo "qemu-system-x86_64 $*"
    exec qemu-system-x86_64 "$@"
}
