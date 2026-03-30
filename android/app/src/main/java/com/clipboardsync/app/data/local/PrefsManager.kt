package com.clipboardsync.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class PrefsManager private constructor(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "clipboard_sync_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getBaseUrl(): String = prefs.getString(KEY_BASE_URL, "") ?: ""

    fun setBaseUrl(url: String) {
        val t = url.trim()
        val normalized = if (t.isNotEmpty() && !t.endsWith("/")) "$t/" else t
        prefs.edit().putString(KEY_BASE_URL, normalized).apply()
    }

    fun getToken(): String = prefs.getString(KEY_TOKEN, "") ?: ""

    fun setToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun getUsername(): String = prefs.getString(KEY_USERNAME, "") ?: ""

    fun setUsername(username: String) {
        prefs.edit().putString(KEY_USERNAME, username).apply()
    }

    fun getLastSyncTime(): String = prefs.getString(KEY_LAST_SYNC, "") ?: ""

    fun setLastSyncTime(time: String) {
        prefs.edit().putString(KEY_LAST_SYNC, time).apply()
    }

    fun isLoggedIn(): Boolean = getToken().isNotEmpty() && getBaseUrl().isNotEmpty()

    fun hasSeenPermissionGuide(): Boolean = prefs.getBoolean(KEY_HAS_SEEN_PERMISSION_GUIDE, false)

    fun setHasSeenPermissionGuide(seen: Boolean) {
        prefs.edit().putBoolean(KEY_HAS_SEEN_PERMISSION_GUIDE, seen).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_TOKEN = "token"
        private const val KEY_USERNAME = "username"
        private const val KEY_LAST_SYNC = "last_sync_time"
        private const val KEY_HAS_SEEN_PERMISSION_GUIDE = "has_seen_permission_guide"

        @Volatile
        private var instance: PrefsManager? = null

        fun getInstance(context: Context): PrefsManager {
            return instance ?: synchronized(this) {
                instance ?: PrefsManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
