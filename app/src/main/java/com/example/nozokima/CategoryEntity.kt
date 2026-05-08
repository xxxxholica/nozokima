package com.example.nozokima

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String, // "EXPENSE" or "INCOME"
    val iconName: String,
    val isDefault: Boolean = false,
    val order: Int = 0
)
