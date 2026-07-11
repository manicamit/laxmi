package com.laxmi.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.laxmi.app.data.LedgerStore
import java.util.concurrent.TimeUnit

/**
 * Daily "aaj ka hisaab" reminder: notifies the pending dues owed to the user so
 * the ledger acts, not just records. Runs whether or not the app is open.
 */
class DigestWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val owed = LedgerStore.balances(LedgerStore.events.value).filter { it.netPaise > 0 }
        if (owed.isNotEmpty()) {
            val total = owed.sumOf { it.netPaise } / 100
            val who = owed.take(3).joinToString(", ") { it.party }
            Notifier.show(
                applicationContext,
                "Aaj ka hisaab",
                "₹%,d aana hai — $who%s".format(total, if (owed.size > 3) " +${owed.size - 3}" else ""),
            )
        }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<DigestWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "laxmi-daily-digest", ExistingPeriodicWorkPolicy.KEEP, req,
            )
        }
    }
}
