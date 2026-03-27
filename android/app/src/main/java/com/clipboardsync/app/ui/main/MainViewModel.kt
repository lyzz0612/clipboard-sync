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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    init {
        loadClips(syncClipboard = false)
    }

    /**
     * @param syncClipboard 为 true 时，在拉取列表成功后把**时间最新**的一条写入系统剪贴板（须主线程，解决真机/ROM 限制）
     */
    fun loadClips(syncClipboard: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            repository.getClips()
                .onSuccess { list ->
                    _clips.value = list
                    if (syncClipboard) {
                        withContext(Dispatchers.Main) {
                            syncLatestToSystemClipboard(list)
                        }
                    }
                }
                .onFailure { _error.value = it.message ?: "加载失败" }
            _isLoading.value = false
        }
    }

    private fun syncLatestToSystemClipboard(clips: List<ClipItem>) {
        if (clips.isEmpty()) {
            _infoMessage.value = "列表为空，无法同步到剪贴板"
            return
        }
        val latest = clips.maxByOrNull { it.createdAt } ?: return
        val cm = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        runCatching {
            cm.setPrimaryClip(ClipData.newPlainText("clipboard_sync", latest.text))
        }.onSuccess {
            _infoMessage.value = "已把最新一条同步到系统剪贴板"
        }.onFailure {
            _infoMessage.value = "无法写入剪贴板：${it.message ?: "系统限制"}"
        }
    }

    fun postClip(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            repository.postClip(text)
                .onSuccess { loadClips(syncClipboard = true) }
                .onFailure {
                    _error.value = it.message ?: "提交失败"
                    _isLoading.value = false
                }
        }
    }

    fun deleteClip(id: String) {
        viewModelScope.launch {
            _error.value = null
            repository.deleteClip(id)
                .onSuccess { loadClips(syncClipboard = false) }
                .onFailure { _error.value = it.message ?: "删除失败" }
        }
    }

    /** 手动刷新：拉列表 + 把最新一条写入系统剪贴板（此前仅拉列表，真机上剪贴板不会变） */
    fun refresh() = loadClips(syncClipboard = true)

    fun clearError() {
        _error.value = null
    }

    fun clearInfoMessage() {
        _infoMessage.value = null
    }

    fun logout() {
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
