package com.clipboardsync.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.clipboardsync.app.util.WorkManagerHelper

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i("BootReceiver", "Boot completed, scheduling periodic sync")
            WorkManagerHelper.schedulePeriodicSync(context)
        }
    }
}
