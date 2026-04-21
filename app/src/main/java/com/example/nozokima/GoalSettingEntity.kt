package com.example.nozokima

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "goal_settings")
data class GoalSettingEntity(
    @PrimaryKey val id: Int = 1, // 常に1行だけ保持
    val title: String = "",
    val targetAmount: Long = 0L,
    val monthlyIncome: Long = 0L,
    val targetDateMillis: Long = 0L,
    val showResults: Boolean = false,
    val startDateMillis: Long = 0L
)
