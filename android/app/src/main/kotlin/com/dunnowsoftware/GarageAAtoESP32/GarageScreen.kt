package com.dunnowsoftware.GarageAAtoESP32

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.dunnowsoftware.GarageAAtoESP32.R
import com.dunnowsoftware.GarageAAtoESP32.ble.BleScanner
import com.dunnowsoftware.GarageAAtoESP32.ble.GarageBleManager
import com.dunnowsoftware.GarageAAtoESP32.geofence.GeofenceResolution
import com.dunnowsoftware.GarageAAtoESP32.geofence.resolveGeofenceTargets
import com.dunnowsoftware.GarageAAtoESP32.transport.MultiDeviceOpenCoordinator
import com.dunnowsoftware.GarageAAtoESP32.transport.OpenResult
import com.dunnowsoftware.GarageAAtoESP32.transport.WebhookTransport
import com.dunnowsoftware.GarageAAtoESP32.data.DevicePreferences
import com.dunnowsoftware.GarageAAtoESP32.data.GarageDevice
import com.dunnowsoftware.GarageAAtoESP32.data.OpenHistoryEntry
import com.dunnowsoftware.GarageAAtoESP32.data.OpenHistoryStore
import com.dunnowsoftware.GarageAAtoESP32.data.OpenOutcome
import com.dunnowsoftware.GarageAAtoESP32.data.TransportType
import com.dunnowsoftware.GarageAAtoESP32.data.TriggerSource
import com.dunnowsoftware.GarageAAtoESP32.wear.notifyWatchResult
import com.dunnowsoftware.GarageAAtoESP32.wear.notifyWatchSending
import com.dunnowsoftware.GarageAAtoESP32.wear.syncDevicesToWatch

class GarageScreen(carContext: CarContext) : Screen(carContext) {

    companion object {
        // AA runs in the OS's separate car-app process, so PhoneActivity can't see
        // its state changes the way it sees same-process events (e.g. geofence
        // auto-open via LocalBroadcastManager). These use a real, package-scoped
        // Context.sendBroadcast — the same mechanism WearableListenerService relies
        // on to be invoked cross-process — so any AA-triggered open (manual tap,
        // device picker, presence-based auto-open, voice) makes the phone's own UI
        // react (open animation) exactly like a watch- or geofence-triggered open
        // already does, and picks up an AA-side device selection as a side effect
        // of the same event instead of a second, narrower signal.
        const val ACTION_AA_OPEN_SENDING = "com.dunnowsoftware.GarageAAtoESP32.ACTION_AA_OPEN_SENDING"
        const val ACTION_AA_OPEN_SUCCESS = "com.dunnowsoftware.GarageAAtoESP32.ACTION_AA_OPEN_SUCCESS"
        const val ACTION_AA_OPEN_FAILURE = "com.dunnowsoftware.GarageAAtoESP32.ACTION_AA_OPEN_FAILURE"
    }

    // The phone activity and the AA car screen run in different processes
    // (`gearhead:car` for AA), so we can't share an in-memory prefs cache
    // with the phone process. Construct a fresh DevicePreferences on every
    // read so we pick up phone-side edits (re-pair, password change, demo
    // toggle) the next time AA reads them.
    private fun prefs() = DevicePreferences(carContext)
    private var coordinator: MultiDeviceOpenCoordinator? = null
    private val presenceScanner = BleScanner(carContext)

    private enum class UiState { IDLE, CONNECTING, SUCCESS, AUTO_SUCCESS, FAILURE, AUTO_FAILURE }
    private var uiState = UiState.IDLE
    private var failureReason = ""
    private var connectAttempt = 0
    private var lastShownAutoFireAt = 0L
    private var lastShownAutoFailAt = 0L
    private var lastShownSendingAt = 0L

    // Shared debounce with GeofenceBroadcastReceiver: both read/write
    // lastAutoFiredAt in DevicePreferences so cross-process races are absorbed.
    // 10s window (design doc): enough to cover geofence latency, prevents the
    // geofence-then-BLE-in-range double-open that toggles the gate back closed.
    private val autoFireDebounceMs = 10_000L

