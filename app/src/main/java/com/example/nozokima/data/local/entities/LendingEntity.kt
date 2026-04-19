package com.example.nozokima.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lendings")
data class LendingEntity(
    @PrimaryKey val id: String,
    val personName: String,
    val amount: Int,
    val loanAsset: String,
    val memo: String,
    val date: Long,
    val isRecovered: Boolean = false,
    val returnAsset: String? = null,
    val recoveredAmount: Int = 0,
    val recoveredDate: Long? = null
)

