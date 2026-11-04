package com.wavetone.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

actual class AudioTransceiver {

    private var record: AudioRecord? = null
    private var captureJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    actual suspend fun playPcm(pcm: ShortArray) = withContext(Dispatchers.IO) {
        val byteCount = pcm.size * 2
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(FskModem.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(byteCount.coerceAtLeast(AudioTrack.getMinBufferSize(FskModem.SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        try {
            track.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
            track.play()
            val durationMs = (pcm.size * 1000L) / FskModem.SAMPLE_RATE
            Thread.sleep(durationMs + 120)
        } finally {
            try {
                track.stop()
            } catch (e: Exception) {
                // Track already stopped.
            }
            track.release()
        }
    }

    actual fun startCapture(onChunk: (ShortArray) -> Unit, onError: () -> Unit): Boolean {
        stopCapture()
        val ctx = com.wavetone.app.AndroidContext.appContext ?: return false
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                ctx, android.Manifest.permission.RECORD_AUDIO
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return false
        // NEVER contend with a live phone call for the microphone: concurrent
        // capture during MODE_IN_CALL/IN_COMMUNICATION degrades call audio
        // (mic path contention) and cannot hear the far party anyway.
        val audioManager = ctx.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
        val mode = audioManager?.mode ?: android.media.AudioManager.MODE_NORMAL
        if (mode == android.media.AudioManager.MODE_IN_CALL ||
            mode == android.media.AudioManager.MODE_IN_COMMUNICATION ||
            mode == android.media.AudioManager.MODE_RINGTONE
        ) return false
        val minBuffer = AudioRecord.getMinBufferSize(
            FskModem.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return false
        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                FskModem.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, 8192)
            )
        } catch (e: Exception) {
            return false
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return false
        }
        record = recorder
        try {
            recorder.startRecording()
        } catch (e: Exception) {
            recorder.release()
            record = null
            return false
        }
        captureJob = scope.launch {
            val chunk = ShortArray(2048)
            var failed = false
            while (isActive) {
                val read = try {
                    recorder.read(chunk, 0, chunk.size)
                } catch (e: Exception) {
                    failed = true
                    break
                }
                if (read > 0) onChunk(chunk.copyOf(read))
                else if (read < 0) {
                    failed = true
                    break
                }
            }
            if (failed && isActive) {
                stopCapture()
                onError()
            }
        }
        return true
    }

    actual fun stopCapture() {
        captureJob?.cancel()
        captureJob = null
        try {
            record?.stop()
        } catch (e: Exception) {
            // Recorder already stopped.
        }
        record?.release()
        record = null
    }
}
