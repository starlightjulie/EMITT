package com.emitt.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.nfc.tech.NfcA
import java.io.IOException

/** NTAG/Ultralight chips EMITT can fully dump and that the Flipper emulates. */
enum class UltralightType(val flipperName: String, val pages: Int, val version: ByteArray?) {
    UL("Mifare Ultralight", 16, null),
    UL11("Mifare Ultralight 11", 20, bytes(0x00, 0x04, 0x03, 0x01, 0x01, 0x00, 0x0B, 0x03)),
    UL21("Mifare Ultralight 21", 41, bytes(0x00, 0x04, 0x03, 0x01, 0x01, 0x00, 0x0E, 0x03)),
    NTAG213("NTAG213", 45, bytes(0x00, 0x04, 0x04, 0x02, 0x01, 0x00, 0x0F, 0x03)),
    NTAG215("NTAG215", 135, bytes(0x00, 0x04, 0x04, 0x02, 0x01, 0x00, 0x11, 0x03)),
    NTAG216("NTAG216", 231, bytes(0x00, 0x04, 0x04, 0x02, 0x01, 0x00, 0x13, 0x03)),
}

private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

/** The raw memory of an NTAG/Ultralight, enough to rebuild it as a Flipper file. */
class UltralightDump(
    val type: UltralightType,
    val pages: List<ByteArray>,
    val version: ByteArray?,
    val signature: ByteArray?,
)

/** Everything we could find out about a tag. */
class TagReport(
    val uid: ByteArray,
    val techs: List<String>,
    val chip: String,
    val atqa: ByteArray?,
    val sak: Int?,
    val ndefType: String?,
    val maxSize: Int?,
    val writable: Boolean?,
    val canLock: Boolean?,
    val message: NdefMessage?,
    val dump: UltralightDump?,
) {
    val usedBytes get() = message?.byteArrayLength
}

sealed interface TagResult {
    data class Read(val report: TagReport) : TagResult
    data class Written(val bytes: Int, val capacity: Int?) : TagResult
    data object Erased : TagResult
    data object Locked : TagResult
    data class Failed(val reason: String) : TagResult
}

object TagOps {

    fun read(tag: Tag): TagResult = runCatching { TagResult.Read(inspect(tag)) }
        .getOrElse { TagResult.Failed(it.message ?: "Couldn't read the tag. Hold it still and try again.") }

    fun write(tag: Tag, msg: NdefMessage): TagResult = try {
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            ndef.connect()
            ndef.use {
                when {
                    !it.isWritable -> TagResult.Failed("This tag is read-only.")
                    msg.byteArrayLength > it.maxSize -> TagResult.Failed("Too big: ${msg.byteArrayLength} bytes, tag holds ${it.maxSize}.")
                    else -> { it.writeNdefMessage(msg); TagResult.Written(msg.byteArrayLength, it.maxSize) }
                }
            }
        } else {
            val f = NdefFormatable.get(tag) ?: return TagResult.Failed("This tag doesn't support NDEF.")
            f.connect()
            f.use { it.format(msg) }
            TagResult.Written(msg.byteArrayLength, null)
        }
    } catch (e: IOException) {
        TagResult.Failed("Lost the tag mid-write. Hold it still and try again.")
    } catch (e: Exception) {
        TagResult.Failed(e.message ?: "Write failed.")
    }

    /** Replaces the content with a single empty record (the tag stays usable). */
    fun erase(tag: Tag): TagResult =
        when (val r = write(tag, NdefMessage(NdefRecord(NdefRecord.TNF_EMPTY, null, null, null)))) {
            is TagResult.Written -> TagResult.Erased
            else -> r
        }

    /** Permanently makes the tag read-only. */
    fun lock(tag: Tag): TagResult = try {
        val ndef = Ndef.get(tag) ?: return TagResult.Failed("This tag has no NDEF data to lock.")
        ndef.connect()
        ndef.use {
            when {
                !it.canMakeReadOnly() -> TagResult.Failed("This tag can't be locked.")
                it.makeReadOnly() -> TagResult.Locked
                else -> TagResult.Failed("The tag refused to lock.")
            }
        }
    } catch (e: Exception) {
        TagResult.Failed("Lost the tag. Hold it still and try again.")
    }

    private fun inspect(tag: Tag): TagReport {
        val techs = tag.techList.map { it.substringAfterLast('.') }
        val nfcA = NfcA.get(tag)
        val atqa = nfcA?.atqa?.reversedArray() // Android gives LSB first, Flipper files want MSB first
        val sak = nfcA?.sak?.toInt()

        var message: NdefMessage? = null
        var ndefType: String? = null
        var max: Int? = null
        var writable: Boolean? = null
        var canLock: Boolean? = null
        Ndef.get(tag)?.let { ndef ->
            runCatching {
                ndef.connect()
                ndef.use {
                    message = it.cachedNdefMessage ?: runCatching { it.ndefMessage }.getOrNull()
                    ndefType = it.type.substringAfterLast('.')
                    max = it.maxSize
                    writable = it.isWritable
                    canLock = it.canMakeReadOnly()
                }
            }
        }

        val dump = if (MifareUltralight.get(tag) != null || (nfcA != null && sak == 0 && tag.id.size == 7)) {
            runCatching { dumpUltralight(tag) }.getOrNull()
        } else {
            null
        }
        val chip = dump?.type?.flipperName ?: when {
            MifareClassic.get(tag) != null -> "MIFARE Classic"
            IsoDep.get(tag) != null -> "ISO 14443-4"
            "NfcV" in techs -> "ISO 15693 (NFC-V)"
            "NfcF" in techs -> "FeliCa (NFC-F)"
            "NfcB" in techs -> "ISO 14443-3B"
            nfcA != null -> "ISO 14443-3A"
            else -> "Unknown"
        }
        return TagReport(tag.id, techs, chip, atqa, sak, ndefType, max, writable, canLock, message, dump)
    }

    private fun dumpUltralight(tag: Tag): UltralightDump {
        val a = NfcA.get(tag)
        a.connect()
        return a.use {
            it.timeout = 600
            val version = runCatching { it.transceive(byteArrayOf(0x60)) }.getOrNull()?.takeIf { v -> v.size == 8 }
            val type = when {
                version == null -> UltralightType.UL
                else -> UltralightType.entries.firstOrNull { t -> t.version?.contentEquals(version) == true }
                    ?: UltralightType.entries.firstOrNull { t -> t.version?.get(6) == version[6] }
                    ?: UltralightType.UL
            }
            // GET_VERSION on an old Ultralight NAKs and drops the connection: reconnect before reading pages
            if (version == null) { it.close(); it.connect() }
            val pages = ArrayList<ByteArray>(type.pages)
            var p = 0
            while (p < type.pages) {
                val block = it.transceive(byteArrayOf(0x30, p.toByte())) // READ returns 4 pages
                for (k in 0 until 4) if (p + k < type.pages) pages += block.copyOfRange(k * 4, k * 4 + 4)
                p += 4
            }
            val sig = if (version != null) runCatching { it.transceive(byteArrayOf(0x3C, 0x00)) }.getOrNull()?.takeIf { s -> s.size == 32 } else null
            UltralightDump(type, pages, version, sig)
        }
    }
}
