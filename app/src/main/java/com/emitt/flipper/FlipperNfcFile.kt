package com.emitt.flipper

import android.nfc.NdefMessage
import com.emitt.nfc.TagReport
import com.emitt.nfc.UltralightType
import com.emitt.nfc.hexToBytes
import com.emitt.nfc.toHex
import kotlin.random.Random

/** A parsed Flipper Zero .nfc file. [pages] is set for NTAG/Ultralight files. */
class FlipperNfc(
    val deviceType: String,
    val uid: ByteArray,
    val ultralight: UltralightType?,
    val pages: List<ByteArray>?,
) {
    /** The NDEF message stored in an NTAG/Ultralight's user memory, if there is one. */
    fun ndef(): NdefMessage? {
        val data = pages?.drop(4)?.fold(ByteArray(0)) { acc, p -> acc + p } ?: return null
        var i = 0
        while (i < data.size) {
            val t = data[i].toInt() and 0xFF
            if (t == 0x00) { i++; continue }
            if (t == 0xFE || i + 1 >= data.size) return null
            var len = data[i + 1].toInt() and 0xFF
            var start = i + 2
            if (len == 0xFF && i + 3 < data.size) {
                len = (data[i + 2].toInt() and 0xFF shl 8) or (data[i + 3].toInt() and 0xFF)
                start = i + 4
            }
            if (t == 0x03) return runCatching { NdefMessage(data.copyOfRange(start, start + len)) }.getOrNull()
            i = start + len
        }
        return null
    }
}

/**
 * Reads and writes Flipper Zero NFC files (format version 4). See the firmware's
 * documentation/file_formats/NfcFileFormats.md.
 */
object FlipperNfcFile {
    const val NFC_DIR = "/ext/nfc"

    /** NTAG chips a payload can be packed into, smallest first. */
    val ntags = listOf(UltralightType.NTAG213, UltralightType.NTAG215, UltralightType.NTAG216)

    /** User memory in bytes (what the capability container advertises). */
    fun capacity(t: UltralightType) = when (t) {
        UltralightType.NTAG213 -> 144
        UltralightType.NTAG215 -> 496
        UltralightType.NTAG216 -> 872
        else -> 0
    }

    /** Bytes [msg] takes in user memory: TLV header + message + terminator. */
    fun footprint(msg: NdefMessage): Int = msg.byteArrayLength.let { it + (if (it < 0xFF) 2 else 4) + 1 }

    fun smallestFit(msg: NdefMessage): UltralightType? = ntags.firstOrNull { footprint(msg) <= capacity(it) }

    /** A random 7-byte UID with NXP's manufacturer byte, like a real NTAG. */
    fun randomUid(): ByteArray = byteArrayOf(0x04) + Random.nextBytes(6)

    /** Builds a blank NTAG holding [msg], ready to emulate on the Flipper. */
    fun fromNdef(msg: NdefMessage, type: UltralightType, uid: ByteArray = randomUid()): String {
        require(type in ntags) { "Only NTAG213/215/216 can be generated" }
        require(uid.size == 7) { "UID must be 7 bytes" }
        val capacity = capacity(type)
        require(footprint(msg) <= capacity) { "Doesn't fit on ${type.flipperName}" }

        val pages = MutableList(type.pages) { ByteArray(4) }
        val bcc0 = (0x88 xor uid[0].toInt() xor uid[1].toInt() xor uid[2].toInt()).toByte()
        val bcc1 = (uid[3].toInt() xor uid[4].toInt() xor uid[5].toInt() xor uid[6].toInt()).toByte()
        pages[0] = byteArrayOf(uid[0], uid[1], uid[2], bcc0)
        pages[1] = byteArrayOf(uid[3], uid[4], uid[5], uid[6])
        pages[2] = byteArrayOf(bcc1, 0x48, 0x00, 0x00) // internal byte, static lock bytes (unlocked)
        pages[3] = byteArrayOf(0xE1.toByte(), 0x10, (capacity / 8).toByte(), 0x00) // capability container

        val raw = msg.toByteArray()
        val tlv = (if (raw.size < 0xFF) byteArrayOf(0x03, raw.size.toByte()) else byteArrayOf(0x03, 0xFF.toByte(), (raw.size shr 8).toByte(), raw.size.toByte())) +
            raw + byteArrayOf(0xFE.toByte())
        tlv.toList().chunked(4).forEachIndexed { i, chunk -> pages[4 + i] = ByteArray(4) { k -> chunk.getOrElse(k) { 0 } } }

        // Factory configuration pages at the end of memory
        val cfg = type.pages - 5
        pages[cfg] = byteArrayOf(0x00, 0x00, 0x00, 0xBD.toByte()) // dynamic lock bytes
        pages[cfg + 1] = byteArrayOf(0x04, 0x00, 0x00, 0xFF.toByte()) // CFG0: AUTH0 = FF, no password
        pages[cfg + 2] = byteArrayOf(0x00, 0x05, 0x00, 0x00) // CFG1
        // PWD and PACK read back as zeros

        return write(uid, byteArrayOf(0x00, 0x44), 0x00, type, type.version!!, null, pages)
    }

