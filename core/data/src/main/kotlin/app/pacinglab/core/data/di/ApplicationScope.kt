package app.pacinglab.core.data.di

import javax.inject.Qualifier

/** Coroutine scope that lives as long as the app process, for work that must outlive a screen. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
