/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.input.voice.js

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.random.Random

/**
 * 分片对齐：插件把每片 base64 直接拼接后整体解码，所以宿主必须保证每片都是 3 字节的整数倍
 * （除最后一片外不带填充），否则拼接串中间会出现 `=`，解码直接失败。
 */
class JsVoicePcmAlignerTest {

    @Test
    fun multiplesOfThreeArePassedThroughWithoutCopying() {
        val aligner = JsVoicePcmAligner()
        val pcm = ByteArray(3201) { it.toByte() }
        assertSame(pcm, aligner.take(pcm))
        assertNull(aligner.flush())
    }

    @Test
    fun capturePacketsLeaveTheRemainderForTheNextSlice() {
        val aligner = JsVoicePcmAligner()
        val first = ByteArray(3200) { 1 }
        val second = ByteArray(3200) { 2 }
        val tail = byteArrayOf(3, 4)

        assertEquals(3198, aligner.take(first)!!.size)
        // 前一片留下 2 字节，与这一片凑成 3202，切走 3201 再留 1。
        assertEquals(3201, aligner.take(second)!!.size)
        // 1 + 2 = 3，正好补齐，不再挂尾巴。
        assertEquals(3, aligner.take(tail)!!.size)
        assertNull(aligner.flush())
    }

    @Test
    fun flushEmitsTheRemainderExactlyOnce() {
        val aligner = JsVoicePcmAligner()
        assertNull(aligner.take(byteArrayOf(1)))
        assertNull(aligner.take(byteArrayOf(2)))
        assertArrayEquals(byteArrayOf(1, 2), aligner.flush())
        assertNull(aligner.flush())
    }

    @Test
    fun noByteIsLostOrReorderedForArbitraryPacketSizes() {
        val random = Random(7)
        val aligner = JsVoicePcmAligner()
        val expected = ByteArrayOutputStream()
        val actual = ByteArrayOutputStream()
        repeat(200) {
            val packet = ByteArray(random.nextInt(0, 5000)) { random.nextInt(0, 256).toByte() }
            expected.write(packet)
            // 空包不该让缓存出问题，也不该产生一次投递。
            aligner.take(packet)?.let { actual.write(it) }
        }
        aligner.flush()?.let { actual.write(it) }
        assertArrayEquals(expected.toByteArray(), actual.toByteArray())
    }
}
