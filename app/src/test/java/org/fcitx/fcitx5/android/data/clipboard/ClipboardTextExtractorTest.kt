/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data.clipboard

import org.junit.Assert.assertEquals
import org.junit.Test

class ClipboardTextExtractorTest {

    private fun all(text: String) = ClipboardTextExtractor.extract(text, urls = true, emails = true)

    @Test
    fun extractsMultipleUrlsInOrder() {
        assertEquals(
            listOf("https://a.com/x?y=1", "www.b.org"),
            all("先看 https://a.com/x?y=1 再看www.b.org，谢谢")
        )
    }

    @Test
    fun stripsTrailingPunctuation() {
        assertEquals(listOf("https://example.com/path"), all("Visit (https://example.com/path)."))
    }

    @Test
    fun extractsEmails() {
        assertEquals(
            listOf("john.doe@mail.example.com", "a+b@x.io"),
            all("Contact john.doe@mail.example.com or a+b@x.io.")
        )
    }

    @Test
    fun mixedTypesKeepTextOrder() {
        assertEquals(
            listOf("me@site.com", "https://site.com"),
            all("mail me@site.com, web https://site.com")
        )
    }

    @Test
    fun emailInsideUrlIsNotDuplicated() {
        assertEquals(listOf("https://u@host.com/p"), all("see https://u@host.com/p now"))
    }

    @Test
    fun respectsTypeSwitches() {
        val text = "a@b.com https://c.com"
        assertEquals(listOf("https://c.com"), ClipboardTextExtractor.extract(text, urls = true, emails = false))
        assertEquals(listOf("a@b.com"), ClipboardTextExtractor.extract(text, urls = false, emails = true))
        assertEquals(emptyList<String>(), ClipboardTextExtractor.extract(text, urls = false, emails = false))
    }

    @Test
    fun skipsWholeTextAndDuplicates() {
        assertEquals(emptyList<String>(), all("  https://example.com  "))
        assertEquals(listOf("https://a.com"), all("https://a.com and https://a.com"))
        assertEquals(emptyList<String>(), all("no links here, v1.2 and www."))
    }
}
