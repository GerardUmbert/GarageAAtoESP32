param(
    [string]$Port  = "",
    [string]$Board = ""
)

$ErrorActionPreference = "Stop"
$placeholder = "change-me-before-flashing"

# Supported boards: env name → friendly label + default trigger pin
$supportedBoards = [ordered]@{
    "esp32c3"     = @{ Label = "ESP32-C3-DevKitM-1 (default)"; Pin = 8;  LedPin = 8;  LedOn = 1; PioBoard = "esp32-c3-devkitm-1" }
    "lolin32lite" = @{ Label = "Wemos Lolin32 Lite";            Pin = 26; LedPin = 22; LedOn = 0; PioBoard = "lolin32_lite"       }
    "esp32dev"    = @{ Label = "ESP32-DevKitC";                 Pin = 26; LedPin = 2;  LedOn = 1; PioBoard = "esp32dev"           }
    "lolin32"     = @{ Label = "Wemos Lolin32";                 Pin = 26; LedPin = 5;  LedOn = 0; PioBoard = "lolin32"            }
    "esp32s3"     = @{ Label = "ESP32-S3-DevKitC-1";            Pin = 4;  LedPin = $null; LedOn = $null; PioBoard = "esp32-s3-devkitc-1" }
    "nodemcu32s"  = @{ Label = "NodeMCU ESP32-S";               Pin = 26; LedPin = 2;  LedOn = 1; PioBoard = "nodemcu-32s"        }
}

# Known ESP32 USB adapter VID/PID pairs
$esp32VidPids = @(
    @{ VID = "10C4"; PID = "EA60"; Name = "CP2102 (Silicon Labs)" },
    @{ VID = "1A86"; PID = "7523"; Name = "CH340"                 },
    @{ VID = "1A86"; PID = "55D4"; Name = "CH9102"                },
    @{ VID = "0403"; PID = "6001"; Name = "FTDI FT232"            },
    @{ VID = "303A"; PID = "1001"; Name = "ESP32-S3/C3 USB-JTAG"  }
)

function Write-Step {
    param([string]$Text)
    Write-Host ""
    Write-Host "  >> $Text" -ForegroundColor Cyan
}

function Write-OK {
    param([string]$Text)
    Write-Host "     OK: $Text" -ForegroundColor Green
}

function Write-Fail {
    param([string]$Text)
    Write-Host ""
    Write-Host "  ERROR: $Text" -ForegroundColor Red
    Write-Host ""
}

function Write-QR {
    $rows = @(
        "000000000000000000000000000000000000000",
        "011111110100100001001001100111011111110",
        "010000010010101011111010000001010000010",
        "010111010001110110110110100100010111010",
        "010111010110100111000100111101010111010",
        "010111010100010010010001101111010111010",
        "010000010101000011111110000001010000010",
        "011111110101010101010101010101011111110",
        "000000000110001010110011010000000000000",
        "010001011110111111001111110010111110010",
        "011101001000111100101000101011100110100",
        "001011110000111110010110111110100011000",
        "000110100000010111111111010101101011100",
        "000101011101100001100011010000110011110",
        "010001101110010011010000111111000110000",
        "011000011100110011100011110110001111000",
        "010000000101100110100010110000001101000",
        "010110011100100110111111110001111011000",
        "010111001010100101000010000011000111100",
        "010101011000010101110000100110010101000",
        "000001100011111100100110010000101101100",
        "001101010111011110101011110000111001000",
        "001110001101000011010001101111001100000",
        "011011010010111110000100111111011111000",
        "011110000001011011110010010100111101010",
        "000101010000001011111011110011111011100",
        "011001001010000000000000111111001100000",
        "000111111000000110110101110110101000000",
        "000101001111101011110011110100001101100",
        "011001010101110000101111010111111101010",
        "000000000100000011000000101101000100100",
        "011111110110001010000110110001010100100",
        "010000010000010111101110000111000111010",
        "010111010110111100010101010001111111010",
        "010111010011100101011000100010111011110",
        "010111010001000001110110101110010000000",
        "010000010000011000100010000010010101100",
        "011111110100011110101110010010001001110",
        "000000000000000000000000000000000000000"
    )

    $full   = [char]0x2588
    $top    = [char]0x2580
    $bottom = [char]0x2584

    Write-Host ""
    $i = 0
    while ($i -lt $rows.Count) {
        $rowTop = $rows[$i]
        $rowBot = if ($i + 1 -lt $rows.Count) { $rows[$i + 1] } else { "0" * $rowTop.Length }
        $line = "  "
        for ($j = 0; $j -lt $rowTop.Length; $j++) {
            $t = $rowTop[$j] -eq '1'
            $b = $rowBot[$j]  -eq '1'
            if     ($t -and $b)  { $line += $full   }
            elseif ($t)          { $line += $top    }
            elseif ($b)          { $line += $bottom }
            else                 { $line += " "     }
        }
        Write-Host $line
        $i += 2
    }
    Write-Host ""
}

