package com.clipboardsync.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.clipboardsync.app.data.local.PrefsManager
import com.clipboardsync.app.data.repository.ClipboardRepository
import com.clipboardsync.app.util.ClipboardBatchWriter
import com.clipboardsync.app.util.FileLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class ClipboardAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())

    /** true = 已 post 待执行，或协程拉取/写剪贴板尚未结束；此期间忽略重复 IME，不取消重排 */
    private val imeSyncBusy = AtomicBoolean(false)

    private val syncRunnable = Runnable { triggerSync() }

    companion object {
        private const val TAG = "ClipSyncA11y"
        /** 0 = 下一帧立即执行；>0 为额外短延迟（毫秒） */
        private const val POST_DELAY_MS = 0L
    }

    override fun onServiceConnected() {
        FileLogger.init(this)
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 500
        }
        FileLogger.i(
            TAG,
            "onServiceConnected eventTypes=TYPE_WINDOW_STATE_CHANGED " +
                "notificationTimeout=500ms postDelayMs=$POST_DELAY_MS"
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) {
            FileLogger.d(TAG, "onAccessibilityEvent: event=null")
            return
        }

        val type = event.eventType
        val pkg = event.packageName?.toString() ?: "null"
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            FileLogger.d(TAG, "skip eventType=$type (want WINDOW_STATE=${
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            }) pkg=$pkg")
            return
        }

        val className = event.className?.toString() ?: run {
            FileLogger.d(TAG, "WINDOW_STATE pkg=$pkg className=null")
            return
        }

        val isImeRelated = className.contains("inputmethod", ignoreCase = true)
            || className.contains("keyboard", ignoreCase = true)
            || className.contains("ime", ignoreCase = true)

        FileLogger.d(
            TAG,
            "WINDOW_STATE pkg=$pkg imeRelated=$isImeRelated class=${FileLogger.preview(className, 400)}"
        )

        if (!isImeRelated) return

        if (!imeSyncBusy.compareAndSet(false, true)) {
            FileLogger.d(TAG, "debounce: skip (sync already queued or running)")
            return
        }
        if (POST_DELAY_MS <= 0L) {
            handler.post(syncRunnable)
            FileLogger.i(TAG, "ime_match: posted triggerSync (immediate)")
        } else {
            handler.postDelayed(syncRunnable, POST_DELAY_MS)
            FileLogger.i(TAG, "ime_match: scheduled triggerSync in ${POST_DELAY_MS}ms")
        }
    }

    private fun triggerSync() {
        FileLogger.i(TAG, "triggerSync: enter")
        scope.launch {
            try {
                val prefs = PrefsManager.getInstance(applicationContext)
                val loggedIn = prefs.isLoggedIn()
                FileLogger.i(
                    TAG,
                    "triggerSync: loggedIn=$loggedIn baseUrlLen=${prefs.getBaseUrl().length} " +
                        "lastSyncLen=${prefs.getLastSyncTime().length}"
                )
                if (!loggedIn) {
                    FileLogger.w(TAG, "triggerSync: skip (not logged in)")
                    return@launch
                }

                val repo = ClipboardRepository(prefs)
                val since = prefs.getLastSyncTime()

                repo.getDelta(since).onSuccess { clips ->
                    FileLogger.i(TAG, "triggerSync: getDelta success count=${clips.size}")
                    if (clips.isEmpty()) {
                        FileLogger.d(TAG, "triggerSync: no new clips, skip clipboard")
                        return@onSuccess
                    }
                    val clipData = ClipboardBatchWriter.buildClipData(clips) ?: run {
                        FileLogger.w(TAG, "triggerSync: buildClipData null")
                        return@onSuccess
                    }
                    FileLogger.i(
                        TAG,
                        "triggerSync: writing batch items=${clipData.itemCount} fromDelta=${clips.size}"
                    )
                    handler.post {
                        runCatching {
                            val clipboard =
                                getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(clipData)
                            FileLogger.i(TAG, "triggerSync: setPrimaryClip on main ok")
                        }.onFailure { e ->
                            FileLogger.e(TAG, "triggerSync: setPrimaryClip failed", e)
                        }
                    }
                }.onFailure { e ->
                    FileLogger.e(TAG, "triggerSync: getDelta failed", e)
                }
            } catch (e: Exception) {
                FileLogger.e(TAG, "triggerSync: exception", e)
            } finally {
                imeSyncBusy.set(false)
            }
        }
    }

    override fun onInterrupt() {
        handler.removeCallbacks(syncRunnable)
        imeSyncBusy.set(false)
        FileLogger.w(TAG, "onInterrupt")
    }

    override fun onDestroy() {
        super.onDestroy()
        FileLogger.w(TAG, "onDestroy")
        handler.removeCallbacks(syncRunnable)
        imeSyncBusy.set(false)
        scope.cancel()
    }
}
