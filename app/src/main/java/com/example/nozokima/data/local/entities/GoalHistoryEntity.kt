package com.example.nozokima.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "goal_histories")
data class GoalHistoryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val targetAmount: Long,
    val startDateMillis: Long,
    val endDateMillis: Long,
    val isAchieved: Boolean,
    val type: String // "SET" or "ACHIEVED"
)
