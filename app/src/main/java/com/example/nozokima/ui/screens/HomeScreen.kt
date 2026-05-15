package com.example.nozokima.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.RequestPage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nozokima.model.*
import com.example.nozokima.data.local.FinanceDao
import com.example.nozokima.data.local.entities.*
import com.example.nozokima.ui.components.ScreenHeader
import com.example.nozokima.ui.viewmodel.HomeViewModel
import com.example.nozokima.ui.viewmodel.HomeUiState
import com.google.mlkit.genai.common.FeatureStatus
import kotlinx.coroutines.launch
import ui.theme.*
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    dao: FinanceDao,
    onConsultClick: (Transaction) -> Unit = {},
    onAiAdviceClick: (String) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onCategoryClick: (String) -> Unit = {},
    onGoalClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val appSettings by dao.getAppSettings().collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..10 -> "おはようございます"
            in 11..16 -> "こんにちは"
            in 17..20 -> "こんばんは"
            else -> "おやすみなさい"
        }
    }

    // 初回生成の自動トリガー
    LaunchedEffect(uiState.isAiReady, uiState.assets, uiState.transactions, uiState.lendings) {
        if (uiState.isAiReady && uiState.homeAiText.isEmpty() && uiState.assets.isNotEmpty() && !uiState.isAiGenerating) {
            viewModel.triggerHomeAnalysis()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader(title = greeting)
        Spacer(modifier = Modifier.height(16.dp))

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            DashboardCard(
                uiState = uiState,
                appSettings = appSettings,
                onRefreshAi = { viewModel.triggerHomeAnalysis() },
                onAiAdviceClick = onAiAdviceClick,
                onGoalClick = onGoalClick,
                onToggleAssetsVisibility = {
                    scope.launch {
                        val current = appSettings ?: AppSettingsEntity()
                        dao.upsertAppSettings(current.copy(isAssetsVisible = !current.isAssetsVisible))
                    }
                }
            )

            Spacer(modifier = Modifier.height(32.dp))

            RecentRecordsSection(
                transactions = uiState.transactions,
                onConsultClick = onConsultClick
            )

            Spacer(modifier = Modifier.height(32.dp))

            MonthlyStatisticsSection(
                transactions = uiState.transactions,
                onCategoryClick = onCategoryClick
            )

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun DashboardCard(
    uiState: HomeUiState,
    appSettings: AppSettingsEntity?,
    onRefreshAi: () -> Unit,
    onAiAdviceClick: (String) -> Unit,
    onGoalClick: () -> Unit,
    onToggleAssetsVisibility: () -> Unit
) {
    val totalLendingAmount = remember(uiState.lendings) { 
        uiState.lendings.filter { !it.isRecovered }.sumOf { it.amount - it.recoveredAmount } 
    }
    val currentAssets = remember(uiState.assets, totalLendingAmount) {
        uiState.assets.sumOf { it.amount } + totalLendingAmount 
    }.toLong()

    val startOfMonth = remember {
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    
    val spentThisMonth = remember(uiState.transactions, startOfMonth) {
        uiState.transactions
            .filter { it.date >= startOfMonth && it.isExpense && it.category != "貸付" }
            .sumOf { it.amount }
    }.toLong()

    val goalMonthlyBudget = remember(uiState.goalSetting, currentAssets) {
        val currentGoal = uiState.goalSetting
        if (currentGoal != null && currentGoal.showResults && currentGoal.targetAmount > 0) {
            val remainingDays = ((currentGoal.targetDateMillis - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(1)
            val remainingMonths = (remainingDays / 30.0).coerceAtLeast(0.1)
            val totalExpectedIncome = (currentGoal.monthlyIncome * remainingMonths).toLong()
            val totalSpendable = (currentAssets + totalExpectedIncome - currentGoal.targetAmount).coerceAtLeast(0L)
            if (remainingMonths > 0) (totalSpendable / remainingMonths).toLong() else 0L
        } else null
    }

    val monthlyBudget = goalMonthlyBudget ?: uiState.budgets.sumOf { it.monthlyAmount }.let { 
        if (it == 0) currentAssets else it.toLong() 
    }
    val daysUntilReset = remember {
        val cal = Calendar.getInstance()
        cal.getActualMaximum(Calendar.DAY_OF_MONTH) - cal.get(Calendar.DAY_OF_MONTH)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.dp, NotionBorder, RoundedCornerShape(16.dp))
            .padding(24.dp)
    ) {
        Column {
            AssetHeader(
                currentAssets = currentAssets,
                daysUntilReset = daysUntilReset,
                isAssetsVisible = appSettings?.isAssetsVisible ?: true,
                onToggleVisibility = onToggleAssetsVisibility
            )
            Spacer(modifier = Modifier.height(24.dp))
            BudgetProgressSection(
                monthlyBudget = monthlyBudget,
                spentThisMonth = spentThisMonth,
                onBudgetClick = onGoalClick
            )
            Spacer(modifier = Modifier.height(20.dp))
            GoalProgressSection(
                goalSetting = uiState.goalSetting,
                actualAssetsForGoal = currentAssets,
                onGoalClick = onGoalClick
            )
            Spacer(modifier = Modifier.height(20.dp))
            AiAnalysisSection(
                uiState = uiState,
                onRefreshAi = onRefreshAi,
                onAiAdviceClick = onAiAdviceClick
            )
        }
    }
}

@Composable
fun AssetHeader(
    currentAssets: Long,
    daysUntilReset: Int,
    isAssetsVisible: Boolean,
    onToggleVisibility: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("資産総額", color = NotionTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                IconButton(
                    onClick = onToggleVisibility,
                    modifier = Modifier.size(24.dp).padding(start = 4.dp)
                ) {
                    Icon(
                        imageVector = if (isAssetsVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Toggle Assets Visibility",
                        modifier = Modifier.size(14.dp),
                        tint = NotionTextSecondary
                    )
                }
            }
            Text(
                text = if (isAssetsVisible) "¥ ${String.format(Locale.JAPAN, "%,d", currentAssets)}" else "¥ ••••••••",
                color = NotionTextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("リセットまで", color = NotionTextSecondary, fontSize = 10.sp)
            Text("$daysUntilReset 日", color = NotionSafeGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun BudgetProgressSection(monthlyBudget: Long, spentThisMonth: Long, onBudgetClick: () -> Unit = {}) {
    val remainingAmount = monthlyBudget - spentThisMonth
    val spentRatio = if (monthlyBudget > 0) spentThisMonth.toFloat() / monthlyBudget.toFloat() else 0f
    val budgetAmountColor = when {
        spentRatio >= 1.0f -> Color(0xFFE57373)
        spentRatio >= 0.5f -> Color(0xFFFFB74D)
        else -> NotionSafeGreen
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onBudgetClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("あといくら使える？", color = NotionTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "¥ ${String.format(Locale.JAPAN, "%,d", remainingAmount)}",
                    color = budgetAmountColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = budgetAmountColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        
        var progressTrigger by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { progressTrigger = true }
        
        val spentProgress = if (progressTrigger && monthlyBudget > 0) (spentThisMonth.toFloat() / monthlyBudget.toFloat()).coerceIn(0f, 1f) else 0f
        val animatedSpentProgress by animateFloatAsState(
            targetValue = spentProgress,
            animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            label = "spentProgress"
        )
        LinearProgressIndicator(
            progress = { animatedSpentProgress },
            modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
            color = budgetAmountColor,
            trackColor = NotionBorder,
            strokeCap = StrokeCap.Round
        )
    }
}

@Composable
fun GoalProgressSection(
    goalSetting: GoalSettingEntity?,
    actualAssetsForGoal: Long,
    onGoalClick: () -> Unit
) {
    val hasGoal = goalSetting != null && goalSetting.targetAmount > 0 && goalSetting.showResults
    
    val progressRatio = if (hasGoal) (actualAssetsForGoal.toFloat() / goalSetting!!.targetAmount.toFloat()).coerceIn(0f, 1.2f) else 0f
    val percentage = (progressRatio * 100).toInt()
    
    val animatedGoalProgress by animateFloatAsState(
        targetValue = progressRatio.coerceAtMost(1f),
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "goalProgress"
    )

    val goalDisplayTitle = when {
        !hasGoal -> "目標未設定"
        goalSetting!!.title.isNotEmpty() -> goalSetting.title
        else -> "目標の貯金"
    }

    val goalBarColor = when {
        !hasGoal -> NotionBorder
        percentage >= 100 -> Color(0xFFFFD700) // Gold
        percentage >= 80 -> Color(0xFFFF9800) // Orange
        percentage >= 50 -> NotionSafeGreen
        else -> Color(0xFF2196F3) // Blue
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onGoalClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = goalDisplayTitle,
                color = if (hasGoal) NotionTextSecondary else NotionTextSecondary.copy(alpha = 0.6f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasGoal) {
                    Text(
                        text = "$percentage%",
                        color = goalBarColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = "—",
                        color = NotionTextSecondary.copy(alpha = 0.5f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = if (hasGoal) goalBarColor else NotionTextSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { animatedGoalProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp)),
            color = goalBarColor,
            trackColor = NotionBorder,
            strokeCap = StrokeCap.Round
        )
    }
}

@Composable
fun AiAnalysisSection(
    uiState: HomeUiState,
    onRefreshAi: () -> Unit,
    onAiAdviceClick: (String) -> Unit
) {
    val isWaitingForCheck = (!uiState.isAiInitialized || uiState.isAiCheckingStatus) && uiState.homeAiText.isEmpty()
    val isGeneratingNow = uiState.isAiGenerating && uiState.homeAiText.isEmpty()
    val showProgress = isWaitingForCheck || isGeneratingNow
    val statusLabel = when {
        isWaitingForCheck && uiState.isAiInitialized -> "AI を確認中..."
        isGeneratingNow   -> "分析中..."
        else              -> null
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp)
            .background(Color(0xFFF5F5F5), RoundedCornerShape(10.dp))
            .padding(12.dp),
        contentAlignment = if (isWaitingForCheck) Alignment.Center else Alignment.TopStart
    ) {
        if (isWaitingForCheck) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = NotionSafeGreen,
                    strokeWidth = 3.dp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("AIの状態を確認中...", color = NotionTextSecondary, fontSize = 12.sp)
            }
        } else {
            val isUnavailable = uiState.aiStatus == FeatureStatus.UNAVAILABLE
            val themeColor = if (isUnavailable) Color(0xFFE57373) else NotionSafeGreen
            
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(themeColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isUnavailable) Icons.Default.Info else Icons.Default.AutoAwesome,
                            contentDescription = "AI",
                            tint = themeColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isUnavailable) "覗き魔AI は利用できません" else "覗き魔 AI",
                        color = themeColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (!isUnavailable) {
                        val isGenerating = uiState.isAiGenerating
                        val hasText = uiState.homeAiText.isNotEmpty()
                        
                        // 再生成ボタン (アイコンのみ)
                        Surface(
                            onClick = onRefreshAi,
                            enabled = !isGenerating,
                            modifier = Modifier.size(28.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = if (isGenerating) NotionBorder.copy(alpha = 0.5f) else themeColor.copy(alpha = 0.1f),
                            contentColor = if (isGenerating) NotionTextSecondary.copy(alpha = 0.5f) else themeColor
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        // 詳しく聞くボタン
                        Surface(
                            onClick = { onAiAdviceClick(uiState.homeAiText) },
                            enabled = !isGenerating && hasText,
                            modifier = Modifier.height(28.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = if (isGenerating || !hasText) NotionBorder.copy(alpha = 0.5f) else themeColor.copy(alpha = 0.1f),
                            contentColor = if (isGenerating || !hasText) NotionTextSecondary.copy(alpha = 0.5f) else themeColor
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Chat, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("詳しく聞く", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                if (showProgress) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)),
                        color = NotionSafeGreen,
                        trackColor = NotionSafeGreen.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                if (statusLabel != null) {
                    Text(statusLabel, color = NotionTextSecondary.copy(alpha = 0.6f), fontSize = 12.sp)
                } else if (uiState.aiStatus == FeatureStatus.UNAVAILABLE) {
                    val uriHandler = LocalUriHandler.current
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Gemini Nanoに対応したデバイスのみ利用可能です。\n対応デバイスは下記リンクをご覧ください。",
                            color = NotionTextPrimary, fontSize = 12.sp, lineHeight = 18.sp
                        )
                        Text(
                            text = "https://developers.google.com/ml-kit/genai?hl=ja",
                            color = Color(0xFF1976D2), fontSize = 12.sp,
                            modifier = Modifier.clickable { uriHandler.openUri("https://developers.google.com/ml-kit/genai?hl=ja") }
                        )
                    }
                } else if (uiState.homeAiText.isNotEmpty()) {
                    Text(uiState.homeAiText, color = NotionTextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
        }
    }
}

@Composable
fun RecentRecordsSection(
    transactions: List<TransactionEntity>,
    onConsultClick: (Transaction) -> Unit
) {
    Text("最近の記録", color = NotionTextSecondary, style = MaterialTheme.typography.titleSmall)
    Spacer(modifier = Modifier.height(12.dp))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NotionBorder, RoundedCornerShape(12.dp))
            .background(Color.White, RoundedCornerShape(12.dp))
    ) {
        if (transactions.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("記録がまだありません", color = NotionTextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
        } else {
            Column {
                transactions.take(5).forEachIndexed { index, entity ->
                    val tx = Transaction(entity.id, entity.name, entity.amount, entity.category, entity.date)
                    RecordRow(tx = tx, isExpense = entity.isExpense, onConsultClick = onConsultClick)
                    if (index < minOf(transactions.size, 5) - 1) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), thickness = 0.5.dp, color = NotionBorder)
                    }
                }
            }
        }
    }
}

@Composable
fun RecordRow(
    tx: Transaction,
    isExpense: Boolean,
    onConsultClick: (Transaction) -> Unit
) {
    val iconColor = when {
        tx.category == "貸付" -> Color(0xFFFFB300)
        tx.category == "回収" -> Color(0xFF00897B)
        isExpense -> Color(0xFFE57373)
        else -> NotionSafeGreen
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier.size(38.dp)
                    .background(iconColor.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                    .border(1.dp, NotionBorder, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getCategoryIcon(tx.category),
                    contentDescription = tx.category,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(tx.name, color = NotionTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(tx.category, color = NotionTextSecondary, fontSize = 12.sp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "¥ ${String.format(Locale.JAPAN, "%,d", tx.amount)}",
                color = iconColor, fontSize = 15.sp, fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = { if (isExpense) onConsultClick(tx) },
                modifier = Modifier.size(32.dp).background(NotionBackground, CircleShape),
                enabled = isExpense
            ) {
                Icon(Icons.Default.Face, "AI相談",
                    tint = if (isExpense) NotionSafeGreen else NotionTextSecondary.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun MonthlyStatisticsSection(
    transactions: List<TransactionEntity>,
    onCategoryClick: (String) -> Unit
) {
    Text("今月の統計", color = NotionTextSecondary, style = MaterialTheme.typography.titleSmall)
    Spacer(modifier = Modifier.height(12.dp))

    val startOfMonth = remember {
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val monthlyCategoryGroups = remember(transactions, startOfMonth) {
        transactions
            .filter { it.date >= startOfMonth && it.isExpense && it.category != "貸付" }
            .groupBy { it.category }
            .mapValues { it.value.sumOf { t -> t.amount } }
            .toList()
            .sortedByDescending { it.second }
            .take(3)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NotionBorder, RoundedCornerShape(12.dp))
            .background(Color.White, RoundedCornerShape(12.dp))
    ) {
        if (monthlyCategoryGroups.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("今月の支出はまだありません", color = NotionTextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
        } else {
            Column {
                monthlyCategoryGroups.forEachIndexed { index, (category, amount) ->
                    StatRow(category = category, amount = amount.toLong(), onClick = { onCategoryClick(category) })
                    if (index < monthlyCategoryGroups.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), thickness = 0.5.dp, color = NotionBorder)
                    }
                }
            }
        }
    }
}

@Composable
fun StatRow(category: String, amount: Long, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier.size(38.dp).background(Color.White, RoundedCornerShape(10.dp)).border(1.dp, NotionBorder, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = getCategoryIcon(category), contentDescription = category, tint = NotionTextSecondary, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(category, color = NotionTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("¥ ${String.format(Locale.JAPAN, "%,d", amount)}", color = NotionTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = NotionTextSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

fun getCategoryIcon(category: String) = when(category) {
    "食費" -> Icons.Default.ShoppingCart
    "日用品" -> Icons.Default.Build
    "交通費" -> Icons.Default.Place
    "交際費" -> Icons.Default.Favorite
    "娯楽" -> Icons.Default.Star
    "美容" -> Icons.Default.Face
    "健康" -> Icons.Default.Info
    "その他" -> Icons.Default.MoreHoriz
    "給与" -> Icons.Default.AccountBalance
    "賞与" -> Icons.Default.Star
    "副業" -> Icons.Default.Build
    "お小遣い" -> Icons.Default.Favorite
    "還付金" -> Icons.Default.Info
    "貸付" -> Icons.Outlined.RequestPage
    "回収" -> Icons.Default.Handshake
    else -> Icons.Default.MoreHoriz
}
