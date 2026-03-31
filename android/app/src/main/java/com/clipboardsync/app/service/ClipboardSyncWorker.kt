package com.clipboardsync.app.service

import android.content.ClipboardManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.clipboardsync.app.data.local.PrefsManager
import com.clipboardsync.app.data.repository.ClipboardRepository
import com.clipboardsync.app.util.ClipboardBatchWriter
import com.clipboardsync.app.util.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ClipboardSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        FileLogger.init(applicationContext)
        val runId = id.toString()
        FileLogger.i("SyncWorker", "doWork start id=$runId attempt=${runAttemptCount}")

        val prefs = PrefsManager.getInstance(applicationContext)
        if (!prefs.isLoggedIn()) {
            FileLogger.d("SyncWorker", "doWork skip: not logged in")
            return Result.success()
        }

        val repo = ClipboardRepository(prefs)
        val since = prefs.getLastSyncTime()
        FileLogger.i("SyncWorker", "doWork sinceLen=${since.length} preview=${FileLogger.preview(since, 80)}")

        return repo.getDelta(since).fold(
            onSuccess = { clips ->
                FileLogger.i("SyncWorker", "getDelta ok count=${clips.size}")
                if (clips.isNotEmpty()) {
                    val clipData = ClipboardBatchWriter.buildClipData(clips)
                    if (clipData != null) {
                        FileLogger.i(
                            "SyncWorker",
                            "clipboard batch items=${clipData.itemCount} fromDelta=${clips.size}"
                        )
                        // 部分真机（尤其 MIUI）要求在主线程写入剪贴板
                        withContext(Dispatchers.Main) {
                            val clipboard =
                                applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            runCatching {
                                clipboard.setPrimaryClip(clipData)
                                FileLogger.i("SyncWorker", "setPrimaryClip ok (worker main)")
                            }.onFailure { e ->
                                FileLogger.e("SyncWorker", "setPrimaryClip failed", e)
                            }
                        }
                    }
                } else {
                    FileLogger.d("SyncWorker", "no clips to paste")
                }
                Result.success()
            },
            onFailure = { e ->
                FileLogger.e("SyncWorker", "getDelta fail -> retry", e)
                Result.retry()
            }
        )
    }

    companion object {
        const val WORK_NAME = "clipboard_sync_periodic"
    }
}
