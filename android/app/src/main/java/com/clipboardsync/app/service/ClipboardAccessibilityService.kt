package com.clipboardsync.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.clipboardsync.app.data.local.PrefsManager
import com.clipboardsync.app.data.repository.ClipboardRepository
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
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 500
        }
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val className = event.className?.toString() ?: return
        val isImeRelated = className.contains("inputmethod", ignoreCase = true)
                || className.contains("keyboard", ignoreCase = true)
                || className.contains("ime", ignoreCase = true)

        if (!isImeRelated) return

        pendingSync?.let { handler.removeCallbacks(it) }
        val syncRunnable = Runnable { triggerSync() }
        pendingSync = syncRunnable
        handler.postDelayed(syncRunnable, DEBOUNCE_MS)
    }

    private fun triggerSync() {
        scope.launch {
            try {
                val prefs = PrefsManager.getInstance(applicationContext)
                if (!prefs.isLoggedIn()) return@launch

                val repo = ClipboardRepository(prefs)
                val since = prefs.getLastSyncTime()

                repo.getDelta(since).onSuccess { clips ->
                    if (clips.isNotEmpty()) {
                        val latest = clips.maxByOrNull { it.createdAt } ?: return@onSuccess
                        handler.post {
                            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("clipboard_sync", latest.text))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed", e)
            }
        }
    }

    override fun onInterrupt() {
        pendingSync?.let { handler.removeCallbacks(it) }
        Log.i(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        pendingSync?.let { handler.removeCallbacks(it) }
    }
}
