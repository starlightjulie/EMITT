package com.emitt.nfc

import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import com.emitt.data.Encoding
import com.emitt.data.Payload
import com.emitt.data.WifiSecurity
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/** One NDEF record, described for people. */
data class RecordInfo(val type: String, val value: String)

object NdefCodec {
    private const val WIFI_MIME = "application/vnd.wfa.wsc"

    /** Builds the message written to a tag. [launchApp], when set, adds an Android Application Record after it. */
    fun build(p: Payload, launchApp: String? = null): NdefMessage {
        val main = when (p) {
            is Payload.Text -> NdefRecord.createTextRecord("en", p.text)
            is Payload.Url -> NdefRecord.createUri(p.url.trim())
            is Payload.Phone -> NdefRecord.createUri("tel:" + p.number.trim())
            is Payload.Email -> NdefRecord.createUri(Encoding.toQrText(p))
            is Payload.Sms -> NdefRecord.createUri(
                "sms:" + p.number.trim() + if (p.message.isNotEmpty()) "?body=" + Uri.encode(p.message) else "",
            )
            is Payload.Geo -> NdefRecord.createUri(Encoding.toQrText(p))
            is Payload.Contact -> NdefRecord.createMime("text/vcard", Encoding.vCard(p).toByteArray())
            is Payload.Wifi -> NdefRecord.createMime(WIFI_MIME, wifiCredential(p))
            is Payload.App -> NdefRecord.createApplicationRecord(p.packageName.trim())
        }
        val extra = launchApp?.trim()?.takeIf { it.isNotEmpty() && p !is Payload.App }?.let { NdefRecord.createApplicationRecord(it) }
        return if (extra != null) NdefMessage(main, extra) else NdefMessage(main)
    }

    /** The first record we understand, turned back into an editable payload. */
    fun toPayload(msg: NdefMessage): Payload? = msg.records.firstNotNullOfOrNull { r ->
        when {
            r.tnf == NdefRecord.TNF_WELL_KNOWN && r.type.contentEquals(NdefRecord.RTD_TEXT) -> Payload.Text(textOf(r))
            r.toUri() != null -> Encoding.parse(r.toUri().toString())
            r.tnf == NdefRecord.TNF_MIME_MEDIA && r.mime() == WIFI_MIME -> parseWifi(r.payload)
            r.tnf == NdefRecord.TNF_MIME_MEDIA && r.mime() in setOf("text/vcard", "text/x-vcard") ->
                Encoding.parse(String(r.payload))
            r.tnf == NdefRecord.TNF_EXTERNAL_TYPE && String(r.type) == "android.com:pkg" -> Payload.App(String(r.payload))
            else -> null
        }
    }

    fun describe(msg: NdefMessage): List<RecordInfo> = msg.records.map { r ->
        when {
            r.tnf == NdefRecord.TNF_EMPTY -> RecordInfo("EMPTY", "")
            r.tnf == NdefRecord.TNF_WELL_KNOWN && r.type.contentEquals(NdefRecord.RTD_TEXT) -> RecordInfo("TEXT", textOf(r))
            r.toUri() != null -> RecordInfo("URI", r.toUri().toString())
            r.tnf == NdefRecord.TNF_MIME_MEDIA && r.mime() == WIFI_MIME ->
                RecordInfo("WIFI", parseWifi(r.payload)?.let { "${it.ssid} · ${it.security.name}" } ?: "Wi-Fi")
            r.tnf == NdefRecord.TNF_MIME_MEDIA -> RecordInfo(
                r.mime().uppercase(),
                if (r.mime().startsWith("text/")) String(r.payload) else "${r.payload.size} B",
            )
            r.tnf == NdefRecord.TNF_EXTERNAL_TYPE && String(r.type) == "android.com:pkg" -> RecordInfo("APP", String(r.payload))
            r.tnf == NdefRecord.TNF_EXTERNAL_TYPE -> RecordInfo(String(r.type), "${r.payload.size} B")
            else -> RecordInfo("TNF ${r.tnf}", r.payload.toHex())
        }
    }

