#!/usr/bin/env bash
set -euo pipefail

PORT=""
BOARD=""
PLACEHOLDER="change-me-before-flashing"

declare -A BOARD_LABELS=(
    [esp32c3]="ESP32-C3-DevKitM-1 (default)"
    [lolin32lite]="Wemos Lolin32 Lite"
    [esp32dev]="ESP32-DevKitC"
    [lolin32]="Wemos Lolin32"
    [esp32s3]="ESP32-S3-DevKitC-1"
    [nodemcu32s]="NodeMCU ESP32-S"
)
declare -A BOARD_PINS=(
    [esp32c3]=8
    [lolin32lite]=22
    [esp32dev]=26
    [lolin32]=26
    [esp32s3]=4
    [nodemcu32s]=26
)
BOARD_ORDER=(esp32c3 lolin32lite esp32dev lolin32 esp32s3 nodemcu32s)

# Known ESP32 USB adapter VID:PID
declare -A VID_PID_NAMES=(
    ["10c4:ea60"]="CP2102 (Silicon Labs)"
    ["1a86:7523"]="CH340"
    ["1a86:55d4"]="CH9102"
    ["0403:6001"]="FTDI FT232"
    ["303a:1001"]="ESP32-S3/C3 USB-JTAG"
)

CYAN='\033[0;36m'
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
GRAY='\033[0;37m'
WHITE='\033[1;37m'
NC='\033[0m'

write_step() { echo -e "\n  ${CYAN}>> $1${NC}"; }
write_ok()   { echo -e "     ${GREEN}OK: $1${NC}"; }
write_fail() { echo -e "\n  ${RED}ERROR: $1${NC}\n"; exit 1; }

