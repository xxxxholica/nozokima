package works.hinata.nozokima.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import works.hinata.nozokima.data.local.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Insert
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Query("SELECT * FROM assets")
    fun getAllAssets(): Flow<List<AssetEntity>>

    @Insert
    suspend fun insertAsset(asset: AssetEntity)

    @Update
    suspend fun updateAsset(asset: AssetEntity)

    @Query("SELECT * FROM budgets")
    fun getAllBudgets(): Flow<List<BudgetEntity>>

    @Insert
    suspend fun insertBudget(budget: BudgetEntity)

    @Update
    suspend fun updateBudget(budget: BudgetEntity)

    @Delete
    suspend fun deleteAsset(asset: AssetEntity)


    @Query("SELECT * FROM assets WHERE name = :assetName LIMIT 1")
    suspend fun getAssetByName(assetName: String): AssetEntity?

    @Query("UPDATE transactions SET assetName = :newName WHERE assetName = :oldName")
    suspend fun updateTransactionAssetName(oldName: String, newName: String)

    @Query("UPDATE transactions SET toAssetName = :newName WHERE toAssetName = :oldName")
    suspend fun updateTransactionToAssetName(oldName: String, newName: String)

    @Query("UPDATE scheduled_expenses SET assetName = :newName WHERE assetName = :oldName")
    suspend fun updateScheduledExpenseAssetName(oldName: String, newName: String)

    @Query("UPDATE lendings SET loanAsset = :newName WHERE loanAsset = :oldName")
    suspend fun updateLendingLoanAsset(oldName: String, newName: String)

    @Query("UPDATE lendings SET returnAsset = :newName WHERE returnAsset = :oldName")
    suspend fun updateLendingReturnAsset(oldName: String, newName: String)

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    // 目標設定
    @Query("SELECT * FROM goal_settings WHERE id = 1 LIMIT 1")
    fun getGoalSetting(): Flow<GoalSettingEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGoalSetting(setting: GoalSettingEntity)

    @Query("SELECT * FROM lendings")
    fun getAllLendings(): Flow<List<LendingEntity>>

    @Insert
    suspend fun insertLending(lending: LendingEntity)

    @Update
    suspend fun updateLending(lending: LendingEntity)

    @Delete
    suspend fun deleteLending(lending: LendingEntity)

    // チャット関連
    @Query("SELECT * FROM chat_sessions ORDER BY lastMessageAt DESC")
    fun getAllChatSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChatSession(session: ChatSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteChatSession(sessionId: String)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: String)

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesForSessionSync(sessionId: String): List<ChatMessageEntity>

    @Query("SELECT * FROM transactions")
    suspend fun getAllTransactionsList(): List<TransactionEntity>

    @Query("SELECT * FROM assets")
    suspend fun getAllAssetsList(): List<AssetEntity>

    @Query("SELECT * FROM budgets")
    suspend fun getAllBudgetsList(): List<BudgetEntity>

    @Query("SELECT * FROM lendings")
    suspend fun getAllLendingsList(): List<LendingEntity>

    @Query("SELECT * FROM chat_sessions")
    suspend fun getAllChatSessionsList(): List<ChatSessionEntity>

    @Query("SELECT * FROM chat_messages")
    suspend fun getAllChatMessagesList(): List<ChatMessageEntity>

    @Query("SELECT * FROM goal_settings WHERE id = 1 LIMIT 1")
    suspend fun getGoalSettingSync(): GoalSettingEntity?

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()

    @Query("DELETE FROM assets")
    suspend fun deleteAllAssets()

    @Query("DELETE FROM budgets")
    suspend fun deleteAllBudgets()

    @Query("DELETE FROM lendings")
    suspend fun deleteAllLendings()

    @Query("DELETE FROM chat_sessions")
    suspend fun deleteAllChatSessions()

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAllChatMessages()

    @Query("DELETE FROM goal_settings")
    suspend fun deleteAllGoalSettings()

    // アプリ設定
    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    fun getAppSettings(): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    suspend fun getAppSettingsSync(): AppSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAppSettings(settings: AppSettingsEntity)


    @Query("SELECT * FROM categories ORDER BY `order` ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY `order` ASC")
    suspend fun getAllCategoriesListSync(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity)

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun getCategoryCount(): Int

    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories()


    // 予定支出関連
    @Query("SELECT * FROM scheduled_expenses ORDER BY date ASC")
    fun getAllScheduledExpenses(): Flow<List<ScheduledExpenseEntity>>

    @Query("SELECT * FROM scheduled_expenses ORDER BY date ASC")
    suspend fun getAllScheduledExpensesList(): List<ScheduledExpenseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScheduledExpense(expense: ScheduledExpenseEntity)

    @Update
    suspend fun updateScheduledExpense(expense: ScheduledExpenseEntity)

    @Delete
    suspend fun deleteScheduledExpense(expense: ScheduledExpenseEntity)

    @Query("DELETE FROM scheduled_expenses")
    suspend fun deleteAllScheduledExpenses()
}
