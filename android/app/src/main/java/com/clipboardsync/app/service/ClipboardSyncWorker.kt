package com.clipboardsync.app.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.clipboardsync.app.data.local.PrefsManager
import com.clipboardsync.app.data.repository.ClipboardRepository

class ClipboardSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = PrefsManager.getInstance(applicationContext)
        if (!prefs.isLoggedIn()) return Result.success()

        val repo = ClipboardRepository(prefs)
        val since = prefs.getLastSyncTime()

        return repo.getDelta(since).fold(
            onSuccess = { clips ->
                if (clips.isNotEmpty()) {
                    val latest = clips.maxByOrNull { it.createdAt } ?: return Result.success()
                    val clipboard = applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("clipboard_sync", latest.text))
                }
                Result.success()
            },
            onFailure = { Result.retry() }
        )
    }

    companion object {
        const val WORK_NAME = "clipboard_sync_periodic"
    }
}
