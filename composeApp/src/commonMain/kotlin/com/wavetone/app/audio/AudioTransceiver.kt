package com.wavetone.app.audio

/** Platform PCM I/O: 44100 Hz, mono, 16-bit. DSP stays in common [FskModem]. */
expect class AudioTransceiver() {
    /** Plays PCM, suspending until playback finishes. */
    suspend fun playPcm(pcm: ShortArray)

    /**
     * Starts capture; [onChunk] receives PCM chunks on a background thread.
     * Returns false if the mic could not be opened (no permission, busy, or an
     * active phone call owns the microphone). [onError] fires if capture dies
     * mid-stream (e.g. a call seized the mic) so the UI never shows a stale
     * "listening" state.
     */
    fun startCapture(onChunk: (ShortArray) -> Unit, onError: () -> Unit = {}): Boolean

    fun stopCapture()
}
