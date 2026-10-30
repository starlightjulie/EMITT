package com.emitt.data

import android.net.Uri

/**
 * Turns payloads into the text formats phone cameras understand (and back again for scanned codes).
 * NFC uses its own record types, see [com.emitt.nfc.NdefCodec].
 */
object Encoding {

    fun toQrText(p: Payload): String = when (p) {
        is Payload.Text -> p.text
        is Payload.Url -> p.url.trim()
        is Payload.Wifi -> buildString {
            append("WIFI:T:").append(
                when (p.security) {
                    WifiSecurity.WPA -> "WPA"
                    WifiSecurity.WEP -> "WEP"
                    WifiSecurity.OPEN -> "nopass"
                },
            )
            append(";S:").append(wifiEscape(p.ssid))
            if (p.security != WifiSecurity.OPEN) append(";P:").append(wifiEscape(p.password))
            if (p.hidden) append(";H:true")
            append(";;")
        }
        is Payload.Contact -> vCard(p)
        is Payload.Email -> buildString {
            append("mailto:").append(p.to.trim())
            val q = listOfNotNull(
                p.subject.takeIf { it.isNotBlank() }?.let { "subject=" + Uri.encode(it) },
                p.body.takeIf { it.isNotBlank() }?.let { "body=" + Uri.encode(it) },
            )
            if (q.isNotEmpty()) append('?').append(q.joinToString("&"))
        }
        is Payload.Sms -> "SMSTO:${p.number.trim()}:${p.message}"
        is Payload.Phone -> "tel:${p.number.trim()}"
        is Payload.Geo -> buildString {
            append("geo:").append(p.lat.trim()).append(',').append(p.lon.trim())
            if (p.label.isNotBlank()) append("?q=").append(p.lat.trim()).append(',').append(p.lon.trim())
                .append('(').append(Uri.encode(p.label)).append(')')
        }
        is Payload.App -> "https://play.google.com/store/apps/details?id=${p.packageName.trim()}"
    }

    fun vCard(p: Payload.Contact): String = buildString {
        append("BEGIN:VCARD\r\nVERSION:3.0\r\n")
        append("FN:").append(vEscape(p.name)).append("\r\n")
        append("N:").append(vEscape(p.name)).append(";;;;\r\n")
        if (p.org.isNotBlank()) append("ORG:").append(vEscape(p.org)).append("\r\n")
        if (p.phone.isNotBlank()) append("TEL:").append(p.phone.trim()).append("\r\n")
        if (p.email.isNotBlank()) append("EMAIL:").append(p.email.trim()).append("\r\n")
        if (p.url.isNotBlank()) append("URL:").append(p.url.trim()).append("\r\n")
        append("END:VCARD")
    }

    /** Best guess at what a scanned or read string is, so it can be edited and re-emitted. */
    fun parse(raw: String): Payload {
        val s = raw.trim()
        val lower = s.lowercase()
        return when {
            lower.startsWith("http://") || lower.startsWith("https://") -> Payload.Url(s)
            lower.startsWith("wifi:") -> parseWifi(s)
            lower.startsWith("begin:vcard") -> parseVCard(s)
            lower.startsWith("mailto:") -> {
                val uri = Uri.parse(s)
                Payload.Email(
                    to = s.substringAfter(':').substringBefore('?'),
                    subject = uri.getQueryParameterSafe("subject"),
                    body = uri.getQueryParameterSafe("body"),
                )
            }
            lower.startsWith("smsto:") -> s.substringAfter(':').let {
                Payload.Sms(it.substringBefore(':'), it.substringAfter(':', ""))
            }
            lower.startsWith("sms:") -> Payload.Sms(
                s.substringAfter(':').substringBefore('?'),
                Uri.parse(s).getQueryParameterSafe("body"),
            )
            lower.startsWith("tel:") -> Payload.Phone(s.substringAfter(':'))
            lower.startsWith("geo:") -> {
                val coords = s.substringAfter(':').substringBefore('?').split(',')
                val label = s.substringAfter("(", "").substringBefore(")")
                Payload.Geo(coords.getOrElse(0) { "" }, coords.getOrElse(1) { "" }, Uri.decode(label))
            }
            else -> Payload.Text(raw)
        }
    }

    private fun Uri.getQueryParameterSafe(key: String): String =
        runCatching { if (isHierarchical) getQueryParameter(key) else null }.getOrNull()
            ?: Regex("[?&]$key=([^&]*)").find(toString())?.groupValues?.get(1)?.let { Uri.decode(it) }
            ?: ""

    private fun parseWifi(s: String): Payload.Wifi {
        val fields = mutableMapOf<String, String>()
        var i = s.indexOf(':') + 1
        while (i < s.length) {
            val colon = s.indexOf(':', i).takeIf { it > 0 } ?: break
            val key = s.substring(i, colon).uppercase()
            val value = StringBuilder()
            var j = colon + 1
            while (j < s.length && s[j] != ';') {
                if (s[j] == '\\' && j + 1 < s.length) j++
                value.append(s[j]); j++
            }
            fields[key] = value.toString()
            i = j + 1
            if (i < s.length && s[i] == ';') break
        }
        val sec = when (fields["T"]?.uppercase()) {
            "WEP" -> WifiSecurity.WEP
            "NOPASS", "", null -> if (fields["P"].isNullOrEmpty()) WifiSecurity.OPEN else WifiSecurity.WPA
            else -> WifiSecurity.WPA
        }
        return Payload.Wifi(fields["S"].orEmpty(), fields["P"].orEmpty(), sec, fields["H"].equals("true", true))
    }

    private fun parseVCard(s: String): Payload.Contact {
        fun field(name: String) = s.lineSequence()
            .firstOrNull { it.uppercase().startsWith("$name:") || it.uppercase().startsWith("$name;") }
            ?.substringAfter(':')?.trim()?.replace("\\,", ",")?.replace("\\;", ";").orEmpty()
        return Payload.Contact(
            name = field("FN").ifBlank { field("N").replace(";", " ").trim() },
            phone = field("TEL"),
            email = field("EMAIL"),
            org = field("ORG"),
            url = field("URL"),
        )
    }

    private fun wifiEscape(s: String) = s.replace(Regex("""([\\;,:"])"""), """\\$1""")
    private fun vEscape(s: String) = s.trim().replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;")
}
