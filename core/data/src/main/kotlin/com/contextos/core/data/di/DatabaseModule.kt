package com.contextos.core.data.di

import android.content.Context
import androidx.room.Room
import com.contextos.core.data.db.ContextOSDatabase
import com.contextos.core.data.db.dao.ActionLogDao
import com.contextos.core.data.db.dao.CalendarEventCacheDao
import com.contextos.core.data.db.dao.LocationMemoryDao
import com.contextos.core.data.db.dao.PreferenceMemoryDao
import com.contextos.core.data.db.dao.RoutineMemoryDao
import com.contextos.core.data.db.dao.UserPreferenceDao
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
    fun provideContextOSDatabase(
        @ApplicationContext context: Context,
    ): ContextOSDatabase =
        Room.databaseBuilder(
            context,
            ContextOSDatabase::class.java,
            "contextos.db",
        ).build()

    @Provides
    @Singleton
    fun provideActionLogDao(db: ContextOSDatabase): ActionLogDao =
        db.actionLogDao()

    @Provides
    @Singleton
    fun provideUserPreferenceDao(db: ContextOSDatabase): UserPreferenceDao =
        db.userPreferenceDao()

    @Provides
    @Singleton
    fun provideCalendarEventCacheDao(db: ContextOSDatabase): CalendarEventCacheDao =
        db.calendarEventCacheDao()

    @Provides
    @Singleton
    fun provideRoutineMemoryDao(db: ContextOSDatabase): RoutineMemoryDao =
        db.routineMemoryDao()

    @Provides
    @Singleton
    fun providePreferenceMemoryDao(db: ContextOSDatabase): PreferenceMemoryDao =
        db.preferenceMemoryDao()

    @Provides
    @Singleton
    fun provideLocationMemoryDao(db: ContextOSDatabase): LocationMemoryDao =
        db.locationMemoryDao()
}
