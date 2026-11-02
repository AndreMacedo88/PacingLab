package app.pacinglab.core.homeassistant

import app.pacinglab.core.model.home.HomeAssistantAuthException
import app.pacinglab.core.model.home.HomeAssistantSource
import app.pacinglab.core.model.home.HomeSensor
import app.pacinglab.core.model.home.HomeServer
import app.pacinglab.core.model.home.SensorStatistic
import app.pacinglab.core.model.home.StatisticsPeriod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Home Assistant over OkHttp: `/api/config`, `/api/states` and webhooks over REST, statistics over the
 * WebSocket API (`recorder/statistics_during_period`, which the REST API does not offer). [url] is the
 * server's base address, such as https://homeassistant.example.ts.net.
 */
@Singleton
class OkHttpHomeAssistantSource @Inject constructor() : HomeAssistantSource {
    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    override suspend fun server(url: String, token: String): HomeServer = HomeAssistantJson.server(get("${base(url)}/api/config", token))

    override suspend fun sensors(url: String, token: String): List<HomeSensor> =
        HomeAssistantJson.sensors(get("${base(url)}/api/states", token))

    override suspend fun sendWebhook(url: String, webhookId: String, json: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${base(url)}/api/webhook/$webhookId")
            .post(json.toRequestBody(JSON))
            .build()
        client.newCall(request).execute().use { check(it) }
    }

    override suspend fun statistics(
        url: String,
        token: String,
        entityIds: List<String>,
        from: Instant,
        to: Instant,
        period: StatisticsPeriod,
    ): Map<String, List<SensorStatistic>> {
        if (entityIds.isEmpty()) return emptyMap()
        val socket = base(url).replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "/api/websocket"
        return suspendCancellableCoroutine { continuation ->
            fun fail(error: Throwable) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
            val listener = object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val message = JSONObject(text)
                        when (message.optString("type")) {
                            "auth_required" -> webSocket.send(HomeAssistantJson.authMessage(token))

                            "auth_ok" -> webSocket.send(HomeAssistantJson.statisticsCommand(COMMAND_ID, entityIds, from, to, period))

                            "auth_invalid" -> {
                                fail(HomeAssistantAuthException(message.optString("message", "Token refused")))
                                webSocket.close(NORMAL_CLOSURE, null)
                            }

                            "result" -> {
                                if (message.optBoolean("success")) {
                                    val result = HomeAssistantJson.statistics(message.optJSONObject("result") ?: JSONObject())
                                    if (continuation.isActive) continuation.resume(result)
                                } else {
                                    fail(IOException(message.optJSONObject("error")?.optString("message") ?: "Statistics refused"))
                                }
                                webSocket.close(NORMAL_CLOSURE, null)
                            }
                        }
                    } catch (e: JSONException) {
                        fail(IOException("Unreadable answer from Home Assistant", e))
                        webSocket.close(NORMAL_CLOSURE, null)
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    fail(if (t is IOException) t else IOException(t))
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    fail(IOException("Home Assistant closed the connection: $reason"))
                }
            }
            val webSocket = client.newWebSocket(Request.Builder().url(socket).build(), listener)
            continuation.invokeOnCancellation { webSocket.cancel() }
        }
    }

    private suspend fun get(url: String, token: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
        client.newCall(request).execute().use { response ->
            check(response)
            response.body.string()
        }
    }

    private fun check(response: Response) {
        when {
            response.code == UNAUTHORIZED || response.code == FORBIDDEN -> throw HomeAssistantAuthException(
                "Token refused (${response.code})",
            )

            !response.isSuccessful -> throw IOException("Home Assistant answered ${response.code}")
        }
    }

    private fun base(url: String): String = url.trim().trimEnd('/')

    private companion object {
        const val TIMEOUT_SECONDS = 20L
        const val COMMAND_ID = 1
        const val NORMAL_CLOSURE = 1000
        const val UNAUTHORIZED = 401
        const val FORBIDDEN = 403
        val JSON = "application/json".toMediaType()
    }
}
