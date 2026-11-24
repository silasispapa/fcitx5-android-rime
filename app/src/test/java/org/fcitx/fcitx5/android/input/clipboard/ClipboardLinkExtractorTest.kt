/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClipboardLinkExtractorTest {

    @Test
    fun extractsUrlFromSentence() {
        assertEquals(
            "https://example.com/a?b=1",
            ClipboardLinkExtractor.extract("看这个 https://example.com/a?b=1 很有用")
        )
    }

    @Test
    fun stripsTrailingPunctuation() {
        assertEquals(
            "https://example.com/path",
            ClipboardLinkExtractor.extract("Visit (https://example.com/path).")
        )
    }

    @Test
    fun stopsAtChinesePunctuation() {
        assertEquals("www.example.com", ClipboardLinkExtractor.extract("网址www.example.com，谢谢"))
    }

    @Test
    fun returnsFirstUrl() {
        assertEquals(
            "http://a.com",
            ClipboardLinkExtractor.extract("http://a.com and https://b.com")
        )
    }

    @Test
    fun ignoresTextThatIsOnlyTheUrl() {
        assertNull(ClipboardLinkExtractor.extract("  https://example.com  "))
    }

    @Test
    fun ignoresTextWithoutUrl() {
        assertNull(ClipboardLinkExtractor.extract("hello world, mail me at a@b.com"))
        assertNull(ClipboardLinkExtractor.extract("www."))
    }
}
