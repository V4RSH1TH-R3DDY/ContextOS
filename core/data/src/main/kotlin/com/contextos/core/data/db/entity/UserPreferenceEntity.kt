package com.contextos.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "user_preferences",
    indices = [Index(value = ["skill_id"], unique = true)],
)
data class UserPreferenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "skill_id") val skillId: String,
    val autoApprove: Boolean = false,
    val sensitivityLevel: Int = 1,   // 0–3
)
