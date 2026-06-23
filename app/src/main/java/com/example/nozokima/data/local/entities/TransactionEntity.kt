package com.example.nozokima.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val amount: Int,
    val category: String,
    val date: Long,
    val assetName: String,
    val isExpense: Boolean = true,
    val toAssetName: String? = null,
    val isTransfer: Boolean = false,
    val sortOrder: Long = System.currentTimeMillis()
)
