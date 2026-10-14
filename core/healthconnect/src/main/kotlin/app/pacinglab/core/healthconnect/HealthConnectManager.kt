package app.pacinglab.core.healthconnect

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.PermissionController
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class HealthConnectAvailability {
    AVAILABLE,

    /** Health Connect exists but must be updated from the Play Store first. */
    UPDATE_REQUIRED,
    UNAVAILABLE,
}

/** Optional Health Connect capabilities that depend on the installed Health Connect version. */
data class HealthConnectFeatureSupport(val readHistory: Boolean, val readInBackground: Boolean)

/** Entry point to Health Connect: availability, permissions and the client itself. */
@Singleton
class HealthConnectManager @Inject constructor(@param:ApplicationContext private val context: Context) {
    fun availability(): HealthConnectAvailability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.UPDATE_REQUIRED
        else -> HealthConnectAvailability.UNAVAILABLE
    }

    /** Only call when [availability] is [HealthConnectAvailability.AVAILABLE]. */
    val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun grantedPermissions(): Set<String> = client.permissionController.getGrantedPermissions()

    fun featureSupport(): HealthConnectFeatureSupport {
        val features = client.features

        fun available(feature: Int) = features.getFeatureStatus(feature) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        return HealthConnectFeatureSupport(
            readHistory = available(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY),
            readInBackground = available(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND),
        )
    }

    /** Contract that opens Health Connect's permission screen and returns the granted set. */
    fun permissionRequestContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    /**
     * Opens Health Connect's home screen, from where the user reaches App permissions. Apps may not
     * open their own permission page directly: that intent requires a privileged permission.
     */
    fun settingsIntent(): Intent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)

    /** Health Connect's data screen, used if the home screen cannot be opened. */
    fun fallbackSettingsIntent(): Intent = HealthConnectClient.getHealthConnectManageDataIntent(context)
}
