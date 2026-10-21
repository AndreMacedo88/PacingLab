package app.pacinglab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.pacinglab.core.data.sync.SyncScheduler
import app.pacinglab.core.designsystem.theme.PacingLabTheme
import app.pacinglab.core.maps.LocalMapSnapshots
import app.pacinglab.core.maps.MapSnapshots
import app.pacinglab.ui.PacingLabApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var syncScheduler: SyncScheduler

    @Inject lateinit var mapSnapshots: MapSnapshots

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PacingLabTheme {
                CompositionLocalProvider(LocalMapSnapshots provides mapSnapshots) {
                    PacingLabApp()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Health Connect allows the most reads while the app is in the foreground.
        syncScheduler.syncNow()
    }
}
