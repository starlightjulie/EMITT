package com.emitt

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.emitt.data.AppUpdate
import com.emitt.data.Settings
import com.emitt.data.UpdateChecker
import com.emitt.data.UpdateResult
import java.util.concurrent.TimeUnit

/**
 * Periodically asks GitHub for a newer release and posts one notification per new version.
 * Tapping it opens the Releases page.
 */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = Settings(applicationContext)
        if (!settings.checkUpdates) return Result.success()
        val result = UpdateChecker.check(BuildConfig.VERSION_NAME)
        if (result is UpdateResult.Available) notifyOnce(applicationContext, result.update)
        return Result.success()
    }

    companion object {
        private const val CHANNEL = "updates"
        private const val WORK = "emitt-update-check"
        private const val NOTIFICATION_ID = 1

        /** (Re)schedules the background check to match the current settings. */
        fun schedule(context: Context, replace: Boolean = false) {
            val settings = Settings(context)
            val wm = WorkManager.getInstance(context)
            if (!settings.checkUpdates) {
                wm.cancelUniqueWork(WORK)
                return
            }
            val req = PeriodicWorkRequestBuilder<UpdateWorker>(settings.updateIntervalHours.toLong(), TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(
                WORK,
                if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
                req,
            )
        }

        fun createChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.channel_updates), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }

        /** Posts the "new version" notification unless this version was already announced. */
        fun notifyOnce(context: Context, update: AppUpdate) {
            val settings = Settings(context)
            if (settings.notifiedVersion == update.version) return
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(Intent.ACTION_VIEW, Uri.parse(UpdateChecker.LATEST_RELEASE_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_emitt)
                .setContentTitle("EMITT ${update.version} is out")
                .setContentText(update.title)
                .setStyle(NotificationCompat.BigTextStyle().bigText(update.notes.ifBlank { update.title }.take(600)))
                .setContentIntent(open)
                .addAction(0, "Open release", open)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n)
            settings.notifiedVersion = update.version
        }
    }
}
