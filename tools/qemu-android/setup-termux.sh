#!/data/data/com.termux/files/usr/bin/bash
# One-time Termux setup: QEMU x86_64, termux-usb, and the libusb no-discovery shim.
set -euo pipefail
source "$(dirname "$0")/common.sh"

pkg update -y
pkg install -y qemu-system-x86-64-headless qemu-utils termux-api libusb clang xorriso inetutils

mkdir -p "$(dirname "$SHIM_SO")" "$VM_DIR" "$VM_LOG_DIR"
clang -O2 -Wall -shared -fPIC -o "$SHIM_SO" "$QA_DIR/libusb_nodiscovery.c" \
    -I"$PREFIX/include/libusb-1.0" -lusb-1.0 -ldl
echo "shim: $SHIM_SO"

qemu-system-x86_64 --version | head -1
qemu-system-x86_64 -device help 2>/dev/null | grep -q '"usb-host"' \
    && echo "usb-host: є" || echo "УВАГА: цей QEMU зібрано без usb-host — проброс кабелю не спрацює"

if ! termux-usb -l >/dev/null 2>&1; then
    echo "УВАГА: termux-usb не відповідає. Постав застосунок Termux:API з того ж джерела, що й Termux (F-Droid або GitHub)."
fi

cat <<EOF

Готово. Далі:
  1. Кабель у телефон (OTG), у машину поки не обов'язково.
  2. bash $QA_DIR/usb-smoke-test.sh      — чи віддає Android кабель у QEMU (без Windows, ~10 с)
EOF
