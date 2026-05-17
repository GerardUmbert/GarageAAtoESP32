param(
    [string]$Port = ""
)

$ErrorActionPreference = "Stop"
$configFile = "$PSScriptRoot\include\config.h"
$placeholder = "change-me-before-flashing"

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

    $full   = [char]0x2588  # both rows filled
    $top    = [char]0x2580  # top row only
    $bottom = [char]0x2584  # bottom row only

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
Write-Host "    2. Find your ESP32 on a USB port"                     -ForegroundColor Gray
Write-Host "    3. Ask which trigger mechanism you wired up"          -ForegroundColor Gray
Write-Host "    4. Ask for your PIN"                                  -ForegroundColor Gray
Write-Host "    5. Compile and flash the firmware"                    -ForegroundColor Gray
Write-Host "    6. Leave no trace of your PIN on disk"                -ForegroundColor Gray
Write-Host ""

# --- Step 1: PlatformIO ------------------------------------------------------

Write-Step "Step 1/4 - Checking for PlatformIO..."

$pioCmd = Get-Command pio -ErrorAction SilentlyContinue
if ($pioCmd) {
    Write-OK "PlatformIO already installed at $($pioCmd.Source)"
} else {
    Write-Host "     PlatformIO not found. Installing via winget..." -ForegroundColor Yellow
    Write-Host "     (This is a one-time download of ~300 MB, please be patient)" -ForegroundColor Yellow
    Write-Host ""

    $winget = Get-Command winget -ErrorAction SilentlyContinue
    if (-not $winget) {
        Write-Fail "winget not found. Please install PlatformIO manually from https://platformio.org/install/cli and re-run this script."
        exit 1
    }

    winget install --id platformio.platformio -e --accept-package-agreements --accept-source-agreements
    if (-not $?) {
        Write-Fail "winget failed to install PlatformIO. Try running as Administrator, or install manually from https://platformio.org/install/cli"
        exit 1
    }

    $machinePath = [System.Environment]::GetEnvironmentVariable("PATH", "Machine")
    $userPath    = [System.Environment]::GetEnvironmentVariable("PATH", "User")
    $env:PATH    = "$machinePath;$userPath"

    $pioCmd = Get-Command pio -ErrorAction SilentlyContinue
    if (-not $pioCmd) {
        Write-Fail "PlatformIO was installed but 'pio' is still not in PATH. Close this window, open a new terminal, and run flash.bat again."
        exit 1
    }

    Write-OK "PlatformIO installed successfully."
}

# --- Step 2: Find the ESP32 --------------------------------------------------

Write-Step "Step 2/4 - Looking for ESP32 on USB ports..."

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

# --- Step 3: Trigger mode ----------------------------------------------------

Write-Step "Step 3/5 - Choose your trigger mechanism..."
Write-Host ""
Write-Host "  [1] Transistor     - NPN transistor wired into the fob (soldering required, recommended)" -ForegroundColor White
Write-Host "  [2] Relay module   - relay module wired into the fob (soldering required)"                -ForegroundColor White
Write-Host "  [3] Capacitive pad - copper tape on a Fingerbot's button (no soldering, renter-friendly)" -ForegroundColor White
Write-Host ""

$triggerChoice = Read-Host "  Enter number (default: 1)"
if ([string]::IsNullOrWhiteSpace($triggerChoice)) { $triggerChoice = "1" }

$triggerMode = switch ($triggerChoice) {
    "1" { "MODE_TRANSISTOR" }
    "2" { "MODE_RELAY"      }
    "3" { "MODE_CAP_PULSE"  }
    default {
        Write-Fail "Invalid selection. Please run flash.bat again."
        exit 1
    }
}

Write-OK "Trigger mode: $triggerMode"

# --- Step 4: PIN -------------------------------------------------------------

Write-Step "Step 4/5 - Set your PIN..."
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

# --- Step 5: Compile and flash -----------------------------------------------

Write-Step "Step 5/5 - Compiling and flashing..."
Write-Host ""
Write-Host "  Patching config.h (temporary)..." -ForegroundColor Gray

$original = Get-Content $configFile -Raw
$patched  = $original -replace "#define USER_PIN\s+`"[^`"]*`"", "#define USER_PIN  `"$pin`""
$patched  = $patched  -replace "#define TRIGGER_MODE\s+\w+",     "#define TRIGGER_MODE       $triggerMode"
Set-Content $configFile $patched -NoNewline -Encoding utf8

Write-Host "  Running PlatformIO build + flash. This may take a few minutes on first run..." -ForegroundColor Gray
Write-Host "  (First run downloads the ESP32 toolchain - subsequent runs are much faster)"   -ForegroundColor Gray
Write-Host ""

$flashSuccess = $false
try {
    pio run -e esp32c3 --target upload --upload-port $Port
    if ($?) { $flashSuccess = $true }
} finally {
    Write-Host ""
    Write-Host "  Restoring config.h..." -ForegroundColor Gray
    $restored = Get-Content $configFile -Raw
    $restored = $restored -replace "#define USER_PIN\s+`"[^`"]*`"", "#define USER_PIN  `"$placeholder`""
    $restored = $restored -replace "#define TRIGGER_MODE\s+\w+",    "#define TRIGGER_MODE       MODE_TRANSISTOR"
    Set-Content $configFile $restored -NoNewline -Encoding utf8
}

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
    Write-Host "  Your PIN was NOT saved to disk (placeholder restored)." -ForegroundColor Gray
    Write-Host "  Fix the issue above and run flash.bat again."           -ForegroundColor Gray
}
Write-Host ""
