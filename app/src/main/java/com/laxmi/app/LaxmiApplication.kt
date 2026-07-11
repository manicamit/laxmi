package com.laxmi.app

import android.app.Application
import com.laxmi.app.agents.Extractor
import com.laxmi.app.data.LedgerStore

class LaxmiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        LedgerStore.init(filesDir)
        Extractor.init(this) // warms the single shared engine at process start
        DigestWorker.schedule(this) // daily "aaj ka hisaab" dues reminder
        // NOTE: KeepWarmService is started from MainActivity, NOT here — starting a
        // foreground service from Application.onCreate crashes when the process was
        // spawned in the background (share, worker, restart).
    }
}
