package com.wavetone.app.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide reactive security session. Written by the UI controller and
 * CallMonitorService; read by ScamLockAccessibilityService and the HUD pill.
 * Android services run in the same process by default, so this singleton is shared.
 */
object CallSecuritySession {

    private val _status = MutableStateFlow(VerificationStatus.IDLE)
    val status: StateFlow<VerificationStatus> = _status.asStateFlow()

    private val _session = MutableStateFlow(CallSession())
    val session: StateFlow<CallSession> = _session.asStateFlow()

    private val _sessionActive = MutableStateFlow(false)
    val sessionActive: StateFlow<Boolean> = _sessionActive.asStateFlow()

    private val _lockdownOverridden = MutableStateFlow(false)
    val lockdownOverridden: StateFlow<Boolean> = _lockdownOverridden.asStateFlow()

    private val _lastLatencyMs = MutableStateFlow(-1L)
    val lastLatencyMs: StateFlow<Long> = _lastLatencyMs.asStateFlow()

    private val _lastDetail = MutableStateFlow("")
    val lastDetail: StateFlow<String> = _lastDetail.asStateFlow()

    /** True while at least one ScamLockAccessibilityService instance is bound. */
    private val _shieldConnected = MutableStateFlow(false)
    val shieldConnected: StateFlow<Boolean> = _shieldConnected.asStateFlow()

    // Owner-token tracking: Android rebinds accessibility services in
    // overlapping instance cycles, and a stale instance's disconnect must
    // not clear the flag owned by the live instance (a counter desyncs).
    private var shieldOwner: Any? = null

    fun shieldInstanceConnected(owner: Any) {
        shieldOwner = owner
        _shieldConnected.value = true
    }

    fun shieldInstanceDisconnected(owner: Any) {
        if (shieldOwner === owner) {
            shieldOwner = null
            _shieldConnected.value = false
        }
    }

    /** Monotonic counter: platform layer bumps it when the mic must be released NOW (call started / app backgrounded). */
    private val _stopListeningRequests = MutableStateFlow(0L)
    val stopListeningRequests: StateFlow<Long> = _stopListeningRequests.asStateFlow()

    fun requestStopListening() {
        _stopListeningRequests.value += 1
    }

    // ---- Durable session (survives process death / reboot mid-call) ----
    // Without this, Android killing the app process during a long call would
    // silently disarm the Scam-Breaker shield. Entry points (activity and
    // both services) attach the platform store; every mutation persists.

    private var store: KeyValueStore? = null

    fun attachStore(store: KeyValueStore) {
        this.store = store
        restore()
    }

    private fun restore() {
        val raw = store?.get(KEY_SESSION) ?: return
        val parts = raw.split(SEP)
        if (parts.size < 8) return
        val status = try {
            VerificationStatus.valueOf(parts[0])
        } catch (e: Exception) {
            return
        }
        _status.value = status
        _sessionActive.value = parts[1] == "1"
        _lockdownOverridden.value = parts[2] == "1"
        _session.value = CallSession(parts[3], parts[4], parts[5] == "1")
        _lastDetail.value = parts[6]
        _lastLatencyMs.value = parts[7].toLongOrNull() ?: -1L
    }

    private fun persist() {
        val s = store ?: return
        val c = _session.value
        s.put(
            KEY_SESSION,
            listOf(
                _status.value.name,
                if (_sessionActive.value) "1" else "0",
                if (_lockdownOverridden.value) "1" else "0",
                c.callerPhone, c.callerName, if (c.isSimulated) "1" else "0",
                _lastDetail.value, _lastLatencyMs.value.toString()
            ).joinToString(SEP)
        )
    }

    private const val KEY_SESSION = "wavetone_call_session"
    private const val SEP = ""

    val isLockdownStatus: Boolean
        get() = _status.value == VerificationStatus.FAILED_SPOOF_DETECTED ||
            _status.value == VerificationStatus.UNPAIRED_CALLER_LOCKDOWN

    fun shouldLockdown(): Boolean = _sessionActive.value && isLockdownStatus && !_lockdownOverridden.value

    fun beginSession(callerPhone: String, callerName: String, simulated: Boolean, status: VerificationStatus) {
        _session.value = CallSession(callerPhone, callerName, simulated)
        _sessionActive.value = true
        _lockdownOverridden.value = false
        _status.value = status
        persist()
    }

    fun setStatus(status: VerificationStatus, detail: String = "", latencyMs: Long = -1L) {
        _status.value = status
        _lastDetail.value = detail
        if (latencyMs >= 0) _lastLatencyMs.value = latencyMs
        persist()
    }

    fun overrideLockdown() {
        _lockdownOverridden.value = true
        persist()
    }

    fun endSession() {
        _status.value = VerificationStatus.IDLE
        _session.value = CallSession()
        _sessionActive.value = false
        _lockdownOverridden.value = false
        _lastDetail.value = ""
        _lastLatencyMs.value = -1L
        persist()
    }
}
