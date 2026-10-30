package com.emitt.flipper

import com.emitt.R
import java.io.ByteArrayOutputStream

/**
 * Just enough protobuf to talk to the Flipper's RPC (flipperzero-protobuf: flipper.proto, storage.proto,
 * system.proto), hand-encoded so EMITT doesn't need a protobuf runtime.
 */
object FlipperProto {
    // PB.Main oneof content field numbers
    const val PING_REQUEST = 5
    const val PING_RESPONSE = 6
    const val LIST_REQUEST = 7
    const val LIST_RESPONSE = 8
    const val WRITE_REQUEST = 11
    const val MKDIR_REQUEST = 13
    const val INFO_REQUEST = 28
    const val INFO_RESPONSE = 29
    const val DEVICE_INFO_REQUEST = 32
    const val DEVICE_INFO_RESPONSE = 33
    const val ALERT_REQUEST = 38

    // PB.CommandStatus values we treat specially
    const val OK = 0
    const val ERROR_STORAGE_EXIST = 6
    val statusNames = mapOf(
        1 to R.string.fs_unknown,
        2 to R.string.fs_decode,
        3 to R.string.fs_unsupported,
        4 to R.string.fs_busy,
        5 to R.string.fs_sd,
        6 to R.string.fs_exists,
        7 to R.string.fs_missing,
        8 to R.string.fs_param,
        9 to R.string.fs_denied,
        10 to R.string.fs_name,
        11 to R.string.fs_internal,
        13 to R.string.fs_open,
        14 to R.string.fs_interrupted,
        15 to R.string.fs_params,
    )

    /** A decoded PB.Main: [field] is the oneof content's field number, [body] its encoded message. */
    class Main(val commandId: Int, val status: Int, val hasNext: Boolean, val field: Int, val body: ByteArray)

    class StorageFile(val isDir: Boolean, val name: String, val size: Long)

    /** Encodes one length-delimited PB.Main frame, as the Flipper's RPC stream expects. */
    fun frame(commandId: Int, field: Int, body: ByteArray, hasNext: Boolean = false): ByteArray {
        val main = Writer().apply {
            varint(1, commandId.toLong())
            if (hasNext) varint(3, 1)
            bytes(field, body)
        }.toByteArray()
        return Writer().apply { rawVarint(main.size.toLong()); raw(main) }.toByteArray()
    }

    fun pathRequest(path: String) = Writer().apply { string(1, path) }.toByteArray()

    /** PB_Storage.WriteRequest{ path, file{ data } }. */
    fun writeRequest(path: String, chunk: ByteArray) = Writer().apply {
        string(1, path)
        bytes(2, Writer().apply { bytes(4, chunk) }.toByteArray())
    }.toByteArray()

    fun empty() = ByteArray(0)

    /**
     * Pulls every complete frame off the front of [buf]; returns the frames and how many bytes they used.
     */
    fun drain(buf: ByteArray, len: Int): Pair<List<Main>, Int> {
        val out = mutableListOf<Main>()
        var pos = 0
        while (pos < len) {
            val r = Reader(buf, pos, len)
            val size = r.varintOrNull() ?: break
            if (r.pos + size > len) break
            out += parseMain(buf.copyOfRange(r.pos, r.pos + size.toInt()))
            pos = r.pos + size.toInt()
        }
        return out to pos
    }

    private fun parseMain(b: ByteArray): Main {
        var id = 0; var status = 0; var next = false; var field = 0; var body = ByteArray(0)
        Reader(b).fields { num, r ->
            when (num) {
                1 -> id = r.varint().toInt()
                2 -> status = r.varint().toInt()
                3 -> next = r.varint() != 0L
                else -> { field = num; body = r.lengthDelimited() }
            }
        }
        return Main(id, status, next, field, body)
    }

    fun parseDeviceInfo(b: ByteArray): Pair<String, String> {
        var k = ""; var v = ""
        Reader(b).fields { num, r -> if (num == 1) k = r.string() else if (num == 2) v = r.string() else r.skip() }
        return k to v
    }

    fun parseList(b: ByteArray): List<StorageFile> {
        val files = mutableListOf<StorageFile>()
        Reader(b).fields { num, r ->
            if (num == 1) {
                var dir = false; var name = ""; var size = 0L
                Reader(r.lengthDelimited()).fields { f, fr ->
                    when (f) {
                        1 -> dir = fr.varint() == 1L
                        2 -> name = fr.string()
                        3 -> size = fr.varint()
                        else -> fr.skip()
                    }
                }
                files += StorageFile(dir, name, size)
            } else {
                r.skip()
            }
        }
        return files
    }

    /** PB_Storage.InfoResponse: total and free bytes. */
    fun parseInfo(b: ByteArray): Pair<Long, Long> {
        var total = 0L; var free = 0L
        Reader(b).fields { num, r -> if (num == 1) total = r.varint() else if (num == 2) free = r.varint() else r.skip() }
        return total to free
    }

    private class Writer {
        private val out = ByteArrayOutputStream()
        fun rawVarint(v: Long) {
            var x = v
            while (x and 0x7FL.inv() != 0L) { out.write(((x and 0x7F) or 0x80).toInt()); x = x ushr 7 }
            out.write(x.toInt())
        }
        fun raw(b: ByteArray) = out.write(b)
        fun varint(field: Int, v: Long) { rawVarint((field shl 3).toLong()); rawVarint(v) }
        fun bytes(field: Int, b: ByteArray) { rawVarint(((field shl 3) or 2).toLong()); rawVarint(b.size.toLong()); out.write(b) }
        fun string(field: Int, s: String) = bytes(field, s.toByteArray())
        fun toByteArray(): ByteArray = out.toByteArray()
    }

    private class Reader(val b: ByteArray, var pos: Int = 0, val end: Int = b.size) {
        private var wire = 0

        fun varintOrNull(): Long? {
            var shift = 0; var result = 0L; var p = pos
            while (p < end) {
                val byte = b[p++].toInt() and 0xFF
                result = result or ((byte and 0x7F).toLong() shl shift)
                if (byte and 0x80 == 0) { pos = p; return result }
                shift += 7
                if (shift > 63) return null
            }
            return null
        }

        fun varint(): Long = varintOrNull() ?: error("Truncated varint")
        fun lengthDelimited(): ByteArray {
            val n = varint().toInt()
            return b.copyOfRange(pos, pos + n).also { pos += n }
        }
        fun string() = String(lengthDelimited())
        fun skip() {
            when (wire) {
                0 -> varint()
                1 -> pos += 8
                2 -> lengthDelimited()
                5 -> pos += 4
            }
        }

        inline fun fields(block: (Int, Reader) -> Unit) {
            while (pos < end) {
                val tag = varint().toInt()
                wire = tag and 7
                block(tag ushr 3, this)
            }
        }
    }
}
