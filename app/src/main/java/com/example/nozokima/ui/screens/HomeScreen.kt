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
import com.example.nozokima.*
import com.example.nozokima.model.*
import com.example.nozokima.data.local.*
import com.example.nozokima.data.local.entities.*
import com.example.nozokima.data.manager.*
import com.example.nozokima.ui.components.ScreenHeader
import com.google.mlkit.genai.common.FeatureStatus
import ui.theme.*
import java.util.*

@Composable
fun HomeScreen(
    dao: FinanceDao,
    gemini: GeminiNanoModel? = null,
    onConsultClick: (Transaction) -> Unit = {},
    onAiAdviceClick: (String) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onCategoryClick: (String) -> Unit = {},
    homeAiText: String = "",
    onRefreshAi: () -> Unit = {}
) {
    val transactions by dao.getAllTransactions().collectAsState(initial = emptyList())
    val assets by dao.getAllAssets().collectAsState(initial = emptyList())
    val lendings by dao.getAllLendings().collectAsState(initial = emptyList())
    val budgets by dao.getAllBudgets().collectAsState(initial = emptyList())
    val goalSetting by dao.getGoalSetting().collectAsState(initial = null)

    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..10 -> listOf(
                "Good morning! ☀️", "おはようございます 🌅", "Bonjour 🥐", "¡Buenos días! 🌻",
                "Guten Morgen 🥨", "좋은 아침! 🐣", "早安 ✨", "Buongiorno ☕", "Bom dia 🌿", "God morgon ☁️"
            )
            in 11..16 -> listOf(
                "Hello! 👋", "こんにちは 😊", "Salut! 🇫🇷", "¡Hola! 🍊", "Ciao! 🍕",
                "你好 🌈", "Hallo! 🍺", "안녕하세요 🍃", "Hi there! 🎈", "Hi! ✨"
            )
            in 17..20 -> listOf(
                "Good evening ✨", "こんばんは 🌆", "Bonsoir 🍷", "¡Buenas tardes! 🌇", "Buonasera 🌙",
                "Guten Abend 🥨", "晚上好 🍵", "저녁이에요 🌿", "Boa tarde 🌊", "Relax time 🛋️"
            )
            else -> listOf(
                "Good night 🌙", "おやすみなさい 💤", "Bonne nuit 🧸", "¡Buenas noches! ⭐", "Buonanotte 🌌",
                "Gute Nacht 😴", "晚安 ✨", "안녕히 주무세요 ☁️", "Sweet dreams 🦄", "Night 🌑"
            )
        }.random()
    }

    var progressTrigger by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        progressTrigger = true
    }

    val totalLendingAmount = remember(lendings) { lendings.filter { !it.isRecovered }.sumOf { it.amount - it.recoveredAmount } }
    val currentAssets = remember(assets, totalLendingAmount) { assets.sumOf { it.amount } + totalLendingAmount }

    // 今月の支出を計算
    val startOfMonth = remember {
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    
    val spentThisMonth = remember(transactions, startOfMonth) {
        transactions
            .filter { it.date >= startOfMonth && it.isExpense && it.category != "貸付" }
            .sumOf { it.amount }
    }

    // 予算ゲージ用
    val currentGoal = goalSetting
    val goalMonthlyBudget = remember(currentGoal, currentAssets) {
        if (currentGoal != null && currentGoal.showResults && currentGoal.targetAmount > 0) {
            val remainingDays = ((currentGoal.targetDateMillis - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(1)
            val remainingMonths = (remainingDays / 30.0).coerceAtLeast(0.1)
            val totalExpectedIncome = (currentGoal.monthlyIncome * remainingMonths).toLong()
            val totalSpendable = (currentAssets + totalExpectedIncome - currentGoal.targetAmount).coerceAtLeast(0L)
            if (remainingMonths > 0) (totalSpendable / remainingMonths).toLong() else 0L
        } else {
            null
        }
    }

    val defaultBudget = budgets.sumOf { it.monthlyAmount }.let { if (it == 0) 100000L else it.toLong() }
    val monthlyBudget = goalMonthlyBudget ?: defaultBudget
    val daysUntilReset = remember {
        val cal = Calendar.getInstance()
        cal.getActualMaximum(Calendar.DAY_OF_MONTH) - cal.get(Calendar.DAY_OF_MONTH)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 0.dp, vertical = 0.dp)
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader(title = greeting)

        Spacer(modifier = Modifier.height(16.dp))

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {

        // ダッシュボードカード
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(16.dp))
                .border(1.dp, NotionBorder, RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    var isAssetsVisible by remember { mutableStateOf(true) }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("資産総額", color = NotionTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            IconButton(
                                onClick = { isAssetsVisible = !isAssetsVisible },
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

                Spacer(modifier = Modifier.height(24.dp))

                // 予算進捗を強調（使用率で色分け）
                val spentRatio = if (monthlyBudget > 0) spentThisMonth.toFloat() / monthlyBudget.toFloat() else 0f
                val progressColor = when {
                    spentRatio > 0.75f -> Color(0xFFE57373) // 赤：75%超
                    spentRatio > 0.5f -> Color(0xFFFFB74D)  // 黄：50%超
                    else -> NotionSafeGreen
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    // 上段：短期予算バー
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text("あといくら使える？", color = NotionTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text(
                            text = "¥ ${String.format(Locale.JAPAN, "%,d", spentThisMonth)} / ¥ ${String.format(Locale.JAPAN, "%,d", monthlyBudget)}",
                            color = NotionTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    // 予算バーは「使った割合」で左から伸びるカウントアップ表示
                    val spentProgress = if (progressTrigger && monthlyBudget > 0) (spentThisMonth.toFloat() / monthlyBudget.toFloat()).coerceIn(0f, 1f) else 0f
                    val animatedSpentProgress by animateFloatAsState(
                        targetValue = spentProgress,
                        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
                        label = "spentProgress"
                    )
                    LinearProgressIndicator(
                        progress = { animatedSpentProgress },
                        modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
                        color = progressColor,
                        trackColor = NotionBorder,
                        strokeCap = StrokeCap.Round
                    )

                    // 下段：長期目標バー（常に表示）
                    Spacer(modifier = Modifier.height(20.dp))

                    val goalForCard = goalSetting
                    val hasGoal = goalForCard != null && goalForCard.targetAmount > 0 && goalForCard.showResults
                    val actualAssetsForGoal = assets.sumOf { it.amount }.toLong()
                    val goalProgressRatio = if (progressTrigger && hasGoal) (actualAssetsForGoal.toFloat() / goalForCard!!.targetAmount.toFloat()).coerceIn(0f, 1f) else 0f
                    val animatedGoalProgress by animateFloatAsState(
                        targetValue = goalProgressRatio,
                        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
                        label = "goalProgress"
                    )
                    val goalDisplayTitle = when {
                        !hasGoal -> "目標未設定"
                        goalForCard!!.title.isNotEmpty() -> goalForCard.title
                        else -> "目標の貯金"
                    }
                    val goalAmountText = if (hasGoal)
                        "¥ ${String.format(Locale.JAPAN, "%,d", actualAssetsForGoal)} / ¥ ${String.format(Locale.JAPAN, "%,d", goalForCard!!.targetAmount)}"
                    else "— 設定してください"
                    val goalBarColor = if (hasGoal) Color(0xFF2196F3) else NotionBorder
                    val goalTrackColor = NotionBorder

                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !hasGoal) { onNavigateToSettings() },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = goalDisplayTitle,
                            color = if (hasGoal) NotionTextSecondary else NotionTextSecondary.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = goalAmountText,
                            color = if (hasGoal) NotionTextPrimary else NotionTextSecondary.copy(alpha = 0.5f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { animatedGoalProgress },
                        modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
                        color = goalBarColor,
                        trackColor = goalTrackColor,
                        strokeCap = StrokeCap.Round
                    )

                    // 覗き魔 AI によるホーム分析
                    Spacer(modifier = Modifier.height(20.dp))

                    val aiStatus by (gemini?.status ?: kotlinx.coroutines.flow.MutableStateFlow(FeatureStatus.UNAVAILABLE)).collectAsState()
                    val aiIsReady by (gemini?.isReady ?: kotlinx.coroutines.flow.MutableStateFlow(false)).collectAsState()
                    val aiIsGenerating by (gemini?.isGenerating ?: kotlinx.coroutines.flow.MutableStateFlow(false)).collectAsState()
                    val aiIsChecking by (gemini?.isCheckingStatus ?: kotlinx.coroutines.flow.MutableStateFlow(false)).collectAsState()

                    // 表示ステートを判定
                    val isWaitingForCheck = aiIsChecking && homeAiText.isEmpty()
                    val isGeneratingNow = aiIsGenerating && homeAiText.isEmpty()
                    val showProgress = isWaitingForCheck || isGeneratingNow
                    val statusLabel = when {
                        isWaitingForCheck -> "AI を確認中..."
                        isGeneratingNow   -> "分析中..."
                        else              -> null
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp) // 最初から高さを確保してレイアウトの跳ねを防止
                            .background(Color(0xFFF5F5F5), RoundedCornerShape(10.dp))
                            .then(if (homeAiText.isNotEmpty() && !aiIsGenerating) Modifier.clickable { onAiAdviceClick(homeAiText) } else Modifier)
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val isUnavailable = aiStatus == FeatureStatus.UNAVAILABLE
                                val themeColor = if (isUnavailable) Color(0xFFE57373) else NotionSafeGreen
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
                                // 手動再生成ボタン（Gemini 利用可能かつ生成・確認中でないとき）
                                if (aiIsReady && !aiIsGenerating) {
                                    IconButton(
                                        onClick = { onRefreshAi() },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "再生成",
                                            tint = themeColor,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            // プログレスバー（確認中 or 生成中）
                            if (showProgress) {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)),
                                    color = NotionSafeGreen,
                                    trackColor = NotionSafeGreen.copy(alpha = 0.2f)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                            // ステータスラベル or テキスト
            if (statusLabel != null) {
                                Text(
                                    text = statusLabel,
                                    color = NotionTextSecondary.copy(alpha = 0.6f),
                                    fontSize = 12.sp
                                )
                            } else if (aiStatus == FeatureStatus.UNAVAILABLE) {
                                val uriHandler = LocalUriHandler.current
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "Gemini Nanoに対応したデバイスのみ利用可能です。\n対応デバイスは下記リンクをご覧ください。",
                                        color = NotionTextPrimary,
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp
                                    )
                                    Text(
                                        text = "https://developers.google.com/ml-kit/genai?hl=ja",
                                        color = Color(0xFF1976D2),
                                        fontSize = 12.sp,
                                        modifier = Modifier.clickable { 
                                            uriHandler.openUri("https://developers.google.com/ml-kit/genai?hl=ja")
                                        }
                                    )
                                }
                            } else if (homeAiText.isNotEmpty()) {
                                Text(
                                    text = homeAiText,
                                    color = NotionTextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // カテゴリアイコンマップ（両セクションで共用）
        val categoryIconMap = mapOf(
            "食費" to Icons.Default.ShoppingCart,
            "日用品" to Icons.Default.Build,
            "交通費" to Icons.Default.Place,
            "交際費" to Icons.Default.Favorite,
            "娯楽" to Icons.Default.Star,
            "美容" to Icons.Default.Face,
            "健康" to Icons.Default.Info,
            "その他" to Icons.Default.MoreHoriz,
            "給与" to Icons.Default.AccountBalance,
            "賞与" to Icons.Default.Star,
            "副業" to Icons.Default.Build,
            "お小遣い" to Icons.Default.Favorite,
            "還付金" to Icons.Default.Info,
            "貸付" to Icons.Outlined.RequestPage,
            "回収" to Icons.Default.Handshake
        )

        // 最近の支出（支出・収入両方、最新5件）
        Text("最近の記録", color = NotionTextSecondary, style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NotionBorder, RoundedCornerShape(12.dp))
                .background(Color.White, RoundedCornerShape(12.dp))
        ) {
            if (transactions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("記録がまだありません", color = NotionTextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            } else {
                Column {
                    transactions.take(5).forEachIndexed { index, entity ->
                        val tx = Transaction(
                            id = entity.id,
                            name = entity.name,
                            amount = entity.amount,
                            category = entity.category,
                            date = entity.date
                        )
                        val isExpenseTx = entity.isExpense
                        val iconColor = when {
                            tx.category == "貸付" -> Color(0xFFFFB300)
                            tx.category == "回収" -> Color(0xFF00897B)
                            isExpenseTx -> Color(0xFFE57373)
                            else -> NotionSafeGreen
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(
                                            iconColor.copy(alpha = 0.08f),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .border(1.dp, NotionBorder, RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = categoryIconMap[tx.category] ?: Icons.Default.MoreHoriz,
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
                                    color = iconColor,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = { if (isExpenseTx) onConsultClick(tx) },
                                    modifier = Modifier.size(32.dp).background(NotionBackground, CircleShape),
                                    enabled = isExpenseTx
                                ) {
                                    Icon(Icons.Default.Face, contentDescription = "AI相談",
                                        tint = if (isExpenseTx) NotionSafeGreen else NotionTextSecondary.copy(alpha = 0.4f),
                                        modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        if (index < minOf(transactions.size, 5) - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                thickness = 0.5.dp,
                                color = NotionBorder
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text("今月の統計", color = NotionTextSecondary, style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(12.dp))

        // 今月の支出をカテゴリ別に集計し、上位3件（貸付は除外）
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
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("今月の支出はまだありません", color = NotionTextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            } else {
                Column {
                    monthlyCategoryGroups.forEachIndexed { index, (category, amount) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onCategoryClick(category) }
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(Color.White, RoundedCornerShape(10.dp))
                                        .border(1.dp, NotionBorder, RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = categoryIconMap[category] ?: Icons.Default.MoreHoriz,
                                        contentDescription = category,
                                        tint = NotionTextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = category,
                                    color = NotionTextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "¥ ${String.format(Locale.JAPAN, "%,d", amount)}",
                                    color = NotionTextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = NotionTextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        if (index < monthlyCategoryGroups.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                thickness = 0.5.dp,
                                color = NotionBorder
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
        } // end inner Column
    }
}
