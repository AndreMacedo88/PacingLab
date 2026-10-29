package app.pacinglab.core.data.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import app.pacinglab.core.model.Coordinates
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import kotlin.coroutines.resume

/** Downloads forecasts. */
interface WeatherService {
    /** The forecast for [place] as the service's raw response; throws [IOException] without a connection. */
    suspend fun fetch(place: Coordinates): String
}

/** Where the phone roughly is, if the athlete allowed it. */
interface ApproximateLocation {
    fun permitted(): Boolean

    /** The phone's approximate position, or null when location is off or unknown. */
    suspend fun current(): Coordinates?
}

/** The last forecast downloaded, kept so that Today shows weather straight away and without a connection. */
interface WeatherCache {
    suspend fun read(): String?

    suspend fun write(text: String)
}

class OpenMeteoService @Inject constructor() : WeatherService {
    override suspend fun fetch(place: Coordinates): String = withContext(Dispatchers.IO) {
        val connection = URL(OpenMeteo.url(place)).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("Open-Meteo answered ${connection.responseCode}")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000
    }
}

/**
 * The platform's location with only the approximate-location permission: Android blurs it to about
 * 2 km before PacingLab sees it.
 */
class PlatformApproximateLocation @Inject constructor(@param:ApplicationContext private val context: Context, private val clock: Clock) :
    ApproximateLocation {
    private val manager: LocationManager? = context.getSystemService(LocationManager::class.java)

    override fun permitted(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    // Both calls run only after permitted() returned true.
    @SuppressLint("MissingPermission")
    override suspend fun current(): Coordinates? {
        val manager = manager ?: return null
        if (!permitted()) return null
        val providers = PROVIDERS.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        val known = providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        if (known != null && Duration.ofMillis(clock.millis() - known.time) < RECENT) return known.coordinates()
        val provider = providers.firstOrNull() ?: return known?.coordinates()
        val fresh = withTimeoutOrNull(FIX_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                manager.getCurrentLocation(provider, signal, context.mainExecutor) { location -> continuation.resume(location) }
            }
        }
        return (fresh ?: known)?.coordinates()
    }

    private fun Location.coordinates() = Coordinates(latitude, longitude)

    private companion object {
        val PROVIDERS = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        val RECENT: Duration = Duration.ofMinutes(30)
        const val FIX_TIMEOUT_MILLIS = 15_000L
    }
}

class FileWeatherCache @Inject constructor(@param:ApplicationContext private val context: Context) : WeatherCache {
    private val file get() = File(context.cacheDir, "weather.json")

    override suspend fun read(): String? = withContext(Dispatchers.IO) { runCatching { file.readText() }.getOrNull() }

    override suspend fun write(text: String) = withContext(Dispatchers.IO) { file.writeText(text) }
}