    /** Converts a tag the phone read. Full memory for NTAG/Ultralight; UID, ATQA and SAK for others. */
    fun fromReport(r: TagReport): String {
        val d = r.dump
        if (d != null) return write(r.uid, r.atqa ?: byteArrayOf(0x00, 0x44), r.sak ?: 0, d.type, d.version, d.signature, d.pages)
        val atqa = r.atqa ?: throw IllegalArgumentException("Only NFC-A tags can be saved for the Flipper")
        return buildString {
            header("ISO14443-3A", r.uid)
            appendLine("# ISO14443-3A specific data")
            appendLine("ATQA: ${atqa.toHex()}")
            appendLine("SAK: %02X".format(r.sak ?: 0))
        }
    }

    private fun write(
        uid: ByteArray, atqa: ByteArray, sak: Int, type: UltralightType,
        version: ByteArray?, signature: ByteArray?, pages: List<ByteArray>,
    ) = buildString {
        header("NTAG/Ultralight", uid)
        appendLine("# ISO14443-3A specific data")
        appendLine("ATQA: ${atqa.toHex()}")
        appendLine("SAK: %02X".format(sak))
        appendLine("# NTAG/Ultralight specific data")
        appendLine("Data format version: 2")
        appendLine("NTAG/Ultralight type: ${type.flipperName}")
        appendLine("Signature: ${(signature ?: ByteArray(32)).toHex()}")
        appendLine("Mifare version: ${(version ?: ByteArray(8)).toHex()}")
        for (i in 0..2) {
            appendLine("Counter $i: 0")
            appendLine("Tearing $i: 00")
        }
        appendLine("Pages total: ${pages.size}")
        appendLine("Pages read: ${pages.size}")
        pages.forEachIndexed { i, p -> appendLine("Page $i: ${p.toHex()}") }
        appendLine("Failed authentication attempts: 0")
    }

    private fun StringBuilder.header(device: String, uid: ByteArray) {
        appendLine("Filetype: Flipper NFC device")
        appendLine("Version: 4")
        appendLine("# Device type can be ISO14443-3A, ISO14443-3B, ISO14443-4A, ISO15693-3, FeliCa, NTAG/Ultralight, Mifare Classic, Mifare DESFire, SLIX, ST25TB")
        appendLine("Device type: $device")
        appendLine("# UID is common for all formats")
        appendLine("UID: ${uid.toHex()}")
        appendLine("# Generated by EMITT")
    }

    fun parse(text: String): FlipperNfc {
        val fields = LinkedHashMap<String, String>()
        text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.forEach { line ->
            val i = line.indexOf(':')
            if (i > 0) fields[line.substring(0, i).trim()] = line.substring(i + 1).trim()
        }
        require(fields["Filetype"] == "Flipper NFC device") { "Not a Flipper NFC file" }
        val device = fields["Device type"] ?: error("Missing device type")
        val uid = fields["UID"]?.hexToBytes() ?: error("Missing UID")
        val ul = if (device == "NTAG/Ultralight" || device.startsWith("NTAG") || device.startsWith("Mifare Ultralight")) {
            val name = fields["NTAG/Ultralight type"] ?: device
            UltralightType.entries.firstOrNull { it.flipperName == name }
        } else {
            null
        }
        val pages = if (ul != null) {
            val total = fields["Pages total"]?.toIntOrNull() ?: ul.pages
            (0 until total).map { fields["Page $it"]?.hexToBytes() ?: ByteArray(4) }
        } else {
            null
        }
        return FlipperNfc(device, uid, ul, pages)
    }

    /** Flipper-friendly file name: letters, digits, dash and underscore only. */
    fun safeName(name: String): String =
        name.trim().replace(Regex("[^A-Za-z0-9_-]+"), "_").trim('_').take(40).ifEmpty { "EMITT" }
}
