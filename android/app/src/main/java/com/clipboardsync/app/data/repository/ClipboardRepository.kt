package com.clipboardsync.app.data.repository

import com.clipboardsync.app.data.api.ClipboardApi
import com.clipboardsync.app.data.api.ClipItem
import com.clipboardsync.app.data.api.LoginRequest
import com.clipboardsync.app.data.api.PostClipRequest
import com.clipboardsync.app.data.api.RegisterRequest
import com.clipboardsync.app.data.local.PrefsManager
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class ClipboardRepository(private val prefs: PrefsManager) {

    private val json = Json { ignoreUnknownKeys = true }

    private var api: ClipboardApi? = null
    private var currentBaseUrl: String? = null

    private fun getApi(): ClipboardApi {
        val baseUrl = prefs.getBaseUrl()
        if (api == null || currentBaseUrl != baseUrl) {
            val authInterceptor = Interceptor { chain ->
                val token = prefs.getToken()
                val request = if (token.isNotEmpty()) {
                    chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $token")
                        .build()
                } else {
                    chain.request()
                }
                chain.proceed(request)
            }

            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .addInterceptor(loggingInterceptor)
                .build()

            val contentType = "application/json".toMediaType()

            api = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(json.asConverterFactory(contentType))
                .build()
                .create(ClipboardApi::class.java)

            currentBaseUrl = baseUrl
        }
        return api!!
    }

    suspend fun register(username: String, password: String): Result<String> = runCatching {
        val response = getApi().register(RegisterRequest(username, password))
        response.message
    }

    suspend fun login(username: String, password: String): Result<String> = runCatching {
        val response = getApi().login(LoginRequest(username, password))
        prefs.setToken(response.token)
        prefs.setUsername(username)
        response.token
    }.onFailure { handleError(it) }

    suspend fun getClips(): Result<List<ClipItem>> = runCatching {
        getApi().getClips().clips
    }.onFailure { handleError(it) }

    suspend fun getDelta(since: String): Result<List<ClipItem>> = runCatching {
        val response = getApi().getClipsDelta(since)
        prefs.setLastSyncTime(response.serverTime)
        response.clips
    }.onFailure { handleError(it) }

    suspend fun postClip(text: String): Result<ClipItem> = runCatching {
        getApi().postClip(PostClipRequest(text)).clip
    }.onFailure { handleError(it) }

    suspend fun deleteClip(id: String): Result<Boolean> = runCatching {
        getApi().deleteClip(id).ok
    }.onFailure { handleError(it) }

    private fun handleError(throwable: Throwable) {
        if (throwable is HttpException && throwable.code() == 401) {
            prefs.setToken("")
        }
    }

    fun invalidateApi() {
        api = null
        currentBaseUrl = null
    }
}
