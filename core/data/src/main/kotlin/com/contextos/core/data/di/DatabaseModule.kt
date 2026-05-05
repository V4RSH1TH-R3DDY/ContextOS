package com.contextos.core.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.contextos.core.data.db.ContextOSDatabase
import com.contextos.core.data.db.dao.ActionLogDao
import com.contextos.core.data.db.dao.CalendarEventCacheDao
import com.contextos.core.data.db.dao.ConfirmedRoutineDao
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

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE action_log ADD COLUMN reasoningPayload TEXT NOT NULL DEFAULT '{}'")
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS confirmed_routine (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    routine_type TEXT NOT NULL,
                    day_of_week INTEGER NOT NULL,
                    time_slot_start INTEGER NOT NULL,
                    time_slot_end INTEGER NOT NULL,
                    confidence REAL NOT NULL,
                    observation_count INTEGER NOT NULL,
                    last_observed INTEGER NOT NULL,
                    suggested_action TEXT NOT NULL,
                    is_active INTEGER NOT NULL DEFAULT 1
                )
            """)
        }
    }

    @Provides
    @Singleton
    fun provideContextOSDatabase(
        @ApplicationContext context: Context,
    ): ContextOSDatabase =
        Room.databaseBuilder(
            context,
            ContextOSDatabase::class.java,
            "contextos.db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
         .build()

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

    @Provides
    @Singleton
    fun provideConfirmedRoutineDao(db: ContextOSDatabase): ConfirmedRoutineDao =
        db.confirmedRoutineDao()
}