# --- Header ------------------------------------------------------------------

Write-Host ""
Write-Host "  ============================================" -ForegroundColor White
Write-Host "     GarageAAtoESP32 - Flash Tool"            -ForegroundColor White
Write-Host "  ============================================" -ForegroundColor White
Write-Host ""
Write-Host "  This script will:"                                      -ForegroundColor Gray
Write-Host "    1. Make sure PlatformIO is installed"                 -ForegroundColor Gray
Write-Host "    2. Ask which board you have"                          -ForegroundColor Gray
Write-Host "    3. Find your ESP32 on a USB port"                     -ForegroundColor Gray
Write-Host "    4. Ask which trigger mechanism you wired up"          -ForegroundColor Gray
Write-Host "    5. Ask for your PIN"                                  -ForegroundColor Gray
Write-Host "    6. Optionally tune sleep interval, pulse, device name"-ForegroundColor Gray
Write-Host "    7. Compile and flash the firmware"                    -ForegroundColor Gray
Write-Host "    8. Leave no trace of your PIN on disk"                -ForegroundColor Gray
Write-Host ""

# --- Step 1: PlatformIO ------------------------------------------------------

Write-Step "Step 1/7 - Checking for PlatformIO..."

$pioCmd = Get-Command pio -ErrorAction SilentlyContinue
if (-not $pioCmd) {
    $pioCmd = Get-Command python -ErrorAction SilentlyContinue
    if ($pioCmd) {
        python -m platformio --version 2>&1 | Out-Null
        if ($LASTEXITCODE -eq 0) {
            $pioCmd = "python -m platformio"
        } else {
            $pioCmd = $null
        }
    }
}

if ($pioCmd) {
    Write-OK "PlatformIO found."
} else {
    Write-Host "     PlatformIO not found. Installing via official installer..." -ForegroundColor Yellow
    Write-Host "     (This is a one-time download, please be patient)" -ForegroundColor Yellow
    Write-Host ""

    $python = Get-Command python -ErrorAction SilentlyContinue
    if (-not $python) {
        $python = Get-Command python3 -ErrorAction SilentlyContinue
    }
    if (-not $python) {
        Write-Fail "Python is required but not found. Download it from https://www.python.org/downloads/ (tick 'Add Python to PATH') and run flash.bat again."
        exit 1
    }

    $curl = Get-Command curl -ErrorAction SilentlyContinue
    if (-not $curl) {
        Write-Fail "curl is required but not found. It ships with Windows 10/11 — if missing, update Windows or install manually from https://curl.se/windows/"
        exit 1
    }

    $installerUrl = "https://raw.githubusercontent.com/platformio/platformio-core-installer/develop/get-platformio.py"
    $installerPath = Join-Path $env:TEMP "get-platformio.py"
    curl -fsSL $installerUrl -o $installerPath
    if (-not $?) {
        Write-Fail "Failed to download the PlatformIO installer. Check your internet connection and try again."
        exit 1
    }

    & $python.Source $installerPath
    if (-not $?) {
        Write-Fail "PlatformIO installer failed. See errors above."
        exit 1
    }

    # PlatformIO installs pio into %USERPROFILE%\.platformio\penv\Scripts
    $pioPath = Join-Path $env:USERPROFILE ".platformio\penv\Scripts"
    $env:PATH = "$pioPath;$env:PATH"

    $pioCmd = Get-Command pio -ErrorAction SilentlyContinue
    if (-not $pioCmd) {
        Write-Fail "PlatformIO was installed but 'pio' is still not in PATH. Close this window, open a new terminal, and run flash.bat again."
        exit 1
    }

    Write-OK "PlatformIO installed successfully."
}

function Invoke-Pio {
    param([string[]]$PioArgs)
    $pioExe = Get-Command pio -ErrorAction SilentlyContinue
    if ($pioExe) {
        & pio @PioArgs
    } else {
        & python -m platformio @PioArgs
    }
}

# --- Step 2: Board selection --------------------------------------------------

Write-Step "Step 2/7 - Select your board..."

