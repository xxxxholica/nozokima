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
    val goalSetting: GoalSettingEntity? = null,
    val homeAiText: String = "",
    val isAiGenerating: Boolean = false,
    val aiStatus: Int = 0,
    val isAiReady: Boolean = false,
    val isAiCheckingStatus: Boolean = false,
    val isAiInitialized: Boolean = false
)

class HomeViewModel(
    dao: FinanceDao,
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
        val homeAiText = params[6] as String
        val isGenerating = params[7] as Boolean
        val aiStatus = params[8] as Int
        val isAiReady = params[9] as Boolean
        val isAiChecking = params[10] as Boolean
        val isAiInitialized = params[11] as Boolean

        HomeUiState(
            transactions = transactions,
            assets = assets,
            lendings = lendings,
            budgets = budgets,
            goalSetting = goalSetting,
            scheduledExpenses = scheduledExpenses,
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

    fun triggerHomeAnalysis(force: Boolean = false) {
        val state = uiState.value
        if (!state.isAiReady || state.isAiGenerating) return
        
        val totalLendingAmount = state.lendings.asSequence().filter { !it.isRecovered }.sumOf { it.amount - it.recoveredAmount }
        val currentAssets = (state.assets.sumOf { it.amount } + totalLendingAmount).toLong()
        val upcomingTotal = state.scheduledExpenses.asSequence().filter { !it.isCompleted }.sumOf { it.amount }
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
            
        val monthlyBudget = state.budgets.sumOf { it.monthlyAmount }.let { if (it == 0) 100000L else it.toLong() }

        val currentGoal = state.goalSetting
        val currentAssetsForGoal = if (currentGoal?.useVirtualBalance == true) virtualBalance else currentAssets

        val hasGoal = currentGoal != null && currentGoal.targetAmount > 0 && currentGoal.showResults
        val goalProgressRatio = if (hasGoal) (currentAssetsForGoal.toFloat() / currentGoal.targetAmount.toFloat()).coerceIn(0f, 1f) else 0f

        val latestExpense = state.transactions.asSequence().filter { it.isExpense && it.category != "貸付" }.maxByOrNull { it.date }

        if (!force && _homeAiText.value.isNotEmpty() &&
            latestExpense?.id == lastHomeTriggerExpenseId &&
            latestExpense?.amount == lastHomeTriggerExpenseAmount &&
            latestExpense?.name == lastHomeTriggerExpenseName &&
            spentThisMonth == lastHomeTriggerSpentThisMonth &&
            virtualBalance.toInt() == lastHomeTriggerVirtualBalance) {
            return
        }

        lastHomeTriggerExpenseId = latestExpense?.id
        lastHomeTriggerExpenseAmount = latestExpense?.amount
        lastHomeTriggerExpenseName = latestExpense?.name
        lastHomeTriggerSpentThisMonth = spentThisMonth
        lastHomeTriggerVirtualBalance = virtualBalance.toInt()

        val hasData = state.transactions.isNotEmpty() || state.assets.any { it.amount != 0 }

        val prompt = buildString {
            appendLine("あなたは丁寧な言葉遣いながらも、痛いところを突き、ユーザーの浪費を煽るような皮肉めいた『家計の覗き魔』です。")
            if (!hasData) {
                appendLine("ユーザーはまだ家計簿にデータを入力していません。")
                appendLine("「覗き魔」らしく、ユーザーの隠し事やこれからの浪費を期待するような、少し意地の悪い一言で記録を促してください。")
            } else if (latestExpense != null) {
                appendLine("最新の支出（${latestExpense.name}: ¥${String.format(Locale.JAPAN, "%,d", latestExpense.amount)}）を踏まえ、ユーザーの自制心を揺さぶる鋭い皮肉を1つだけ返してください。")
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
}
