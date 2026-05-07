package com.contextos.core.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.contextos.core.data.db.ContextOSDatabase
import com.contextos.core.data.db.dao.ActionLogDao
import com.contextos.core.data.db.dao.BudsContextDao
import com.contextos.core.data.db.dao.CalendarEventCacheDao
import com.contextos.core.data.db.dao.ConfirmedRoutineDao
import com.contextos.core.data.db.dao.LocationMemoryDao
import com.contextos.core.data.db.dao.PreferenceMemoryDao
import com.contextos.core.data.db.dao.RoutineMemoryDao
import com.contextos.core.data.db.dao.UserPreferenceDao
import com.contextos.core.data.db.dao.WearableContextDao
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
                    is_active INTEGER NOT NULL
                )
            """)
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_confirmed_routine_day_of_week_time_slot_start ON confirmed_routine(day_of_week, time_slot_start)")
        }
    }

    /**
     * Phase 11 migration: adds wearable_context and buds_context tables
     * for Galaxy Watch and Galaxy Buds integration.
     */
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS wearable_context (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    activity_type TEXT NOT NULL,
                    heart_rate INTEGER,
                    step_count_delta INTEGER NOT NULL,
                    device_connected INTEGER NOT NULL DEFAULT 0
                )
            """)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS buds_context (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    buds_in_ear TEXT NOT NULL,
                    anc_active INTEGER NOT NULL DEFAULT 0,
                    ambient_sound_active INTEGER NOT NULL DEFAULT 0,
                    device_connected INTEGER NOT NULL DEFAULT 0
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
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
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

    @Provides
    @Singleton
    fun provideWearableContextDao(db: ContextOSDatabase): WearableContextDao =
        db.wearableContextDao()

    @Provides
    @Singleton
    fun provideBudsContextDao(db: ContextOSDatabase): BudsContextDao =
        db.budsContextDao()
}
