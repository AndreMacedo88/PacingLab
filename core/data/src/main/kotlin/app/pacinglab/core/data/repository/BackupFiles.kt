package app.pacinglab.core.data.repository

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/** Writes and reads backups in files the athlete picked with the system file picker. */
class BackupFiles @Inject constructor(@param:ApplicationContext private val context: Context, private val backup: BackupRepository) {
    /** Saves a backup to [uri]; throws [IOException] when the file cannot be written. */
    suspend fun save(uri: Uri): BackupSummary {
        val (text, summary) = backup.export()
        withContext(Dispatchers.IO) {
            val stream = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot open $uri")
            stream.bufferedWriter().use { it.write(text) }
        }
        return summary
    }

    /** Restores the backup in [uri]; throws [IOException] or [BackupFormatException]. */
    suspend fun restore(uri: Uri): BackupSummary {
        val text = withContext(Dispatchers.IO) {
            val stream = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
            stream.bufferedReader().use { it.readText() }
        }
        return backup.restore(text)
    }
}
