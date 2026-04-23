package com.example.nozokima

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "loans")
data class LoanEntity(
    @PrimaryKey val id: String,
    val counterpartName: String,   // 貸付相手
    val amount: Int,               // 貸付金額
    val assetName: String,         // 出金元資産
    val date: Long,                // 貸付日
    val memo: String = "",
    val isCollected: Boolean = false, // 回収済みフラグ
    val collectedAmount: Int = 0,  // 回収金額（部分返済対応）
    val collectedAssetName: String = "" // 回収先資産
)

