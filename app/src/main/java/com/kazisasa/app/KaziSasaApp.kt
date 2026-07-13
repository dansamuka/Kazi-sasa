package com.kazisasa.app

import android.app.Application
import com.kazisasa.app.work.FeedRefreshWorker

class KaziSasaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FeedRefreshWorker.schedulePeriodic(this)
    }
}
