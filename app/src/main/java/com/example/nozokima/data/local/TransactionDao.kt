package com.example.nozokima.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.nozokima.data.local.entities.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Insert
    suspend fun insertTransaction(transaction: TransactionEntity)
}
