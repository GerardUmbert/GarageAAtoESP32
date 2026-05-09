param(
    [string]$Port = ""
)

$ErrorActionPreference = "Stop"
$configFile = "$PSScriptRoot\include\config.h"
$placeholder = "change-me-before-flashing"

Write-Host ""
Write-Host "========================================"
Write-Host "   GarageAAtoESP32 - Flash Tool"
Write-Host "========================================"
Write-Host ""

# Ask for PIN
$pin = Read-Host "Enter your PIN (will not appear in the repo)"
if ([string]::IsNullOrWhiteSpace($pin)) {
    Write-Host "ERROR: PIN cannot be empty." -ForegroundColor Red
    exit 1
}
if ($pin -eq $placeholder) {
    Write-Host "ERROR: You must choose a real PIN, not the placeholder." -ForegroundColor Red
    exit 1
}

# Patch config.h
Write-Host ""
Write-Host "Patching config.h..." -ForegroundColor Cyan
$original = Get-Content $configFile -Raw
$patched  = $original -replace "#define USER_PIN\s+`"[^`"]*`"", "#define USER_PIN  `"$pin`""
Set-Content $configFile $patched -NoNewline

# Build and flash
Write-Host "Building and flashing..." -ForegroundColor Cyan
Write-Host ""

try {
    if ($Port -ne "") {
        pio run -e esp32 --target upload --upload-port $Port
    } else {
        pio run -e esp32 --target upload
    }
} finally {
    # Always restore placeholder so the PIN never stays in the file
    Write-Host ""
    Write-Host "Restoring placeholder PIN in config.h..." -ForegroundColor Cyan
    $restored = Get-Content $configFile -Raw
    $restored = $restored -replace "#define USER_PIN\s+`"[^`"]*`"", "#define USER_PIN  `"$placeholder`""
    Set-Content $configFile $restored -NoNewline
}

Write-Host ""
Write-Host "Done! PIN has been restored to placeholder in config.h." -ForegroundColor Green
Write-Host "Your PIN was never saved to disk permanently." -ForegroundColor Green
Write-Host ""
