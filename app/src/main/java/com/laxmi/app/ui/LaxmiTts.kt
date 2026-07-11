package com.laxmi.app.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Shared, lazily-initialised TTS so any screen can speak results aloud (voice-first). */
object LaxmiTts {
    private var tts: TextToSpeech? = null
    private var ready = false

    fun init(context: Context) {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("hi", "IN")
                ready = true
            }
        }
    }

    fun speak(context: Context, text: String) {
        init(context)
        tts?.let {
            if (ready) it.speak(sanitize(text), TextToSpeech.QUEUE_FLUSH, null, "laxmi")
        }
    }

    /** Strip markdown so TTS never literally says "star", "hash", etc. */
    fun sanitize(t: String): String = t
        .replace(Regex("\\[(.*?)]\\(.*?\\)"), "$1")     // [text](url) -> text
        .replace(Regex("https?://\\S+"), "")            // bare URLs
        .replace(Regex("[*_`~#>|]"), "")                // md emphasis/heading/code
        .replace(Regex("(?m)^\\s*[-•]\\s*"), "")        // bullet markers
        .replace(Regex("\\n{2,}"), ". ")
        .replace(Regex("[ \\t]{2,}"), " ")
        .trim()
}
