package com.emitt.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * Tells official EMITT builds (signed with the creator's release key) apart from rebuilds
 * published elsewhere. Anyone can build the open-source code, but only official builds carry
 * this certificate.
 */
object OfficialBuild {
    /** SHA-256 of StarlightJulie's EMITT release signing certificate. */
    const val CERT_SHA256 = "398F58735645892D8F1412EE77F681CBC6552788323677BB8B784974DDD8D552"

    fun isOfficial(context: Context): Boolean = runCatching {
        val pm = context.packageManager
        val certs = if (Build.VERSION.SDK_INT >= 28) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners.orEmpty()
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures.orEmpty()
        }
        certs.any { sig ->
            MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).joinToString("") { "%02X".format(it) } == CERT_SHA256
        }
    }.getOrDefault(false)
}
