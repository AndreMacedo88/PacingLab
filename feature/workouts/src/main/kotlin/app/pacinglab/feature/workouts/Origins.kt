package app.pacinglab.feature.workouts

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** The name of the app behind a Health Connect origin, or the package name for apps PacingLab does not know. */
@Composable
internal fun originName(origin: String): String = when (origin) {
    FITBIT -> stringResource(R.string.origin_fitbit)
    GOOGLE_FIT -> stringResource(R.string.origin_google_fit)
    else -> origin
}

private const val FITBIT = "com.fitbit.FitbitMobile"
private const val GOOGLE_FIT = "com.google.android.apps.fitness"
