package app.pacinglab.core.data.repository

/** The home-screen widget, which the app module draws; settings ask it to redraw after a change. */
fun interface HomeScreenWidget {
    suspend fun update()
}
