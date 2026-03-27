package com.clipboardsync.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.clipboardsync.app.util.FileLogger
import com.clipboardsync.app.util.WorkManagerHelper

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        FileLogger.init(context)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            FileLogger.i("BootReceiver", "ACTION_BOOT_COMPLETED -> schedulePeriodicSync")
            WorkManagerHelper.schedulePeriodicSync(context)
        } else {
            FileLogger.d("BootReceiver", "ignore action=${intent.action}")
        }
    }
}
