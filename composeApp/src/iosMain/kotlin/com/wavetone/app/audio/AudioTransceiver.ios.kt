package com.wavetone.app.audio

import kotlinx.coroutines.delay

/** iOS companion stub: DSP/verifier UI runs, PCM I/O is wired in the full AVAudioEngine build. */
actual class AudioTransceiver {

    actual suspend fun playPcm(pcm: ShortArray) {
        delay((pcm.size * 1000L) / FskModem.SAMPLE_RATE)
    }

    actual fun startCapture(onChunk: (ShortArray) -> Unit, onError: () -> Unit): Boolean {
        // No microphone capture in the companion stub; never emits fake frames.
        return false
    }

    actual fun stopCapture() {
    }
}
