/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.clipboard

/** 从剪贴板文字中提取网址，供提示栏单独上屏。 */
object ClipboardLinkExtractor {

    private val URL = Regex("""(?i)(?<![A-Za-z0-9.@/])(?:https?://|www\.)[^\s<>"'，。；！？、（）「」《》]+""")

    private const val TRAILING = ".,;:!?)]}'\""

    /** 返回第一个网址；整段文字本身就是这个网址时返回 null（主提示已覆盖）。 */
    fun extract(text: String): String? {
        val raw = text.trim()
        val match = URL.find(raw) ?: return null
        val url = match.value.trimEnd { it in TRAILING }
        if (url.length <= "www.".length || url == raw) return null
        return url
    }
}
