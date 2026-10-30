package com.emitt.nfc

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NdefMessage
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.emitt.MainActivity
import com.emitt.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the phone is currently pretending to be. [reads] counts readers that got the whole message. */
data class Emulation(val label: String, val ndef: ByteArray, val reads: Int = 0)

/**
 * Turns the phone into an NFC tag. The [EmulationService] is only enabled while emulating, so
 * EMITT never answers NFC readers unless the user started it. The active message survives the app
 * being closed; it keeps working until Stop (from the app or the notification).
 */
object Emulator {
    private const val CHANNEL = "emulation"
    private const val NOTIFICATION_ID = 2
    private val _state = MutableStateFlow<Emulation?>(null)
    val state: StateFlow<Emulation?> = _state.asStateFlow()

    @Volatile var tag: Type4Tag? = null
        private set

    fun supported(context: Context) =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)

    /** Restores an emulation that was running when the app was last closed. */
    fun restore(context: Context) {
        val prefs = prefs(context)
        val data = prefs.getString("ndef", null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        if (data == null) {
            setService(context, false)
            return
        }
        tag = Type4Tag(data)
        _state.value = Emulation(prefs.getString("label", "tag").orEmpty(), data)
    }

    fun start(context: Context, message: NdefMessage, label: String) {
        val data = message.toByteArray()
        prefs(context).edit {
            putString("ndef", Base64.encodeToString(data, Base64.NO_WRAP))
            putString("label", label)
        }
        tag = Type4Tag(data)
        _state.value = Emulation(label, data)
        setService(context, true)
        notify(context, label)
    }

    fun stop(context: Context) {
        prefs(context).edit { clear() }
        tag = null
        _state.value = null
        setService(context, false)
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /** Called by the service when a reader has read the whole message. */
    internal fun onRead() {
        _state.value = _state.value?.let { it.copy(reads = it.reads + 1) }
    }

    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.channel_emulation), NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("emulation", Context.MODE_PRIVATE)

    private fun setService(context: Context, on: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, EmulationService::class.java),
            if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    private fun notify(context: Context, label: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(
            context, 3, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getBroadcast(
            context, 4, Intent(context, StopEmulationReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_emitt)
            .setContentTitle("Your phone is an NFC tag")
            .setContentText("Emitting $label. Other phones can tap to read it while the screen is on.")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n)
    }
}

class StopEmulationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Emulator.stop(context)
}