if ($Board -ne "" -and $supportedBoards.Contains($Board)) {
    Write-OK "Board supplied by caller: $Board ($($supportedBoards[$Board].Label))"
} else {
    if ($Board -ne "") {
        Write-Host "     Unknown board '$Board'. Please pick from the list below." -ForegroundColor Yellow
    }
    Write-Host ""
    $boardKeys = @($supportedBoards.Keys)
    for ($i = 0; $i -lt $boardKeys.Count; $i++) {
        $key = $boardKeys[$i]
        Write-Host "    [$($i+1)] $($supportedBoards[$key].Label)  (env: $key)" -ForegroundColor White
    }
    Write-Host ""
    $boardChoice = Read-Host "  Enter number (default: 1)"
    if ([string]::IsNullOrWhiteSpace($boardChoice)) { $boardChoice = "1" }
    $boardIdx = [int]$boardChoice - 1
    if ($boardIdx -lt 0 -or $boardIdx -ge $boardKeys.Count) {
        Write-Fail "Invalid selection."
        exit 1
    }
    $Board = $boardKeys[$boardIdx]
    Write-OK "Board: $($supportedBoards[$Board].Label)"
}

$defaultPin = $supportedBoards[$Board].Pin

# --- Step 3: Find the ESP32 --------------------------------------------------

Write-Step "Step 3/7 - Looking for ESP32 on USB ports..."

if ($Port -ne "") {
    Write-OK "Using port supplied by caller: $Port"
} else {
    $foundPorts = @()

    foreach ($device in Get-PnpDevice -Class Ports -Status OK -ErrorAction SilentlyContinue) {
        $hwIds = $device.HardwareID
        foreach ($id in $hwIds) {
            foreach ($entry in $esp32VidPids) {
                if ($id -match "VID_$($entry.VID)&PID_$($entry.PID)") {
                    if ($device.FriendlyName -match "\(COM(\d+)\)") {
                        $comPort = "COM$($Matches[1])"
                        $foundPorts += [PSCustomObject]@{
                            Port = $comPort
                            Chip = $entry.Name
                            Name = $device.FriendlyName
                        }
                    }
                }
            }
        }
    }

    if ($foundPorts.Count -eq 0) {
        Write-Host ""
        Write-Host "  No ESP32 detected on any USB port." -ForegroundColor Red
        Write-Host ""
        Write-Host "  Possible causes:"                                                                              -ForegroundColor Yellow
        Write-Host "    - The board is not plugged in"                                                              -ForegroundColor Yellow
        Write-Host "    - The USB cable is charge-only (no data lines)"                                             -ForegroundColor Yellow
        Write-Host "    - The USB driver is not installed"                                                          -ForegroundColor Yellow
        Write-Host ""
        Write-Host "  Driver downloads:"                                                                            -ForegroundColor Yellow
        Write-Host "    CP2102 (Silicon Labs): https://www.silabs.com/developers/usb-to-uart-bridge-vcp-drivers"   -ForegroundColor Gray
        Write-Host "    CH340 / CH341:         https://www.wch-ic.com/downloads/CH341SER_EXE.html"                 -ForegroundColor Gray
        Write-Host ""
        Write-Host "  After installing the driver, unplug and re-plug the board, then run flash.bat again." -ForegroundColor Yellow
        Write-Host ""
        exit 1

    } elseif ($foundPorts.Count -eq 1) {
        $Port = $foundPorts[0].Port
        Write-OK "Found: $($foundPorts[0].Name) on $Port ($($foundPorts[0].Chip))"

    } else {
        Write-Host ""
        Write-Host "  Multiple ESP32-compatible devices found. Pick one:" -ForegroundColor Yellow
        Write-Host ""
        for ($i = 0; $i -lt $foundPorts.Count; $i++) {
            Write-Host "    [$($i+1)] $($foundPorts[$i].Port)  -  $($foundPorts[$i].Name)" -ForegroundColor White
        }
        Write-Host ""
        $choice = Read-Host "  Enter number"
        $idx = [int]$choice - 1
        if ($idx -lt 0 -or $idx -ge $foundPorts.Count) {
            Write-Fail "Invalid selection."
            exit 1
        }
        $Port = $foundPorts[$idx].Port
        Write-OK "Using $Port"
    }
}

# --- Step 4: Trigger mode ----------------------------------------------------

Write-Step "Step 4/7 - Choose your trigger mechanism..."
Write-Host ""
Write-Host "  [1] Transistor          - NPN transistor wired into the fob (soldering required, recommended)" -ForegroundColor White
Write-Host "  [2] Relay active-low    - relay module that triggers on LOW  (older/opto-isolated modules)"   -ForegroundColor White
Write-Host "  [3] Relay active-high   - relay module that triggers on HIGH (most common, e.g. JQC-3FF)"     -ForegroundColor White
Write-Host "  [4] Capacitive pad      - copper tape on a Fingerbot's button (no soldering, renter-friendly)" -ForegroundColor White
Write-Host ""

