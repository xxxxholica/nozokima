package com.example.nozokima

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [TransactionEntity::class, AssetEntity::class, BudgetEntity::class, GoalSettingEntity::class, LoanEntity::class], version = 6)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun financeDao(): FinanceDao
}
