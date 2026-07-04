package works.hinata.nozokima.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "goal_settings")
data class GoalSettingEntity(
    @PrimaryKey val id: Int = 1, // 常に1行だけ保持
    val title: String = "",
    val targetAmount: Long = 0L,
    val monthlyIncome: Long = 0L,
    val targetDateMillis: Long = 0L,
    val showResults: Boolean = false,
    val startDateMillis: Long = 0L,
    val useVirtualBalance: Boolean = false,
    val aiTargetDate: String = "",
    val aiMonthlyBudget: Long = 0L,
    
    // 3種類のプラン用
    val relaxedTargetDate: String = "",
    val relaxedMonthlyBudget: Long = 0L,
    val speedTargetDate: String = "",
    val speedMonthlyBudget: Long = 0L,
    
    val selectedPlanType: String = "RECOMMENDED", // RECOMMENDED, RELAXED, SPEED
    val aiMessage: String = ""
)
