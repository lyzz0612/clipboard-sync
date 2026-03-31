package com.clipboardsync.app.util

import android.content.ClipData
import com.clipboardsync.app.data.api.ClipItem

/**
 * 后台同步时把多条剪贴板项写入系统剪贴板（单条 [ClipData] 内多段文本，最多 [MAX_ITEMS] 条）。
 * 若本次增量多于 [MAX_ITEMS] 条，保留时间最近的 [MAX_ITEMS] 条，并按 [createdAt] 升序排列。
 */
object ClipboardBatchWriter {
    const val MAX_ITEMS = 5

    fun buildClipData(clips: List<ClipItem>): ClipData? {
        if (clips.isEmpty()) return null
        val ordered = clips
            .sortedBy { it.createdAt }
            .takeLast(MAX_ITEMS)
        val first = ordered.first()
        val clip = ClipData.newPlainText("clipboard_sync", first.text)
        for (i in 1 until ordered.size) {
            clip.addItem(ClipData.Item(ordered[i].text))
        }
        return clip
    }
}
