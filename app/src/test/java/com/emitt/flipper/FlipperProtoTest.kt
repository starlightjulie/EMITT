package com.emitt.flipper

import com.emitt.data.UpdateChecker
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlipperProtoTest {

    @Test
    fun pingFrameMatchesProtobufEncoding() {
        // Main{command_id=1, system_ping_request(5){}} -> 08 01 2A 00, prefixed with its length 4
        val f = FlipperProto.frame(1, FlipperProto.PING_REQUEST, FlipperProto.empty())
        assertArrayEquals(byteArrayOf(4, 0x08, 0x01, 0x2A, 0x00), f)
    }

    @Test
    fun hasNextIsEncodedForChunkedWrites() {
        val f = FlipperProto.frame(7, FlipperProto.WRITE_REQUEST, FlipperProto.writeRequest("/ext/nfc/a.nfc", byteArrayOf(1, 2)), hasNext = true)
        val (frames, used) = FlipperProto.drain(f, f.size)
        assertEquals(f.size, used)
        val m = frames.single()
        assertEquals(7, m.commandId)
        assertTrue(m.hasNext)
        assertEquals(FlipperProto.WRITE_REQUEST, m.field)
    }

    @Test
    fun drainKeepsPartialFramesForLater() {
        val a = FlipperProto.frame(3, FlipperProto.PING_RESPONSE, byteArrayOf(0x0A, 0x01, 0x55))
        val b = FlipperProto.frame(4, FlipperProto.PING_RESPONSE, FlipperProto.empty())
        val joined = a + b
        val (frames, used) = FlipperProto.drain(joined, a.size + 2)
        assertEquals(listOf(3), frames.map { it.commandId })
        assertEquals(a.size, used)
        assertFalse(frames.single().hasNext)
    }

    @Test
    fun parsesListResponse() {
        // ListResponse{ file{type=0,name="x.nfc",size=42}, file{type=1,name="dir"} }
        val file1 = byteArrayOf(0x12, 5) + "x.nfc".toByteArray() + byteArrayOf(0x18, 42)
        val file2 = byteArrayOf(0x08, 1, 0x12, 3) + "dir".toByteArray()
        val body = byteArrayOf(0x0A, file1.size.toByte()) + file1 + byteArrayOf(0x0A, file2.size.toByte()) + file2
        val files = FlipperProto.parseList(body)
        assertEquals(listOf("x.nfc", "dir"), files.map { it.name })
        assertEquals(42L, files[0].size)
        assertTrue(files[1].isDir)
    }

    @Test
    fun versionsCompareNumerically() {
        assertTrue(UpdateChecker.compare("0.1.10", "0.1.9") > 0)
        assertTrue(UpdateChecker.compare("1.0", "1.0.0") == 0)
        assertTrue(UpdateChecker.compare("0.2.0", "1.0.0") < 0)
    }
}
