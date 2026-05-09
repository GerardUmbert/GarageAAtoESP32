# Changelog

All notable changes to this project will be documented in this file.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
Versions follow [Semantic Versioning](https://semver.org/).

## [1.0.0] - 2025-05-09

### Added
- ESP32 firmware: BLE service with nonce/command/status characteristics
- HMAC-SHA256 challenge-response authentication (PIN never sent over air)
- Relay and transistor wiring support (configurable via `RELAY_ACTIVE_LOW` in `config.h`)
- Deep sleep between advertising windows for power efficiency
- Android Auto app: single "Open Garage" button on car screen
- One-time phone setup via SettingsActivity (PIN entry + BLE device scan)
- Automatic retry on connection failure (up to 3 attempts, 2 s apart)
- Wiring diagram, power budget, and provisioning guide
- CC BY-NC-SA 4.0 license