$triggerChoice = Read-Host "  Enter number (default: 1)"
if ([string]::IsNullOrWhiteSpace($triggerChoice)) { $triggerChoice = "1" }

$triggerMode = switch ($triggerChoice) {
    "1" { "MODE_TRANSISTOR" }
    "2" { "MODE_RELAY"      }
    "3" { "MODE_RELAY_HIGH" }
    "4" { "MODE_CAP_PULSE"  }
    default {
        Write-Fail "Invalid selection. Please run flash.bat again."
        exit 1
    }
}

Write-OK "Trigger mode: $triggerMode"

# --- Step 5: PIN -------------------------------------------------------------

Write-Step "Step 5/7 - Set your PIN..."
Write-Host ""
Write-Host "  This PIN must match the one you enter in the Android app." -ForegroundColor Gray
Write-Host "  It will be compiled into the firmware and never saved to disk." -ForegroundColor Gray
Write-Host ""

$pin = Read-Host "  Enter PIN"

if ([string]::IsNullOrWhiteSpace($pin)) {
    Write-Fail "PIN cannot be empty."
    exit 1
}
if ($pin -eq $placeholder) {
    Write-Fail "You must choose a real PIN, not the placeholder."
    exit 1
}

$pinConfirm = Read-Host "  Confirm PIN"

if ($pin -ne $pinConfirm) {
    Write-Fail "PINs do not match. Please run flash.bat again."
    exit 1
}

Write-OK "PIN confirmed."

# --- Step 6: Advanced options ------------------------------------------------

Write-Step "Step 6/7 - Advanced options (press Enter to keep defaults)..."
Write-Host ""
Write-Host "  These are optional. Hit Enter at each prompt to use the default." -ForegroundColor Gray
Write-Host ""

# Trigger pin
Write-Host "  GPIO pin used to fire the trigger (transistor base / relay coil / capacitive pad)." -ForegroundColor Gray
Write-Host "  Default for $($supportedBoards[$Board].Label): GPIO $defaultPin" -ForegroundColor Gray
$pinInput = Read-Host "  Trigger GPIO (default: $defaultPin)"
if ([string]::IsNullOrWhiteSpace($pinInput)) {
    $triggerPin = $defaultPin
} else {
    $parsed = 0
    if (-not [int]::TryParse($pinInput, [ref]$parsed) -or $parsed -lt 0 -or $parsed -gt 39) {
        Write-Fail "Trigger GPIO must be a number between 0 and 39."
        exit 1
    }
    $triggerPin = $parsed
}
Write-OK "Trigger GPIO: $triggerPin"

# Sleep duration
Write-Host ""
Write-Host "  How long the ESP32 sleeps between BLE advertising windows." -ForegroundColor Gray
Write-Host "  Lower values reduce the wait at the door. 1-2 s is recommended." -ForegroundColor Gray
Write-Host "  Default: 5 s. Valid range: 1-10 s." -ForegroundColor Gray
$sleepInput = Read-Host "  Sleep duration in seconds (default: 5)"
if ([string]::IsNullOrWhiteSpace($sleepInput)) {
    $sleepDuration = 5
} else {
    $parsed = 0
    if (-not [int]::TryParse($sleepInput, [ref]$parsed) -or $parsed -lt 1 -or $parsed -gt 10) {
        Write-Fail "Sleep duration must be a number between 1 and 10."
        exit 1
    }
    $sleepDuration = $parsed
}
Write-OK "Sleep duration: $sleepDuration s"

# Relay pulse duration
Write-Host ""
Write-Host "  How long the ESP32 holds the fob button pressed." -ForegroundColor Gray
Write-Host "  Too short: fob doesn't register and the door stays closed." -ForegroundColor Gray
Write-Host "  Too long: fob may double-trigger if it re-arms quickly." -ForegroundColor Gray
Write-Host "  Default: 500 ms. Try 300 ms for snappy fobs, 800 ms for slow ones. Valid range: 100-20000 ms." -ForegroundColor Gray
$pulseInput = Read-Host "  Pulse duration in ms (default: 500)"
if ([string]::IsNullOrWhiteSpace($pulseInput)) {
    $pulseDuration = 500
} else {
    $parsed = 0
    if (-not [int]::TryParse($pulseInput, [ref]$parsed) -or $parsed -lt 100 -or $parsed -gt 20000) {
        Write-Fail "Pulse duration must be a number between 100 and 20000 ms."
        exit 1
    }
    $pulseDuration = $parsed
}
Write-OK "Pulse duration: $pulseDuration ms"

