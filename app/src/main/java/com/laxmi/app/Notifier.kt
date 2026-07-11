package com.laxmi.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.laxmi.app.ui.MainActivity

/** Small helper for background-result notifications (share-in, extraction done). */
object Notifier {
    private const val CHANNEL = "laxmi_events"
    private var nextId = 100

    fun show(context: Context, title: String, text: String) {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL, "Laxmi updates", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = android.app.Notification.Builder(context, CHANNEL)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        mgr.notify(nextId++, n)
    }
}
