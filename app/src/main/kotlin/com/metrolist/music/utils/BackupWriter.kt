package com.metrolist.music.utils

import android.content.Context
import com.metrolist.music.db.InternalDatabase
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.extensions.div
import com.metrolist.music.extensions.zipOutputStream
import com.metrolist.music.viewmodels.BackupRestoreViewModel
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.util.zip.ZipEntry

/**
 * Writes a full backup to [output]: the settings, the artist name aliases and the database with
 * its journal. Manual backups and the weekly automatic one share this, so they restore the same.
 */
fun writeBackup(
    context: Context,
    database: MusicDatabase,
    output: OutputStream,
) {
    output.buffered().zipOutputStream().use { outputStream ->
        (context.filesDir / "datastore" / BackupRestoreViewModel.SETTINGS_FILENAME).inputStream().buffered().use { inputStream ->
            outputStream.putNextEntry(ZipEntry(BackupRestoreViewModel.SETTINGS_FILENAME))
            inputStream.copyTo(outputStream)
        }
        outputStream.putNextEntry(ZipEntry(ArtistNameAliases.BACKUP_FILENAME))
        outputStream.write(ArtistNameAliases.serialize().encodeToByteArray())
        database.checkpoint()
        val dbPath = database.openHelper.writableDatabase.path ?: return
        FileInputStream(dbPath).use { inputStream ->
            outputStream.putNextEntry(ZipEntry(InternalDatabase.DB_NAME))
            inputStream.copyTo(outputStream)
        }
        for (suffix in listOf("-wal", "-shm")) {
            val file = File(dbPath + suffix)
            if (file.exists()) {
                FileInputStream(file).use { inputStream ->
                    outputStream.putNextEntry(ZipEntry(InternalDatabase.DB_NAME + suffix))
                    inputStream.copyTo(outputStream)
                }
            }
        }
    }
}
