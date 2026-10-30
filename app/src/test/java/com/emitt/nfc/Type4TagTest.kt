package com.emitt.nfc

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Walks through the exact command sequence a phone uses to read a Type 4 tag. */
class Type4TagTest {
    private fun hex(s: String) = s.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private val ok = hex("9000")

    @Test
    fun readerCanReadTheWholeMessage() {
        val ndef = ByteArray(600) { (it % 251).toByte() } // bigger than one READ BINARY
        val tag = Type4Tag(ndef)

        assertArrayEquals(ok, tag.process(hex("00A4040007D276000085010100")))
        assertArrayEquals(ok, tag.process(hex("00A4000C02E103")))
        val cc = tag.process(hex("00B000000F"))
        assertArrayEquals(ok, cc.takeLast(2).toByteArray())
        // CC: version 2.0, NDEF file E104, max size = 2 + 600, read open, write none
        assertArrayEquals(hex("000F2000F600F60406E104025A00FF"), cc.copyOfRange(0, 15))

        assertArrayEquals(ok, tag.process(hex("00A4000C02E104")))
        val nlen = tag.process(hex("00B0000002"))
        assertArrayEquals(hex("0258") + ok, nlen)

        val out = java.io.ByteArrayOutputStream()
        var offset = 2
        while (offset < 602) {
            val chunk = minOf(Type4Tag.MAX_LE, 602 - offset)
            val r = tag.process(byteArrayOf(0x00, 0xB0.toByte(), (offset shr 8).toByte(), offset.toByte(), chunk.toByte()))
            assertArrayEquals(ok, r.takeLast(2).toByteArray())
            out.write(r, 0, r.size - 2)
            offset += r.size - 2
        }
        assertArrayEquals(ndef, out.toByteArray())
        assertTrue(tag.fullyRead)
    }

    @Test
    fun filesNeedTheApplicationSelectedFirst() {
        val tag = Type4Tag(byteArrayOf(1, 2, 3))
        assertArrayEquals(Type4Tag.SW_NOT_FOUND, tag.process(hex("00A4000C02E104")))
        assertArrayEquals(Type4Tag.SW_NOT_FOUND, tag.process(hex("00A4040007A000000003101000")))
        assertFalse(tag.fullyRead)
    }

    @Test
    fun writesAreRefused() {
        val tag = Type4Tag(byteArrayOf(1, 2, 3))
        tag.process(hex("00A4040007D276000085010100"))
        tag.process(hex("00A4000C02E104"))
        assertArrayEquals(Type4Tag.SW_SECURITY, tag.process(hex("00D60000020000")))
    }
}
