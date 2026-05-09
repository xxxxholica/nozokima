package com.example.nozokima.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val category: String,
    val monthlyAmount: Int
)
