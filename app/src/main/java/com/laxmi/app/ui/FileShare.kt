package com.laxmi.app.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Save agent output as a real file and share it (the downloadable artifact). */
object FileShare {
    fun shareText(context: Context, title: String, body: String) {
        runCatching {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val safe = title.replace(Regex("[^A-Za-z0-9]+"), "_").take(30).ifBlank { "laxmi" }
            val file = File(dir, "$safe.txt")
            file.writeText("$title\n\n$body\n\n— Laxmi")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Save / Share"))
        }
    }
}
