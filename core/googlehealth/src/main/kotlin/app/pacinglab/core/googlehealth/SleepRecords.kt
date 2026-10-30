package app.pacinglab.core.googlehealth

import app.pacinglab.core.model.BriefAwakening
import app.pacinglab.core.model.DailyVitals
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/**
 * The API's sleep records, read for the brief awakenings of each main sleep, by the morning it ended in
 * the athlete's zone (the records' own offsets have read zero). Naps are left out; a sleep without stages
 * gives no entry, since the app then keeps no brief awakenings either.
 */
internal object SleepRecords {
    const val TYPE = "sleep"

    /** Reads [text]; throws [org.json.JSONException] when it is not a page of data points. */
    fun parse(text: String, zone: ZoneId): DailyPage {
        val json = JSONObject(text)
        val points = json.optJSONArray("dataPoints")
        val sleeps = if (points == null) emptyList() else (0 until points.length()).mapNotNull { read(points.getJSONObject(it), zone) }
        // Two main sleeps ending on one morning share it.
        val days = sleeps.groupBy { it.date }.map { (date, found) ->
            DailyVitals(date, briefAwakenings = found.flatMap { it.briefAwakenings.orEmpty() }.sortedBy { it.start })
        }
        return DailyPage(days, json.optString("nextPageToken").takeIf { it.isNotEmpty() })
    }

    private fun read(point: JSONObject, zone: ZoneId): DailyVitals? {
        val sleep = point.optJSONObject("sleep") ?: return null
        if (sleep.optJSONObject("metadata")?.optBoolean("mainSleep") != true) return null
        if ((sleep.optJSONArray("stages")?.length() ?: 0) == 0) return null
        val end = sleep.optJSONObject("interval")?.instant("endTime") ?: return null
        val list = sleep.optJSONArray("shortAwakenings")
        val awakenings = if (list == null) {
            emptyList()
        } else {
            (0 until list.length()).mapNotNull { i ->
                val item = list.getJSONObject(i)
                val from = item.instant("startTime") ?: return@mapNotNull null
                val to = item.instant("endTime") ?: return@mapNotNull null
                BriefAwakening(from, to)
            }
        }
        return DailyVitals(end.atZone(zone).toLocalDate(), briefAwakenings = awakenings)
    }

    private fun JSONObject.instant(name: String): Instant? = optString(name).takeIf { it.isNotEmpty() }?.let {
        try {
            OffsetDateTime.parse(it).toInstant()
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
