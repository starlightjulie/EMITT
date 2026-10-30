package com.emitt.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What a code or tag carries. The same payload can be drawn as a QR code, written to an NFC tag,
 * or packed into a Flipper Zero .nfc file.
 */
@Serializable
sealed interface Payload {
    val kind: PayloadKind

    @Serializable @SerialName("text")
    data class Text(val text: String = "") : Payload {
        override val kind get() = PayloadKind.TEXT
    }

    @Serializable @SerialName("url")
    data class Url(val url: String = "https://") : Payload {
        override val kind get() = PayloadKind.URL
    }

    @Serializable @SerialName("wifi")
    data class Wifi(
        val ssid: String = "",
        val password: String = "",
        val security: WifiSecurity = WifiSecurity.WPA,
        val hidden: Boolean = false,
    ) : Payload {
        override val kind get() = PayloadKind.WIFI
    }

    @Serializable @SerialName("contact")
    data class Contact(
        val name: String = "",
        val phone: String = "",
        val email: String = "",
        val org: String = "",
        val url: String = "",
    ) : Payload {
        override val kind get() = PayloadKind.CONTACT
    }

    @Serializable @SerialName("email")
    data class Email(val to: String = "", val subject: String = "", val body: String = "") : Payload {
        override val kind get() = PayloadKind.EMAIL
    }

    @Serializable @SerialName("sms")
    data class Sms(val number: String = "", val message: String = "") : Payload {
        override val kind get() = PayloadKind.SMS
    }

    @Serializable @SerialName("phone")
    data class Phone(val number: String = "") : Payload {
        override val kind get() = PayloadKind.PHONE
    }

    @Serializable @SerialName("geo")
    data class Geo(val lat: String = "", val lon: String = "", val label: String = "") : Payload {
        override val kind get() = PayloadKind.GEO
    }

    /** Opens (or offers to install) an Android app. On NFC this is an Android Application Record. */
    @Serializable @SerialName("app")
    data class App(val packageName: String = "") : Payload {
        override val kind get() = PayloadKind.APP
    }
}

@Serializable
enum class WifiSecurity(val label: String) { WPA("WPA/WPA2/WPA3"), WEP("WEP"), OPEN("None") }

enum class PayloadKind(val label: String) {
    TEXT("TEXT"), URL("URL"), WIFI("WIFI"), CONTACT("CONTACT"), EMAIL("EMAIL"),
    SMS("SMS"), PHONE("PHONE"), GEO("GEO"), APP("APP");

    fun blank(): Payload = when (this) {
        TEXT -> Payload.Text()
        URL -> Payload.Url()
        WIFI -> Payload.Wifi()
        CONTACT -> Payload.Contact()
        EMAIL -> Payload.Email()
        SMS -> Payload.Sms()
        PHONE -> Payload.Phone()
        GEO -> Payload.Geo()
        APP -> Payload.App()
    }
}

/** A short one-line description, used for list rows and default file names. */
fun Payload.summary(): String = when (this) {
    is Payload.Text -> text.lineSequence().firstOrNull().orEmpty()
    is Payload.Url -> url.removePrefix("https://").removePrefix("http://")
    is Payload.Wifi -> ssid
    is Payload.Contact -> name.ifBlank { phone.ifBlank { email } }
    is Payload.Email -> to
    is Payload.Sms -> number
    is Payload.Phone -> number
    is Payload.Geo -> label.ifBlank { "$lat, $lon" }
    is Payload.App -> packageName
}.trim()

/** Why this payload can't be used yet, or null when it's ready. */
fun Payload.problem(): String? = when (this) {
    is Payload.Text -> if (text.isEmpty()) "Enter some text" else null
    is Payload.Url -> if (url.isBlank() || url == "https://" || url == "http://") "Enter a link" else null
    is Payload.Wifi -> when {
        ssid.isEmpty() -> "Enter the network name"
        security != WifiSecurity.OPEN && password.isEmpty() -> "Enter the password"
        else -> null
    }
    is Payload.Contact -> if (name.isBlank() && phone.isBlank() && email.isBlank()) "Enter a name, phone or email" else null
    is Payload.Email -> if (to.isBlank()) "Enter an address" else null
    is Payload.Sms -> if (number.isBlank()) "Enter a number" else null
    is Payload.Phone -> if (number.isBlank()) "Enter a number" else null
    is Payload.Geo -> if (lat.toDoubleOrNull() == null || lon.toDoubleOrNull() == null) "Enter latitude and longitude" else null
    is Payload.App -> if (!Regex("[A-Za-z][\\w]*(\\.[A-Za-z][\\w]*)+").matches(packageName)) "Enter a package name like com.example.app" else null
}
