# Security Policy

## Reporting a vulnerability

Please report suspected vulnerabilities privately via GitHub's
**Report a vulnerability** button (Security → Advisories) on this repository.
Do not open public issues for exploitable flaws.

## Scope & honest limitations

Wavetone is a research/demo-grade attestation prototype:

- The **OOB channel** (ECDSA P-256 in StrongBox/TEE) is the hardware-backed
  proof. The **acoustic channel** proves possession of a paired shared
  secret (truncated HMAC-SHA256), *not* enclave possession, and is labeled
  as such in the UI.
- Acoustic tokens are valid for ±15 s with an in-memory replay guard;
  they are not a replacement for full signatures where bandwidth allows.
- Android cannot inject audio into a cellular call uplink; the acoustic
  path travels speaker → microphone (co-located devices / speakerphone).
- The contact vault is encrypted at rest (AES-256-GCM, AndroidKeyStore).
  Pairing exchanges secrets out-of-band; protect that exchange as you
  would any pre-shared key.
