#!/data/data/com.termux/files/usr/bin/bash
# Runs under termux-usb with the cable's usbfs fd (TERMUX_USB_FD, or $1 without -E).
# Checks VID:PID from the fd, then starts QEMU with the fd passed in as an fdset.
set -uo pipefail
source "$(dirname "$0")/common.sh"

QA_MODE="${QA_MODE:-run}"
QA_LOG="${QA_LOG:-$VM_LOG_DIR/$QA_MODE-$(date +%Y%m%d_%H%M%S).log}"
mkdir -p "$(dirname "$QA_LOG")"
exec >>"$QA_LOG" 2>&1

FD="${TERMUX_USB_FD:-${1:-}}"
[[ "$FD" =~ ^[0-9]+$ ]] || qa_die "termux-usb не передав fd (отримано '$FD')"

# usbfs returns the 18-byte device descriptor on read; libusb seeks back to 0 itself.
read -r -a D <<<"$(od -An -tx1 -N18 <&"$FD" | tr -s ' \n' ' ')"
[ "${#D[@]}" -eq 18 ] || qa_die "не вдалося прочитати дескриптор з fd $FD"
VID="${D[9]}${D[8]}"
PID="${D[11]}${D[10]}"
echo "$(date '+%F %T') fd=$FD VID:PID=$VID:$PID mode=$QA_MODE"

if [ "$VID" != "$CABLE_VID" ] || [[ " $CABLE_PIDS " != *" $PID "* ]]; then
    [ "${ALLOW_ANY_USB:-0}" = 1 ] || qa_die "це не Ross-Tech HEX ($VID:$PID). ALLOW_ANY_USB=1 — якщо так і треба"
fi

export LD_PRELOAD="$SHIM_SO${LD_PRELOAD:+:$LD_PRELOAD}"
# guest-reset=false: a USB port reset could re-enumerate the cable and the
# Android fd would then point at a device that no longer exists.
USB_ARGS=(-add-fd "fd=$FD,set=1"
          -device "usb-host,hostdevice=/dev/fdset/1,guest-reset=false,id=cable")

if [ "$QA_MODE" = smoke ]; then
    # No guest at all: only proves Android -> libusb -> QEMU attaches the cable.
    OUT="$({ sleep 3; echo 'info usb'; sleep 1; echo quit; } |
        qemu-system-x86_64 -M pc -accel tcg -nodefaults -display none -usb \
            "${USB_ARGS[@]}" -monitor stdio -S 2>&1)"
    echo "$OUT" | sed 's/\x1b\[[0-9;]*[A-Za-z]//g' | grep -v '^(qemu) [a-z ]*$'
    if echo "$OUT" | grep -q 'ID: cable'; then
        echo "SMOKE PASS: кабель $VID:$PID підключено до віртуального USB QEMU"
    else
        echo "SMOKE FAIL: QEMU не зміг забрати кабель (див. повідомлення вище)"
        exit 1
    fi
    exit 0
fi

[ -f "$VM_DISK" ] || qa_die "немає образу $VM_DISK (див. README.uk.md, розділ «Образ»)"
qa_base_args
echo "VNC: $VM_VNC (порт 5900), монітор QEMU: telnet 127.0.0.1 4444"
qa_qemu "${QA_ARGS[@]}" "${USB_ARGS[@]}"
