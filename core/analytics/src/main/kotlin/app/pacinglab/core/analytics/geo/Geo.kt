package app.pacinglab.core.analytics.geo

import app.pacinglab.core.model.Route
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_M = 6_371_008.8

/** Great-circle distance in metres between two WGS84 points (haversine formula). */
fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val dPhi = Math.toRadians(lat2 - lat1)
    val dLambda = Math.toRadians(lon2 - lon1)
    val a = sin(dPhi / 2).pow(2) + cos(phi1) * cos(phi2) * sin(dLambda / 2).pow(2)
    return 2 * EARTH_RADIUS_M * asin(sqrt(a.coerceIn(0.0, 1.0)))
}

/** Sum of the distances between consecutive route points. */
fun Route.lengthMeters(): Double {
    var total = 0.0
    for (i in 1 until size) {
        total += haversineMeters(latitudes[i - 1], longitudes[i - 1], latitudes[i], longitudes[i])
    }
    return total
}
