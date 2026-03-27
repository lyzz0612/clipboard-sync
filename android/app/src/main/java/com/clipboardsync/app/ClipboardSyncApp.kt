package com.clipboardsync.app

import android.app.Application
import com.clipboardsync.app.util.WorkManagerHelper

class ClipboardSyncApp : Application() {
    override fun onCreate() {
        super.onCreate()
        WorkManagerHelper.schedulePeriodicSync(this)
    }
}
