package com.contextos.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "preference_memory",
    indices = [Index(value = ["skill_id", "context_hash"], unique = true)],
)
data class PreferenceMemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "skill_id") val skillId: String,
    @ColumnInfo(name = "context_hash") val contextHash: String,
    val userChoice: String,     // "APPROVED" or "DISMISSED"
    val frequency: Int,
    val lastObservedMs: Long,
)
