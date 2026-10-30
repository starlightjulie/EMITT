package com.emitt

import android.app.Application
import com.emitt.nfc.Emulator

class EmittApp : Application() {
    override fun onCreate() {
        super.onCreate()
        UpdateWorker.createChannel(this)
        UpdateWorker.schedule(this)
        Emulator.createChannel(this)
        Emulator.restore(this)
    }
}
