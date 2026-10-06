#!/data/data/com.termux/files/usr/bin/bash
# Gets an Android USB permission + fd for the cable via termux-usb, then runs
# usb-callback.sh with that fd. Usage: usb-launch.sh smoke|run [/dev/bus/usb/BBB/DDD]
set -euo pipefail
source "$(dirname "$0")/common.sh"

QA_MODE="${1:-}"
case "$QA_MODE" in smoke|run) ;; *) qa_die "usage: $0 smoke|run [device]";; esac
export QA_MODE

[ -f "$SHIM_SO" ] || qa_die "немає $SHIM_SO, запусти setup-termux.sh"

DEV="${2:-}"
if [ -z "$DEV" ]; then
    echo "Шукаю USB-пристрої (termux-usb -l)..."
    LIST="$(timeout 15 termux-usb -l)" || {
        [ $? -eq 124 ] && qa_die "termux-usb -l висить 15 с: застосунок Termux:API не відповідає (не встановлений, з іншого джерела, ніж Termux, або заблокований енергозбереженням)"
        qa_die "termux-usb -l завершився з помилкою"
    }
    echo "$LIST"
    mapfile -t DEVS < <(echo "$LIST" | tr -d '[]", ' | grep '^/dev/bus/usb/' || true)
    case "${#DEVS[@]}" in
        0) qa_die "Android не бачить жодного USB-пристрою. Перевір OTG-перехідник і що кабель вставлено в телефон." ;;
        1) DEV="${DEVS[0]}" ;;
        *) printf '%s\n' "${DEVS[@]}"
           qa_die "кілька USB-пристроїв; передай потрібний другим аргументом" ;;
    esac
fi

mkdir -p "$VM_LOG_DIR"
export QA_LOG="$VM_LOG_DIR/$QA_MODE-$(date +%Y%m%d_%H%M%S).log"
echo "Пристрій: $DEV"
echo "Лог: $QA_LOG"
echo "Якщо Android спитає дозвіл на USB для Termux:API — дозволь."

# termux-usb captures the callback's stdout until it exits, so the callback
# writes to $QA_LOG and we show the log afterwards.
termux-usb -r -E -e "$QA_DIR/usb-callback.sh" "$DEV" || true
echo "----- $QA_LOG -----"
cat "$QA_LOG" 2>/dev/null || echo "(лог порожній: callback не запустився — дозвіл не дано або пристрій відключився)"
