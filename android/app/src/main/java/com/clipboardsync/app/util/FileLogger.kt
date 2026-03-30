package com.clipboardsync.app.util

import android.content.Context
import android.os.Build
import android.util.Log
import com.clipboardsync.app.BuildConfig
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 调试日志：同时写入 logcat 与应用专属目录下的 UTF-8 文本文件。
 *
 * 导出示例（路径以 init 打印为准）：
 * `adb pull /sdcard/Android/data/com.clipboardsync.app/files/clipboard_sync_debug.log`
 *
 * 注意：可能包含剪贴板内容预览，请勿把文件提交到 git 或公开分享。
 *
 * 设为 `false` 时关闭所有日志输出（不写文件、不打 logcat），调用点保留便于以后排查。
 */
object FileLogger {

    private const val ENABLED = BuildConfig.ENABLE_FILE_LOGGER

    /** 与 [ENABLED] 一致；网络库等可据此关闭 logcat 输出。 */
    fun isEnabled(): Boolean = ENABLED

    private const val FILE_NAME = "clipboard_sync_debug.log"
    private const val BACKUP_SUFFIX = ".bak"
    private const val MAX_BYTES = 2_000_000L

    const val DEFAULT_PREVIEW = 200

    private const val TAG = "FileLogger"
    private val zone = ZoneId.systemDefault()
    private val formatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).withZone(zone)

    private val lock = Any()

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        if (!ENABLED) return
        if (appContext != null) return
        synchronized(lock) {
            if (appContext != null) return
            val app = context.applicationContext
            appContext = app
            val path = logFileUnchecked(app).absolutePath
            val ver = runCatching {
                app.packageManager.getPackageInfo(app.packageName, 0).versionName
            }.getOrNull() ?: "?"
            Log.i(TAG, "log file: $path")
            writeRaw(
                "I", TAG,
                "=== session ver=$ver path=$path " +
                    "device=${Build.MANUFACTURER} ${Build.MODEL} " +
                    "sdk=${Build.VERSION.SDK_INT} release=${Build.VERSION.RELEASE} ==="
            )
        }
    }

    /** 供调试说明；可能为 null 若未 init */
    fun absolutePathOrNull(): String? =
        appContext?.let { logFileUnchecked(it).absolutePath }

    private fun logFileUnchecked(ctx: Context): File {
        val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        return File(dir, FILE_NAME)
    }

    fun d(tag: String, message: String) {
        if (!ENABLED) return
        Log.d(tag, message)
        writeRaw("D", tag, message)
    }

    fun i(tag: String, message: String) {
        if (!ENABLED) return
        Log.i(tag, message)
        writeRaw("I", tag, message)
    }

    fun w(tag: String, message: String) {
        if (!ENABLED) return
        Log.w(tag, message)
        writeRaw("W", tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (!ENABLED) return
        Log.e(tag, message, throwable)
        val body = if (throwable != null) {
            "$message\n${Log.getStackTraceString(throwable)}"
        } else {
            message
        }
        writeRaw("E", tag, body)
    }

    /** 长文本截断，避免单条日志过大；仍保留总长度信息 */
    fun preview(text: String?, max: Int = DEFAULT_PREVIEW): String {
        if (text == null) return "null"
        if (text.length <= max) return text
        return text.take(max) + "...[totalLen=${text.length}]"
    }

    private fun writeRaw(level: String, tag: String, message: String) {
        val ctx = appContext ?: return
        synchronized(lock) {
            try {
                val f = logFileUnchecked(ctx)
                maybeRotate(f)
                val ts = formatter.format(Instant.now())
                val thread = Thread.currentThread().name
                val sanitized = message.replace("\r\n", " ").replace("\n", "\\n")
                f.appendText("$ts $level/$tag [$thread] $sanitized\n")
            } catch (e: Exception) {
                Log.e(TAG, "file append failed", e)
            }
        }
    }

    private fun maybeRotate(f: File) {
        if (!f.exists() || f.length() <= MAX_BYTES) return
        val bak = File(f.parent, FILE_NAME + BACKUP_SUFFIX)
        if (bak.exists()) bak.delete()
        f.renameTo(bak)
        appContext?.let {
            i(TAG, "rotated log -> ${bak.name} (max ${MAX_BYTES} bytes)")
        }
    }
}
