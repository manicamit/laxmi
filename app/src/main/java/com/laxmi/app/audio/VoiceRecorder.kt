package com.laxmi.app.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Minimal mic capture: 16 kHz mono PCM16, returned as WAV bytes for the model's
 * audio input. Caller owns the RECORD_AUDIO permission check (hence the
 * @SuppressLint) — recording is only ever started from the recorder UI after the
 * permission grant.
 */
class VoiceRecorder {

    companion object {
        const val SAMPLE_RATE = 16_000
    }

    private val recording = AtomicBoolean(false)
    private var thread: Thread? = null
    private val buffer = ByteArrayOutputStream()

    val isRecording: Boolean get() = recording.get()

    @SuppressLint("MissingPermission")
    fun start() {
        if (!recording.compareAndSet(false, true)) return
        buffer.reset()
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf * 4,
        )
        thread = Thread {
            val chunk = ByteArray(minBuf)
            record.startRecording()
            try {
                while (recording.get()) {
                    val n = record.read(chunk, 0, chunk.size)
                    if (n > 0) buffer.write(chunk, 0, n)
                }
            } finally {
                record.stop()
                record.release()
            }
        }.also { it.start() }
    }

    /** Stops capture and returns the complete recording as WAV bytes. */
    fun stop(): ByteArray {
        recording.set(false)
        thread?.join()
        thread = null
        return pcmToWav(buffer.toByteArray(), SAMPLE_RATE)
    }

    private fun pcmToWav(pcm: ByteArray, sampleRate: Int): ByteArray {
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcm.size
        val out = ByteArrayOutputStream(44 + dataSize)

        fun writeIntLE(v: Int) {
            out.write(v and 0xFF); out.write(v shr 8 and 0xFF)
            out.write(v shr 16 and 0xFF); out.write(v shr 24 and 0xFF)
        }

        fun writeShortLE(v: Int) {
            out.write(v and 0xFF); out.write(v shr 8 and 0xFF)
        }

        out.write("RIFF".toByteArray())
        writeIntLE(36 + dataSize)
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray())
        writeIntLE(16)              // PCM fmt chunk size
        writeShortLE(1)             // PCM format
        writeShortLE(channels)
        writeIntLE(sampleRate)
        writeIntLE(byteRate)
        writeShortLE(blockAlign)
        writeShortLE(bitsPerSample)
        out.write("data".toByteArray())
        writeIntLE(dataSize)
        out.write(pcm)
        return out.toByteArray()
    }
}
