package com.example.nozokima.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "scheduled_expenses")
data class ScheduledExpenseEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val amount: Int,
    val category: String,
    val date: Long,           // 次回の支払予定日
    val assetName: String,    // どの資産から引かれるか
    val isRecurring: Boolean = true,
    val repeatInterval: String? = "MONTHLY",
    val isCompleted: Boolean = false // 当月の支払いが完了したか
)
