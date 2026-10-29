package app.pacinglab.core.googlehealth

import app.pacinglab.core.model.source.SourceAuthorizationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject

/** Reads pages of data points from the Google Health API (health.googleapis.com, v4). */
interface GoogleHealthApi {
    /** One page of [dataType]'s data points, newest first, as the raw JSON response. */
    suspend fun dataPoints(dataType: String, token: String, pageToken: String?): String
}

class HttpGoogleHealthApi @Inject constructor() : GoogleHealthApi {
    override suspend fun dataPoints(dataType: String, token: String, pageToken: String?): String = withContext(Dispatchers.IO) {
        val query = "pageSize=$PAGE_SIZE" + (pageToken?.let { "&pageToken=" + URLEncoder.encode(it, Charsets.UTF_8) } ?: "")
        val connection = URL("$BASE/users/me/dataTypes/$dataType/dataPoints?$query").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.setRequestProperty("Authorization", "Bearer $token")
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> connection.inputStream.bufferedReader().use { it.readText() }

                HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN ->
                    throw SourceAuthorizationException("Google Health API answered $code for $dataType")

                else -> throw IOException("Google Health API answered $code for $dataType")
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val BASE = "https://health.googleapis.com/v4"
        const val PAGE_SIZE = 100
        const val TIMEOUT_MILLIS = 20_000
    }
}
