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
import java.text.SimpleDateFormat
import java.util.*

data class GoalProposal(
    val title: String,
    val targetAmount: Long,
    val targetDateMillis: Long,
    val advice: String
)

data class HomeUiState(
    val transactions: List<TransactionEntity> = emptyList(),
    val assets: List<AssetEntity> = emptyList(),
    val lendings: List<LendingEntity> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList(),
    val scheduledExpenses: List<ScheduledExpenseEntity> = emptyList(),
    val goalSetting: GoalSettingEntity? = null,
    val homeAiText: String = "",
    val goalAiText: String = "",
    val goalProposal: GoalProposal? = null,
    val goalPlanningMessages: List<com.example.nozokima.model.ChatMessage> = emptyList(),
    val isAiGenerating: Boolean = false,
    val aiStatus: Int = 0, // Using Int as per GeminiNanoModel
    val isAiReady: Boolean = false,
    val isAiCheckingStatus: Boolean = false,
    val isAiInitialized: Boolean = false,
    val planningStep: Int = 0 // 0: Purpose, 1: Amount, 2: Date, 3: Income, 4: AI Refinement
)

class HomeViewModel(
    dao: FinanceDao,
    private val gemini: GeminiNanoModel,
) : ViewModel() {

    private val _homeAiText = MutableStateFlow("")
    private val _goalAiText = MutableStateFlow("")
    private val _goalProposal = MutableStateFlow<GoalProposal?>(null)
    private val _goalPlanningMessages = MutableStateFlow<List<com.example.nozokima.model.ChatMessage>>(emptyList())
    private val _planningStep = MutableStateFlow(0)
    
    val uiState: StateFlow<HomeUiState> = combine(
        dao.getAllTransactions(),
        dao.getAllAssets(),
        dao.getAllLendings(),
        dao.getAllBudgets(),
        dao.getGoalSetting(),
        dao.getAllScheduledExpenses(),
        _homeAiText.asStateFlow(),
        _goalAiText.asStateFlow(),
        _goalProposal.asStateFlow(),
        _goalPlanningMessages.asStateFlow(),
        _planningStep.asStateFlow(),
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
        val goalProposal = params[8] as GoalProposal?
        @Suppress("UNCHECKED_CAST")
        val goalPlanningMessages = params[9] as List<com.example.nozokima.model.ChatMessage>
        val planningStep = params[10] as Int
        val isGenerating = params[11] as Boolean
        val aiStatus = params[12] as Int
        val isAiReady = params[13] as Boolean
        val isAiChecking = params[14] as Boolean
        val isAiInitialized = params[15] as Boolean

        HomeUiState(
            transactions = transactions,
            assets = assets,
            lendings = lendings,
            budgets = budgets,
            goalSetting = goalSetting,
            scheduledExpenses = scheduledExpenses,
            homeAiText = homeAiText,
            goalAiText = goalAiText,
            goalProposal = goalProposal,
            goalPlanningMessages = goalPlanningMessages,
            isAiGenerating = isGenerating,
            aiStatus = aiStatus,
            isAiReady = isAiReady,
            isAiCheckingStatus = isAiChecking,
            isAiInitialized = isAiInitialized,
            planningStep = planningStep
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
        
        // データの計算 (in background via Flow combination or here if needed immediately)
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
            
        val defaultBudget = state.budgets.sumOf { it.monthlyAmount }.let { if (it == 0) 100000L else it.toLong() }
        
        val currentGoal = state.goalSetting
        val currentAssetsForGoal = if (currentGoal?.useVirtualBalance == true) virtualBalance else currentAssets
        val goalMonthlyBudget = if ((currentGoal != null) && currentGoal.showResults && (currentGoal.targetAmount > 0)) {
            val remainingDays = ((currentGoal.targetDateMillis - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(1)
            val remainingMonths = (remainingDays / 30.0).coerceAtLeast(0.1)
            val totalExpectedIncome = (currentGoal.monthlyIncome * remainingMonths).toLong()
            val totalSpendable = (currentAssetsForGoal + totalExpectedIncome - currentGoal.targetAmount).coerceAtLeast(0L)
            if (remainingMonths > 0) (totalSpendable / remainingMonths).toLong() else 0L
        } else null
        val monthlyBudget = goalMonthlyBudget ?: defaultBudget

        val hasGoal = currentGoal != null && currentGoal.targetAmount > 0 && currentGoal.showResults
        val goalProgressRatio = if (hasGoal) (currentAssetsForGoal.toFloat() / currentGoal!!.targetAmount.toFloat()).coerceIn(0f, 1f) else 0f

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

    fun triggerGoalAnalysis() {
        val state = uiState.value
        if (!state.isAiReady || state.isAiGenerating) return
        
        val currentGoal = state.goalSetting ?: return
        if (currentGoal.targetAmount <= 0L) return

        val totalLendingAmount = state.lendings.asSequence().filter { !it.isRecovered }.sumOf { it.amount - it.recoveredAmount }
        val currentAssets = state.assets.sumOf { it.amount } + totalLendingAmount
        val upcomingTotal = state.scheduledExpenses.asSequence().filter { !it.isCompleted }.sumOf { it.amount }
        val virtualBalance = currentAssets - upcomingTotal

        val remainingDays = ((currentGoal.targetDateMillis - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(1)
        val remainingMonths = (remainingDays / 30.0).coerceAtLeast(0.1)
        val totalExpectedIncome = (currentGoal.monthlyIncome * remainingMonths).toLong()
        
        val currentAssetsForGoal = if (currentGoal.useVirtualBalance) virtualBalance else currentAssets
        val totalSpendable = (currentAssetsForGoal + totalExpectedIncome - currentGoal.targetAmount).coerceAtLeast(0L)
        val monthlyBudget = if (remainingMonths > 0) (totalSpendable / remainingMonths).toLong() else 0L

        val progressRatio = (currentAssetsForGoal.toFloat() / currentGoal.targetAmount.toFloat()).coerceIn(0f, 1.2f)
        val progressPercent = (progressRatio * 100).toInt()

        val totalGoalDays = ((currentGoal.targetDateMillis - currentGoal.startDateMillis) / (1000 * 60 * 60 * 24)).coerceAtLeast(1L)
        val passedDays = ((System.currentTimeMillis() - currentGoal.startDateMillis) / (1000 * 60 * 60 * 24)).coerceAtLeast(0L)
        val timeProgressPercent = (passedDays.toFloat() / totalGoalDays.toFloat() * 100).toInt().coerceIn(0, 100)

        val prompt = buildString {
            appendLine("あなたは実用的で実利的な家計の相棒です。")
            appendLine("貯金目標に対する進捗を分析し、アドバイスを返してください。")
            appendLine("【出力構成】")
            appendLine("1. 内容に対する肯定")
            appendLine("2. 痛いところをつく（鋭い指摘）")
            appendLine("3. 建設的な議論への誘い（問いかけ）")
            appendLine("【出力ルール】")
            appendLine("・合計120文字以内で、上記3ステップを1つの自然な文章として構成してください。")
            appendLine("・「肯定：」などの見出しは一切含めないでください。")
            appendLine("・コンテキスト節約のため、極めて簡潔で的確な表現を心がけてください。")
            appendLine("・最後に自然な問いかけを一文添えてください。")
            appendLine("自己紹介、タメ口、精神論、回答傾向に関する説明、および「深掘りする問いかけ」という言葉の使用は禁止です。")
            appendLine("丁寧な言葉遣い（です・ます調）で、数字に裏打ちされた具体的な指摘を端的に伝えてください。")
            appendLine()
            appendLine("目標: ${currentGoal.title}")
            appendLine("目標金額: ¥${String.format(Locale.JAPAN, "%,d", currentGoal.targetAmount)}")
            appendLine("現在の資産（計算用）: ¥${String.format(Locale.JAPAN, "%,d", currentAssetsForGoal)}（達成率$progressPercent%）")
            appendLine("期限まで: $remainingDays 日（期間経過率$timeProgressPercent%）")
            appendLine("今後の月予算目安: ¥${String.format(Locale.JAPAN, "%,d", monthlyBudget)}")
            appendLine("計算ベース: ${if (currentGoal.useVirtualBalance) "自由に使える残高" else "実資産"}")
            
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

    fun planGoal(text: String) {
        val state = uiState.value
        if (text.isBlank()) return

        val userMsg = com.example.nozokima.model.ChatMessage(id = UUID.randomUUID().toString(), text = text, isUser = true)
        _goalPlanningMessages.value = _goalPlanningMessages.value + userMsg

        if (_planningStep.value < 4) {
            // ここではテキストボックス入力を受け付けない（ボタンUIからのみ入る想定だが、念のためガード）
            return
        } else {
            // ステップ4以降は対話モード
            triggerPlanningAiDialogue()
        }
    }

    fun updatePlanningValue(step: Int, value: String) {
        val currentStep = _planningStep.value
        if (step != currentStep) return

        // チャットメッセージとして追加しない
        _planningStep.value += 1
        if (_planningStep.value == 4) {
            triggerPlanningAiAnalysis()
        }
    }

    private fun triggerPlanningAiAnalysis() {
        val state = uiState.value
        if (!state.isAiReady || state.isAiGenerating) return

        val totalLendingAmount = state.lendings.asSequence().filter { !it.isRecovered }.sumOf { it.amount - it.recoveredAmount }
        val currentAssets = state.assets.sumOf { it.amount } + totalLendingAmount
        val upcomingTotal = state.scheduledExpenses.asSequence().filter { !it.isCompleted }.sumOf { it.amount }
        val virtualBalance = currentAssets - upcomingTotal
        
        val history = _goalPlanningMessages.value.joinToString("\n") {
            if (it.isUser) "ユーザー: ${it.text}" else "AI: ${it.text}"
        }

        val prompt = buildString {
            appendLine("あなたは家計コンサルタントです。思考過程は省き、おすすめのプランと理由のみを簡潔に出力してください。コンテキスト長を節約するため、冗長な挨拶や説明は不要です。")
            appendLine("【状況】総資産:¥${String.format(Locale.JAPAN, "%,d", currentAssets)}, 実質残高:¥${String.format(Locale.JAPAN, "%,d", virtualBalance)}")
            appendLine("【希望】")
            appendLine(history)
            appendLine()
            appendLine("【回答形式】")
            appendLine("[プランの理由を200文字程度で記述]")
            appendLine("TITLE: [目標名]")
            appendLine("AMOUNT: [数値のみ]")
            appendLine("DATE: [YYYY/MM/DD]")
            appendLine("ADVICE: [短いアドバイス]")
        }

        generatePlanningResponse(prompt)
    }

    private fun triggerPlanningAiDialogue() {
        val state = uiState.value
        if (!state.isAiReady || state.isAiGenerating) return

        val totalLendingAmount = state.lendings.asSequence().filter { !it.isRecovered }.sumOf { it.amount - it.recoveredAmount }
        val currentAssets = state.assets.sumOf { it.amount } + totalLendingAmount
        val upcomingTotal = state.scheduledExpenses.asSequence().filter { !it.isCompleted }.sumOf { it.amount }
        val virtualBalance = currentAssets - upcomingTotal
        
        val history = _goalPlanningMessages.value.joinToString("\n") { 
            if (it.isUser) "ユーザー: ${it.text}" else "AI: ${it.text}"
        }

        val prompt = buildString {
            appendLine("家計コンサルタントとして、ユーザーの要望に応じてプランを修正してください。思考過程は出力せず、結論のみを簡潔に回答してください。")
            appendLine("【状況】総資産:¥${String.format(Locale.JAPAN, "%,d", currentAssets)}, 実質残高:¥${String.format(Locale.JAPAN, "%,d", virtualBalance)}")
            appendLine("【会話】")
            appendLine(history)
            appendLine()
            appendLine("【回答形式】")
            appendLine("[修正理由や回答を簡潔に記述]")
            appendLine("TITLE: [目標名]")
            appendLine("AMOUNT: [数値のみ]")
            appendLine("DATE: [YYYY/MM/DD]")
            appendLine("ADVICE: [短いアドバイス]")
        }

        generatePlanningResponse(prompt)
    }

    private fun generatePlanningResponse(prompt: String) {
        val aiMsgId = UUID.randomUUID().toString()
        viewModelScope.launch {
            var accumulatedText = ""
            try {
                gemini.generateResponseStream(prompt).collect { chunk ->
                    accumulatedText += chunk
                    val currentMessages = _goalPlanningMessages.value.toMutableList()
                    val existingAiMsgIndex = currentMessages.indexOfFirst { it.id == aiMsgId }
                    val aiMsg = com.example.nozokima.model.ChatMessage(id = aiMsgId, text = accumulatedText, isUser = false)
                    
                    if (existingAiMsgIndex != -1) {
                        currentMessages[existingAiMsgIndex] = aiMsg
                    } else {
                        currentMessages.add(aiMsg)
                    }
                    _goalPlanningMessages.value = currentMessages
                }
                parseAndSetProposal(accumulatedText)
            } catch (_: Exception) {
            }
        }
    }

    fun startGoalPlanning() {
        if (_goalPlanningMessages.value.isNotEmpty()) return
        _planningStep.value = 0
        _goalPlanningMessages.value = listOf(
            com.example.nozokima.model.ChatMessage(
                id = UUID.randomUUID().toString(),
                text = "こんにちは。目標設定のお手伝いをします。\nまずは、何のために貯金をしたいか教えてください。（例：旅行、車の購入など）",
                isUser = false
            )
        )
    }

    private fun parseAndSetProposal(text: String) {
        val lines = text.lines()
        var title = ""
        var amount = 0L
        var dateMillis = 0L
        var advice = ""

        lines.forEach { line ->
            val trimmedLine = line.trim()
            when {
                trimmedLine.startsWith("TITLE:") -> title = trimmedLine.removePrefix("TITLE:").trim()
                trimmedLine.startsWith("AMOUNT:") -> {
                    val amountStr = trimmedLine.removePrefix("AMOUNT:").trim().filter { it.isDigit() }
                    amount = amountStr.toLongOrNull() ?: 0L
                }
                trimmedLine.startsWith("DATE:") -> {
                    val dateStr = trimmedLine.removePrefix("DATE:").trim()
                    try {
                        val sdf = SimpleDateFormat("yyyy/MM/dd", Locale.JAPAN)
                        dateMillis = sdf.parse(dateStr)?.time ?: 0L
                    } catch (_: Exception) {}
                }
                trimmedLine.startsWith("ADVICE:") -> advice = trimmedLine.removePrefix("ADVICE:").trim()
            }
        }

        if (title.isNotEmpty() && amount > 0L && dateMillis > 0L) {
            _goalProposal.value = GoalProposal(title, amount, dateMillis, advice)
        }
    }

    fun clearGoalProposal() {
        _goalProposal.value = null
        _goalPlanningMessages.value = emptyList()
        _planningStep.value = 0
    }
}
