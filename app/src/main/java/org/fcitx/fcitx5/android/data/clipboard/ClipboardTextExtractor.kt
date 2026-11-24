/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data.clipboard

/** 从复制的文字中提取网址、电邮等，另存为独立的剪贴板条目。 */
object ClipboardTextExtractor {

    private const val STOP = """\s<>"'，。；：！？、（）「」《》【】"""

    private val URL = Regex("""(?i)(?<![A-Za-z0-9.@/])(?:https?://|www\.)[^$STOP]+""")

    private val EMAIL = Regex(
        """(?<![A-Za-z0-9._%+-])[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}(?![A-Za-z0-9-])"""
    )

    private const val TRAILING = ".,;:!?)]}'\""

    /**
     * 按出现顺序返回去重后的结果；与整段文字相同的项不返回（原条目已覆盖）。
     */
    fun extract(text: String, urls: Boolean, emails: Boolean): List<String> {
        val raw = text.trim()
        if (raw.isEmpty() || (!urls && !emails)) return emptyList()
        val found = mutableListOf<Pair<Int, String>>()
        if (urls) URL.findAll(raw).forEach { m ->
            val url = m.value.trimEnd { it in TRAILING }
            if (url.length > "www.".length) found += m.range.first to url
        }
        if (emails) EMAIL.findAll(raw).forEach { m ->
            // 网址里的 user@host 已随网址提取
            if (found.none { (start, url) -> m.range.first in start until start + url.length }) {
                found += m.range.first to m.value
            }
        }
        return found.sortedBy { it.first }.map { it.second }.distinct().filter { it != raw }
    }
}
