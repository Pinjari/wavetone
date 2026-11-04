package com.wavetone.app.audio

import com.wavetone.app.crypto.Sha256
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure-Kotlin BFSK modem. 44100 Hz mono PCM16, 352 samples/bit (125 baud),
 * mark (1) = 2400 Hz, space (0) = 1600 Hz. Full frame = 0xAA 0x7E + 8-byte
 * payload (80 bits, 28160 samples, ~640 ms). Zero native dependencies.
 */
object FskModem {

    const val SAMPLE_RATE = 44100
    const val SAMPLES_PER_BIT = 352
    const val FREQ_ZERO = 1600.0
    const val FREQ_ONE = 2400.0
    const val PREAMBLE_0: Byte = 0xAA.toByte()
    const val PREAMBLE_1: Byte = 0x7E
    const val PAYLOAD_SIZE = 8
    const val FRAME_TOTAL_BYTES = 10
    const val FRAME_TOTAL_SAMPLES = FRAME_TOTAL_BYTES * 8 * SAMPLES_PER_BIT

    fun modulateFrame(payload8Bytes: ByteArray): ShortArray {
        require(payload8Bytes.size == PAYLOAD_SIZE) { "Payload must be 8 bytes: 2B salt + 5B MAC + 1B CRC8" }
        val fullFrame = byteArrayOf(PREAMBLE_0, PREAMBLE_1) + payload8Bytes
        val totalBits = fullFrame.size * 8
        val pcm = ShortArray(totalBits * SAMPLES_PER_BIT)

        var sampleIndex = 0
        for (byte in fullFrame) {
            for (bitPos in 7 downTo 0) {
                val bit = (byte.toInt() shr bitPos) and 1
                val freq = if (bit == 1) FREQ_ONE else FREQ_ZERO
                for (n in 0 until SAMPLES_PER_BIT) {
                    val env = when {
                        n < 32 -> 0.5 * (1.0 - cos(PI * n / 32.0))
                        n > SAMPLES_PER_BIT - 32 -> 0.5 * (1.0 - cos(PI * (SAMPLES_PER_BIT - n) / 32.0))
                        else -> 1.0
                    }
                    val angle = 2.0 * PI * freq * n / SAMPLE_RATE
                    pcm[sampleIndex++] = (sin(angle) * env * 28000.0).toInt().toShort()
                }
            }
        }
        return pcm
    }

    fun goertzelMagnitude(samples: ShortArray, offset: Int, length: Int, targetFreq: Double): Double {
        if (offset < 0 || length <= 0 || offset + length > samples.size) return 0.0
        val k = (0.5 + (length * targetFreq) / SAMPLE_RATE).toInt()
        val omega = (2.0 * PI * k) / length
        val coeff = 2.0 * cos(omega)
        var q0 = 0.0
        var q1 = 0.0
        var q2 = 0.0
        for (i in 0 until length) {
            q0 = coeff * q1 - q2 + (samples[offset + i] / 32768.0)
            q2 = q1
            q1 = q0
        }
        return sqrt(q1 * q1 + q2 * q2 - q1 * q2 * coeff)
    }

    /** Decodes one bit at [bitOffsetSamples] by comparing mark/space Goertzel magnitudes. */
    fun decodeBit(samples: ShortArray, bitOffsetSamples: Int): Int {
        val magOne = goertzelMagnitude(samples, bitOffsetSamples, SAMPLES_PER_BIT, FREQ_ONE)
        val magZero = goertzelMagnitude(samples, bitOffsetSamples, SAMPLES_PER_BIT, FREQ_ZERO)
        return if (magOne >= magZero) 1 else 0
    }

    /**
     * Attempts to decode a full frame starting exactly at [startOffset].
     * Returns the 8-byte payload only if preamble matches and CRC-8 is valid.
     */
    fun decodeFrameAt(samples: ShortArray, startOffset: Int): ByteArray? {
        if (startOffset < 0 || startOffset + FRAME_TOTAL_SAMPLES > samples.size) return null
        val bytes = ByteArray(FRAME_TOTAL_BYTES)
        for (byteIdx in 0 until FRAME_TOTAL_BYTES) {
            var value = 0
            for (bitPos in 7 downTo 0) {
                val bitOffset = startOffset + (byteIdx * 8 + (7 - bitPos)) * SAMPLES_PER_BIT
                value = (value shl 1) or decodeBit(samples, bitOffset)
            }
            bytes[byteIdx] = value.toByte()
        }
        if (bytes[0] != PREAMBLE_0 || bytes[1] != PREAMBLE_1) return null
        val payload = bytes.copyOfRange(2, FRAME_TOTAL_BYTES)
        if (Sha256.crc8(payload, 0, 7) != payload[7]) return null
        return payload
    }

    /**
     * Sliding-window frame synchronizer: steps in half-bit increments (176
     * samples), hunting the 0xAA7E preamble. Returns every valid payload found.
     */
    fun extractFrames(samples: ShortArray): List<ByteArray> {
        val results = mutableListOf<ByteArray>()
        if (samples.size < FRAME_TOTAL_SAMPLES) return results
        var offset = 0
        val step = SAMPLES_PER_BIT / 2
        while (offset + FRAME_TOTAL_SAMPLES <= samples.size) {
            // Cheap preamble probe: decode first 16 bits only.
            var firstByte = 0
            for (bitPos in 7 downTo 0) {
                firstByte = (firstByte shl 1) or decodeBit(samples, offset + (7 - bitPos) * SAMPLES_PER_BIT)
            }
            if (firstByte == (PREAMBLE_0.toInt() and 0xFF)) {
                val payload = decodeFrameAt(samples, offset)
                if (payload != null) {
                    results.add(payload)
                    offset += FRAME_TOTAL_SAMPLES
                    continue
                }
            }
            offset += step
        }
        return results
    }

    /** 16-bin spectrogram magnitudes spanning 1200–3000 Hz for the Compose Canvas. */
    val SPECTRUM_FREQS: DoubleArray = DoubleArray(16) { i -> 1200.0 + i * 120.0 }

    fun spectrumBars(samples: ShortArray, windowSize: Int = 1024): FloatArray {
        if (samples.size < windowSize) return FloatArray(SPECTRUM_FREQS.size)
        val start = samples.size - windowSize
        val mags = DoubleArray(SPECTRUM_FREQS.size) { i ->
            goertzelMagnitude(samples, start, windowSize, SPECTRUM_FREQS[i])
        }
        val max = mags.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
        return FloatArray(mags.size) { i -> (mags[i] / max).toFloat().coerceIn(0f, 1f) }
    }
}
