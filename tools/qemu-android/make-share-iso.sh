#!/data/data/com.termux/files/usr/bin/bash
# Packs a folder (VCDS installer, logs to take in) into an ISO for the VM's CD drive.
# Usage: make-share-iso.sh DIR [OUT.iso]; then run-windows.sh --cd OUT.iso
set -euo pipefail
source "$(dirname "$0")/common.sh"
SRC="${1:?папка}"
OUT="${2:-$VM_DIR/share.iso}"
command -v xorriso >/dev/null || qa_die "pkg install xorriso"
xorriso -as mkisofs -J -R -V SHARE -o "$OUT" "$SRC"
echo "$OUT"
