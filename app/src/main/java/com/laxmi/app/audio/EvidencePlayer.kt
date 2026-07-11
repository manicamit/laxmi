package com.laxmi.app.audio

import android.content.Context
import android.media.MediaPlayer
import java.io.File

/**
 * Plays WAV evidence bytes. One player at a time — starting a new clip stops the
 * previous one (fine for a review flow; nobody wants overlapping evidence).
 */
object EvidencePlayer {

    private var player: MediaPlayer? = null

    fun play(context: Context, wavBytes: ByteArray) {
        stop()
        val f = File.createTempFile("evidence", ".wav", context.cacheDir)
        f.writeBytes(wavBytes)
        player = MediaPlayer().apply {
            setDataSource(f.absolutePath)
            setOnCompletionListener {
                it.release()
                f.delete()
                player = null
            }
            prepare()
            start()
        }
    }

    fun stop() {
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
    }
}
