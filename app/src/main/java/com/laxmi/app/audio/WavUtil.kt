package com.laxmi.app.audio

import java.io.ByteArrayOutputStream

/** Wrap raw mono PCM16 as a WAV byte array. */
fun pcmToWav(pcm: ByteArray, sampleRate: Int): ByteArray {
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

    out.write("RIFF".toByteArray()); writeIntLE(36 + dataSize); out.write("WAVE".toByteArray())
    out.write("fmt ".toByteArray()); writeIntLE(16); writeShortLE(1); writeShortLE(channels)
    writeIntLE(sampleRate); writeIntLE(byteRate); writeShortLE(blockAlign); writeShortLE(bitsPerSample)
    out.write("data".toByteArray()); writeIntLE(dataSize); out.write(pcm)
    return out.toByteArray()
}
