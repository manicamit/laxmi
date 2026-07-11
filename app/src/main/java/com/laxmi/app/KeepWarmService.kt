package com.laxmi.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.laxmi.app.ui.CaptureActivity

/**
 * Keeps Laxmi's process resident so the ~4 GB Gemma engine stays warm (no
 * cold reload, no mid-demo eviction). This is the ordinary-app approximation of
 * a system assistant's always-in-RAM residency. The persistent notification is
 * also a capture entry point ("tap to speak").
 */
class KeepWarmService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL, "Laxmi ready", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val tapToSpeak = PendingIntent.getActivity(
            this, 0,
            Intent(this, CaptureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("Laxmi ready")
            .setContentText("Bolo — hisaab likhwao ya poocho")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(tapToSpeak)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "laxmi_warm"
        private const val NOTIF_ID = 1

        fun start(context: Context) {
            val i = Intent(context, KeepWarmService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }
    }
}
