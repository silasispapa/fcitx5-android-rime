/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.update

/** 按本机 ABI 挑出匹配的发布 APK（CI 资产名形如 `zhongzhoue-<版本>-<abi>.apk`）。 */
object UpdateAbiFilter {

    /**
     * 依 [supportedAbis] 的优先顺序找第一个有匹配 APK 的 ABI，只保留它的 APK 与非 APK 资产；
     * 都不匹配时原样返回，避免列表为空。
     */
    fun <T> filter(items: List<T>, supportedAbis: List<String>, name: (T) -> String): List<T> {
        for (abi in supportedAbis) {
            val suffix = "-$abi.apk"
            if (items.none { name(it).endsWith(suffix, ignoreCase = true) }) continue
            return items.filter {
                val n = name(it)
                !n.endsWith(".apk", ignoreCase = true) || n.endsWith(suffix, ignoreCase = true)
            }
        }
        return items
    }
}
