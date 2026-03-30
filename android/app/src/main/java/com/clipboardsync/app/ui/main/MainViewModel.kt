package com.clipboardsync.app.ui.main

import android.app.Application
import android.content.ClipData
import android.content.Context
import android.content.ClipboardManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.clipboardsync.app.data.api.ClipItem
import com.clipboardsync.app.data.local.PrefsManager
import com.clipboardsync.app.data.repository.ClipboardRepository
import com.clipboardsync.app.util.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

class MainViewModel(
    application: Application,
    private val prefs: PrefsManager,
    private val repository: ClipboardRepository
) : AndroidViewModel(application) {

    private val _clips = MutableStateFlow<List<ClipItem>>(emptyList())
    val clips: StateFlow<List<ClipItem>> = _clips.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** 成功类提示（如已写入剪贴板），展示后由 UI 清空 */
    private val _infoMessage = MutableStateFlow<String?>(null)
    val infoMessage: StateFlow<String?> = _infoMessage.asStateFlow()

    /** 防止并发 loadClips 时较慢的旧请求覆盖较新的列表 */
    private val loadClipsGeneration = AtomicInteger(0)

    init {
        FileLogger.i("MainVM", "init loadClips syncClipboard=true (same as manual refresh)")
        loadClips(syncClipboard = true)
    }

    /**
     * @param syncClipboard 为 true 时，在拉取列表成功后把**时间最新**的一条写入系统剪贴板（须主线程，解决真机/ROM 限制）
     */
    fun loadClips(syncClipboard: Boolean = false) {
        val gen = loadClipsGeneration.incrementAndGet()
        viewModelScope.launch {
            FileLogger.i("MainVM", "loadClips start gen=$gen syncClipboard=$syncClipboard")
            _isLoading.value = true
            _error.value = null
            val result = repository.getClips()
            if (gen != loadClipsGeneration.get()) {
                FileLogger.d("MainVM", "loadClips drop stale gen=$gen current=${loadClipsGeneration.get()}")
                return@launch
            }
            result
                .onSuccess { list ->
                    FileLogger.i("MainVM", "loadClips ok gen=$gen count=${list.size} syncClipboard=$syncClipboard")
                    _clips.value = list
                    if (syncClipboard) {
                        withContext(Dispatchers.Main) {
                            syncLatestToSystemClipboard(list)
                        }
                    }
                }
                .onFailure {
                    FileLogger.e("MainVM", "loadClips fail gen=$gen: ${it.message}", it)
                    _error.value = it.message ?: "加载失败"
                }
            if (gen == loadClipsGeneration.get()) {
                _isLoading.value = false
                FileLogger.d("MainVM", "loadClips end gen=$gen isLoading=false")
            }
        }
    }

    private fun syncLatestToSystemClipboard(clips: List<ClipItem>) {
        if (clips.isEmpty()) {
            FileLogger.w("MainVM", "syncLatestToSystemClipboard: empty list")
            _infoMessage.value = "列表为空，无法同步到剪贴板"
            return
        }
        val latest = clips.maxByOrNull { it.createdAt } ?: return
        FileLogger.i(
            "MainVM",
            "syncLatestToSystemClipboard id=${latest.id} textLen=${latest.text.length} " +
                "preview=${FileLogger.preview(latest.text, 120)}"
        )
        val cm = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        runCatching {
            cm.setPrimaryClip(ClipData.newPlainText("clipboard_sync", latest.text))
        }.onSuccess {
            FileLogger.i("MainVM", "syncLatestToSystemClipboard setPrimaryClip ok")
            _infoMessage.value = "已把最新一条同步到系统剪贴板"
        }.onFailure {
            FileLogger.e("MainVM", "syncLatestToSystemClipboard failed", it)
            _infoMessage.value = "无法写入剪贴板：${it.message ?: "系统限制"}"
        }
    }

    fun postClip(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            FileLogger.i("MainVM", "postClip textLen=${text.length} preview=${FileLogger.preview(text, 100)}")
            _isLoading.value = true
            _error.value = null
            repository.postClip(text)
                .onSuccess {
                    FileLogger.i("MainVM", "postClip success -> loadClips syncClipboard=true")
                    loadClips(syncClipboard = true)
                }
                .onFailure {
                    FileLogger.e("MainVM", "postClip fail", it)
                    _error.value = it.message ?: "提交失败"
                    _isLoading.value = false
                }
        }
    }

    fun deleteClip(id: String) {
        viewModelScope.launch {
            FileLogger.i("MainVM", "deleteClip id=$id")
            _error.value = null
            repository.deleteClip(id)
                .onSuccess { loadClips(syncClipboard = false) }
                .onFailure {
                    FileLogger.e("MainVM", "deleteClip fail", it)
                    _error.value = it.message ?: "删除失败"
                }
        }
    }

    /** 手动刷新：拉列表 + 把最新一条写入系统剪贴板（此前仅拉列表，真机上剪贴板不会变） */
    fun refresh() {
        FileLogger.i("MainVM", "refresh()")
        loadClips(syncClipboard = true)
    }

    /** 点击单条记录：复制该条全文到系统剪贴板 */
    fun copyTextToClipboard(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch(Dispatchers.Main) {
            FileLogger.i("MainVM", "copyTextToClipboard textLen=${text.length}")
            val cm = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            runCatching {
                cm.setPrimaryClip(ClipData.newPlainText("clipboard_sync", text))
            }.onSuccess {
                FileLogger.d("MainVM", "copyTextToClipboard ok")
                _infoMessage.value = "已复制到剪贴板"
            }.onFailure {
                FileLogger.e("MainVM", "copyTextToClipboard fail", it)
                _infoMessage.value = "复制失败：${it.message ?: "系统限制"}"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun clearInfoMessage() {
        _infoMessage.value = null
    }

    fun logout() {
        FileLogger.w("MainVM", "logout clearing prefs")
        prefs.clear()
    }

    class Factory(
        private val application: Application,
        private val prefs: PrefsManager,
        private val repository: ClipboardRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(application, prefs, repository) as T
        }
    }
}
