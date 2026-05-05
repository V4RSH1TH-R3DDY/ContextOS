package com.contextos.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.contextos.core.data.db.dao.ActionLogDao
import com.contextos.core.data.db.dao.CalendarEventCacheDao
import com.contextos.core.data.db.dao.ConfirmedRoutineDao
import com.contextos.core.data.db.dao.LocationMemoryDao
import com.contextos.core.data.db.dao.PreferenceMemoryDao
import com.contextos.core.data.db.dao.RoutineMemoryDao
import com.contextos.core.data.db.dao.UserPreferenceDao
import com.contextos.core.data.db.entity.ActionLogEntity
import com.contextos.core.data.db.entity.CalendarEventCacheEntity
import com.contextos.core.data.db.entity.ConfirmedRoutineEntity
import com.contextos.core.data.db.entity.LocationMemoryEntity
import com.contextos.core.data.db.entity.PreferenceMemoryEntity
import com.contextos.core.data.db.entity.RoutineMemoryEntity
import com.contextos.core.data.db.entity.UserPreferenceEntity
import org.json.JSONArray

// ---------------------------------------------------------------------------
// Type converters
// ---------------------------------------------------------------------------

class Converters {

    @TypeConverter
    fun fromStringList(value: List<String>): String {
        val array = JSONArray()
        value.forEach { array.put(it) }
        return array.toString()
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        val array = JSONArray(value)
        return List(array.length()) { array.getString(it) }
    }
}

// ---------------------------------------------------------------------------
// Database
// ---------------------------------------------------------------------------

@Database(
    entities = [
        ActionLogEntity::class,
        UserPreferenceEntity::class,
        CalendarEventCacheEntity::class,
        RoutineMemoryEntity::class,
        PreferenceMemoryEntity::class,
        LocationMemoryEntity::class,
        ConfirmedRoutineEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ContextOSDatabase : RoomDatabase() {
    abstract fun actionLogDao(): ActionLogDao
    abstract fun userPreferenceDao(): UserPreferenceDao
    abstract fun calendarEventCacheDao(): CalendarEventCacheDao
    abstract fun routineMemoryDao(): RoutineMemoryDao
    abstract fun preferenceMemoryDao(): PreferenceMemoryDao
    abstract fun locationMemoryDao(): LocationMemoryDao
    abstract fun confirmedRoutineDao(): ConfirmedRoutineDao
}