    private fun NdefRecord.mime() = String(type, Charsets.US_ASCII).lowercase()

    private fun textOf(r: NdefRecord): String {
        val p = r.payload
        if (p.isEmpty()) return ""
        val utf16 = p[0].toInt() and 0x80 != 0
        val langLen = p[0].toInt() and 0x3F
        return String(p, 1 + langLen, p.size - 1 - langLen, if (utf16) Charsets.UTF_16 else Charset.forName("UTF-8"))
    }

    // Wi-Fi Simple Configuration credential, the format Android reads from Wi-Fi tags.
    private fun wifiCredential(w: Payload.Wifi): ByteArray {
        val (auth, enc) = when (w.security) {
            WifiSecurity.WPA -> 0x0022 to 0x000C // WPA-PSK|WPA2-PSK, TKIP|AES
            WifiSecurity.WEP -> 0x0001 to 0x0002
            WifiSecurity.OPEN -> 0x0001 to 0x0001
        }
        val cred = ByteArrayOutputStream().apply {
            tlv(0x1026, byteArrayOf(1)) // network index
            tlv(0x1045, w.ssid.toByteArray())
            tlv(0x1003, u16(auth))
            tlv(0x100F, u16(enc))
            tlv(0x1027, w.password.toByteArray())
            tlv(0x1020, ByteArray(6) { 0xFF.toByte() }) // any MAC
        }.toByteArray()
        return ByteArrayOutputStream().apply {
            tlv(0x104A, byteArrayOf(0x10)) // version 1.0
            tlv(0x100E, cred)
        }.toByteArray()
    }

    private fun parseWifi(data: ByteArray): Payload.Wifi? {
        fun walk(b: ByteArray, out: MutableMap<Int, ByteArray>) {
            var i = 0
            while (i + 4 <= b.size) {
                val t = (b[i].toInt() and 0xFF shl 8) or (b[i + 1].toInt() and 0xFF)
                val l = (b[i + 2].toInt() and 0xFF shl 8) or (b[i + 3].toInt() and 0xFF)
                if (i + 4 + l > b.size) break
                val v = b.copyOfRange(i + 4, i + 4 + l)
                if (t == 0x100E) walk(v, out) else out[t] = v
                i += 4 + l
            }
        }
        val f = mutableMapOf<Int, ByteArray>()
        walk(data, f)
        val ssid = f[0x1045]?.let { String(it) } ?: return null
        val key = f[0x1027]?.let { String(it) }.orEmpty()
        val auth = f[0x1003]?.let { (it[0].toInt() and 0xFF shl 8) or (it[1].toInt() and 0xFF) } ?: 1
        val enc = f[0x100F]?.let { (it[0].toInt() and 0xFF shl 8) or (it[1].toInt() and 0xFF) } ?: 1
        val sec = when {
            auth and 0x0022 != 0 || auth and 0x0002 != 0 || auth and 0x0020 != 0 -> WifiSecurity.WPA
            enc == 0x0002 -> WifiSecurity.WEP
            else -> WifiSecurity.OPEN
        }
        return Payload.Wifi(ssid, key, sec)
    }

    private fun ByteArrayOutputStream.tlv(type: Int, value: ByteArray) {
        write(u16(type)); write(u16(value.size)); write(value)
    }

    private fun u16(v: Int) = byteArrayOf((v shr 8).toByte(), v.toByte())
}

fun ByteArray.toHex(sep: String = " "): String = joinToString(sep) { "%02X".format(it) }

fun String.hexToBytes(): ByteArray? {
    val clean = filterNot { it.isWhitespace() || it == ':' }
    if (clean.length % 2 != 0 || clean.any { it.digitToIntOrNull(16) == null }) return null
    return ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
