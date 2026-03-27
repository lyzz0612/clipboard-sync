package com.clipboardsync.app.util

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.clipboardsync.app.service.ClipboardSyncWorker
import java.util.concurrent.TimeUnit

object WorkManagerHelper {
    fun schedulePeriodicSync(context: Context) {
        FileLogger.init(context)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<ClipboardSyncWorker>(
            15, TimeUnit.MINUTES
        ).setConstraints(constraints).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            ClipboardSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        FileLogger.i(
            "WorkMgr",
            "enqueueUniquePeriodic work=${ClipboardSyncWorker.WORK_NAME} interval=15m network=CONNECTED"
        )
    }

    fun cancelPeriodicSync(context: Context) {
        FileLogger.init(context)
        WorkManager.getInstance(context).cancelUniqueWork(ClipboardSyncWorker.WORK_NAME)
        FileLogger.i("WorkMgr", "cancelUniqueWork ${ClipboardSyncWorker.WORK_NAME}")
    }
}
