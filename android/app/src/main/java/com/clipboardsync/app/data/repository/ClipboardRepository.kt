package com.clipboardsync.app.data.repository

import com.clipboardsync.app.data.api.ClipboardApi
import com.clipboardsync.app.data.api.ClipItem
import com.clipboardsync.app.data.api.LoginRequest
import com.clipboardsync.app.data.api.PostClipRequest
import com.clipboardsync.app.data.api.RegisterRequest
import com.clipboardsync.app.data.local.PrefsManager
import com.clipboardsync.app.util.FileLogger
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
            FileLogger.d("Repo", "build OkHttp/Retrofit baseUrl=${FileLogger.preview(baseUrl, 120)}")
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
                // BASIC：避免 BODY 把 Authorization / 剪贴板正文打到 logcat
                level = HttpLoggingInterceptor.Level.BASIC
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
        FileLogger.i("Repo", "register start userLen=${username.length}")
        val response = getApi().register(RegisterRequest(username, password))
        FileLogger.i("Repo", "register ok msgLen=${response.message.length}")
        response.message
    }.onFailure { e ->
        FileLogger.e("Repo", "register fail ${e.javaClass.simpleName}: ${e.message}", e)
        handleError(e)
    }

    suspend fun login(username: String, password: String): Result<String> = runCatching {
        FileLogger.i("Repo", "login start user=${FileLogger.preview(username, 32)}")
        val response = getApi().login(LoginRequest(username, password))
        prefs.setToken(response.token)
        prefs.setUsername(username)
        FileLogger.i("Repo", "login ok tokenLen=${response.token.length}")
        response.token
    }.onFailure { e ->
        FileLogger.e("Repo", "login fail ${e.javaClass.simpleName}: ${e.message}", e)
        handleError(e)
    }

    suspend fun getClips(): Result<List<ClipItem>> = runCatching {
        FileLogger.d("Repo", "getClips start")
        val list = getApi().getClips().clips
        val idSample = list.take(40).joinToString(",") { it.id.take(12) }
        val idSuffix = if (list.size > 40) " ...(${list.size} total)" else ""
        FileLogger.i("Repo", "getClips ok count=${list.size} ids=$idSample$idSuffix")
        list
    }.onFailure { e ->
        FileLogger.e("Repo", "getClips fail ${e.javaClass.simpleName}: ${e.message}", e)
        handleError(e)
    }

    suspend fun getDelta(since: String): Result<List<ClipItem>> = runCatching {
        FileLogger.i(
            "Repo",
            "getDelta start sinceLen=${since.length} since=${FileLogger.preview(since, 80)}"
        )
        val response = getApi().getClipsDelta(since)
        prefs.setLastSyncTime(response.serverTime)
        val clips = response.clips
        FileLogger.i(
            "Repo",
            "getDelta ok serverTime=${FileLogger.preview(response.serverTime, 80)} count=${clips.size}"
        )
        clips.forEachIndexed { i, c ->
            FileLogger.d(
                "Repo",
                "  clip[$i] id=${c.id} createdAt=${c.createdAt} textLen=${c.text.length} " +
                    "preview=${FileLogger.preview(c.text, 120)}"
            )
        }
        clips
    }.onFailure { e ->
        FileLogger.e("Repo", "getDelta fail ${e.javaClass.simpleName}: ${e.message}", e)
        handleError(e)
    }

    suspend fun postClip(text: String): Result<ClipItem> = runCatching {
        FileLogger.i("Repo", "postClip textLen=${text.length} preview=${FileLogger.preview(text, 120)}")
        val clip = getApi().postClip(PostClipRequest(text)).clip
        FileLogger.i("Repo", "postClip ok id=${clip.id} createdAt=${clip.createdAt}")
        clip
    }.onFailure { e ->
        FileLogger.e("Repo", "postClip fail ${e.javaClass.simpleName}: ${e.message}", e)
        handleError(e)
    }

    suspend fun deleteClip(id: String): Result<Boolean> = runCatching {
        FileLogger.i("Repo", "deleteClip id=$id")
        val ok = getApi().deleteClip(id).ok
        FileLogger.i("Repo", "deleteClip ok=$ok")
        ok
    }.onFailure { e ->
        FileLogger.e("Repo", "deleteClip fail ${e.javaClass.simpleName}: ${e.message}", e)
        handleError(e)
    }

    private fun handleError(throwable: Throwable) {
        if (throwable is HttpException) {
            FileLogger.w("Repo", "http code=${throwable.code()}")
            if (throwable.code() == 401) {
                FileLogger.w("Repo", "clearing token (401)")
                prefs.setToken("")
            }
        }
    }

    fun invalidateApi() {
        FileLogger.d("Repo", "invalidateApi")
        api = null
        currentBaseUrl = null
    }
}
