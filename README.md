# Wavetone

[![Build & Test](https://github.com/Pinjari/wavetone/actions/workflows/build.yml/badge.svg)](https://github.com/Pinjari/wavetone/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

**Who is really on this call?** Voice can be cloned in seconds; a hardware
key cannot. Wavetone proves caller identity *cryptographically* during a
call, over two independent channels, and locks payments when proof fails.

📺 **Demo:** [`Wavetone_Demo.mp4`](Wavetone_Demo.mp4) (87 s, narrated) —
hardware verification, live acoustic decode, a real incoming scam call,
and the Scam-Breaker shield, end to end.

## How it works

1. **Out-of-band (hardware proof).** ECDSA P-256 challenge–response signed
   inside Android **StrongBox/TEE** (iOS Secure Enclave in the full build),
   relayed over a Ktor WebSocket. UI label: `HARDWARE VERIFIED`.
2. **In-band acoustic (paired-device proof).** A pure-Kotlin BFSK modem
   (1600/2400 Hz, 352 samples/bit, ~640 ms burst, Goertzel demodulator,
   CRC-8 + 40-bit truncated HMAC-SHA256, ±15 s window, replay guard) —
   zero native DSP libraries. UI label: `PAIRED DEVICE VERIFIED`. This
   proves possession of the paired shared secret, *not* enclave
   possession, and is labeled honestly as such.
3. **Scam-Breaker (OS-layer protection).** A `CallScreeningService` seeds
   a call session; if attestation fails or the caller is unpaired, an
   `AccessibilityService` covers the actionable area of UPI/banking/
   remote-access apps (or any window advertising "Pay ₹" / "Enter UPI
   PIN") with a lockdown shield (`TYPE_ACCESSIBILITY_OVERLAY` — no
   draw-over-apps permission needed) until the call ends, the user ends
   it from the shield, or a 3-second override is held.

## Repository layout

```text
composeApp/src/
├── commonMain/     # protocol, pure-Kotlin crypto + DSP, CMP UI, demo console
├── commonTest/     # 35 tests: NIST/RFC vectors, handshake windows/replay, modem loopback
├── androidMain/    # KeyStore signer, AudioTrack/Record, screening + accessibility services
├── androidInstrumentedTest/  # 6 on-device tests (StrongBox, over-the-air audio, vault)
└── iosMain/        # companion target (compiles; enclave signing not faked)
```

## Build & test

```bash
./gradlew :composeApp:testDebugUnitTest        # 35 unit tests
./gradlew :composeApp:lintDebug                # must be 0 errors
./gradlew :composeApp:assembleDebug            # debug APK
./gradlew :composeApp:assembleRelease          # R8-minified APK (~2.4 MB)
./gradlew :composeApp:connectedDebugAndroidTest # 6 instrumented tests (real device)
```

Verified on real hardware (Pixel 9 Pro XL): StrongBox key generation
reported as `StrongBox (discrete secure element)`, ECDSA sign/verify with
tamper rejection, OOB protocol verification with the enclave key, true
over-the-air speaker→microphone BFSK decode (full and 25 % volume),
replay rejection, and vault ciphertext-at-rest. The audio instrumented
tests are hardware-in-the-loop: they skip on emulators (no microphone)
and when the burst is not physically audible at the mic (phone occluded,
media volume near zero, or another app's capture silencing/distorting
the stream — all observed live with consumer recorder apps). Run them
with the phone stationary, media volume up, and no other recorder
active; the decode assertions themselves never relax.

Toolchain (recorded in `gradle/libs.versions.toml`): Gradle 8.13,
AGP 8.9.2, Kotlin 2.1.20, Compose Multiplatform 1.8.0, Ktor 3.1.1 —
the latest mutually compatible set on the classic `androidTarget` DSL.

Release signing is env-based, never hardcoded:
`WAVETONE_KEYSTORE`, `WAVETONE_KEYSTORE_PASSWORD`, `WAVETONE_KEY_ALIAS`,
`WAVETONE_KEY_PASSWORD`. Without them the release APK stays unsigned.

## Device setup (one-time)

1. **Settings → Accessibility → Installed apps → Wavetone → enable**
   (the Scam-Breaker shield; the dashboard shows its live ON/OFF state
   with a shortcut here).
2. **Phone → Settings → Call screening → select Wavetone**
   (so real incoming numbers seed attestation sessions).
3. Grant **Microphone** + **Phone** when asked — requested once, never
   nagged; every feature degrades with an explanation, not a crash.

## Permissions & call-audio safety

| Permission | Used for | If denied |
|---|---|---|
| `RECORD_AUDIO` | acoustic listener | Listen reports unavailable; nothing fakes "listening" |
| Call Screening role | seed sessions from real calls | screening stays inert; simulations still work |
| `READ_PHONE_STATE` | hang-up auto-clear | auto-granted with the screening role; sessions end manually |
| `ANSWER_PHONE_CALLS` | shield's "End Call & Unlock" | shield still unlocks the local session |
| `SYSTEM_ALERT_WINDOW` | floating status pill only | no pill; shield is permission-free |
| Accessibility | Scam-Breaker shield | dashboard flags it OFF with a settings shortcut |

The microphone is treated as call-critical: capture **refuses to start
during a phone call**, auto-releases the instant a call starts or the app
is backgrounded, and a mid-stream capture seizure surfaces as
"Microphone stopped" instead of a stale listening state.

## Stage demo (offline, one device)

Open Wavetone → **Create Demo Peer Contact**, then:

1. **Run OOB Hardware Handshake (ECDSA)** → green `HARDWARE VERIFIED`
   (real enclave sign + verify; loopback relay with 80 ms simulated
   latency — point `OobRelayClient.DEFAULT_RELAY_URL` at a real `wss://`
   relay for two-device OOB; loopback deliberately refuses real peers,
   since it can only sign as this device).
2. **Listen & Verify** → **Simulate Peer Burst** → green
   `PAIRED DEVICE VERIFIED` with live spectrogram + bitstream decode.
3. **Simulate Deepfake Attack Call** → red `SPOOFED CALL`, lockdown armed.
4. **Launch Mock UPI Payment** → shield slams over **Pay ₹15,000**;
   dismiss via **End Call & Unlock** or the 3-second override.

Two-device acoustic pairing: on both phones open **Pairing / Vault**,
generate ONE shared secret on one phone and paste the *same* secret on
both, swap public keys, then Emit on one phone while the other Listens.

## Production notes

- Contact vault (incl. shared secrets) is **AES-256-GCM encrypted at
  rest** (AndroidKeyStore key) and excluded from cloud backup/device
  transfer; early plaintext values migrate transparently.
- The call session is **persisted** in the same encrypted store —
  process death or reinstall mid-call no longer disarms the shield.
- Real (screened) calls **auto-clear on hang-up**; verifying mid-call
  preserves the session's real-call origin so auto-clear keeps working.
- Shield copy is status-aware (`UNPAIRED CALLER` vs `HANDSHAKE FAILED`);
  its End Call button terminates the telecom call when permitted.
- Screening always responds to telecom, even on internal errors —
  a Wavetone fault can never stall an incoming call.

## Honest scope

- Android cannot inject audio into a cellular uplink; the acoustic
  channel travels speaker → microphone (co-located devices/speakerphone).
- `CallScreeningService` covers telephony calls, not WhatsApp/VoIP audio;
  VoIP callers arrive as unpaired unless paired manually.
- iOS compiles as a companion target; Secure Enclave signing reports
  unavailable until the CryptoKit Swift bridge lands — never faked.

## License

[MIT](LICENSE) · Security reports: see [SECURITY.md](SECURITY.md).
