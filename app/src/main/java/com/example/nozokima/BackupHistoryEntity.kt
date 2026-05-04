package com.example.nozokima

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "backup_history")
data class BackupHistoryEntity(
    @PrimaryKey
    val id: String,
    val date: Long,
    val password: String,
    val fileName: String
)
