package com.metrolist.music.utils

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.metrolist.music.constants.AutoBackupKey
import com.metrolist.music.constants.LastAutoBackupKey
import com.metrolist.music.di.DatabaseEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Once a week, while the phone charges or has battery to spare, saves a full backup to
 * Downloads/Metrolist backups and keeps the last [KEEP] of them. Survives a reinstall, so the
 * library, likes and settings can always come back.
 */
class AutoBackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (applicationContext.dataStore.data.first()[AutoBackupKey] == false) return Result.success()
        val database = EntryPointAccessors.fromApplication(applicationContext, DatabaseEntryPoint::class.java).database()
        val name = PREFIX + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".backup"
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveToDownloads(database, name) else saveToAppFolder(database, name)
            applicationContext.safeDataStoreEdit { it[LastAutoBackupKey] = System.currentTimeMillis() }
        }.fold(
            onSuccess = { Result.success() },
            onFailure = {
                Timber.w(it, "Automatic backup failed")
                Result.retry()
            },
        )
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToDownloads(
        database: com.metrolist.music.db.MusicDatabase,
        name: String,
    ) {
        val resolver = applicationContext.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val values =
            ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                put(MediaStore.Downloads.RELATIVE_PATH, RELATIVE_PATH)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        val uri = resolver.insert(collection, values) ?: error("Couldn't create $name")
        try {
            resolver.openOutputStream(uri)?.use { writeBackup(applicationContext, database, it) } ?: error("Couldn't open $name")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
        // Only the newest copies stay; the app can remove files it created itself.
        resolver
            .query(
                collection,
                arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.RELATIVE_PATH} = ? AND ${MediaStore.Downloads.DISPLAY_NAME} LIKE ?",
                arrayOf(RELATIVE_PATH, "$PREFIX%"),
                "${MediaStore.Downloads.DATE_ADDED} DESC",
            )?.use { cursor ->
                var index = 0
                while (cursor.moveToNext()) {
                    if (index++ >= KEEP) {
                        resolver.delete(android.content.ContentUris.withAppendedId(collection, cursor.getLong(0)), null, null)
                    }
                }
            }
    }

    private fun saveToAppFolder(
        database: com.metrolist.music.db.MusicDatabase,
        name: String,
    ) {
        val folder = File(applicationContext.getExternalFilesDir(null), "backups").apply { mkdirs() }
        File(folder, name).outputStream().use { writeBackup(applicationContext, database, it) }
        folder
            .listFiles { file -> file.name.startsWith(PREFIX) }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(KEEP)
            ?.forEach { it.delete() }
    }

    companion object {
        private const val WORK_NAME = "autoBackup"
        private const val PREFIX = "metrolist_auto_"
        private const val KEEP = 3
        val RELATIVE_PATH = Environment.DIRECTORY_DOWNLOADS + "/Metrolist backups/"

        fun schedule(
            context: Context,
            enabled: Boolean,
        ) {
            val work = WorkManager.getInstance(context)
            if (!enabled) {
                work.cancelUniqueWork(WORK_NAME)
                return
            }
            val request =
                PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS)
                    .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).build())
                    .build()
            work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