write_qr() {
    local rows=(
        "000000000000000000000000000000000000000"
        "011111110100100001001001100111011111110"
        "010000010010101011111010000001010000010"
        "010111010001110110110110100100010111010"
        "010111010110100111000100111101010111010"
        "010111010100010010010001101111010111010"
        "010000010101000011111110000001010000010"
        "011111110101010101010101010101011111110"
        "000000000110001010110011010000000000000"
        "010001011110111111001111110010111110010"
        "011101001000111100101000101011100110100"
        "001011110000111110010110111110100011000"
        "000110100000010111111111010101101011100"
        "000101011101100001100011010000110011110"
        "010001101110010011010000111111000110000"
        "011000011100110011100011110110001111000"
        "010000000101100110100010110000001101000"
        "010110011100100110111111110001111011000"
        "010111001010100101000010000011000111100"
        "010101011000010101110000100110010101000"
        "000001100011111100100110010000101101100"
        "001101010111011110101011110000111001000"
        "001110001101000011010001101111001100000"
        "011011010010111110000100111111011111000"
        "011110000001011011110010010100111101010"
        "000101010000001011111011110011111011100"
        "011001001010000000000000111111001100000"
        "000111111000000110110101110110101000000"
        "000101001111101011110011110100001101100"
        "011001010101110000101111010111111101010"
        "000000000100000011000000101101000100100"
        "011111110110001010000110110001010100100"
        "010000010000010111101110000111000111010"
        "010111010110111100010101010001111111010"
        "010111010011100101011000100010111011110"
        "010111010001000001110110101110010000000"
        "010000010000011000100010000010010101100"
        "011111110100011110101110010010001001110"
        "000000000000000000000000000000000000000"
    )
    local FULL=$'█' TOP=$'▀' BOT=$'▄'
    echo ""
    local i=0
    while (( i < ${#rows[@]} )); do
        local rowT="${rows[$i]}"
        local rowB="${rows[$((i+1))]:-}"
        [[ -z "$rowB" ]] && rowB="$(printf '0%.0s' $(seq 1 ${#rowT}))"
        local line="  "
        for (( j=0; j<${#rowT}; j++ )); do
            local t="${rowT:$j:1}" b="${rowB:$j:1}"
            if [[ "$t" == "1" && "$b" == "1" ]]; then
                line+="$FULL"
            elif [[ "$t" == "1" ]]; then
                line+="$TOP"
            elif [[ "$b" == "1" ]]; then
                line+="$BOT"
            else
                line+=" "
            fi
        done
        echo "$line"
        (( i+=2 ))
    done
    echo ""
}

# --- Parse args ---------------------------------------------------------------

while [[ $# -gt 0 ]]; do
    case "$1" in
        --port)  PORT="$2";  shift 2 ;;
        --board) BOARD="$2"; shift 2 ;;
        *)       shift ;;
    esac
done

# --- Header -------------------------------------------------------------------

echo ""
echo -e "  ${WHITE}============================================${NC}"
echo -e "  ${WHITE}   GarageAAtoESP32 - Flash Tool${NC}"
echo -e "  ${WHITE}============================================${NC}"
echo ""
echo -e "  ${GRAY}This script will:${NC}"
echo -e "  ${GRAY}  1. Make sure PlatformIO is installed${NC}"
echo -e "  ${GRAY}  2. Ask which board you have${NC}"
echo -e "  ${GRAY}  3. Find your ESP32 on a USB port${NC}"
echo -e "  ${GRAY}  4. Ask which trigger mechanism you wired up${NC}"
echo -e "  ${GRAY}  5. Ask for your PIN${NC}"
echo -e "  ${GRAY}  6. Optionally tune sleep interval, pulse, device name${NC}"
echo -e "  ${GRAY}  7. Compile and flash the firmware${NC}"
echo -e "  ${GRAY}  8. Leave no trace of your PIN on disk${NC}"
echo ""

# --- Step 1: PlatformIO -------------------------------------------------------

write_step "Step 1/7 - Checking for PlatformIO..."

PIO_CMD=""
if command -v pio &>/dev/null; then
    PIO_CMD="pio"
elif python3 -m platformio --version &>/dev/null 2>&1; then
    PIO_CMD="python3 -m platformio"
elif python -m platformio --version &>/dev/null 2>&1; then
    PIO_CMD="python -m platformio"
fi

if [[ -n "$PIO_CMD" ]]; then
    write_ok "PlatformIO found ($PIO_CMD)."
else
    echo -e "  ${YELLOW}  PlatformIO not found. Installing via official installer...${NC}"
    echo -e "  ${YELLOW}  (One-time download of ~300 MB, please be patient)${NC}"
    echo ""
    if ! command -v python3 &>/dev/null; then
        write_fail "Python 3 is required but not found. Install it with your package manager (e.g. sudo apt install python3) and re-run this script."
    fi
    if ! command -v curl &>/dev/null; then
        write_fail "curl is required but not found. Install it with your package manager (e.g. sudo apt install curl) and re-run this script."
    fi
    curl -fsSL https://raw.githubusercontent.com/platformio/platformio-core-installer/develop/get-platformio.py | python3
    # Reload PATH for ~/.local/bin
    export PATH="$HOME/.local/bin:$PATH"
    if command -v pio &>/dev/null; then
        PIO_CMD="pio"
        write_ok "PlatformIO installed successfully."
    elif python3 -m platformio --version &>/dev/null 2>&1; then
        PIO_CMD="python3 -m platformio"
        write_ok "PlatformIO installed successfully."
    else
        write_fail "'pio' still not found after install. Open a new terminal and try again."
    fi
fi

invoke_pio() { $PIO_CMD "$@"; }

# --- Step 2: Board selection --------------------------------------------------

write_step "Step 2/7 - Select your board..."

if [[ -n "$BOARD" ]] && [[ -n "${BOARD_LABELS[$BOARD]+_}" ]]; then
    write_ok "Board supplied via --board: $BOARD (${BOARD_LABELS[$BOARD]})"
else
    if [[ -n "$BOARD" ]]; then
        echo -e "  ${YELLOW}  Unknown board '$BOARD'. Please pick from the list below.${NC}"
    fi
    echo ""
    for i in "${!BOARD_ORDER[@]}"; do
        local_key="${BOARD_ORDER[$i]}"
        echo -e "  ${WHITE}  [$((i+1))] ${BOARD_LABELS[$local_key]}  (env: $local_key)${NC}"
    done
    echo ""
    read -rp "  Enter number (default: 1): " board_choice
    [[ -z "$board_choice" ]] && board_choice="1"
    board_idx=$(( board_choice - 1 ))
    if (( board_idx < 0 || board_idx >= ${#BOARD_ORDER[@]} )); then
        write_fail "Invalid selection."
    fi
    BOARD="${BOARD_ORDER[$board_idx]}"
    write_ok "Board: ${BOARD_LABELS[$BOARD]}"
fi

DEFAULT_PIN="${BOARD_PINS[$BOARD]}"

# --- Step 3: Find the ESP32 ---------------------------------------------------

write_step "Step 3/7 - Looking for ESP32 on USB ports..."

if [[ -n "$PORT" ]]; then
    write_ok "Using port supplied via --port: $PORT"
else
    declare -a found_ports=()
    declare -a found_chips=()

    # Enumerate USB devices via /sys
    if [[ -d /sys/bus/usb/devices ]]; then
        for dev_path in /sys/bus/usb/devices/*/; do
            vid_file="$dev_path/idVendor"
            pid_file="$dev_path/idProduct"
            [[ -f "$vid_file" && -f "$pid_file" ]] || continue
            vid=$(cat "$vid_file" | tr '[:upper:]' '[:lower:]')
            pid=$(cat "$pid_file" | tr '[:upper:]' '[:lower:]')
            key="${vid}:${pid}"
            chip_name="${VID_PID_NAMES[$key]:-}"
            [[ -z "$chip_name" ]] && continue
            # Find associated tty via find (globstar not reliable by default)
            while IFS= read -r tty_path; do
                tty_name=$(basename "$tty_path")
                [[ -c "/dev/$tty_name" ]] || continue
                found_ports+=("/dev/$tty_name")
                found_chips+=("$chip_name")
            done < <(find "$dev_path" \( -name "tty[A-Z]*" \) 2>/dev/null)
        done 2>/dev/null
    fi

    # Fallback: just list all likely ESP32 ttys if /sys scan found nothing
    if (( ${#found_ports[@]} == 0 )); then
        for tty in /dev/ttyUSB* /dev/ttyACM*; do
            [[ -c "$tty" ]] && found_ports+=("$tty") && found_chips+=("unknown")
        done
    fi

    if (( ${#found_ports[@]} == 0 )); then
        echo ""
        echo -e "  ${RED}No ESP32 detected on any USB port.${NC}"
        echo ""
        echo -e "  ${YELLOW}Possible causes:${NC}"
        echo -e "  ${YELLOW}  - The board is not plugged in${NC}"
        echo -e "  ${YELLOW}  - The USB cable is charge-only (no data lines)${NC}"
        echo -e "  ${YELLOW}  - The USB driver is not loaded${NC}"
        echo ""
        echo -e "  ${YELLOW}Try: ls /dev/ttyUSB* /dev/ttyACM* to see what's available${NC}"
        echo -e "  ${YELLOW}If the device appears there, pass it with --port /dev/ttyUSB0${NC}"
        echo ""
        exit 1
    elif (( ${#found_ports[@]} == 1 )); then
        PORT="${found_ports[0]}"
        write_ok "Found: $PORT (${found_chips[0]})"
    else
        echo ""
        echo -e "  ${YELLOW}Multiple ESP32-compatible devices found. Pick one:${NC}"
        echo ""
        for i in "${!found_ports[@]}"; do
            echo -e "  ${WHITE}  [$((i+1))] ${found_ports[$i]}  —  ${found_chips[$i]}${NC}"
        done
        echo ""
        read -rp "  Enter number: " port_choice
        port_idx=$(( port_choice - 1 ))
        if (( port_idx < 0 || port_idx >= ${#found_ports[@]} )); then
            write_fail "Invalid selection."
        fi
        PORT="${found_ports[$port_idx]}"
        write_ok "Using $PORT"
    fi
fi

# --- Step 4: Trigger mode -----------------------------------------------------

write_step "Step 4/7 - Choose your trigger mechanism..."
echo ""
echo -e "  ${WHITE}[1] Transistor          - NPN transistor wired into the fob (soldering required, recommended)${NC}"
echo -e "  ${WHITE}[2] Relay active-low    - relay module that triggers on LOW  (older/opto-isolated modules)${NC}"
echo -e "  ${WHITE}[3] Relay active-high   - relay module that triggers on HIGH (most common, e.g. JQC-3FF)${NC}"
echo -e "  ${WHITE}[4] Capacitive pad      - copper tape on a Fingerbot's button (no soldering, renter-friendly)${NC}"
echo ""

read -rp "  Enter number (default: 1): " trigger_choice
[[ -z "$trigger_choice" ]] && trigger_choice="1"

case "$trigger_choice" in
    1) TRIGGER_MODE="MODE_TRANSISTOR" ;;
    2) TRIGGER_MODE="MODE_RELAY"      ;;
    3) TRIGGER_MODE="MODE_RELAY_HIGH" ;;
    4) TRIGGER_MODE="MODE_CAP_PULSE"  ;;
    *) write_fail "Invalid selection. Please run flash.sh again." ;;
esac

write_ok "Trigger mode: $TRIGGER_MODE"

# --- Step 5: PIN --------------------------------------------------------------

write_step "Step 5/7 - Set your PIN..."
echo ""
echo -e "  ${GRAY}This PIN must match the one you enter in the Android app.${NC}"
echo -e "  ${GRAY}It will be compiled into the firmware and never saved to disk.${NC}"
echo ""

read -rsp "  Enter PIN: " PIN
echo ""

if [[ -z "$PIN" ]]; then
    write_fail "PIN cannot be empty."
fi
if [[ "$PIN" == "$PLACEHOLDER" ]]; then
    write_fail "You must choose a real PIN, not the placeholder."
fi

read -rsp "  Confirm PIN: " PIN_CONFIRM
echo ""

if [[ "$PIN" != "$PIN_CONFIRM" ]]; then
    unset PIN PIN_CONFIRM
    write_fail "PINs do not match. Please run flash.sh again."
fi

write_ok "PIN confirmed."

# --- Step 6: Advanced options -------------------------------------------------

write_step "Step 6/7 - Advanced options (press Enter to keep defaults)..."
echo ""
echo -e "  ${GRAY}These are optional. Hit Enter at each prompt to use the default.${NC}"
echo ""

# Trigger pin
echo -e "  ${GRAY}GPIO pin used to fire the trigger (transistor base / relay coil / capacitive pad).${NC}"
echo -e "  ${GRAY}Default for ${BOARD_LABELS[$BOARD]}: GPIO $DEFAULT_PIN${NC}"
read -rp "  Trigger GPIO (default: $DEFAULT_PIN): " pin_input
if [[ -z "$pin_input" ]]; then
    TRIGGER_PIN="$DEFAULT_PIN"
else
    if ! [[ "$pin_input" =~ ^[0-9]+$ ]] || (( pin_input < 0 || pin_input > 39 )); then
        write_fail "Trigger GPIO must be a number between 0 and 39."
    fi
    TRIGGER_PIN="$pin_input"
fi
write_ok "Trigger GPIO: $TRIGGER_PIN"

# Sleep duration
echo ""
echo -e "  ${GRAY}How long the ESP32 sleeps between BLE advertising windows.${NC}"
echo -e "  ${GRAY}Lower values reduce the wait at the door. 1-2 s is recommended.${NC}"
echo -e "  ${GRAY}Default: 5 s. Valid range: 1-10 s.${NC}"
read -rp "  Sleep duration in seconds (default: 5): " sleep_input
if [[ -z "$sleep_input" ]]; then
    SLEEP_DURATION=5
else
    if ! [[ "$sleep_input" =~ ^[0-9]+$ ]] || (( sleep_input < 1 || sleep_input > 10 )); then
        write_fail "Sleep duration must be a number between 1 and 10."
    fi
    SLEEP_DURATION="$sleep_input"
fi
write_ok "Sleep duration: ${SLEEP_DURATION} s"

# Relay pulse duration
echo ""
echo -e "  ${GRAY}How long the ESP32 holds the fob button pressed.${NC}"
echo -e "  ${GRAY}Too short: fob doesn't register and the door stays closed.${NC}"
echo -e "  ${GRAY}Too long: fob may double-trigger if it re-arms quickly.${NC}"
echo -e "  ${GRAY}Default: 500 ms. Try 300 ms for snappy fobs, 800 ms for slow ones. Valid range: 100-2000 ms.${NC}"
read -rp "  Pulse duration in ms (default: 500): " pulse_input
if [[ -z "$pulse_input" ]]; then
    PULSE_DURATION=500
else
    if ! [[ "$pulse_input" =~ ^[0-9]+$ ]] || (( pulse_input < 100 || pulse_input > 2000 )); then
        write_fail "Pulse duration must be a number between 100 and 2000 ms."
    fi
    PULSE_DURATION="$pulse_input"
fi
write_ok "Pulse duration: ${PULSE_DURATION} ms"

# Device name
echo ""
echo -e "  ${GRAY}BLE device name — only matters if you have multiple units.${NC}"
echo -e "  ${GRAY}The Android app will scan for this name. Default: Garage-Opener${NC}"
read -rp "  Device name (default: Garage-Opener): " device_name_input
if [[ -z "$device_name_input" ]]; then
    DEVICE_NAME="Garage-Opener"
else
    if (( ${#device_name_input} > 20 )); then
        write_fail "Device name must be 20 characters or fewer (BLE limit)."
    fi
    DEVICE_NAME="$device_name_input"
fi
write_ok "Device name: $DEVICE_NAME"

# --- Step 7: Compile and flash ------------------------------------------------

write_step "Step 7/7 - Compiling and flashing..."
echo ""
echo -e "  ${GRAY}Running PlatformIO build + flash. This may take a few minutes on first run...${NC}"
echo -e "  ${GRAY}(First run downloads the ESP32 toolchain - subsequent runs are much faster)${NC}"
echo ""

EXTRA_FLAGS="-DCORE_DEBUG_LEVEL=0 -DTRIGGER_MODE=${TRIGGER_MODE} -DTRIGGER_PIN=${TRIGGER_PIN} -DUSER_PIN=\\\"${PIN}\\\" -DDEVICE_NAME=\\\"${DEVICE_NAME}\\\" -DSLEEP_DURATION_S=${SLEEP_DURATION} -DRELAY_PULSE_MS=${PULSE_DURATION}"

FLASH_OK=0
PLATFORMIO_BUILD_FLAGS="$EXTRA_FLAGS" invoke_pio run -e "$BOARD" --target upload --upload-port "$PORT" \
    && FLASH_OK=1 || true

unset PIN PIN_CONFIRM

# --- Done --------------------------------------------------------------------

echo ""
if (( FLASH_OK )); then
    echo -e "  ${GREEN}============================================${NC}"
    echo -e "  ${GREEN}   All done! Your ESP32 is ready to use.${NC}"
    echo -e "  ${GREEN}============================================${NC}"
    echo ""
    echo -e "  ${GRAY}Your PIN was never saved to disk.${NC}"
    echo ""
    echo -e "  ${WHITE}Scan to install the Android app:${NC}"
    write_qr
    echo -e "  ${GRAY}https://play.google.com/store/apps/details?id=com.dunnowsoftware.GarageAAtoESP32${NC}"
    echo ""
    echo -e "  ${GRAY}Then open the app and enter the same PIN you just flashed.${NC}"
else
    echo -e "  ${RED}============================================${NC}"
    echo -e "  ${RED}   Flash failed. See errors above.${NC}"
    echo -e "  ${RED}============================================${NC}"
    echo ""
    echo -e "  ${GRAY}Your PIN was NOT saved to disk.${NC}"
    echo -e "  ${GRAY}Fix the issue above and run flash.sh again.${NC}"
fi
echo ""
