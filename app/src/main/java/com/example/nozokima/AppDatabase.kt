package com.example.nozokima

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [TransactionEntity::class, AssetEntity::class, BudgetEntity::class, GoalSettingEntity::class, LendingEntity::class, ChatSessionEntity::class, ChatMessageEntity::class, BackupHistoryEntity::class, AppSettingsEntity::class, CategoryEntity::class, RecurringTransactionEntity::class], version = 11)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun financeDao(): FinanceDao
}
