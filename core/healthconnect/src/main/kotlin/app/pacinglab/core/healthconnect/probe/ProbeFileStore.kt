package app.pacinglab.core.healthconnect.probe

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Clock
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Writes probe output to the app's private files directory (files/probe/). From there it can be
 * shared through the system share sheet or pulled with `adb shell run-as` on debug builds.
 */
class ProbeFileStore @Inject constructor(@param:ApplicationContext private val context: Context, private val clock: Clock) {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    val directory: File get() = File(context.filesDir, DIRECTORY)

    suspend fun save(report: ProbeReport): File {
        val content = json.encodeToString(ProbeReport.serializer(), report)
        return write("report", content)
    }

    suspend fun save(export: RawSessionsExport): File {
        val content = json.encodeToString(RawSessionsExport.serializer(), export)
        return write("raw-sessions", content)
    }

    private suspend fun write(prefix: String, content: String): File = withContext(Dispatchers.IO) {
        val stamp = FILE_STAMP.format(clock.instant().atOffset(ZoneOffset.UTC))
        directory.mkdirs()
        File(directory, "$prefix-$stamp.json").apply { writeText(content) }
    }

    companion object {
        const val DIRECTORY = "probe"
        private val FILE_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }
}
