#!/data/data/com.termux/files/usr/bin/bash
# Starts the Windows VM. Display: VNC viewer app -> 127.0.0.1:5900.
#   run-windows.sh                 with the cable passed through (via termux-usb)
#   run-windows.sh --no-usb        without the cable
#   run-windows.sh --install X.iso create the disk if missing and boot the installer (no USB)
#   --cd X.iso                     put an ISO in the CD drive (combine with the above)
set -euo pipefail
source "$(dirname "$0")/common.sh"

USB=1
while [ $# -gt 0 ]; do
    case "$1" in
        --no-usb)  USB=0 ;;
        --install) USB=0; export VM_CDROM="${2:?ISO}"; shift ;;
        --cd)      export VM_CDROM="${2:?ISO}"; shift ;;
        *) qa_die "невідомий аргумент $1" ;;
    esac
    shift
done
[ -z "$VM_CDROM" ] || [ -f "$VM_CDROM" ] || qa_die "немає ISO $VM_CDROM"

# Android stops background CPU hogs without a wake lock.
command -v termux-wake-lock >/dev/null && termux-wake-lock

if [ ! -f "$VM_DISK" ]; then
    [ -n "$VM_CDROM" ] || qa_die "немає $VM_DISK; або скопіюй готовий образ з ПК, або --install win7.iso"
    mkdir -p "$(dirname "$VM_DISK")"
    qemu-img create -f qcow2 "$VM_DISK" "${VM_DISK_SIZE:-40G}"
fi

if [ "$USB" = 1 ]; then
    exec bash "$QA_DIR/usb-launch.sh" run
fi
qa_base_args
[ -n "$VM_CDROM" ] && QA_ARGS+=(-boot order=dc)
echo "VNC: $VM_VNC (порт 5900), монітор QEMU: telnet 127.0.0.1 4444"
qa_qemu "${QA_ARGS[@]}"