# Device name
Write-Host ""
Write-Host "  BLE device name — only matters if you have multiple units." -ForegroundColor Gray
Write-Host "  The Android app will scan for this name. Default: Garage-Opener" -ForegroundColor Gray
$deviceNameInput = Read-Host "  Device name (default: Garage-Opener)"
if ([string]::IsNullOrWhiteSpace($deviceNameInput)) {
    $deviceName = "Garage-Opener"
} else {
    if ($deviceNameInput.Length -gt 20) {
        Write-Fail "Device name must be 20 characters or fewer (BLE limit)."
        exit 1
    }
    $deviceName = $deviceNameInput
}
Write-OK "Device name: $deviceName"

# Debug mode
Write-Host ""
Write-Host "  Enable debug output on serial? (y/N)" -ForegroundColor Gray
$debugInput = Read-Host "  Debug mode"
$debugLevel = if ($debugInput -eq "y" -or $debugInput -eq "Y") { 1 } else { 0 }
if ($debugLevel -eq 1) { Write-OK "Debug: ON (connect serial monitor at 115200 baud)" } else { Write-OK "Debug: OFF" }

# --- Step 7: Compile and flash -----------------------------------------------

Write-Step "Step 7/7 - Compiling and flashing..."
Write-Host ""
Write-Host "  Running PlatformIO build + flash. This may take a few minutes on first run..." -ForegroundColor Gray
Write-Host "  (First run downloads the ESP32 toolchain - subsequent runs are much faster)"   -ForegroundColor Gray
Write-Host ""

$ledPin = $supportedBoards[$Board].LedPin
$ledOn  = $supportedBoards[$Board].LedOn

$buildIniFile = Join-Path $PSScriptRoot "platformio.build.ini"
$ledFlagsIni = if ($null -ne $ledPin) { "`n    -DLED_PIN=$ledPin`n    -DLED_ON_LEVEL=$ledOn" } else { "" }
$buildIni = @"
[env]
platform = espressif32
framework = arduino
lib_deps =
    h2zero/NimBLE-Arduino @ ^1.4.2
monitor_speed = 115200

[env:$Board]
board = $($supportedBoards[$Board].PioBoard)
build_flags =
    -DCORE_DEBUG_LEVEL=$debugLevel
    -DTRIGGER_MODE=$triggerMode
    -DTRIGGER_PIN=$triggerPin
    -DUSER_PIN=\`"$pin\`"
    -DDEVICE_NAME=\`"$deviceName\`"
    -DSLEEP_DURATION_S=$sleepDuration
    -DRELAY_PULSE_MS=$pulseDuration$ledFlagsIni

[env:native]
platform = native
test_build_src = false
"@

[System.IO.File]::WriteAllText($buildIniFile, $buildIni, [System.Text.Encoding]::ASCII)

$flashSuccess = $false
Invoke-Pio @("run", "-c", $buildIniFile, "-e", $Board, "--target", "clean")
Invoke-Pio @("run", "-c", $buildIniFile, "-e", $Board, "--target", "upload", "--upload-port", $Port)
if ($LASTEXITCODE -eq 0) { $flashSuccess = $true }
Remove-Item $buildIniFile -ErrorAction SilentlyContinue

# --- Done --------------------------------------------------------------------

Write-Host ""
if ($flashSuccess) {
    Write-Host "  ============================================" -ForegroundColor Green
    Write-Host "     All done! Your ESP32 is ready to use."   -ForegroundColor Green
    Write-Host "  ============================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "  Your PIN was never saved to disk." -ForegroundColor Gray
    Write-Host ""
    Write-Host "  Scan to install the Android app:" -ForegroundColor White
    Write-QR
    Write-Host "  https://play.google.com/store/apps/details?id=com.dunnowsoftware.GarageAAtoESP32" -ForegroundColor Gray
    Write-Host ""
    Write-Host "  Then open the app and enter the same PIN you just flashed." -ForegroundColor Gray
} else {
    Write-Host "  ============================================" -ForegroundColor Red
    Write-Host "     Flash failed. See errors above."          -ForegroundColor Red
    Write-Host "  ============================================" -ForegroundColor Red
    Write-Host ""
    Write-Host "  Your PIN was NOT saved to disk." -ForegroundColor Gray
    Write-Host "  Fix the issue above and run flash.bat again."           -ForegroundColor Gray
}
Write-Host ""
