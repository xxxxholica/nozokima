package works.hinata.nozokima.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import works.hinata.nozokima.data.local.entities.*

@Database(entities = [TransactionEntity::class, AssetEntity::class, BudgetEntity::class, GoalSettingEntity::class, LendingEntity::class, ChatSessionEntity::class, ChatMessageEntity::class, AppSettingsEntity::class, CategoryEntity::class, ScheduledExpenseEntity::class], version = 20)
abstract class AppDatabase : RoomDatabase() {
    abstract fun financeDao(): FinanceDao
}
