package com.emitt.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import com.emitt.HapticEvent
import com.emitt.Haptics

/**
 * Answers NFC readers as a Type 4 tag (see [Type4Tag]). Android routes the NDEF application's
 * AID here only while [Emulator] has this service enabled.
 */
class EmulationService : HostApduService() {

    override fun processCommandApdu(apdu: ByteArray, extras: Bundle?): ByteArray {
        val tag = Emulator.tag ?: run { Emulator.restore(this); Emulator.tag } ?: return Type4Tag.SW_NOT_FOUND
        val wasRead = tag.fullyRead
        val reply = tag.process(apdu)
        android.util.Log.i("EMITT/HCE", "rx ${apdu.toHex("")} -> tx ${reply.takeLast(2).toByteArray().toHex("")} (${reply.size - 2} data bytes)")
        if (!wasRead && tag.fullyRead) {
            Emulator.onRead()
            Haptics.play(this, HapticEvent.EMULATED)
        }
        return reply
    }

    override fun onDeactivated(reason: Int) {
        android.util.Log.i("EMITT/HCE", "deactivated reason=$reason")
        Emulator.tag?.reset()
    }
}
