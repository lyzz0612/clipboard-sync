package com.clipboardsync.app.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.clipboardsync.app.data.api.QrLoginPayload
import com.clipboardsync.app.data.local.PrefsManager
import com.clipboardsync.app.data.repository.ClipboardRepository
import com.clipboardsync.app.util.FileLogger
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SetupUiState(
    val baseUrl: String = "",
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val loginSuccess: Boolean = false
)

class SetupViewModel(
    private val prefs: PrefsManager,
    private val repository: ClipboardRepository
) : ViewModel() {

    private val qrJson = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    fun updateBaseUrl(url: String) {
        _uiState.update { it.copy(baseUrl = url) }
    }

    fun updateUsername(username: String) {
        _uiState.update { it.copy(username = username) }
    }

    fun updatePassword(password: String) {
        _uiState.update { it.copy(password = password) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun onCameraPermissionDenied() {
        _uiState.update { it.copy(error = "需要相机权限才能扫码") }
    }

    /**
     * 扫描网页生成的 JSON：`{"v":1,"u":"https://…/","c":"…48位hex…"}`（无密码）
     */
    fun onQrScan(raw: String) {
        viewModelScope.launch {
            val trimmed = raw.trim()
            val payload = try {
                qrJson.decodeFromString<QrLoginPayload>(trimmed)
            } catch (e: Exception) {
                FileLogger.e("SetupVM", "qr json parse fail", e)
                _uiState.update {
                    it.copy(error = "无法识别二维码，请使用网页已登录后生成的二维码")
                }
                return@launch
            }
            if (payload.v != 1) {
                _uiState.update { it.copy(error = "不支持的二维码版本") }
                return@launch
            }
            if (payload.u.isBlank() || payload.c.isBlank()) {
                _uiState.update { it.copy(error = "二维码内容不完整") }
                return@launch
            }

            FileLogger.i("SetupVM", "qr redeem start baseUrlLen=${payload.u.length}")
            _uiState.update { it.copy(isLoading = true, error = null) }
            prefs.setBaseUrl(payload.u)
            repository.invalidateApi()

            repository.qrRedeem(payload.c)
                .onSuccess {
                    FileLogger.i("SetupVM", "qr redeem success")
                    _uiState.update { it.copy(isLoading = false, loginSuccess = true) }
                }
                .onFailure { e ->
                    FileLogger.e("SetupVM", "qr redeem fail", e)
                    _uiState.update {
                        it.copy(isLoading = false, error = e.message ?: "扫码登录失败")
                    }
                }
        }
    }

    fun login() {
        val state = _uiState.value
        if (state.baseUrl.isBlank() || state.username.isBlank() || state.password.isBlank()) {
            FileLogger.w("SetupVM", "login: empty fields")
            _uiState.update { it.copy(error = "请填写所有字段") }
            return
        }

        viewModelScope.launch {
            FileLogger.i(
                "SetupVM",
                "login start baseUrlLen=${state.baseUrl.length} user=${FileLogger.preview(state.username, 40)}"
            )
            _uiState.update { it.copy(isLoading = true, error = null) }
            prefs.setBaseUrl(state.baseUrl)
            repository.invalidateApi()

            repository.login(state.username, state.password)
                .onSuccess {
                    FileLogger.i("SetupVM", "login success")
                    _uiState.update { it.copy(isLoading = false, loginSuccess = true) }
                }
                .onFailure { e ->
                    FileLogger.e("SetupVM", "login fail", e)
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "登录失败") }
                }
        }
    }

    fun register() {
        val state = _uiState.value
        if (state.baseUrl.isBlank() || state.username.isBlank() || state.password.isBlank()) {
            FileLogger.w("SetupVM", "register: empty fields")
            _uiState.update { it.copy(error = "请填写所有字段") }
            return
        }

        viewModelScope.launch {
            FileLogger.i("SetupVM", "register start user=${FileLogger.preview(state.username, 40)}")
            _uiState.update { it.copy(isLoading = true, error = null) }
            prefs.setBaseUrl(state.baseUrl)
            repository.invalidateApi()

            repository.register(state.username, state.password)
                .onSuccess {
                    FileLogger.i("SetupVM", "register ok -> auto login")
                    repository.login(state.username, state.password)
                        .onSuccess {
                            FileLogger.i("SetupVM", "register flow: auto login ok")
                            _uiState.update { it.copy(isLoading = false, loginSuccess = true) }
                        }
                        .onFailure { e ->
                            FileLogger.e("SetupVM", "register flow: auto login fail", e)
                            _uiState.update { it.copy(isLoading = false, error = e.message ?: "自动登录失败") }
                        }
                }
                .onFailure { e ->
                    FileLogger.e("SetupVM", "register fail", e)
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "注册失败") }
                }
        }
    }

    class Factory(
        private val prefs: PrefsManager,
        private val repository: ClipboardRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SetupViewModel(prefs, repository) as T
        }
    }
}
