package app.pacinglab

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pacinglab.core.data.repository.DarkMode
import app.pacinglab.core.data.repository.UserPreferences
import app.pacinglab.core.data.repository.UserPreferencesRepository
import app.pacinglab.core.data.sync.SyncScheduler
import app.pacinglab.core.designsystem.theme.PacingLabTheme
import app.pacinglab.core.designsystem.theme.ThemePalette
import app.pacinglab.core.maps.LocalMapSnapshots
import app.pacinglab.core.maps.MapSnapshots
import app.pacinglab.ui.PacingLabApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var syncScheduler: SyncScheduler

    @Inject lateinit var mapSnapshots: MapSnapshots

    @Inject lateinit var preferences: UserPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by preferences.preferences.collectAsStateWithLifecycle(UserPreferences())
            val dark = when (settings.darkMode) {
                DarkMode.SYSTEM -> isSystemInDarkTheme()
                DarkMode.LIGHT -> false
                DarkMode.DARK -> true
            }
            // System bar icons follow the app's own light or dark setting, not only the system's.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
                onDispose {}
            }
            PacingLabTheme(darkTheme = dark, palette = ThemePalette.valueOf(settings.theme.name)) {
                CompositionLocalProvider(LocalMapSnapshots provides mapSnapshots) {
                    PacingLabApp(syncProblems = syncScheduler.problems)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Health Connect allows the most reads while the app is in the foreground.
        syncScheduler.syncNow()
    }

    private companion object {
        // The scrims Android's own edge-to-edge default draws behind three-button navigation.
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
