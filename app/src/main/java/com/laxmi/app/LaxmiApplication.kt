package com.laxmi.app

import android.app.Application
import com.laxmi.app.agents.Extractor
import com.laxmi.app.data.LedgerStore

class LaxmiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        LedgerStore.init(filesDir)
        Extractor.init(this) // warms the single shared engine at process start
    }
}
