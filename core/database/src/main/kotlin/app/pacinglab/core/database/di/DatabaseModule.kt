package app.pacinglab.core.database.di

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import app.pacinglab.core.database.PacingLabDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): PacingLabDatabase =
        Room.databaseBuilder(context, PacingLabDatabase::class.java, PacingLabDatabase.FILE_NAME)
            .setDriver(AndroidSQLiteDriver())
            .build()
}
