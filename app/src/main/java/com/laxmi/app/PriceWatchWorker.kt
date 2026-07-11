package com.laxmi.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.laxmi.app.agents.Extractor
import com.laxmi.app.missions.AgentWorkflows
import com.laxmi.app.missions.MissionClient
import java.util.concurrent.TimeUnit

/**
 * Standing background agent: a weekly market-price watch. Runs the Bazaar
 * Researcher→Advisor chain over the shop's (anonymized) purchase list and notifies
 * if there's a saving to be had — without the user opening the app.
 */
class PriceWatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val purchases = Extractor.purchaseContext()
        if (purchases.contains("koi purchase record nahi")) return Result.success()
        return try {
            val prices = MissionClient.run(AgentWorkflows.marketResearcherPrompt(purchases))
            val advice = MissionClient.run(
                AgentWorkflows.marketAdvisorPrompt("Paid:\n$purchases\nMarket:\n${prices.text}"),
                prices.interactionId, prices.environmentId,
            )
            Notifier.show(applicationContext, "🛒 Bazaar bhav update", advice.text.take(160))
            Result.success()
        } catch (t: Throwable) {
            Result.retry()
        }
    }

    companion object {
        private const val NAME = "laxmi-price-watch"
        fun enable(context: Context) {
            val req = PeriodicWorkRequestBuilder<PriceWatchWorker>(7, TimeUnit.DAYS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }
        fun disable(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
