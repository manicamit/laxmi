package com.laxmi.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.laxmi.app.data.LedgerStore

/** Send text to a party: direct to their WhatsApp chat if a number is linked
 *  (Click-to-Chat deep link), otherwise the normal share sheet. */
object WhatsAppSend {
    fun toParty(context: Context, party: String, text: String) {
        val number = LedgerStore.phoneFor(party)
        if (number != null) {
            val url = "https://wa.me/$number?text=" + Uri.encode(text)
            val direct = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            if (direct.resolveActivity(context.packageManager) != null) {
                context.startActivity(direct)
                return
            }
            Toast.makeText(context, "WhatsApp nahi mila — share sheet khol rahe hain", Toast.LENGTH_SHORT).show()
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, "Bhejo"))
    }
}
