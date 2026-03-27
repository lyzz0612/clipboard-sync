package com.clipboardsync.app

import android.app.Application
import com.clipboardsync.app.util.FileLogger
import com.clipboardsync.app.util.WorkManagerHelper

class ClipboardSyncApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FileLogger.init(this)
        FileLogger.i("ClipboardSyncApp", "onCreate schedule periodic work")
        WorkManagerHelper.schedulePeriodicSync(this)
    }
}
