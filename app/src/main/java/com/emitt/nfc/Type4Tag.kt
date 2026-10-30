package com.emitt.nfc

/**
 * A read-only NFC Forum Type 4 Tag holding one NDEF message, answered over ISO 7816-4 APDUs.
 * This is what lets the phone itself be "a tag": other phones read it exactly like a sticker.
 * (NFC Forum Type 4 Tag Operation spec, mapping version 2.0.)
 */
class Type4Tag(ndef: ByteArray) {
    private val ndefFile = byteArrayOf((ndef.size shr 8).toByte(), ndef.size.toByte()) + ndef

    // Capability container: version 2.0, max read/write chunk, then the NDEF file's control TLV
    private val ccFile = byteArrayOf(
        0x00, 0x0F, // CC length
        0x20, // mapping version 2.0
        0x00, MAX_LE.toByte(), // MLe: most bytes we return per READ BINARY
        0x00, MAX_LE.toByte(), // MLc
        0x04, 0x06, // NDEF file control TLV
        0xE1.toByte(), 0x04, // NDEF file id
        (ndefFile.size shr 8).toByte(), ndefFile.size.toByte(), // max NDEF file size
        0x00, // read access: open
        0xFF.toByte(), // write access: none (read-only tag)
    )

    private var appSelected = false
    private var file: ByteArray? = null

    /** True once a reader has read the last byte of the NDEF message in this session. */
    var fullyRead = false
        private set

    fun reset() {
        appSelected = false
        file = null
        fullyRead = false
    }

    fun process(apdu: ByteArray): ByteArray {
        if (apdu.size < 4) return SW_WRONG_LENGTH
        if (apdu[0] != 0x00.toByte()) return SW_CLA_NOT_SUPPORTED
        val p1 = apdu[2].toInt() and 0xFF
        val p2 = apdu[3].toInt() and 0xFF
        return when (apdu[1].toInt() and 0xFF) {
            0xA4 -> select(apdu, p1)
            0xB0 -> read(apdu, (p1 shl 8) or p2)
            0xD6 -> SW_SECURITY // UPDATE BINARY: this tag is read-only
            else -> SW_INS_NOT_SUPPORTED
        }
    }

    private fun select(apdu: ByteArray, p1: Int): ByteArray {
        val lc = if (apdu.size > 4) apdu[4].toInt() and 0xFF else return SW_WRONG_LENGTH
        if (apdu.size < 5 + lc) return SW_WRONG_LENGTH
        val data = apdu.copyOfRange(5, 5 + lc)
        return when (p1) {
            0x04 -> if (data.contentEquals(NDEF_AID)) { appSelected = true; file = null; SW_OK } else SW_NOT_FOUND
            // P1 00 = select by file id (what Android sends); 02 = select EF under the current DF
            // (what the Flipper and other ISO 7816 readers send). Both mean the same thing here.
            0x00, 0x02 -> when {
                !appSelected -> SW_NOT_FOUND
                data.contentEquals(CC_ID) -> { file = ccFile; SW_OK }
                data.contentEquals(NDEF_ID) -> { file = ndefFile; SW_OK }
                else -> SW_NOT_FOUND
            }
            else -> SW_WRONG_P1P2
        }
    }

    private fun read(apdu: ByteArray, offset: Int): ByteArray {
        val f = file ?: return SW_NOT_ALLOWED
        if (offset > f.size) return SW_WRONG_P1P2
        val le = if (apdu.size > 4) (apdu[apdu.size - 1].toInt() and 0xFF).let { if (it == 0) 256 else it } else 256
        val n = minOf(le, MAX_LE, f.size - offset)
        if (f === ndefFile && offset + n >= f.size) fullyRead = true
        return f.copyOfRange(offset, offset + n) + SW_OK
    }

    companion object {
        const val MAX_LE = 0xF6
        val NDEF_AID = byteArrayOf(0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01)
        private val CC_ID = byteArrayOf(0xE1.toByte(), 0x03)
        private val NDEF_ID = byteArrayOf(0xE1.toByte(), 0x04)

        val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
        val SW_NOT_FOUND = byteArrayOf(0x6A, 0x82.toByte())
        val SW_WRONG_P1P2 = byteArrayOf(0x6B, 0x00)
        val SW_WRONG_LENGTH = byteArrayOf(0x67, 0x00)
        val SW_SECURITY = byteArrayOf(0x69, 0x82.toByte())
        val SW_NOT_ALLOWED = byteArrayOf(0x69, 0x86.toByte())
        val SW_INS_NOT_SUPPORTED = byteArrayOf(0x6D, 0x00)
        val SW_CLA_NOT_SUPPORTED = byteArrayOf(0x6E, 0x00)
    }
}
