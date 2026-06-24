package com.example.nozokima.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nozokima.data.local.FinanceDao
import com.example.nozokima.data.local.entities.*
import com.example.nozokima.ui.components.getCategoryIcon
import com.example.nozokima.ui.components.ThinkingAnimation
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
        val hour = Calendar.getInstance()[Calendar.HOUR_OF_DAY]
        when (hour) {
            in 5..10 -> "おはようございます ☀️"
            in 11..16 -> "こんにちは 😊"
            in 17..20 -> "こんばんは 🌙"
            else -> "おやすみなさい 😴"
        }
    }

    // 最新の支出を監視
    val latestTxId = remember(uiState.transactions) {
        uiState.transactions.asSequence().filter { it.isExpense && it.category != "貸付" }.maxByOrNull { it.date }?.id
    }

    // 初回生成および最新支出変更時の自動トリガー
    LaunchedEffect(uiState.isAiReady, latestTxId) {
        if (uiState.isAiReady && !uiState.isAiGenerating) {
            viewModel.triggerHomeAnalysis()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
                color = MaterialTheme.colorScheme.onBackground,
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
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
            ) {
                Icon(Icons.Default.Settings, contentDescription = "設定", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            DashboardCard(
                uiState = uiState,
                appSettings = appSettings,
                onRefreshAi = { viewModel.triggerHomeAnalysis(force = true) },
                onAiAdviceClick = onAiAdviceClick,
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
    val virtualBalance = currentAssets - upcomingTotal - totalLendingAmount.toLong()


    val daysUntilReset = remember {
        val cal = Calendar.getInstance()
        cal.getActualMaximum(Calendar.DAY_OF_MONTH) - cal.get(Calendar.DAY_OF_MONTH)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(24.dp)
    ) {
        Column {
            AssetHeader(
                currentAssets = currentAssets,
                daysUntilReset = daysUntilReset,
                isAssetsVisible = appSettings?.isAssetsVisible ?: true,
                onToggleVisibility = onToggleAssetsVisibility
            )
            Spacer(modifier = Modifier.height(20.dp))
            GoalProgressSection(
                goalSetting = uiState.goalSetting,
                actualAssetsForGoal = if (uiState.goalSetting?.useVirtualBalance == true) virtualBalance else currentAssets
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
                Text("資産総額", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                IconButton(
                    onClick = onToggleVisibility,
                    modifier = Modifier.size(24.dp).padding(start = 4.dp)
                ) {
                    Icon(
                        imageVector = if (isAssetsVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Toggle Assets Visibility",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = if (isAssetsVisible) "¥ ${String.format(Locale.JAPAN, "%,d", currentAssets)}" else "¥ ••••••••",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("リセットまで", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
            Text("$daysUntilReset 日", color = NotionSafeGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun GoalProgressSection(
    goalSetting: GoalSettingEntity?,
    actualAssetsForGoal: Long
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
        (goalSetting.title.isNotEmpty()) -> goalSetting.title
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
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = goalDisplayTitle,
                color = if (hasGoal) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
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
            trackColor = MaterialTheme.colorScheme.outline,
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(12.dp),
        contentAlignment = if (isWaitingForCheck) Alignment.Center else Alignment.TopStart
    ) {
        if (isWaitingForCheck) {
            Column(
                modifier = Modifier.heightIn(min = 150.dp), // 全体の高さをある程度維持
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = NotionSafeGreen,
                    strokeWidth = 3.dp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("AIの状態を確認中...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        } else {
            val isUnavailable = uiState.aiStatus == FeatureStatus.UNAVAILABLE
            val themeColor = if (isUnavailable) Color(0xFFE57373) else NotionSafeGreen
            
            Column(modifier = Modifier.fillMaxWidth()) {
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
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isGenerating) MaterialTheme.colorScheme.outline.copy(alpha = 0.5f) else themeColor.copy(alpha = 0.15f))
                                .clickable(enabled = !isGenerating, onClick = onRefreshAi),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = themeColor
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                // 詳しく聞くボタン
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isGenerating || !hasText) MaterialTheme.colorScheme.outline.copy(alpha = 0.5f) else themeColor.copy(alpha = 0.15f))
                        .clickable(enabled = !isGenerating && hasText, onClick = { onAiAdviceClick(uiState.homeAiText) }),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = themeColor
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "詳しく聞く",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = themeColor
                        )
                    }
                }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
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
                                Text(latestExpense.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(latestExpense.category, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Pending, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Text("記録がありません", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), fontWeight = FontWeight.Medium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                
                Box(modifier = Modifier.heightIn(min = 72.dp)) {
                    if (isGeneratingNow) {
                        ThinkingAnimation()
                    } else if (uiState.aiStatus == FeatureStatus.UNAVAILABLE) {
                        val uriHandler = LocalUriHandler.current
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Gemini Nanoに対応したデバイスのみ利用可能です。\n対応デバイスは下記リンクをご覧ください。",
                                color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, lineHeight = 18.sp
                            )
                            Text(
                                text = "https://developers.google.com/ml-kit/genai?hl=ja",
                                color = Color(0xFF1976D2), fontSize = 12.sp,
                                modifier = Modifier.clickable { uriHandler.openUri("https://developers.google.com/ml-kit/genai?hl=ja") }
                            )
                        }
                    } else if (uiState.homeAiText.isNotEmpty()) {
                        Text(
                            text = uiState.homeAiText,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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
                modifier = Modifier.weight(1f),
                enabled = false // Gray out and disable Goal button
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
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Surface(
        onClick = { if (enabled) onClick() },
        modifier = modifier.aspectRatio(1f),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        val displayColor = if (enabled) color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(displayColor.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = displayColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


