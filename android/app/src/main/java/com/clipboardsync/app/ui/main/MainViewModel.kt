package com.clipboardsync.app.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.clipboardsync.app.data.api.ClipItem
import com.clipboardsync.app.data.local.PrefsManager
import com.clipboardsync.app.data.repository.ClipboardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    private val prefs: PrefsManager,
    private val repository: ClipboardRepository
) : ViewModel() {

    private val _clips = MutableStateFlow<List<ClipItem>>(emptyList())
    val clips: StateFlow<List<ClipItem>> = _clips.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadClips()
    }

    fun loadClips() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            repository.getClips()
                .onSuccess { _clips.value = it }
                .onFailure { _error.value = it.message ?: "加载失败" }
            _isLoading.value = false
        }
    }

    fun postClip(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            repository.postClip(text)
                .onSuccess { loadClips() }
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
                .onSuccess { loadClips() }
                .onFailure { _error.value = it.message ?: "删除失败" }
        }
    }

    fun refresh() = loadClips()

    fun clearError() {
        _error.value = null
    }

    fun logout() {
        prefs.clear()
    }

    class Factory(
        private val prefs: PrefsManager,
        private val repository: ClipboardRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(prefs, repository) as T
        }
    }
}
