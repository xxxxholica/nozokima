package com.example.nozokima.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nozokima.data.local.FinanceDao
import com.example.nozokima.data.local.entities.GoalSettingEntity
import com.example.nozokima.data.local.entities.TransactionEntity
import com.example.nozokima.data.local.entities.AssetEntity
import com.example.nozokima.data.local.entities.LendingEntity
import com.example.nozokima.data.local.entities.BudgetEntity
import com.example.nozokima.data.local.entities.ScheduledExpenseEntity
import com.example.nozokima.data.local.entities.CategoryEntity
import com.example.nozokima.data.manager.GeminiNanoModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

data class HomeUiState(
    val transactions: List<TransactionEntity> = emptyList(),
    val assets: List<AssetEntity> = emptyList(),
    val lendings: List<LendingEntity> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList(),
    val scheduledExpenses: List<ScheduledExpenseEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val goalSetting: GoalSettingEntity? = null,
    val virtualBalance: Long = 0L,
    val homeAiText: String = "",
    val isAiGenerating: Boolean = false,
    val aiStatus: Int = 0,
    val isAiReady: Boolean = false,
    val isAiCheckingStatus: Boolean = false,
    val isAiInitialized: Boolean = false
)

class HomeViewModel(
    private val dao: FinanceDao,
    private val gemini: GeminiNanoModel,
) : ViewModel() {

    private val _homeAiText = MutableStateFlow("")
    
    val uiState: StateFlow<HomeUiState> = combine(
        dao.getAllTransactions(),
        dao.getAllAssets(),
        dao.getAllLendings(),
        dao.getAllBudgets(),
        dao.getGoalSetting(),
        dao.getAllScheduledExpenses(),
        dao.getAllCategories(),
        _homeAiText.asStateFlow(),
        gemini.isGenerating,
        gemini.status,
        gemini.isReady,
        gemini.isCheckingStatus,
        gemini.isInitialized,
    ) { params ->
        @Suppress("UNCHECKED_CAST")
        val transactions = params[0] as List<TransactionEntity>
        @Suppress("UNCHECKED_CAST")
        val assets = params[1] as List<AssetEntity>
        @Suppress("UNCHECKED_CAST")
        val lendings = params[2] as List<LendingEntity>
        @Suppress("UNCHECKED_CAST")
        val budgets = params[3] as List<BudgetEntity>
        val goalSetting = params[4] as GoalSettingEntity?
        @Suppress("UNCHECKED_CAST")
        val scheduledExpenses = params[5] as List<ScheduledExpenseEntity>
        @Suppress("UNCHECKED_CAST")
        val categories = params[6] as List<CategoryEntity>
        val homeAiText = params[7] as String
        val isGenerating = params[8] as Boolean
        val aiStatus = params[9] as Int
        val isAiReady = params[10] as Boolean
        val isAiChecking = params[11] as Boolean
        val isAiInitialized = params[12] as Boolean

        val totalLendingAmount = lendings.asSequence().filter { !it.isRecovered }.sumOf { it.amount - it.recoveredAmount }
        val currentAssets = (assets.sumOf { it.amount } + totalLendingAmount).toLong()
        val upcomingTotal = scheduledExpenses.asSequence().filter { !it.isCompleted }.sumOf { it.amount }
        val virtualBalance = currentAssets - upcomingTotal

        HomeUiState(
            transactions = transactions,
            assets = assets,
            lendings = lendings,
            budgets = budgets,
            scheduledExpenses = scheduledExpenses,
            categories = categories,
            goalSetting = goalSetting,
            virtualBalance = virtualBalance,
            homeAiText = homeAiText,
            isAiGenerating = isGenerating,
            aiStatus = aiStatus,
            isAiReady = isAiReady,
            isAiCheckingStatus = isAiChecking,
            isAiInitialized = isAiInitialized
        )
    }.flowOn(Dispatchers.Default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    private var lastHomeTriggerExpenseId: String? = null
    private var lastHomeTriggerExpenseAmount: Int? = null
    private var lastHomeTriggerExpenseName: String? = null
    private var lastHomeTriggerSpentThisMonth: Int? = null
    private var lastHomeTriggerVirtualBalance: Int? = null

    private var lastGoalTriggerId: Int? = null
    private var lastGoalTriggerProgress: Float? = null
    private var lastGoalTriggerAssets: Long? = null

    fun triggerGoalAnalysis(force: Boolean = false) {
        val state = uiState.value
        val goal = state.goalSetting ?: return
        if (!state.isAiReady || state.isAiGenerating || !goal.showResults) return

        val totalLendingAmount = state.lendings.asSequence().filter { !it.isRecovered }.sumOf { it.amount - it.recoveredAmount }
        val currentAssets = (state.assets.sumOf { it.amount } + totalLendingAmount).toLong()
        val upcomingTotal = state.scheduledExpenses.asSequence().filter { !it.isCompleted }.sumOf { it.amount }
        val virtualBalance = currentAssets - upcomingTotal
        val baseAssets = if (goal.useVirtualBalance) virtualBalance else currentAssets
        val progressRatio = (baseAssets.toFloat() / goal.targetAmount.toFloat()).coerceIn(0f, 1f)

        if (!force && goal.aiMessage.isNotEmpty() &&
            lastGoalTriggerId == goal.id &&
            lastGoalTriggerProgress == progressRatio &&
            lastGoalTriggerAssets == baseAssets) {
            return
        }

        lastGoalTriggerId = goal.id
        lastGoalTriggerProgress = progressRatio
        lastGoalTriggerAssets = baseAssets

        val prompt = buildString {
            appendLine("あなたは丁寧な言葉遣いながらも、痛いところを突き、ユーザーの浪費を煽るような皮肉めいた『家計の覗き魔』です。")
            appendLine("ユーザーが設定した貯金目標「${goal.title}」の現在の進捗について、1つだけ鋭い皮肉を返してください。")
            
            appendLine("【ユーザーデータ】")
            appendLine("目標額: ¥${String.format(Locale.JAPAN, "%,d", goal.targetAmount)}")
            appendLine("現在の資産: ¥${String.format(Locale.JAPAN, "%,d", baseAssets)}")
            appendLine("達成率: ${(progressRatio * 100).toInt()}%")
            appendLine("選択プラン: ${goal.selectedPlanType}")

            appendLine("【出力構成：2文で簡潔に出力してください】")
            appendLine("・現状への皮肉と、データに基づく鋭い煽りを凝縮してください。")
            appendLine("・丁寧な言葉遣い（です・ます調）を維持しつつ、最大限に「煽って」ください。")
            appendLine("・合計60文字程度で構成してください。")
            appendLine("・改行は一切行わず、すべての文章を繋げて1つの段落として出力してください。")
            appendLine("【禁止事項】自己紹介、挨拶、タメ口、精神論、構成見出しの出力、改行の挿入、長文")
        }

        viewModelScope.launch {
            var result = ""
            try {
                gemini.generateResponseStream(prompt).collect { chunk ->
                    result += chunk
                    // ストリーミング中はUI側のStateFlowは更新しない（DB負荷を避ける）
                }
                if (result.isNotEmpty()) {
                    dao.upsertGoalSetting(goal.copy(aiMessage = result))
                }
            } catch (_: Exception) {
            }
        }
    }

    fun triggerHomeAnalysis(force: Boolean = false) {
        val state = uiState.value
        if (!state.isAiReady || state.isAiGenerating) return
        
        val virtualBalance = state.virtualBalance

        val calendar = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfMonth = calendar.timeInMillis
        val nextMonthCalendar = (calendar.clone() as Calendar).apply {
            add(Calendar.MONTH, 1)
        }
        val startOfNextMonth = nextMonthCalendar.timeInMillis

        val spentThisMonth = state.transactions.asSequence()
            .filter { (it.date >= startOfMonth) && (it.date < startOfNextMonth) && it.isExpense && (it.category != "貸付") }
            .sumOf { it.amount } +
            state.scheduledExpenses.asSequence()
                .filter { (it.date >= startOfMonth) && (it.date < startOfNextMonth) && !it.isCompleted }
                .sumOf { it.amount }
            
        val currentGoal = state.goalSetting
        val hasGoal = currentGoal != null && currentGoal.targetAmount > 0 && currentGoal.showResults
        
        val monthlyBudget = if (hasGoal) {
            when (currentGoal!!.selectedPlanType) {
                "RELAXED" -> currentGoal.relaxedMonthlyBudget
                "SPEED" -> currentGoal.speedMonthlyBudget
                else -> currentGoal.aiMonthlyBudget
            }
        } else {
            state.budgets.sumOf { it.monthlyAmount }.let { if (it == 0) 100000L else it.toLong() }
        }

        val totalLendingAmount = state.lendings.asSequence().filter { !it.isRecovered }.sumOf { it.amount - it.recoveredAmount }
        val currentAssets = (state.assets.sumOf { it.amount } + totalLendingAmount).toLong()
        val currentAssetsForGoal = if (currentGoal?.useVirtualBalance == true) virtualBalance else currentAssets

        val goalProgressRatio = if (hasGoal) (currentAssetsForGoal.toFloat() / currentGoal!!.targetAmount.toFloat()).coerceIn(0f, 1f) else 0f

        val latestTx = state.transactions.asSequence().filter { it.isExpense && it.category != "貸付" }.maxByOrNull { it.date }
        val latestSe = state.scheduledExpenses.asSequence().filter { !it.isCompleted }.maxByOrNull { it.date }
        
        val latestId = if (latestTx != null && latestSe != null) {
            if (latestTx.date > latestSe.date) latestTx.id else latestSe.id
        } else {
            latestTx?.id ?: latestSe?.id
        }
        val latestAmount = if (latestTx != null && latestSe != null) {
            if (latestTx.date > latestSe.date) latestTx.amount else latestSe.amount
        } else {
            latestTx?.amount ?: latestSe?.amount
        }
        val latestName = if (latestTx != null && latestSe != null) {
            if (latestTx.date > latestSe.date) latestTx.name else latestSe.name
        } else {
            latestTx?.name ?: latestSe?.name
        }

        if (!force && _homeAiText.value.isNotEmpty() &&
            latestId == lastHomeTriggerExpenseId &&
            latestAmount == lastHomeTriggerExpenseAmount &&
            latestName == lastHomeTriggerExpenseName &&
            spentThisMonth == lastHomeTriggerSpentThisMonth &&
            virtualBalance.toInt() == lastHomeTriggerVirtualBalance) {
            return
        }

        lastHomeTriggerExpenseId = latestId
        lastHomeTriggerExpenseAmount = latestAmount
        lastHomeTriggerExpenseName = latestName
        lastHomeTriggerSpentThisMonth = spentThisMonth
        lastHomeTriggerVirtualBalance = virtualBalance.toInt()

        val hasData = state.transactions.isNotEmpty() || state.assets.any { it.amount != 0 }

        val prompt = buildString {
            appendLine("あなたは丁寧な言葉遣いながらも、痛いところを突き、ユーザーの浪費を煽るような皮肉めいた『家計の覗き魔』です。")
            if (!hasData) {
                appendLine("ユーザーはまだ家計簿にデータを入力していません。")
                appendLine("「覗き魔」らしく、ユーザーの隠し事やこれからの浪費を期待するような、少し意地の悪い一言で記録を促してください。")
            } else if (latestId != null) {
                appendLine("最新の支出（${latestName}: ¥${String.format(Locale.JAPAN, "%,d", latestAmount)}）を踏まえ、ユーザーの自制心を揺さぶる鋭い皮肉を1つだけ返してください。")
            } else {
                appendLine("現在の資産状況を踏まえ、将来への不安を煽るような、あるいは現在の緩みを指摘する鋭いアドバイスを1つだけ返してください。")
            }
            appendLine("【出力構成：2文で簡潔に出力してください】")
            appendLine("・現状への皮肉と、データに基づく鋭い煽りを凝縮してください。")
            appendLine("【出力ルール】")
            appendLine("・未来の自制（「明日からは我慢しましょう」等）は一切促さないでください。ただただ現状を皮肉り、突き放してください。")
            appendLine("・必ず2文（句点2つ）で構成してください。")
            appendLine("・改行は一切行わず、すべての文章を繋げて1つの段落として出力してください。")
            appendLine("・合計65文字程度で構成してください。")
            appendLine("・丁寧な言葉遣い（です・ます調）を維持しつつ、最大限に「煽って」ください。")
            appendLine("【禁止事項】自己紹介、挨拶、タメ口、精神論、構成見出しの出力、改行の挿入、未来の自制を促す表現、長文")
            appendLine("丁寧な言葉遣い（です・ます調）を維持しつつ、最大限に「煽って」ください。")
            
            if (hasData) {
                appendLine()
                appendLine("今月の支出合計: ¥${String.format(Locale.JAPAN, "%,d", spentThisMonth)}")
                appendLine("月の予算: ¥${String.format(Locale.JAPAN, "%,d", monthlyBudget)}")
                appendLine("自由に使える残高: ¥${String.format(Locale.JAPAN, "%,d", virtualBalance)}")
                if (hasGoal) appendLine("貯金目標: ¥${String.format(Locale.JAPAN, "%,d", currentGoal.targetAmount)}（達成率${goalProgressRatio * 100}%）")
            }
        }

        _homeAiText.value = "" 
        viewModelScope.launch {
            var result = ""
            try {
                gemini.generateResponseStream(prompt).collect { chunk ->
                    result += chunk
                    _homeAiText.value = result
                }
            } catch (_: Exception) {
            }
        }
    }

    suspend fun calculateGoalSimulation(
        targetAmount: Long,
        monthlyIncome: Long,
        useVirtualBalance: Boolean
    ): Map<String, Pair<String, Long>> {
        val state = uiState.value
        if (!state.isAiReady) return emptyMap()

        val totalLendingAmount = state.lendings.asSequence().filter { !it.isRecovered }.sumOf { it.amount - it.recoveredAmount }
        val currentAssets = (state.assets.sumOf { it.amount } + totalLendingAmount).toLong()
        val upcomingTotal = state.scheduledExpenses.asSequence().filter { !it.isCompleted }.sumOf { it.amount }
        val virtualBalance = currentAssets - upcomingTotal
        
        val baseAssets = if (useVirtualBalance) virtualBalance else currentAssets
        val remainingAmount = (targetAmount - baseAssets).coerceAtLeast(0L)

        if (remainingAmount <= 0L) return mapOf("RECOMMENDED" to ("達成済み" to 0L))

        // 過去3ヶ月の平均支出を計算
        val calendar = Calendar.getInstance()
        val nowMillis = calendar.timeInMillis
        calendar.add(Calendar.MONTH, -3)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        val threeMonthsAgo = calendar.timeInMillis
        
        val recentExpenses = state.transactions.asSequence()
            .filter { it.date >= threeMonthsAgo && it.date <= nowMillis && it.isExpense && it.category != "貸付" }
            .sumOf { it.amount }
        
        val avgMonthlyExpense = (recentExpenses / 3).toLong()
        val currentSurplus = (monthlyIncome - avgMonthlyExpense).coerceAtLeast(0L)

        val prompt = """
            あなたは優秀な資産運用アドバイザーです。ユーザーの貯金目標達成のために3つのプランを提案してください。
            
            【ユーザーデータ】
            目標額: ¥$targetAmount
            現在資産: ¥$baseAssets
            不足額: ¥$remainingAmount
            月の手取り: ¥$monthlyIncome
            直近の平均支出: ¥$avgMonthlyExpense
            現在の月間余剰金: ¥$currentSurplus
            
            【提案する3つのプラン】
            1. RECOMMENDED (おすすめ): 現在の支出ペースを維持、またはわずかな節約で無理なく達成するプラン。
            2. RELAXED (ゆったり): 支出に余裕を持たせ、生活の質を下げずに時間をかけて達成するプラン。
            3. SPEED (スピード重視): 支出を極限まで削り、最短期間で目標達成を目指すスパルタプラン。
            
            【依頼】
            ・各プランの「達成予想時期 (YYYY年MM月)」と「推奨される月間予算（支出上限）」を算出してください。
            ・結果を以下のJSON形式でのみ出力してください。
            
            {
              "RECOMMENDED": {"targetDate": "YYYY年MM月", "monthlyBudget": 0},
              "RELAXED": {"targetDate": "YYYY年MM月", "monthlyBudget": 0},
              "SPEED": {"targetDate": "YYYY年MM月", "monthlyBudget": 0}
            }
            
            ※現在の月は ${Calendar.getInstance().get(Calendar.YEAR)}年${Calendar.getInstance().get(Calendar.MONTH) + 1}月 です。
        """.trimIndent()

        val response = gemini.generateResponse(prompt)
        
        return try {
            val plans = mutableMapOf<String, Pair<String, Long>>()
            val types = listOf("RECOMMENDED", "RELAXED", "SPEED")
            
            types.forEach { type ->
                val typeBlockRegex = "\"$type\"\\s*:\\s*\\{([^\\}]+)\\}".toRegex()
                val blockMatch = typeBlockRegex.find(response)
                if (blockMatch != null) {
                    val block = blockMatch.groupValues[1]
                    val dateRegex = "\"targetDate\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                    val budgetRegex = "\"monthlyBudget\"\\s*:\\s*(\\d+)".toRegex()
                    
                    val targetDate = dateRegex.find(block)?.groupValues?.get(1) ?: "計算不可"
                    val monthlyBudget = budgetRegex.find(block)?.groupValues?.get(1)?.toLongOrNull() ?: avgMonthlyExpense
                    plans[type] = targetDate to monthlyBudget
                }
            }
            plans
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
