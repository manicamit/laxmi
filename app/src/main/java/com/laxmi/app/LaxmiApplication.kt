package com.laxmi.app

import android.app.Application
import com.laxmi.app.data.LedgerStore

class LaxmiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        LedgerStore.init(filesDir)
    }
}
