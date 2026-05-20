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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nozokima.data.local.FinanceDao
import com.example.nozokima.data.local.entities.*
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
    onInputClick: (String) -> Unit = {},
    onConsultClick: () -> Unit = {},
    onAssetsClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    onGoalClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onAiAdviceClick: (String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val appSettings by dao.getAppSettings().collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..10 -> "おはようございます ☀️"
            in 11..16 -> "こんにちは 😊"
            in 17..20 -> "こんばんは 🌙"
            else -> "おやすみなさい 😴"
        }
    }

    // 初回生成の自動トリガー
    LaunchedEffect(uiState.isAiReady, uiState.transactions, uiState.lendings) {
        if (uiState.isAiReady && uiState.homeAiText.isEmpty() && !uiState.isAiGenerating) {
            viewModel.triggerHomeAnalysis()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = greeting,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                color = NotionTextPrimary,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .wrapContentHeight(Alignment.CenterVertically),
                letterSpacing = (-1).sp
            )
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(40.dp)
                    .background(NotionBorder.copy(alpha = 0.3f), CircleShape)
            ) {
                Icon(Icons.Default.Settings, contentDescription = "設定", tint = NotionTextSecondary)
            }
        }

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            DashboardCard(
                uiState = uiState,
                appSettings = appSettings,
                onRefreshAi = { viewModel.triggerHomeAnalysis() },
                onAiAdviceClick = onAiAdviceClick,
                onGoalClick = onGoalClick,
            ) {
                scope.launch {
                    val current = appSettings ?: AppSettingsEntity()
                    dao.upsertAppSettings(current.copy(isAssetsVisible = !current.isAssetsVisible))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            QuickAccessSection(
                onInputClick = onInputClick,
                onAssetsClick = onAssetsClick,
                onHistoryClick = onHistoryClick,
                onGoalClick = onGoalClick,
                onConsultClick = { onConsultClick() }
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
    onToggleAssetsVisibility: () -> Unit,
) {
    val totalLendingAmount = remember(uiState.lendings) {
        uiState.lendings.asSequence().filter { !it.isRecovered }.sumOf { it.amount - it.recoveredAmount }
    }
    val currentAssets = remember(uiState.assets, totalLendingAmount) {
        uiState.assets.sumOf { it.amount } + totalLendingAmount
    }.toLong()

    val upcomingTotal = remember(uiState.scheduledExpenses) {
        uiState.scheduledExpenses.asSequence().filter { !it.isCompleted }.sumOf { it.amount }
    }
    val virtualBalance = currentAssets - upcomingTotal

    val startOfMonth = remember {
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val spentThisMonth = remember(uiState.transactions, startOfMonth) {
        uiState.transactions.asSequence()
            .filter { (it.date >= startOfMonth) && it.isExpense && (it.category != "貸付") }
            .sumOf { it.amount }
    }.toLong()

    val isGoalSet = uiState.goalSetting != null && uiState.goalSetting.showResults && uiState.goalSetting.targetAmount > 0
    val goalMonthlyBudget = remember(uiState.goalSetting, virtualBalance, isGoalSet) {
        val currentGoal = uiState.goalSetting
        if (isGoalSet && currentGoal != null) {
            val remainingDays = ((currentGoal.targetDateMillis - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(1)
            val remainingMonths = (remainingDays / 30.0).coerceAtLeast(0.1)
            val totalExpectedIncome = (currentGoal.monthlyIncome * remainingMonths).toLong()
            val totalSpendable = (virtualBalance + totalExpectedIncome - currentGoal.targetAmount).coerceAtLeast(0L)
            if (remainingMonths > 0) (totalSpendable / remainingMonths).toLong() else 0L
        } else null
    }

    val monthlyBudget = goalMonthlyBudget ?: virtualBalance
    val spentThisMonthForProgress = if (isGoalSet) spentThisMonth else 0L

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
            Spacer(modifier = Modifier.height(16.dp))
            BudgetProgressSection(
                monthlyBudget = monthlyBudget,
                spentThisMonth = spentThisMonthForProgress,
                onBudgetClick = onGoalClick
            )
            Spacer(modifier = Modifier.height(20.dp))
            GoalProgressSection(
                goalSetting = uiState.goalSetting,
                actualAssetsForGoal = virtualBalance,
                onGoalClick = onGoalClick
            )

            val latestExpense = remember(uiState.transactions) {
                uiState.transactions.asSequence().filter { it.isExpense && it.category != "貸付" }.maxByOrNull { it.date }
            }

            Spacer(modifier = Modifier.height(24.dp))

            AiAnalysisSection(
                uiState = uiState,
                latestExpense = latestExpense,
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
        spentThisMonth == 0L -> NotionSafeGreen // 目標未設定時など
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
        
        // spentThisMonthが0の場合は進捗バーを空にする（逆転して「残り100%」を表現）
        val displaySpentProgress = if (progressTrigger && monthlyBudget > 0) (spentThisMonth.toFloat() / monthlyBudget.toFloat()).coerceIn(0f, 1f) else 0f
        val animatedSpentProgress by animateFloatAsState(
            targetValue = displaySpentProgress,
            animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            label = "spentProgress"
        )
        
        // spentThisMonthが0の場合、trackColorをNotionSafeGreenに、color（消費分）を透明にすることで「100%残り」を表現
        LinearProgressIndicator(
            progress = { animatedSpentProgress },
            modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
            color = if (spentThisMonth == 0L) Color.Transparent else budgetAmountColor,
            trackColor = if (spentThisMonth == 0L) NotionSafeGreen else NotionBorder,
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
    
    val progressRatio = if (hasGoal) (actualAssetsForGoal.toFloat() / goalSetting.targetAmount.toFloat()).coerceIn(0f, 1.2f) else 0f
    val percentage = (progressRatio * 100).toInt()
    
    val animatedGoalProgress by animateFloatAsState(
        targetValue = progressRatio.coerceAtMost(1f),
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "goalProgress"
    )

    val goalDisplayTitle = when {
        !hasGoal -> "目標未設定"
        (goalSetting?.title?.isNotEmpty() == true) -> goalSetting.title
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
                fontWeight = FontWeight.Bold
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
    latestExpense: TransactionEntity?,
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
            .heightIn(min = 210.dp)
            .background(Color(0xFFF5F5F5), RoundedCornerShape(16.dp))
            .border(1.dp, NotionBorder, RoundedCornerShape(16.dp))
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
                
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(10.dp))
                        .padding(8.dp)
                ) {
                    if (latestExpense != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color(0xFFE57373).copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(getCategoryIcon(latestExpense.category), null, tint = Color(0xFFE57373), modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(latestExpense.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NotionTextPrimary, maxLines = 1)
                                Text(latestExpense.category, fontSize = 10.sp, color = NotionTextSecondary)
                            }
                            Text("¥ ${String.format(Locale.JAPAN, "%,d", latestExpense.amount)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE57373))
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(NotionBorder.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Pending, null, tint = NotionTextSecondary.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Text("記録がありません", fontSize = 13.sp, color = NotionTextSecondary.copy(alpha = 0.4f), fontWeight = FontWeight.Medium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
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
fun QuickAccessSection(
    onInputClick: (String) -> Unit,
    onAssetsClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onGoalClick: () -> Unit,
    onConsultClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickAccessItem(
                label = "収入",
                icon = Icons.Default.Payments,
                color = NotionSafeGreen,
                onClick = { onInputClick("収入") },
                modifier = Modifier.weight(1f)
            )
            QuickAccessItem(
                label = "支出",
                icon = Icons.Default.ShoppingCart,
                color = Color(0xFFD32F2F),
                onClick = { onInputClick("支出") },
                modifier = Modifier.weight(1f)
            )
            QuickAccessItem(
                label = "AI相談",
                icon = Icons.Default.AutoAwesome,
                color = Color(0xFF9C27B0),
                onClick = onConsultClick,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickAccessItem(
                label = "資産状況",
                icon = Icons.Default.AccountBalanceWallet,
                color = Color(0xFFFB8C00),
                onClick = onAssetsClick,
                modifier = Modifier.weight(1f)
            )
            QuickAccessItem(
                label = "目標",
                icon = Icons.Default.Flag,
                color = Color(0xFF00897B),
                onClick = onGoalClick,
                modifier = Modifier.weight(1f)
            )
            QuickAccessItem(
                label = "履歴",
                icon = Icons.Default.History,
                color = Color(0xFF607D8B),
                onClick = onHistoryClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun QuickAccessItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.aspectRatio(1f),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, NotionBorder)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = NotionTextPrimary
            )
        }
    }
}

fun getCategoryIcon(category: String) = when(category) {
    "食生活" -> Icons.Default.Restaurant
    "住まい" -> Icons.Default.Home
    "インフラ" -> Icons.Default.Wifi
    "日用雑貨" -> Icons.Default.LocalMall
    "移動・交通" -> Icons.Default.Place
    "健康・医療" -> Icons.Default.MedicalServices
    "自分磨き" -> Icons.Default.School
    "レジャー" -> Icons.Default.Star
    "交際・贈答" -> Icons.Default.Favorite
    "美容・装い" -> Icons.Default.Face
    "特別な支出" -> Icons.Default.CardGiftcard
    "給与" -> Icons.Default.AccountBalance
    "事業・副業" -> Icons.Default.Build
    "資産運用" -> Icons.Default.Savings
    "臨時収入" -> Icons.Default.Star
    "給付・手当" -> Icons.Default.Info
    "還付・返金" -> Icons.Default.Refresh
    "贈与・祝金" -> Icons.Default.Favorite
    "ポイ活" -> Icons.Default.Payments
    "不用品売却" -> Icons.Default.LocalMall
    "繰越金" -> Icons.Default.History
    "利息・配当" -> Icons.Default.ShowChart
    "貸付" -> Icons.Outlined.RequestPage
    "回収" -> Icons.Default.Handshake
    else -> Icons.Default.MoreHoriz
}
