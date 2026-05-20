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
import com.example.nozokima.data.manager.GeminiNanoModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

data class HomeUiState(
    val transactions: List<TransactionEntity> = emptyList(),
    val assets: List<AssetEntity> = emptyList(),
    val lendings: List<LendingEntity> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList(),
    val scheduledExpenses: List<ScheduledExpenseEntity> = emptyList(),
    val goalSetting: GoalSettingEntity? = null,
    val homeAiText: String = "",
    val goalAiText: String = "",
    val isAiGenerating: Boolean = false,
    val aiStatus: Int = 0, // Using Int as per GeminiNanoModel
    val isAiReady: Boolean = false,
    val isAiCheckingStatus: Boolean = false,
    val isAiInitialized: Boolean = false,
)

class HomeViewModel(
    private val dao: FinanceDao,
    private val gemini: GeminiNanoModel,
) : ViewModel() {

    private val _homeAiText = MutableStateFlow("")
    private val _goalAiText = MutableStateFlow("")
    
    val uiState: StateFlow<HomeUiState> = combine(
        dao.getAllTransactions(),
        dao.getAllAssets(),
        dao.getAllLendings(),
        dao.getAllBudgets(),
        dao.getGoalSetting(),
        dao.getAllScheduledExpenses(),
        _homeAiText.asStateFlow(),
        _goalAiText.asStateFlow(),
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
        val homeAiText = params[6] as String
        val goalAiText = params[7] as String
        val isGenerating = params[8] as Boolean
        val aiStatus = params[9] as Int
        val isAiReady = params[10] as Boolean
        val isAiChecking = params[11] as Boolean
        val isAiInitialized = params[12] as Boolean

        HomeUiState(
            transactions = transactions,
            assets = assets,
            lendings = lendings,
            budgets = budgets,
            goalSetting = goalSetting,
            scheduledExpenses = scheduledExpenses,
            homeAiText = homeAiText,
            goalAiText = goalAiText,
            isAiGenerating = isGenerating,
            aiStatus = aiStatus,
            isAiReady = isAiReady,
            isAiCheckingStatus = isAiChecking,
            isAiInitialized = isAiInitialized
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun triggerHomeAnalysis() {
        val state = uiState.value
        if (!state.isAiReady || state.isAiGenerating) return
        
        // データの計算 (in background via Flow combination or here if needed immediately)
        val totalLendingAmount = state.lendings.asSequence().filter { !it.isRecovered }.sumOf { it.amount - it.recoveredAmount }
        val currentAssets = state.assets.sumOf { it.amount } + totalLendingAmount
        val upcomingTotal = state.scheduledExpenses.filter { !it.isCompleted }.sumOf { it.amount }
        val virtualBalance = currentAssets - upcomingTotal

        val calendar = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfMonth = calendar.timeInMillis
        val spentThisMonth = state.transactions.asSequence()
            .filter { (it.date >= startOfMonth) && it.isExpense && (it.category != "貸付") }
            .sumOf { it.amount }
            
        val defaultBudget = state.budgets.sumOf { it.monthlyAmount }.let { if (it == 0) 100000L else it.toLong() }
        
        val currentGoal = state.goalSetting
        val goalMonthlyBudget = if (currentGoal != null && currentGoal.showResults && currentGoal.targetAmount > 0) {
            val remainingDays = ((currentGoal.targetDateMillis - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(1)
            val remainingMonths = (remainingDays / 30.0).coerceAtLeast(0.1)
            val totalExpectedIncome = (currentGoal.monthlyIncome * remainingMonths).toLong()
            val totalSpendable = (virtualBalance + totalExpectedIncome - currentGoal.targetAmount).coerceAtLeast(0L)
            if (remainingMonths > 0) (totalSpendable / remainingMonths).toLong() else 0L
        } else null
        val monthlyBudget = goalMonthlyBudget ?: defaultBudget

        val hasGoal = currentGoal != null && currentGoal.targetAmount > 0 && currentGoal.showResults
        val goalProgressRatio = if (hasGoal) (virtualBalance.toFloat() / currentGoal.targetAmount.toFloat()).coerceIn(0f, 1f) else 0f

        val latestExpense = state.transactions.asSequence().filter { it.isExpense && it.category != "貸付" }.maxByOrNull { it.date }

        val prompt = buildString {
            appendLine("あなたは丁寧で実利的な家計の相棒です。")
            if (latestExpense != null) {
                appendLine("最新の支出（${latestExpense.name}: ¥${String.format(Locale.JAPAN, "%,d", latestExpense.amount)}）を踏まえ、短く鋭いアドバイスを1つだけ返してください。")
            } else {
                appendLine("現在の資産状況を踏まえ、短く鋭いアドバイスを1つだけ返してください。")
            }
            appendLine("【出力ルール】")
            appendLine("・60文字以内の簡潔な1文で回答してください。")
            appendLine("・「深掘りする問いかけ：」などの見出しは一切含めないでください。")
            appendLine("・最後に必ず、本文の一部として自然な質問を一文添えてください。")
            appendLine("【禁止事項】自己紹介、挨拶、タメ口、精神論、長文、複数の指摘")
            appendLine("丁寧な言葉遣い（です・ます調）で、最も重要な1点に絞って伝えてください。")
            appendLine()
            appendLine("今月の支出合計: ¥${String.format(Locale.JAPAN, "%,d", spentThisMonth)}")
            appendLine("月の予算: ¥${String.format(Locale.JAPAN, "%,d", monthlyBudget)}")
            appendLine("自由に使える残高: ¥${String.format(Locale.JAPAN, "%,d", virtualBalance)}")
            if (hasGoal) appendLine("貯金目標: ¥${String.format(Locale.JAPAN, "%,d", currentGoal.targetAmount)}（達成率${(goalProgressRatio * 100).toInt()}%）")
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

    fun triggerGoalAnalysis() {
        val state = uiState.value
        if (!state.isAiReady || state.isAiGenerating) return
        
        val currentGoal = state.goalSetting ?: return
        if (currentGoal.targetAmount <= 0L) return

        val totalLendingAmount = state.lendings.filter { !it.isRecovered }.sumOf { it.amount - it.recoveredAmount }
        val currentAssets = state.assets.sumOf { it.amount } + totalLendingAmount
        val upcomingTotal = state.scheduledExpenses.filter { !it.isCompleted }.sumOf { it.amount }
        val virtualBalance = currentAssets - upcomingTotal

        val remainingDays = ((currentGoal.targetDateMillis - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(1)
        val remainingMonths = (remainingDays / 30.0).coerceAtLeast(0.1)
        val totalExpectedIncome = (currentGoal.monthlyIncome * remainingMonths).toLong()
        val totalSpendable = (virtualBalance + totalExpectedIncome - currentGoal.targetAmount).coerceAtLeast(0L)
        val monthlyBudget = if (remainingMonths > 0) (totalSpendable / remainingMonths).toLong() else 0L

        val progressRatio = (virtualBalance.toFloat() / currentGoal.targetAmount.toFloat()).coerceIn(0f, 1.2f)
        val progressPercent = (progressRatio * 100).toInt()

        val totalGoalDays = ((currentGoal.targetDateMillis - currentGoal.startDateMillis) / (1000 * 60 * 60 * 24)).coerceAtLeast(1L)
        val passedDays = ((System.currentTimeMillis() - currentGoal.startDateMillis) / (1000 * 60 * 60 * 24)).coerceAtLeast(0L)
        val timeProgressPercent = (passedDays.toFloat() / totalGoalDays.toFloat() * 100).toInt().coerceIn(0, 100)

        val prompt = buildString {
            appendLine("あなたは実用的で実利的な家計の相棒です。")
            appendLine("貯金目標に対する進捗を分析し、アドバイスを返してください。")
            appendLine("【出力ルール】")
            appendLine("・求められているものを端的に回答してください。")
            appendLine("・120文字以内の簡潔な1〜2文でまとめてください。")
            appendLine("・「深掘りする問いかけ：」「問いかけ：」などのラベルやプレフィックスは絶対に付けないでください。")
            appendLine("・最後に自然な問いかけを一文添えてください。")
            appendLine("自己紹介、タメ口、精神論、回答傾向に関する説明、および「深掘りする問いかけ」という言葉の使用は禁止です。")
            appendLine("丁寧な言葉遣い（です・ます調）で、数字に裏打ちされた具体的な指摘を端的に伝えてください。")
            appendLine()
            appendLine("目標: ${currentGoal.title}")
            appendLine("目標金額: ¥${String.format(Locale.JAPAN, "%,d", currentGoal.targetAmount)}")
            appendLine("自由に使える残高: ¥${String.format(Locale.JAPAN, "%,d", virtualBalance)}（達成率$progressPercent%）")
            appendLine("期限まで: $remainingDays 日（期間経過率${timeProgressPercent}%）")
            appendLine("今後の月予算目安: ¥${String.format(Locale.JAPAN, "%,d", monthlyBudget)}")
            
            if (progressPercent < timeProgressPercent) {
                appendLine("状況: 貯金のペースが期間経過に対して遅れ気味です。")
            } else if (progressPercent >= 100) {
                appendLine("状況: 目標金額に到達しました。")
            } else {
                appendLine("状況: ペースは安定しています。")
            }
        }

        _goalAiText.value = ""
        viewModelScope.launch {
            var result = ""
            try {
                gemini.generateResponseStream(prompt).collect { chunk ->
                    result += chunk
                    _goalAiText.value = result
                }
            } catch (_: Exception) {
            }
        }
    }
}
