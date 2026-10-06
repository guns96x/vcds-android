#!/data/data/com.termux/files/usr/bin/bash
# Step 1: proves Android lets QEMU take the cable. No Windows needed, ~10 s.
exec bash "$(dirname "$0")/usb-launch.sh" smoke "$@"
