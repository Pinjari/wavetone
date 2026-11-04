# Wavetone

Dual-channel call attestation for Android (Compose Multiplatform):

1. **OOB channel (hardware):** ECDSA P-256 challenge-response signed inside
   Android StrongBox/TEE (iOS Secure Enclave in the full build), relayed over
   a Ktor WebSocket. Result label: `HARDWARE VERIFIED`.
2. **In-band acoustic channel (paired device):** pure-Kotlin BFSK modem
   (1600/2400 Hz, 352 samples/bit, ~640 ms burst, Goertzel demodulator,
   CRC-8 + 40-bit truncated HMAC-SHA256, ±15 s window, replay guard).
   Result label: `PAIRED DEVICE VERIFIED`. This proves possession of the
   paired shared secret — not enclave possession — and is labeled as such.
3. **Scam-Breaker:** an `AccessibilityService` that, during a FAILED/UNPAIRED
   call session, covers the bottom 65% of UPI/banking/remote-access apps
   (or any window showing "Pay ₹" / "Enter UPI PIN") with a crimson shield
   (`TYPE_ACCESSIBILITY_OVERLAY`, no draw-over-apps permission needed).

## Build

```bash
./gradlew :composeApp:testDebugUnitTest   # crypto vectors + FSK loopback + vault/session (27 tests)
./gradlew :composeApp:lintDebug           # must be 0 errors
./gradlew :composeApp:assembleDebug       # debug APK
./gradlew :composeApp:assembleRelease     # R8-minified APK (~2.4 MB)
```

Release signing (optional, env-based — no secrets in the repo):
`WAVETONE_KEYSTORE`, `WAVETONE_KEYSTORE_PASSWORD`, `WAVETONE_KEY_ALIAS`,
`WAVETONE_KEY_PASSWORD`. Without them the release APK stays unsigned.

Production notes:

- The contact vault (incl. shared secrets) is encrypted at rest on Android
  with AES-256-GCM, key in AndroidKeyStore; vault prefs are also excluded
  from cloud backup / device transfer. Plaintext values from early builds
  migrate transparently.
- The call session is **persisted** (same encrypted store): process death
  or reinstall mid-call no longer disarms the shield; services restore it
  on bind.
- When a real (screened) call returns to idle, the session auto-clears —
  the shield disarms itself after hang-up. Verifying mid-call preserves
  the session's real-call origin so auto-clear keeps working.
- Shield copy is status-aware ("UNPAIRED CALLER" vs "HANDSHAKE FAILED"),
  and the shield's **End Call & Unlock** actually terminates the telecom
  call (with `ANSWER_PHONE_CALLS` granted).

Verified on real hardware (Pixel 9 Pro XL, instrumented tests in
`androidInstrumentedTest`): StrongBox key generation reported as
`StrongBox (discrete secure element)`, ECDSA sign/verify + tamper
rejection, OOB protocol verification with the enclave key, and a true
over-the-air speaker→microphone BFSK loopback decode. Run them with
`./gradlew :composeApp:connectedDebugAndroidTest` (audio test skips on
emulators, which have no microphone).
- The dashboard shows the live **Scam-Breaker shield** state
  (Accessibility service ON/OFF) with a shortcut to system settings —
  Android revokes accessibility enablement after a force-stop, and the app
  now surfaces that instead of failing silently.
- OOB loopback deliberately refuses to run for real peers: it signs with
  this device's key, so it can only verify the Demo Peer (self-key). Real
  peers answer through the relay; on stage, verify them acoustically.

Toolchain recorded in `gradle/libs.versions.toml` (Gradle 8.13, AGP 8.9.2,
Kotlin 2.1.20, Compose Multiplatform 1.8.0, Ktor 3.1.1). The spec's newer
pins (CMP 1.12/Kotlin 2.4) require AGP 9's new KMP plugin; this stack is the
latest mutually compatible set and keeps the classic `androidTarget` DSL.

## One-time device setup (for the Scam-Breaker demo)

1. Settings → Accessibility → Installed apps → **Wavetone** → enable.
2. (Real calls) Phone app → Settings → Caller ID & spam / call screening →
   select Wavetone as the call screening app, so `CallMonitorService` seeds
   sessions for real incoming numbers.
3. Grant Microphone + Phone permissions when prompted (needed for the
   acoustic listener and "End Call & Unlock").

## Stage demo script (works fully offline, one device)

Open Wavetone → **Create Demo Peer Contact**, then:

1. **Run OOB Hardware Handshake (ECDSA)** → green `HARDWARE VERIFIED`
   (real StrongBox/TEE sign + verify; loopback relay with 80 ms simulated
   latency — point `OobRelayClient.DEFAULT_RELAY_URL` at a real `wss://`
   relay for two-device OOB).
2. **Listen & Verify Acoustic Burst** → **Simulate Peer Burst** → green
   `PAIRED DEVICE VERIFIED`; watch the 1600/2400 Hz bars and the
   `AA 7E | SALT | MAC | CRC8` bitstream decode live.
3. **Simulate Deepfake Attack Call** → red `SPOOFED CALL`.
4. **Launch Mock UPI Payment** → the Accessibility shield slams over
   **Pay ₹15,000**. Dismiss via **End Call & Unlock** (resets session) or
   **Hold 3 Seconds to Override** (session stays locked).

Two-device acoustic demo: on both phones open **Pairing / Vault**, set my
number, generate ONE shared secret and paste the *same* secret on both
phones, swap public keys, then Emit on one phone while the other Listens.

## Honest scope notes

- Android cannot inject audio into a cellular call uplink; the acoustic
  channel travels speaker → microphone (speakerphone / nearby devices).
- `CallScreeningService` covers telephony calls, not WhatsApp/VoIP in-call
  audio; VoIP numbers arrive as unpaired unless paired manually.
- iOS target compiles; Secure Enclave signing is deliberately not faked —
  it reports unavailable until the CryptoKit Swift bridge is added.
