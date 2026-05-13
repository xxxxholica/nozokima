package com.example.nozokima.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.nozokima.data.local.entities.*

@Database(entities = [TransactionEntity::class, AssetEntity::class, BudgetEntity::class, GoalSettingEntity::class, LendingEntity::class, ChatSessionEntity::class, ChatMessageEntity::class, AppSettingsEntity::class, CategoryEntity::class, RecurringTransactionEntity::class], version = 14)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun financeDao(): FinanceDao
}
