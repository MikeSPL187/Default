package com.metrolist.music.update

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.metrolist.music.MainActivity
import com.metrolist.music.R
import com.metrolist.music.constants.UpdateNotificationsEnabledKey
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.safeDataStoreEdit
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** Looks for a new build twice a day and tells the user once per build. */
class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.dataStore.data.first()
        if (prefs[UpdateNotificationsEnabledKey] == false) return Result.success()
        val release = WatchUpdater.checkForUpdate(force = true) ?: return Result.success()
        if (prefs[NotifiedBuildKey] == release.tag) return Result.success()
        notify(applicationContext, release)
        applicationContext.safeDataStoreEdit { it[NotifiedBuildKey] = release.tag }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "watchUpdateCheck"
        private const val NOTIFICATION_ID = 1002
        const val EXTRA_SHOW_UPDATE = "com.metrolist.music.extra.SHOW_UPDATE"
        private val NotifiedBuildKey = stringPreferencesKey("notifiedWatchUpdate")

        fun schedule(
            context: Context,
            enabled: Boolean,
        ) {
            val work = WorkManager.getInstance(context)
            if (!enabled || WatchUpdater.currentBuild == null) {
                work.cancelUniqueWork(WORK_NAME)
                return
            }
            val request =
                PeriodicWorkRequestBuilder<UpdateCheckWorker>(12, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        private fun notify(
            context: Context,
            release: WatchRelease,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val intent =
                Intent(context, MainActivity::class.java)
                    .putExtra(EXTRA_SHOW_UPDATE, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pending = PendingIntent.getActivity(context, NOTIFICATION_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val text = release.notes.firstOrNull() ?: context.getString(R.string.update_build, release.build)
            val notification =
                NotificationCompat
                    .Builder(context, "updates")
                    .setSmallIcon(R.drawable.update)
                    .setContentTitle(context.getString(R.string.update_ready_title, release.build))
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(release.notes.joinToString("\n") { "• $it" }.ifEmpty { text }))
                    .setContentIntent(pending)
                    .setAutoCancel(true)
                    .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }
}
