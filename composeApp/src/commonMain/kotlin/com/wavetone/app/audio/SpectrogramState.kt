package com.wavetone.app.audio

import com.wavetone.app.crypto.toHexString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Live UI state fed by the acoustic decoder: spectrum bars + decoded frame hex. */
class SpectrogramState {

    private val _bars = MutableStateFlow(FloatArray(16))
    val bars: StateFlow<FloatArray> = _bars.asStateFlow()

    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening.asStateFlow()

    private val _lastFrameHex = MutableStateFlow("")
    val lastFrameHex: StateFlow<String> = _lastFrameHex.asStateFlow()

    private val _bitstreamLog = MutableStateFlow("")
    val bitstreamLog: StateFlow<String> = _bitstreamLog.asStateFlow()

    fun setListening(value: Boolean) {
        _listening.value = value
        if (!value) _bars.value = FloatArray(16)
    }

    fun updateBars(bars: FloatArray) {
        _bars.value = bars
    }

    fun onFrameDecoded(payload: ByteArray, verifiedName: String?) {
        val hex = (byteArrayOf(FskModem.PREAMBLE_0, FskModem.PREAMBLE_1) + payload).toHexString().uppercase()
        _lastFrameHex.value = hex
        val salt = hex.substring(4, 8)
        val mac = hex.substring(8, 18)
        val crc = hex.substring(18, 20)
        val verdict = verifiedName?.let { "VERIFIED paired device: $it" } ?: "CRC ok, HMAC unverified"
        _bitstreamLog.value = "0xAA 0x7E | SALT=$salt | MAC=$mac | CRC8=$crc -> $verdict"
    }

    fun log(message: String) {
        _bitstreamLog.value = message
    }
}

/**
 * Streaming decoder: accumulates PCM chunks, runs the sliding Goertzel
 * synchronizer, emits CRC-valid payloads. Buffer capped at ~2 s of audio.
 */
class AcousticDecoder(
    private val spectrogram: SpectrogramState,
    private val onPayload: (ByteArray) -> Unit
) {
    private var buffer = ShortArray(0)
    private val maxBufferSamples = FskModem.SAMPLE_RATE * 2

    // Chunks arrive on the AudioRecord thread while simulated bursts are fed
    // from a UI coroutine — buffer state must be mutually exclusive.
    private val lock = com.wavetone.app.crypto.PlatformLock()

    fun onChunk(chunk: ShortArray) = lock.withLock { onChunkLocked(chunk) }

    private fun onChunkLocked(chunk: ShortArray) {
        buffer = if (buffer.isEmpty()) chunk.copyOf() else buffer + chunk
        if (buffer.size > maxBufferSamples) {
            buffer = buffer.copyOfRange(buffer.size - maxBufferSamples, buffer.size)
        }
        spectrogram.updateBars(FskModem.spectrumBars(buffer))
        if (buffer.size >= FskModem.FRAME_TOTAL_SAMPLES) {
            // Energy gate: a BFSK burst peaks near 28000 pre-attenuation and
            // stays far above room noise after speaker->mic coupling. Scanning
            // silent buffers with Goertzel probes is wasted CPU/battery.
            if (peakOfLastFrameWindow() < ENERGY_GATE_PEAK) return
            val frames = FskModem.extractFrames(buffer)
            if (frames.isNotEmpty()) {
                frames.forEach(onPayload)
                buffer = ShortArray(0)
            }
        }
    }

    private fun peakOfLastFrameWindow(): Int {
        val start = maxOf(0, buffer.size - FskModem.FRAME_TOTAL_SAMPLES)
        var peak = 0
        for (i in start until buffer.size) {
            val v = if (buffer[i] < 0) -buffer[i].toInt() else buffer[i].toInt()
            if (v > peak) peak = v
        }
        return peak
    }

    private companion object {
        const val ENERGY_GATE_PEAK = 900
    }

    fun reset() = lock.withLock {
        buffer = ShortArray(0)
    }
}
