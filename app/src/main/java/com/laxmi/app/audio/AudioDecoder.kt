package com.laxmi.app.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

private const val TAG = "AudioDecoder"
private const val TARGET_RATE = 16_000

/**
 * Decodes a shared audio file (WhatsApp .opus/.ogg, .m4a, .mp3, ...) to 16 kHz
 * mono PCM16 WAV — the format the on-device engine expects. Same normalize step
 * the mic path targets, so both inputs converge.
 */
object AudioDecoder {

    fun decodeToWav(context: Context, uri: Uri): ByteArray? {
        val extractor = MediaExtractor()
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                extractor.setDataSource(pfd.fileDescriptor)
            } ?: run { extractor.setDataSource(context, uri, null) }
            decode(extractor)
        } catch (t: Throwable) {
            Log.e(TAG, "decode(uri) failed", t); null
        } finally {
            runCatching { extractor.release() }
        }
    }

    /** Decode from a local file path (used for background share processing). */
    fun decodeToWav(path: String): ByteArray? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(path)
            decode(extractor)
        } catch (t: Throwable) {
            Log.e(TAG, "decode(path) failed", t); null
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun decode(extractor: MediaExtractor): ByteArray? {
        return try {
            val trackIndex = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null

            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val srcRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = runCatching { format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }.getOrDefault(1)

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val pcm = ByteArrayOutputStream()
            val info = MediaCodec.BufferInfo()
            var sawInputEnd = false
            var sawOutputEnd = false
            // May be updated by the decoder's real output format.
            var outRate = srcRate
            var outChannels = channels
            var isFloat = false

            while (!sawOutputEnd) {
                if (!sawInputEnd) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val inBuf = codec.getInputBuffer(inIndex)!!
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEnd = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val of = codec.outputFormat
                        outRate = runCatching { of.getInteger(MediaFormat.KEY_SAMPLE_RATE) }.getOrDefault(outRate)
                        outChannels = runCatching { of.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }.getOrDefault(outChannels)
                        val enc = runCatching { of.getInteger(MediaFormat.KEY_PCM_ENCODING) }.getOrDefault(2)
                        isFloat = enc == 4 // AudioFormat.ENCODING_PCM_FLOAT
                        Log.i(TAG, "output format: rate=$outRate ch=$outChannels float=$isFloat")
                    }
                    outIndex >= 0 -> {
                        val outBuf: ByteBuffer = codec.getOutputBuffer(outIndex)!!
                        val chunk = ByteArray(info.size)
                        outBuf.get(chunk); outBuf.clear()
                        pcm.write(chunk)
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEnd = true
                    }
                }
            }
            codec.stop(); codec.release()

            var samples = pcm.toByteArray()
            if (isFloat) samples = floatToInt16(samples)
            if (outChannels > 1) samples = downmixToMono(samples, outChannels)
            if (outRate != TARGET_RATE) samples = resample(samples, outRate, TARGET_RATE)
            Log.i(TAG, "decoded: ${samples.size} bytes @ ${TARGET_RATE}Hz mono")
            pcmToWav(samples, TARGET_RATE)
        } catch (t: Throwable) {
            Log.e(TAG, "decode failed", t)
            null
        } finally {
            runCatching { extractor.release() }
        }
    }

    /** Convert little-endian 32-bit float PCM (range -1..1) to 16-bit PCM. */
    private fun floatToInt16(bytes: ByteArray): ByteArray {
        val bb = ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val floats = bb.asFloatBuffer()
        val out = ByteArray(floats.remaining() * 2)
        var o = 0
        while (floats.hasRemaining()) {
            val v = (floats.get().coerceIn(-1f, 1f) * 32767f).toInt()
            out[o++] = (v and 0xFF).toByte()
            out[o++] = (v shr 8 and 0xFF).toByte()
        }
        return out
    }

    private fun downmixToMono(pcm: ByteArray, channels: Int): ByteArray {
        val out = ByteArrayOutputStream(pcm.size / channels)
        val frameBytes = 2 * channels
        var i = 0
        while (i + frameBytes <= pcm.size) {
            var sum = 0
            for (c in 0 until channels) {
                val lo = pcm[i + c * 2].toInt() and 0xFF
                val hi = pcm[i + c * 2 + 1].toInt()
                sum += (hi shl 8) or lo
            }
            val avg = (sum / channels)
            out.write(avg and 0xFF); out.write(avg shr 8 and 0xFF)
            i += frameBytes
        }
        return out.toByteArray()
    }

    private fun resample(pcm: ByteArray, srcRate: Int, dstRate: Int): ByteArray {
        val srcSamples = pcm.size / 2
        val dstSamples = (srcSamples.toLong() * dstRate / srcRate).toInt()
        val out = ByteArray(dstSamples * 2)
        for (i in 0 until dstSamples) {
            val srcPos = (i.toLong() * srcRate / dstRate).toInt().coerceIn(0, srcSamples - 1)
            out[i * 2] = pcm[srcPos * 2]
            out[i * 2 + 1] = pcm[srcPos * 2 + 1]
        }
        return out
    }
}
