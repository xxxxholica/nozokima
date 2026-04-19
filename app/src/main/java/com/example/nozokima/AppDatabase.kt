package com.example.nozokima

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [TransactionEntity::class, AssetEntity::class, BudgetEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun financeDao(): FinanceDao
}
