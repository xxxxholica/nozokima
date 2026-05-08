package com.example.nozokima

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recurring_transactions")
data class RecurringTransactionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val amount: Int,
    val category: String,
    val assetName: String,
    val dayOfMonth: Int,
    val isExpense: Boolean,
    val lastProcessedDate: Long = 0L
)
