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

# ─── Header ───────────────────────────────────────────────────────────────────

Write-Host ""
Write-Host "  ============================================" -ForegroundColor White
Write-Host "     GarageAAtoESP32 - Flash Tool" -ForegroundColor White
Write-Host "  ============================================" -ForegroundColor White
Write-Host ""
Write-Host "  This script will:" -ForegroundColor Gray
Write-Host "    1. Make sure PlatformIO is installed" -ForegroundColor Gray
Write-Host "    2. Find your ESP32 on a USB port" -ForegroundColor Gray
Write-Host "    3. Ask for your PIN" -ForegroundColor Gray
Write-Host "    4. Compile and flash the firmware" -ForegroundColor Gray
Write-Host "    5. Leave no trace of your PIN on disk" -ForegroundColor Gray
Write-Host ""

# ─── Step 1: PlatformIO ───────────────────────────────────────────────────────

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
        Write-Fail "winget failed to install PlatformIO. Try running this script as Administrator, or install PlatformIO manually from https://platformio.org/install/cli"
        exit 1
    }

    # Refresh PATH for this session so pio is findable immediately
    $machinePath = [System.Environment]::GetEnvironmentVariable("PATH", "Machine")
    $userPath    = [System.Environment]::GetEnvironmentVariable("PATH", "User")
    $env:PATH    = "$machinePath;$userPath"

    $pioCmd = Get-Command pio -ErrorAction SilentlyContinue
    if (-not $pioCmd) {
        Write-Fail "PlatformIO was installed but 'pio' is still not in PATH. Please close this window, open a new terminal, and run flash.bat again."
        exit 1
    }

    Write-OK "PlatformIO installed successfully."
}

# ─── Step 2: Find the ESP32 ───────────────────────────────────────────────────

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
                    # Extract COMx from the friendly name
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
        Write-Host "  Possible causes:" -ForegroundColor Yellow
        Write-Host "    - The board is not plugged in" -ForegroundColor Yellow
        Write-Host "    - The USB cable is charge-only (no data lines)" -ForegroundColor Yellow
        Write-Host "    - The USB driver is not installed" -ForegroundColor Yellow
        Write-Host ""
        Write-Host "  Driver downloads:" -ForegroundColor Yellow
        Write-Host "    CP2102 (Silicon Labs): https://www.silabs.com/developers/usb-to-uart-bridge-vcp-drivers" -ForegroundColor Gray
        Write-Host "    CH340 / CH341:         https://www.wch-ic.com/downloads/CH341SER_EXE.html" -ForegroundColor Gray
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

# ─── Step 3: PIN ──────────────────────────────────────────────────────────────

Write-Step "Step 3/4 - Set your PIN..."
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

# ─── Step 4: Compile and flash ────────────────────────────────────────────────

Write-Step "Step 4/4 - Compiling and flashing..."
Write-Host ""
Write-Host "  Patching PIN into config.h (temporary)..." -ForegroundColor Gray

$original = Get-Content $configFile -Raw
$patched  = $original -replace "#define USER_PIN\s+`"[^`"]*`"", "#define USER_PIN  `"$pin`""
Set-Content $configFile $patched -NoNewline -Encoding utf8

Write-Host "  Running PlatformIO build + flash. This may take a few minutes on first run..." -ForegroundColor Gray
Write-Host "  (First run downloads the ESP32 toolchain — subsequent runs are much faster)" -ForegroundColor Gray
Write-Host ""

$flashSuccess = $false
try {
    pio run -e esp32c3 --target upload --upload-port $Port
    if ($?) { $flashSuccess = $true }
} finally {
    Write-Host ""
    Write-Host "  Restoring placeholder PIN in config.h..." -ForegroundColor Gray
    $restored = Get-Content $configFile -Raw
    $restored = $restored -replace "#define USER_PIN\s+`"[^`"]*`"", "#define USER_PIN  `"$placeholder`""
    Set-Content $configFile $restored -NoNewline -Encoding utf8
}

# ─── Done ─────────────────────────────────────────────────────────────────────

Write-Host ""
if ($flashSuccess) {
    Write-Host "  ============================================" -ForegroundColor Green
    Write-Host "     All done! Your ESP32 is ready to use." -ForegroundColor Green
    Write-Host "  ============================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "  Your PIN was never saved to disk." -ForegroundColor Gray
    Write-Host "  Open the GarageAAtoESP32 app on your phone and enter the same PIN." -ForegroundColor Gray
} else {
    Write-Host "  ============================================" -ForegroundColor Red
    Write-Host "     Flash failed. See errors above." -ForegroundColor Red
    Write-Host "  ============================================" -ForegroundColor Red
    Write-Host ""
    Write-Host "  Your PIN was NOT saved to disk (placeholder restored)." -ForegroundColor Gray
    Write-Host "  Fix the issue above and run flash.bat again." -ForegroundColor Gray
}
Write-Host ""