    // Presence: matches the phone main screen. No entry for an address in
    // lastSeenByAddress means we've never heard from that device this session.
    private val presenceHandler = Handler(Looper.getMainLooper())
    // Per-BLE-address last-seen timestamps, so the 2+ device picker can show each
    // device's own in-range status instead of only ever knowing about whichever
    // one is currently selected. Keyed by MAC; a single-device install only ever
    // has one entry, same effective behavior as before.
    private val lastSeenByAddress = mutableMapOf<String, Long>()
    private var inRange = false
    // Addresses considered in-range as of the last tick, so we can detect which
    // specific device just transitioned into range this tick (rather than only
    // ever knowing about the currently-selected device) and fire that one.
    private var inRangeAddresses: Set<String> = emptySet()
    // At most one BLE presence auto-fire per AA screen session (reset whenever
    // the screen starts, i.e. the user opens/returns to it) — first device
    // detected in range fires once, everything after that in the same session
    // is manual-only. Prevents repeat-fires from a flaky BLE connection
    // dropping and reconnecting while the car is still parked at the door.
    private var hasAutoFiredThisSession = false
    // The set of MACs the running scan was started for. If prefs change (user
    // re-pairs on the phone, or adds/removes a device), we'll notice the
    // divergence on the next tick and restart the scan with the new set.
    private var scanStartedForAddresses: Set<String> = emptySet()
    // Long enough to absorb a couple of missed advertisement cycles without the
    // in-range/out-of-range line flickering, short enough that unplugging a
    // device shows up in a reasonable time rather than staying "in range" for
    // a long stale window.
    private val presenceStaleAfterMs = 8_000L
    private val presenceCheckIntervalMs = 1500L
    private val presenceCheckRunnable = object : Runnable {
        override fun run() {
            // If the set of paired BLE addresses has changed since we started the
            // scan (re-pair on the phone, device added/removed), restart the scan
            // with the new set.
            val currentAddresses = prefs().devices.mapNotNull { it.ble?.address }.toSet()
            if (currentAddresses != scanStartedForAddresses) {
                stopPresenceScan()
                startPresenceScan()
                presenceHandler.postDelayed(this, presenceCheckIntervalMs)
                return
            }
            val now = System.currentTimeMillis()
            val nowInRangeAddresses = lastSeenByAddress
                .filterValues { (now - it) < presenceStaleAfterMs }
                .keys
            // Any BLE address that's newly in range this tick vs. last tick — could be
            // more than one if two devices happen to come into range in the same
            // 1.5s window, but that's rare enough not to warrant its own tie-break.
            val newlyInRange = nowInRangeAddresses - inRangeAddresses
            inRangeAddresses = nowInRangeAddresses

            val selectedAddress = prefs().selectedDevice?.ble?.address
            val nowInRange = selectedAddress != null && selectedAddress in nowInRangeAddresses
            if (nowInRange != inRange) {
                inRange = nowInRange
                if (uiState == UiState.IDLE) invalidate()
            }

            if (newlyInRange.isNotEmpty() && uiState == UiState.IDLE && !hasAutoFiredThisSession) {
                val p = prefs()
                val lastFired = p.lastAutoFiredAt
                val debounceOk = (now - lastFired) > autoFireDebounceMs
                // A BLE device coming into range is a safe auto-fire signal regardless
                // of how many devices are paired — physical RF range is the same
                // "can't accidentally open a door you're nowhere near" guarantee the
                // single-device case already relied on informally. This never applies
                // to webhook devices (no presence concept, never populate
                // lastSeenByAddress in the first place) — only a detected BLE address
                // triggers here, and it's fired as that specific device, not whatever
                // happens to be globally selected. Limited to once per screen session
                // (hasAutoFiredThisSession, reset on screen start) so a flaky BLE
                // connection dropping and reconnecting while still parked can't
                // re-trigger a toggle-based opener and close the door back.
                val toFire = newlyInRange.firstNotNullOfOrNull { addr ->
                    p.devices.firstOrNull { it.ble?.address == addr }
                }
                if (toFire != null && debounceOk && p.isConfigured) {
                    hasAutoFiredThisSession = true
                    triggerOpen(autoFiredDeviceId = toFire.id)
                }
            }
            if (uiState == UiState.IDLE) {
                val now = System.currentTimeMillis()
                val p = prefs()
                val lastSending = p.lastSendingAt
                if (lastSending > lastShownSendingAt && (now - lastSending) < 60_000L) {
                    lastShownSendingAt = lastSending
                    uiState = UiState.CONNECTING
                    invalidate()
                }
            }
            // Show result if a result arrived — works from both IDLE and CONNECTING.
            if (uiState == UiState.IDLE || uiState == UiState.CONNECTING) {
                val now = System.currentTimeMillis()
                val p = prefs()
                val lastAutoFired = p.lastAutoFiredAt
                if (lastAutoFired > lastShownAutoFireAt && (now - lastAutoFired) < 15_000L) {
                    lastShownAutoFireAt = lastAutoFired
                    uiState = UiState.AUTO_SUCCESS
                    invalidate()
                    Handler(Looper.getMainLooper()).postDelayed({
                        carContext.mainExecutor.execute { resetToIdle() }
                    }, 5_000L)
                }
                val lastAutoFailed = p.lastAutoFailedAt
                if (lastAutoFailed > lastShownAutoFailAt && (now - lastAutoFailed) < 15_000L) {
                    lastShownAutoFailAt = lastAutoFailed
                    uiState = UiState.AUTO_FAILURE
                    invalidate()
                    Handler(Looper.getMainLooper()).postDelayed({
                        carContext.mainExecutor.execute { resetToIdle() }
                    }, 5_000L)
                }
            }

            presenceHandler.postDelayed(this, presenceCheckIntervalMs)
        }
    }

