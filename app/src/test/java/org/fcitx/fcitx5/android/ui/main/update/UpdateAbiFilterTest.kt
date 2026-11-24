/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.update

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateAbiFilterTest {

    private val assets = listOf(
        "zhongzhoue-1.0-arm64-v8a.apk",
        "zhongzhoue-1.0-armeabi-v7a.apk",
        "zhongzhoue-1.0-x86.apk",
        "zhongzhoue-1.0-x86_64.apk",
    )

    private fun run(items: List<String>, vararg abis: String) =
        UpdateAbiFilter.filter(items, abis.toList()) { it }

    @Test
    fun picksPrimaryAbi() {
        assertEquals(listOf("zhongzhoue-1.0-arm64-v8a.apk"), run(assets, "arm64-v8a", "armeabi-v7a", "armeabi"))
    }

    @Test
    fun x86DoesNotMatchX86_64() {
        assertEquals(listOf("zhongzhoue-1.0-x86.apk"), run(assets, "x86"))
        assertEquals(listOf("zhongzhoue-1.0-x86_64.apk"), run(assets, "x86_64", "x86"))
    }

    @Test
    fun fallsBackToNextSupportedAbi() {
        val noArm64 = assets.filterNot { it.contains("arm64") }
        assertEquals(listOf("zhongzhoue-1.0-armeabi-v7a.apk"), run(noArm64, "arm64-v8a", "armeabi-v7a"))
    }

    @Test
    fun keepsNonApkAssets() {
        assertEquals(
            listOf("zhongzhoue-1.0-arm64-v8a.apk", "notes.txt"),
            run(assets + "notes.txt", "arm64-v8a")
        )
    }

    @Test
    fun noMatchReturnsAll() {
        val legacy = listOf("app-release.apk", "other.apk")
        assertEquals(legacy, run(legacy, "arm64-v8a"))
        assertEquals(assets, run(assets, "mips"))
    }
}
