package com.emitt.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import com.emitt.data.Settings

/**
 * Answers NFC readers as a Type 4 tag (see [Type4Tag]). Android routes the NDEF application's
 * AID here only while [Emulator] has this service enabled.
 */
class EmulationService : HostApduService() {

    override fun processCommandApdu(apdu: ByteArray, extras: Bundle?): ByteArray {
        val tag = Emulator.tag ?: run { Emulator.restore(this); Emulator.tag } ?: return Type4Tag.SW_NOT_FOUND
        val wasRead = tag.fullyRead
        val reply = tag.process(apdu)
        if (!wasRead && tag.fullyRead) {
            Emulator.onRead()
            if (Settings(this).haptics) {
                runCatching { getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(40, 180)) }
            }
        }
        return reply
    }

    override fun onDeactivated(reason: Int) {
        Emulator.tag?.reset()
    }
}
