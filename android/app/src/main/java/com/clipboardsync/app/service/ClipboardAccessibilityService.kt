package com.clipboardsync.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.clipboardsync.app.data.local.PrefsManager
import com.clipboardsync.app.data.repository.ClipboardRepository
import com.clipboardsync.app.util.FileLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ClipboardAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())
    private var pendingSync: Runnable? = null

    companion object {
        private const val TAG = "ClipSyncA11y"
        private const val DEBOUNCE_MS = 2000L
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
                "notificationTimeout=500ms debounce=${DEBOUNCE_MS}ms"
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

        pendingSync?.let {
            handler.removeCallbacks(it)
            FileLogger.d(TAG, "debounce: removed previous pending sync")
        }
        val syncRunnable = Runnable { triggerSync() }
        pendingSync = syncRunnable
        handler.postDelayed(syncRunnable, DEBOUNCE_MS)
        FileLogger.i(TAG, "ime_match: scheduled triggerSync in ${DEBOUNCE_MS}ms")
    }

    private fun triggerSync() {
        FileLogger.i(TAG, "triggerSync: enter")
        pendingSync = null
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
                    val latest = clips.maxByOrNull { it.createdAt } ?: run {
                        FileLogger.w(TAG, "triggerSync: maxBy createdAt null")
                        return@onSuccess
                    }
                    FileLogger.i(
                        TAG,
                        "triggerSync: writing clip id=${latest.id} createdAt=${latest.createdAt} " +
                            "textLen=${latest.text.length} preview=${FileLogger.preview(latest.text, 160)}"
                    )
                    handler.post {
                        runCatching {
                            val clipboard =
                                getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("clipboard_sync", latest.text)
                            )
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
            }
        }
    }

    override fun onInterrupt() {
        pendingSync?.let { handler.removeCallbacks(it) }
        FileLogger.w(TAG, "onInterrupt")
    }

    override fun onDestroy() {
        super.onDestroy()
        FileLogger.w(TAG, "onDestroy")
        scope.cancel()
        pendingSync?.let { handler.removeCallbacks(it) }
    }
}