    init {
        // Start/stop the presence scan tied to the screen's visibility so we
        // don't burn the BT radio when the AA UI isn't showing this screen.
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                hasAutoFiredThisSession = false
                startPresenceScan()
            }
            override fun onStop(owner: LifecycleOwner) {
                stopPresenceScan()
            }
        })
    }

    private fun startPresenceScan() {
        val p = prefs()
        val addresses = p.devices.mapNotNull { it.ble?.address }.toSet()
        if (addresses.isEmpty() || p.demoMode) return
        try {
            presenceScanner.startPresenceMulti(addresses) { found ->
                lastSeenByAddress[found.address] = System.currentTimeMillis()
            }
            scanStartedForAddresses = addresses
        } catch (_: Throwable) {
            // Permissions / BT off — leave inRange=false so the UI shows the
            // out-of-range hint. Don't crash.
        }
        presenceHandler.removeCallbacks(presenceCheckRunnable)
        presenceHandler.postDelayed(presenceCheckRunnable, presenceCheckIntervalMs)
    }

    private fun stopPresenceScan() {
        presenceScanner.stop()
        presenceHandler.removeCallbacks(presenceCheckRunnable)
        lastSeenByAddress.clear()
        inRange = false
        inRangeAddresses = emptySet()
        scanStartedForAddresses = emptySet()
    }

    // Which device(s) the in-flight/just-finished open actually acted on — set at
    // the top of triggerOpen() so it correctly reflects a resolved multi-device
    // fire ("Main Garage + Side Gate"), not just whatever selectedDeviceId is.
    // Only worth naming once there's more than one device paired to disambiguate —
    // single-device installs keep the plain "Connecting…"/"Opened!" wording.
    private var firingDevices: List<GarageDevice> = emptyList()

    private fun connectingDeviceName(): String? {
        if (prefs().devices.size <= 1) return null
        return firingDevices.takeIf { it.isNotEmpty() }?.joinToString(" + ") { it.name }
    }

    override fun onGetTemplate(): Template {
        val name = connectingDeviceName()
        return when (uiState) {
            UiState.CONNECTING   -> buildLoading(name)
            UiState.SUCCESS      -> buildMessage(
                if (name != null) carContext.getString(R.string.aa_opened_named, name)
                else carContext.getString(R.string.aa_opened),
                false,
            )
            UiState.AUTO_SUCCESS -> buildMessage(
                if (name != null) carContext.getString(R.string.aa_auto_opened_named, name)
                else carContext.getString(R.string.aa_auto_opened),
                false,
            )
            UiState.AUTO_FAILURE -> buildMessage(
                if (name != null) carContext.getString(R.string.aa_auto_failed_named, name)
                else carContext.getString(R.string.aa_auto_failed),
                false,
            )
            UiState.FAILURE      -> buildMessage(
                if (name != null) carContext.getString(R.string.aa_failed_named, name, failureReason)
                else carContext.getString(R.string.aa_failed, failureReason),
                true,
            )
            UiState.IDLE         -> buildMain()
        }
    }

    // ── Templates ─────────────────────────────────────────────────────────────

    private fun buildMain(): Template {
        val p = prefs()
        // 2+ real devices (not demo mode, which has nothing to pick between): check
        // live geofence resolution first. A confident answer (raw presence, no
        // driving gates — see PLAN_multiple_garages.md Phase 3) gets a one-tap
        // screen, same shape as the 1-device case. Empty resolution falls through
        // to the existing row-per-device picker unchanged — that fallback always
        // shows every device (including ungeofenced webhooks), never a narrowed set.
        if (!p.demoMode && p.devices.size > 1) {
            val resolved = (resolveGeofenceTargets(p) as? GeofenceResolution.Resolved)?.devices
            if (!resolved.isNullOrEmpty()) {
                return buildResolvedOneTap(resolved)
            }
            return buildDevicePicker(p)
        }
        return buildSingleDevice(p)
    }

    private fun buildResolvedOneTap(devices: List<GarageDevice>): Template {
        val name = devices.joinToString(" + ") { it.name }
        val openAction = Action.Builder()
            .setTitle(carContext.getString(R.string.aa_open_garage))
            .setOnClickListener { triggerOpen(resolvedDevices = devices) }
            .build()

        val pane = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.aa_garage_door))
                    .addText(name)
                    .build()
            )
            .addAction(openAction)
            .build()

        return PaneTemplate.Builder(pane)
            .setHeader(Header.Builder().setStartHeaderAction(Action.APP_ICON).build())
            .build()
    }

    private fun buildDevicePicker(p: DevicePreferences): Template {
        val itemList = ItemList.Builder()
        p.devices.forEach { device ->
            val isWebhook = device.transport == TransportType.WEBHOOK
            val presenceTag = if (isWebhook) null else {
                val lastSeen = device.ble?.address?.let { lastSeenByAddress[it] } ?: 0L
                val deviceInRange = lastSeen > 0 && (System.currentTimeMillis() - lastSeen) < presenceStaleAfterMs
                if (deviceInRange)
                    carContext.getString(R.string.aa_in_range)
                else
                    carContext.getString(R.string.aa_out_of_range)
            }
            val title = if (presenceTag != null) "$presenceTag - ${device.name}" else device.name
            itemList.addItem(
                Row.Builder()
                    .setTitle(title)
                    .addText(
                        if (isWebhook) carContext.getString(R.string.settings_webhook_badge)
                        else carContext.getString(R.string.settings_paired_badge)
                    )
                    .setOnClickListener {
                        p.selectedDeviceId = device.id
                        syncDevicesToWatch(carContext)
                        triggerOpen()
                    }
                    .build()
            )
        }
        return ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setStartHeaderAction(Action.APP_ICON)
                    .setTitle(carContext.getString(R.string.aa_garage_door))
                    .build()
            )
            .setSingleList(itemList.build())
            .build()
    }

    private fun buildSingleDevice(p: DevicePreferences): Template {
        val configured = p.isConfigured
        val selected = p.selectedDevice
        val deviceName = when {
            p.demoMode        -> carContext.getString(R.string.aa_demo_mode)
            selected != null  -> selected.name
            else              -> carContext.getString(R.string.aa_not_configured)
        }
        // BLE presence (in range/out of range) has no equivalent for webhook
        // targets — there's no proximity signal to report (no BLE, and this
        // screen doesn't check geofence/location), so the tag is omitted
        // entirely rather than showing a permanently-wrong "out of range".
        val presenceTag = when {
            !configured           -> null
            p.demoMode            -> carContext.getString(R.string.aa_in_range)
            selected?.ble == null -> null
            inRange               -> carContext.getString(R.string.aa_in_range)
            else                  -> carContext.getString(R.string.aa_out_of_range)
        }
        // Merge presence + device name on the same body line, with the
        // presence tag in front so the dot is the first thing the eye
        // catches in a glance.
        val deviceLine = if (presenceTag != null) "$presenceTag - $deviceName" else deviceName

        val openAction = Action.Builder()
            .setTitle(carContext.getString(R.string.aa_open_garage))
            .setOnClickListener { if (configured) triggerOpen() }
            .build()

        val pane = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.aa_garage_door))
                    .addText(deviceLine)
                    .build()
            )
            .addAction(openAction)
            .build()

        return PaneTemplate.Builder(pane)
            .setHeader(Header.Builder().setStartHeaderAction(Action.APP_ICON).build())
            .build()
    }

    private fun buildLoading(name: String?): Template {
        // firingDevices may mix BLE and webhook in a multi-device fire; show
        // whichever max-attempts figure applies to the majority transport rather
        // than picking one arbitrarily — cosmetic only, doesn't affect retry logic.
        val anyBle = firingDevices.any { it.transport != TransportType.WEBHOOK }
        val maxAttempts = if (anyBle) GarageBleManager.MAX_ATTEMPTS else WebhookTransport.MAX_ATTEMPTS
        val msg = if (name != null) {
            if (connectAttempt <= 1)
                carContext.getString(R.string.aa_connecting_named, name)
            else
                carContext.getString(R.string.aa_connecting_attempt_named, name, connectAttempt, maxAttempts)
        } else {
            if (connectAttempt <= 1)
                carContext.getString(R.string.aa_connecting)
            else
                carContext.getString(R.string.aa_connecting_attempt, connectAttempt, maxAttempts)
        }
        return MessageTemplate.Builder(msg)
            .setHeader(Header.Builder().setStartHeaderAction(Action.APP_ICON).build())
            .setLoading(true)
            .build()
    }

    private fun buildMessage(text: String, isError: Boolean): Template {
        val template = MessageTemplate.Builder(text)
            .setHeader(Header.Builder().setStartHeaderAction(Action.APP_ICON).build())

        if (isError) {
            template.addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.aa_try_again))
                    .setOnClickListener { triggerOpen() }
                    .build()
            )
        }
        return template.build()
    }

    // ── Voice entry point ─────────────────────────────────────────────────────

    fun onVoiceOpen() {
        if (uiState == UiState.IDLE && prefs().isConfigured) {
            carContext.mainExecutor.execute { triggerOpen() }
        }
    }

    // ── Open trigger ──────────────────────────────────────────────────────────

    /**
     * [autoFiredDeviceId] is set only by BLE-presence auto-fire, which detects a
     * *specific* device coming into range rather than acting on whatever is
     * currently selected. [resolvedDevices] is set only by [buildResolvedOneTap]'s
     * tap — the live geofence-resolved set (1+ devices) for this specific tap.
     * Manual picker taps, voice, and demo mode leave both null and fall through to
     * [DevicePreferences.selectedDevice] as before.
     */
    private fun triggerOpen(autoFiredDeviceId: String? = null, resolvedDevices: List<GarageDevice>? = null) {
        val p = prefs()
        if (p.demoMode) {
            triggerDemo()
            return
        }
        if (autoFiredDeviceId != null && autoFiredDeviceId != p.selectedDeviceId) {
            p.selectedDeviceId = autoFiredDeviceId
            syncDevicesToWatch(carContext)
        }
        val targets = when {
            !resolvedDevices.isNullOrEmpty() -> resolvedDevices
            autoFiredDeviceId != null -> listOfNotNull(p.device(autoFiredDeviceId))
            else -> listOfNotNull(p.selectedDevice)
        }
        if (targets.isEmpty()) return
        firingDevices = targets

        uiState = UiState.CONNECTING
        connectAttempt = 0
        invalidate()
        notifyWatchSending(carContext)
        broadcastToPhone(ACTION_AA_OPEN_SENDING)

        val newCoordinator = MultiDeviceOpenCoordinator(carContext)
        coordinator = newCoordinator
        newCoordinator.open(
            devices = targets,
            trigger = TriggerSource.MANUAL_AA,
            onDeviceAttempt = { _, n ->
                carContext.mainExecutor.execute {
                    connectAttempt = maxOf(connectAttempt, n)
                    invalidate()
                }
            },
            onDeviceResult = { outcome ->
                val deviceOutcome = when {
                    outcome.result is OpenResult.Success -> OpenOutcome.SUCCESS
                    outcome.device.transport == TransportType.WEBHOOK -> OpenOutcome.FAILED_WEBHOOK
                    else -> OpenOutcome.FAILED_BLE
                }
                OpenHistoryStore.append(
                    carContext,
                    OpenHistoryEntry(
                        timestampMs   = System.currentTimeMillis(),
                        deviceAddress = outcome.device.addressKey,
                        deviceName    = outcome.device.name,
                        trigger       = TriggerSource.MANUAL_AA,
                        outcome       = deviceOutcome,
                        detail        = (outcome.result as? OpenResult.Failure)?.reason,
                        deviceId      = outcome.device.id,
                        sessionId     = if (targets.size > 1) newCoordinator.sessionId else null,
                    ),
                )
            },
            onAllComplete = { outcomes ->
                carContext.mainExecutor.execute {
                    val anySuccess = outcomes.any { it.result is OpenResult.Success }
                    if (anySuccess) {
                        prefs().lastAutoFiredAt = System.currentTimeMillis()
                        notifyWatchResult(carContext, true)
                        broadcastToPhone(ACTION_AA_OPEN_SUCCESS)
                        uiState = UiState.SUCCESS
                        invalidate()
                        Handler(Looper.getMainLooper()).postDelayed({
                            carContext.mainExecutor.execute { resetToIdle() }
                        }, 2000)
                    } else {
                        notifyWatchResult(carContext, false)
                        broadcastToPhone(ACTION_AA_OPEN_FAILURE)
                        uiState = UiState.FAILURE
                        failureReason = (outcomes.firstOrNull()?.result as? OpenResult.Failure)?.reason ?: ""
                        invalidate()
                    }
                }
            },
        )
    }

    private fun triggerDemo() {
        uiState = UiState.CONNECTING
        connectAttempt = 1
        invalidate()
        Handler(Looper.getMainLooper()).postDelayed({
            carContext.mainExecutor.execute {
                when (val result = DemoOpener.nextResult()) {
                    is OpenResult.Success -> {
                        uiState = UiState.SUCCESS
                        invalidate()
                        Handler(Looper.getMainLooper()).postDelayed({
                            carContext.mainExecutor.execute { resetToIdle() }
                        }, 2000)
                    }
                    is OpenResult.Failure -> {
                        uiState = UiState.FAILURE
                        failureReason = result.reason
                        invalidate()
                    }
                }
            }
        }, DemoOpener.DELAY_MS)
    }

    private fun resetToIdle() {
        uiState = UiState.IDLE
        invalidate()
    }

    private fun broadcastToPhone(action: String) {
        carContext.sendBroadcast(Intent(action).setPackage(carContext.packageName))
    }
}
