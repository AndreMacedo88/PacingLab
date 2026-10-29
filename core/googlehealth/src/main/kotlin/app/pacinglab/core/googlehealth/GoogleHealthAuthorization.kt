package app.pacinglab.core.googlehealth

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** The outcome of asking Google for access to the athlete's Google Health data. */
sealed interface GoogleHealthAccess {
    /** Access is granted; [token] lasts about an hour, and asking again gives a fresh one without a prompt. */
    data class Granted(val token: String) : GoogleHealthAccess

    /** The athlete has to sign in or consent first: start [intent] from an activity. */
    data class NeedsConsent(val intent: PendingIntent) : GoogleHealthAccess

    /** Google refused or could not be reached; [reason] says why, for the data sources screen. */
    data class Failed(val reason: String) : GoogleHealthAccess
}

/**
 * Asks Google Play services for read access to the athlete's Google Health data. Play services
 * recognises the app by its package name and signing certificate, which are registered as Android
 * OAuth clients of the "PacingLab" Google Cloud project; no client secret is involved, and Play
 * services keeps and refreshes the grant.
 */
@Singleton
class GoogleHealthAuthorization @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val request = AuthorizationRequest.builder().setRequestedScopes(SCOPES.map(::Scope)).build()

    /** Access without showing anything, or what the athlete has to do first. */
    suspend fun authorize(): GoogleHealthAccess = try {
        Identity.getAuthorizationClient(context).authorize(request).await().toAccess()
    } catch (e: CancellationException) {
        throw e
    } catch (e: ApiException) {
        GoogleHealthAccess.Failed("Google Play services: ${e.statusCode} ${e.message.orEmpty()}".trim())
    }

    /** The result of the consent screen started from [GoogleHealthAccess.NeedsConsent]. */
    fun fromConsent(data: Intent?): GoogleHealthAccess = try {
        Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data).toAccess()
    } catch (e: ApiException) {
        GoogleHealthAccess.Failed("Google Play services: ${e.statusCode} ${e.message.orEmpty()}".trim())
    }

    private fun AuthorizationResult.toAccess(): GoogleHealthAccess {
        if (hasResolution()) pendingIntent?.let { return GoogleHealthAccess.NeedsConsent(it) }
        return accessToken?.let { GoogleHealthAccess.Granted(it) } ?: GoogleHealthAccess.Failed("No access token")
    }

    companion object {
        /**
         * Read-only access to nightly measurements (HRV, SpO2, breathing, resting heart rate), sleep (skin
         * temperature) and activity and fitness (daily VO2max).
         */
        val SCOPES = listOf(
            "https://www.googleapis.com/auth/googlehealth.health_metrics_and_measurements.readonly",
            "https://www.googleapis.com/auth/googlehealth.sleep.readonly",
            "https://www.googleapis.com/auth/googlehealth.activity_and_fitness.readonly",
        )
    }
}
