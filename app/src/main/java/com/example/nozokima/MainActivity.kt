@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.nozokima

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ui.theme.*
import androidx.room.Room
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

// --- データモデル ---

data class AssetItemData(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val amount: Int,
    val lastUpdated: Long = System.currentTimeMillis()
)

data class AssetCategoryData(
    val id: String = UUID.randomUUID().toString(),
    var title: String,
    val items: SnapshotStateList<AssetItemData>
)

data class CategoryData(
    val name: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

data class Transaction(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val amount: Int,
    val category: String,
    val date: Long = System.currentTimeMillis()
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    val imageUri: android.net.Uri? = null,
    val isUser: Boolean = true
)

// --- メインアクティビティ ---

class MainActivity : ComponentActivity() {
    private val db by lazy { (application as NozokimaApplication).database }
    private val gemma by lazy { GemmaModel(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 13+ で通知権限をリクエスト
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 0)
        }

        setContent {
            var selectedTab by remember { mutableIntStateOf(0) }
            var consultingTransaction by remember { mutableStateOf<Transaction?>(null) }
            val chatMessages = remember {
                mutableStateListOf(
                    ChatMessage("1", "こんにちは！「覗き魔」AIコンシェルジュです。Gemma-4-E4Bがあなたの支出判定や未来設計をサポートします。レシート画像を送っていただければ内容の解析も可能です。", isUser = false)
                )
            }

            val dao = db.financeDao()

            // --- バックグラウンドダウンロード管理（タブ切替後も継続）---
            val needsDownload by gemma.needsDownloadPermission.collectAsState()
            val scope = rememberCoroutineScope()

            // AI相談タブを開いたとき、未準備なら許可ダイアログをリクエスト
            LaunchedEffect(selectedTab) {
                if (selectedTab == 3) {
                    gemma.requestModelDownload()
                }
            }

            // ダウンロード許可ダイアログ（Activityレベル → タブ切替しても継続）
            if (needsDownload) {
                AlertDialog(
                    onDismissRequest = { gemma.declineModelDownload() },
                    title = { Text("追加のダウンロード") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("AI相談の利用にはAIモデルが必要です。\nダウンロードはWi-Fi 接続を推奨します。", fontSize = 14.sp)
                            Spacer(Modifier.height(2.dp))
                            Text("・Gemma-4-E4B-it（約5GB）", fontSize = 13.sp, color = NotionTextSecondary)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            scope.launch { gemma.startDownloadIfPermitted() }
                        }) { Text("ダウンロード開始") }
                    },
                    dismissButton = {
                        TextButton(onClick = { gemma.declineModelDownload() }) { Text("キャンセル") }
                    }
                )
            }

            Surface(modifier = Modifier.fillMaxSize(), color = NotionBackground) {
                Scaffold(
                    bottomBar = {
                        NavigationBar(containerColor = NotionWhite, tonalElevation = 0.dp) {
                            val items = listOf("ホーム", "記録", "資産状況", "AI相談", "設定")
                            val icons = listOf(Icons.Default.Home, Icons.Default.Add, Icons.Default.List, Icons.Default.Face, Icons.Default.Settings)
                            items.forEachIndexed { index, item ->
                                NavigationBarItem(
                                    icon = { Icon(icons[index], contentDescription = item) },
                                    label = { Text(item, fontSize = 10.sp) },
                                    selected = selectedTab == index,
                                    onClick = {
                                        if (index != 3) consultingTransaction = null
                                        selectedTab = index
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = NotionSafeGreen,
                                        unselectedIconColor = NotionTextSecondary,
                                        indicatorColor = Color.Transparent
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (selectedTab) {
                            0 -> HomeScreen(
                                dao = dao,
                                onConsultClick = { tx ->
                                    consultingTransaction = tx
                                    selectedTab = 3
                                },
                                onNavigateToSettings = {
                                    selectedTab = 4
                                }
                            )
                            1 -> InputScreen(dao)
                            2 -> AssetsScreen(dao)
                            3 -> ConsultationScreen(
                                messages = chatMessages,
                                initialTransaction = consultingTransaction,
                                onClearConsultation = { consultingTransaction = null },
                                gemma = gemma
                            )
                            4 -> BudgetSettingsScreen(dao)
                        }
                    }
                }
            }
        }
    }
}

// --- 1. ホーム画面 ---

@Composable
fun HomeScreen(
    dao: FinanceDao,
    onConsultClick: (Transaction) -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val transactions by dao.getAllTransactions().collectAsState(initial = emptyList())
    val assets by dao.getAllAssets().collectAsState(initial = emptyList())
    val budgets by dao.getAllBudgets().collectAsState(initial = emptyList())
    val goalSetting by dao.getGoalSetting().collectAsState(initial = null)

    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..10 -> "おはようございます"
            in 11..17 -> "こんにちは"
            else -> "こんばんは"
        }
    }

    val currentAssets = assets.sumOf { it.amount }

    // 今月の支出を計算
    val calendar = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val startOfMonth = calendar.timeInMillis
    
    val spentThisMonth = transactions
        .filter { it.date >= startOfMonth && it.isExpense }
        .sumOf { it.amount }

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
    val daysUntilReset = calendar.getActualMaximum(Calendar.DAY_OF_MONTH) - Calendar.getInstance().get(Calendar.DAY_OF_MONTH)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 32.dp, top = 8.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.nozokima),
                contentDescription = "App Icon",
                modifier = Modifier.size(40.dp),
                tint = Color.Unspecified
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = greeting,
                color = NotionTextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
        }

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
                            text = if (isAssetsVisible) "¥ ${String.format("%,d", currentAssets)}" else "¥ ••••••••",
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
                    val spentProgress = if (monthlyBudget > 0) (spentThisMonth.toFloat() / monthlyBudget.toFloat()).coerceIn(0f, 1f) else 0f
                    LinearProgressIndicator(
                        progress = { spentProgress },
                        modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
                        color = progressColor,
                        trackColor = NotionBorder,
                        strokeCap = StrokeCap.Round
                    )

                    // 下段：長期目標バー（常に表示）
                    Spacer(modifier = Modifier.height(20.dp))

                    val goalForCard = goalSetting
                    val hasGoal = goalForCard != null && goalForCard.targetAmount > 0
                    val actualAssetsForGoal = assets.sumOf { it.amount }.toLong()
                    val goalProgressRatio = if (hasGoal) (actualAssetsForGoal.toFloat() / goalForCard!!.targetAmount.toFloat()).coerceIn(0f, 1f) else 0f
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
                        progress = { goalProgressRatio },
                        modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
                        color = goalBarColor,
                        trackColor = goalTrackColor,
                        strokeCap = StrokeCap.Round
                    )

                    // AIによる分析
                    Spacer(modifier = Modifier.height(20.dp))

                    val aiAnalysisText = run {
                        val spentRatio = if (monthlyBudget > 0) spentThisMonth.toFloat() / monthlyBudget.toFloat() else 0f
                        val goalRatio = goalProgressRatio
                        when {
                            spentRatio > 0.9f ->
                                "今月の予算をほぼ使い切っています。残りの日数を考えると、支出を大幅に抑える必要があります。"
                            spentRatio > 0.7f ->
                                "今月の支出ペースはやや速めです。このまま続くと月末に予算を超える可能性があります。"
                            spentRatio > 0.4f ->
                                "支出ペースは概ね順調です。目標達成に向けて引き続き管理を続けましょう。"
                            spentRatio > 0f ->
                                "今月の支出は順調にコントロールできています。この調子を維持しましょう！"
                            else ->
                                "まだ今月の支出がありません。記録を始めて家計を把握しましょう。"
                        } + if (hasGoal && goalRatio < 1f) {
                            val pct = (goalRatio * 100).toInt()
                            " 目標への達成率は ${pct}% です。"
                        } else if (hasGoal && goalRatio >= 1f) {
                            " 🎉 目標達成済みです！"
                        } else ""
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF5F5F5), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(NotionSafeGreen.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Face,
                                    contentDescription = "AI",
                                    tint = NotionSafeGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "覗き魔 AI",
                                    color = NotionSafeGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = aiAnalysisText,
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
            "還付金" to Icons.Default.Info
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
                                            if (isExpenseTx) Color(0xFFFFEBEE) else NotionSafeGreen.copy(alpha = 0.08f),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .border(1.dp, NotionBorder, RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = categoryIconMap[tx.category] ?: Icons.Default.MoreHoriz,
                                        contentDescription = tx.category,
                                        tint = if (isExpenseTx) Color(0xFFE57373) else NotionSafeGreen,
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
                                    color = if (isExpenseTx) Color(0xFFE57373) else NotionSafeGreen,
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

        // 今月の支出をカテゴリ別に集計し、上位3件
        val monthlyCategoryGroups = transactions
            .filter { it.date >= startOfMonth && it.isExpense }
            .groupBy { it.category }
            .mapValues { it.value.sumOf { t -> t.amount } }
            .toList()
            .sortedByDescending { it.second }
            .take(3)

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
                                .clickable { /* 将来的に統計詳細タブへ遷移 */ }
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
    }
}


// --- 2. 記録画面 ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputScreen(dao: FinanceDao) {
    var isExpense by remember { mutableStateOf(true) } // true=支出, false=収入
    var amountText by remember { mutableStateOf("") }
    var memoText by remember { mutableStateOf("") }
    var selectedAssetEntity by remember { mutableStateOf<AssetEntity?>(null) }
    var showAssetSheet by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    val dateFormatter = remember { SimpleDateFormat("yyyy/MM/dd", Locale.JAPAN) }
    var selectedCategory by remember { mutableStateOf<CategoryData?>(null) }
    var showKeypad by remember { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val dbAssets by dao.getAllAssets().collectAsState(initial = emptyList())

    val expenseCategories = listOf(
        CategoryData("食費", Icons.Default.ShoppingCart),
        CategoryData("日用品", Icons.Default.Build),
        CategoryData("交通費", Icons.Default.Place),
        CategoryData("交際費", Icons.Default.Favorite),
        CategoryData("娯楽", Icons.Default.Star),
        CategoryData("美容", Icons.Default.Face),
        CategoryData("健康", Icons.Default.Info),
        CategoryData("その他", Icons.Default.MoreHoriz)
    )
    val incomeCategories = listOf(
        CategoryData("給与", Icons.Default.AccountBalance),
        CategoryData("賞与", Icons.Default.Star),
        CategoryData("副業", Icons.Default.Build),
        CategoryData("お小遣い", Icons.Default.Favorite),
        CategoryData("還付金", Icons.Default.Info),
        CategoryData("その他", Icons.Default.MoreHoriz)
    )
    val categories = if (isExpense) expenseCategories else incomeCategories

    // モード切替時にカテゴリをリセット
    LaunchedEffect(isExpense) { selectedCategory = null }

    val themeColor = NotionTextPrimary
    val accentColor = if (isExpense) Color(0xFF2196F3) else NotionSafeGreen

    val isSaveEnabled = amountText.isNotEmpty() && selectedAssetEntity != null && selectedCategory != null
    val onSave: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val amountValue = evaluateExpression(amountText)
        val asset = selectedAssetEntity
        if (asset != null) {
            val transaction = TransactionEntity(
                id = UUID.randomUUID().toString(),
                name = memoText.ifBlank { selectedCategory?.name ?: "不明" },
                amount = amountValue,
                category = selectedCategory?.name ?: "その他",
                date = selectedDate,
                assetName = asset.name,
                isExpense = isExpense
            )
            scope.launch {
                dao.insertTransaction(transaction)
                val updatedAsset = asset.copy(
                    amount = if (isExpense) asset.amount - amountValue else asset.amount + amountValue,
                    lastUpdated = System.currentTimeMillis()
                )
                dao.updateAsset(updatedAsset)
                amountText = ""
                memoText = ""
                selectedCategory = null
                selectedAssetEntity = null
                showKeypad = false
                snackbarHostState.showSnackbar(if (isExpense) "支出を記録しました" else "収入を記録しました")
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = if (isExpense) "消費の記録" else "収入の記録",
                color = themeColor,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 支出 / 収入 切り替えタブ
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(Color(0xFFF0F0F0), RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(true to "支出", false to "収入").forEach { (mode, label) ->
                    val selected = isExpense == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (selected) Color.White else Color.Transparent)
                            .clickable { isExpense = mode },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) (if (mode) NotionTextPrimary else NotionSafeGreen) else NotionTextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("総額", color = NotionTextSecondary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth().height(64.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .border(1.dp, NotionBorder, RoundedCornerShape(12.dp))
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .clickable { showKeypad = !showKeypad }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (amountText.isEmpty()) "¥ 0" else "¥ $amountText",
                        color = if (amountText.isEmpty()) NotionBorder else themeColor,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                IconButton(
                    onClick = { /* OCR / 明細撮影 */ },
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color(0xFFF0F0F0), RoundedCornerShape(12.dp))
                        .border(1.dp, NotionBorder, RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        Icons.Default.PhotoCamera,
                        contentDescription = if (isExpense) "レシートを撮影" else "給与明細を撮影",
                        tint = NotionTextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("カテゴリ", color = NotionTextSecondary, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier.fillMaxWidth().border(1.dp, NotionBorder, RoundedCornerShape(12.dp)).background(Color.White, RoundedCornerShape(12.dp)).padding(12.dp)
            ) {
                // 行数に応じて高さを計算（アイテム1行≒57dp、行間8dp）
                val categoryCount = categories.size
                val rowCount = (categoryCount + 3) / 4
                val gridHeight = (rowCount * 57 + (rowCount - 1) * 8).dp
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.height(gridHeight),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    userScrollEnabled = false
                ) {
                    items(categories) { category ->
                        val isSelected = selectedCategory == category
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) accentColor.copy(alpha = 0.1f) else Color.Transparent)
                                .clickable { selectedCategory = category }
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = category.icon,
                                contentDescription = category.name,
                                tint = if (isSelected) accentColor else NotionTextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                category.name,
                                fontSize = 10.sp,
                                color = if (isSelected) accentColor else NotionTextSecondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(if (isExpense) "品目" else "内容", color = NotionTextSecondary, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .border(1.dp, NotionBorder, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = memoText,
                    onValueChange = { memoText = it },
                    textStyle = androidx.compose.ui.text.TextStyle(color = NotionTextPrimary, fontSize = 15.sp),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        if (memoText.isEmpty()) {
                            Text(
                                if (isExpense) "何にお金を使いましたか？（例: カフェ代）" else "収入の内容を入力（例: 3月分給与）",
                                color = NotionTextSecondary,
                                fontSize = 15.sp
                            )
                        }
                        innerTextField()
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("詳細", color = NotionTextSecondary, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier.fillMaxWidth().border(1.dp, NotionBorder, RoundedCornerShape(12.dp)).background(Color.White, RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showAssetSheet = true }.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (isExpense) "支払い元" else "入金先",
                        color = NotionTextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = selectedAssetEntity?.name ?: "未選択",
                            color = if (selectedAssetEntity == null) NotionTextSecondary else accentColor,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = NotionTextSecondary)
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = NotionBorder)
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true }.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("日付", color = NotionTextPrimary, fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = dateFormatter.format(Date(selectedDate)), color = accentColor, fontWeight = FontWeight.Bold)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = NotionTextSecondary)
                    }
                }
            }

            if (!showKeypad) {
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor, disabledContainerColor = NotionBorder),
                    shape = RoundedCornerShape(12.dp),
                    enabled = isSaveEnabled
                ) {
                    Text(
                        if (isExpense) "支出を記録する" else "収入を記録する",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.imePadding())
        }

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if(showKeypad) 300.dp else 16.dp))

        AnimatedVisibility(
            visible = showKeypad,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            Surface(
                color = Color(0xFFF8F8F8),
                shadowElevation = 16.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, NotionBorder)
            ) {
                CustomKeypad(
                    onNumberClick = { num -> 
                        if (amountText == "0") amountText = num else amountText += num 
                    },
                    onOperatorClick = { op -> 
                        if (amountText.isNotEmpty() && !amountText.last().toString().matches(Regex("[-+*/.]"))) {
                            amountText += op 
                        }
                    },
                    onDeleteClick = { if (amountText.isNotEmpty()) amountText = amountText.dropLast(1) },
                    onConfirmClick = { 
                        if (amountText.isNotEmpty()) {
                            amountText = evaluateExpression(amountText).toString()
                        }
                    },
                    onSaveClick = onSave,
                    onCloseClick = { showKeypad = false },
                    isSaveEnabled = isSaveEnabled
                )
            }
        }

        if (showAssetSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAssetSheet = false },
                containerColor = Color.White,
                dragHandle = { BottomSheetDefaults.DragHandle(color = NotionBorder) }
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp).verticalScroll(rememberScrollState())) {
                    Text("支払い元を選択", modifier = Modifier.padding(16.dp), color = NotionTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    
                    if (dbAssets.isEmpty()) {
                        Text("資産が登録されていません。資産タブから追加してください。", modifier = Modifier.padding(16.dp), color = NotionTextSecondary)
                    }
                    
                    dbAssets.forEach { asset ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { selectedAssetEntity = asset; showAssetSheet = false }.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(asset.name, color = NotionTextPrimary)
                            Text("¥ ${String.format("%,d", asset.amount)}", color = NotionTextSecondary)
                        }
                    }
                }
            }
        }

        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate)
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        selectedDate = datePickerState.selectedDateMillis ?: selectedDate
                        showDatePicker = false
                    }) { Text("OK", color = NotionSafeGreen) }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text("キャンセル") }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }
    }
}

@Composable
fun CustomKeypad(
    onNumberClick: (String) -> Unit,
    onOperatorClick: (String) -> Unit,
    onDeleteClick: () -> Unit,
    onConfirmClick: () -> Unit,
    onSaveClick: () -> Unit,
    onCloseClick: () -> Unit,
    isSaveEnabled: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 保存ボタンをキーパッド上部に配置
            Button(
                onClick = onSaveClick,
                modifier = Modifier.weight(1f).height(52.dp).padding(horizontal = 4.dp, vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NotionSafeGreen,
                    disabledContainerColor = NotionBorder
                ),
                shape = RoundedCornerShape(8.dp),
                enabled = isSaveEnabled
            ) {
                Text("この内容で保存する", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            
            IconButton(onClick = onCloseClick, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "閉じる", tint = NotionTextSecondary)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        val keys = listOf(
            listOf("7", "8", "9", "÷"),
            listOf("4", "5", "6", "×"),
            listOf("1", "2", "3", "−"),
            listOf("0", "C", "=", "+") // 確定を「=」に変更して電卓らしさを
        )
        keys.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    val isNumber = key.all { it.isDigit() }
                    val isEqual = key == "="
                    val isDelete = key == "C"
                    
                    Button(
                        onClick = {
                            when {
                                isNumber -> onNumberClick(key)
                                isEqual -> onConfirmClick()
                                isDelete -> onDeleteClick()
                                else -> {
                                    val op = when(key) {
                                        "÷" -> "/"
                                        "×" -> "*"
                                        "−" -> "-"
                                        else -> key
                                    }
                                    onOperatorClick(op)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).height(56.dp).padding(vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isEqual) Color(0xFFE0E0E0) else if (isNumber) Color.White else Color(0xFFF0F0F0),
                            contentColor = NotionTextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp)
                    ) {
                        Text(text = key, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

fun evaluateExpression(expression: String): Int {
    if (expression.isEmpty()) return 0
    return try {
        // 全角記号の正規化と不要な記号の除去
        val normalized = expression.replace("−", "-")
            .replace("×", "*")
            .replace("÷", "/")
            .trim()
        
        // 末尾が演算子の場合は、その演算子を除去して計算する
        var cleanExpression = normalized
        while (cleanExpression.isNotEmpty() && cleanExpression.last() in "+-*/") {
            cleanExpression = cleanExpression.dropLast(1)
        }
        
        if (cleanExpression.isEmpty()) return 0

        val tokens = cleanExpression.split(Regex("(?<=[-+*/])|(?=[-+*/])"))
        if (tokens.isEmpty()) return 0
        
        var result = tokens[0].trim().toLong()
        var i = 1
        while (i < tokens.size) {
            val op = tokens[i].trim()
            if (i + 1 >= tokens.size) break
            val nextStr = tokens[i+1].trim()
            if (nextStr.isEmpty()) { i += 2; continue }
            val next = nextStr.toLong()
            result = when(op) {
                "+" -> result + next
                "-" -> result - next
                "*" -> result * next
                "/" -> if (next != 0L) result / next else result
                else -> result
            }
            i += 2
        }
        result.toInt()
    } catch (e: Exception) {
        0
    }
}

// --- 3. 資産状況画面 ---

@Composable
fun AssetsScreen(dao: FinanceDao) {
    val assetsFromDb by dao.getAllAssets().collectAsState(initial = emptyList())
    val transactions by dao.getAllTransactions().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    // DBの状態を反映するリスト
    val categories = remember { mutableStateListOf<AssetCategoryData>() }

    // DBに資産データがある場合は同期
    LaunchedEffect(assetsFromDb) {
        categories.clear()
        assetsFromDb.groupBy { it.category }.forEach { (categoryTitle, items) ->
            val assetItems = items.map { 
                AssetItemData(id = it.id, name = it.name, amount = it.amount, lastUpdated = it.lastUpdated)
            }
            val stateItems = mutableStateListOf<AssetItemData>()
            stateItems.addAll(assetItems)
            categories.add(AssetCategoryData(title = categoryTitle, items = stateItems))
        }
    }

    var showGroupSheet by remember { mutableStateOf(false) }
    var showAddItemDialog by remember { mutableStateOf(false) }
    var selectedGroupTitle by remember { mutableStateOf("") }
    var editNameText by remember { mutableStateOf("") }
    var editAmountText by remember { mutableStateOf("") }

    var editingCategory by remember { mutableStateOf<AssetCategoryData?>(null) }
    var editingItem by remember { mutableStateOf<Pair<AssetCategoryData, AssetItemData>?>(null) }
    var itemToDelete by remember { mutableStateOf<Pair<AssetCategoryData, AssetItemData>?>(null) }
    var categoryToDelete by remember { mutableStateOf<AssetCategoryData?>(null) }
    var transactionToDelete by remember { mutableStateOf<TransactionEntity?>(null) }

    val assetGroups = listOf("現金", "銀行", "電子マネー", "カード", "貯蓄", "投資", "貸付", "カードローン", "ローン", "保険", "デビットカード", "その他")
    
    var selectedTab by remember { mutableStateOf(0) } // 0: 残高内訳, 1: 履歴

    val totalAssets = assetsFromDb.filter { it.amount > 0 }.sumOf { it.amount }
    val totalLiabilities = assetsFromDb.filter { it.amount < 0 }.sumOf { it.amount }.let { if (it < 0) it * -1 else it }
    val totalNet = totalAssets - totalLiabilities

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 24.dp).verticalScroll(rememberScrollState())) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("資産状況", color = NotionTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            IconButton(
                onClick = { showGroupSheet = true },
                modifier = Modifier.background(NotionSafeGreen.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = NotionSafeGreen)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 純資産をメインに据えたサマリー
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("純資産", color = NotionTextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(
                text = "¥ ${String.format("%,d", totalNet)}",
                color = NotionTextPrimary,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                SummaryItemMini("資産", totalAssets, Color(0xFF2196F3))
                SummaryItemMini("負債", totalLiabilities, Color(0xFFE57373))
                val loanAmount = assetsFromDb.filter { it.category == "貸付" }.sumOf { it.amount }
                SummaryItemMini("貸付", loanAmount, Color(0xFFFFB74D))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // セグメントコントロール
        Row(
            modifier = Modifier.fillMaxWidth().height(44.dp),
            horizontalArrangement = Arrangement.Start
        ) {
            listOf("残高内訳", "履歴").forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Box(
                    modifier = Modifier
                        .clickable { selectedTab = index }
                        .padding(horizontal = 16.dp)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = title,
                            color = if (isSelected) NotionTextPrimary else NotionTextSecondary,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .height(2.dp)
                                .background(if (isSelected) NotionTextPrimary else Color.Transparent)
                        )
                    }
                }
            }
        }

        HorizontalDivider(thickness = 1.dp, color = NotionBorder)

        Spacer(modifier = Modifier.height(24.dp))

        if (selectedTab == 0) {
            // 残高内訳タブ
            Column {
                categories.forEach { category ->
                    val categoryTotal = category.items.sumOf { it.amount }
                    Column(modifier = Modifier.padding(bottom = 24.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = category.title,
                                color = NotionTextSecondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.combinedClickable(
                                    onClick = { editingCategory = category; editNameText = category.title },
                                    onLongClick = { categoryToDelete = category }
                                )
                            )
                            Text(
                                text = "¥ ${String.format("%,d", categoryTotal)}",
                                color = NotionTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(12.dp)).border(1.dp, NotionBorder, RoundedCornerShape(12.dp))) {
                            Column {
                                if (category.items.isEmpty()) {
                                    Text("項目がありません", modifier = Modifier.padding(16.dp).fillMaxWidth(), textAlign = TextAlign.Center, color = NotionTextSecondary, fontSize = 13.sp)
                                }
                                category.items.forEach { item ->
                                    AssetListItemRow(item, 
                                        onClick = { 
                                            editingItem = category to item
                                            editNameText = item.name
                                            editAmountText = item.amount.toString()
                                        }, 
                                        onLongClick = { itemToDelete = category to item }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // 履歴タブ
            val historyIconMap = mapOf(
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
                "還付金" to Icons.Default.Info
            )
            val dateFormatter = SimpleDateFormat("yyyy/MM/dd", Locale.JAPAN)
            if (transactions.isEmpty()) {
                Text("記録がありません", modifier = Modifier.padding(16.dp).fillMaxWidth(), textAlign = TextAlign.Center, color = NotionTextSecondary)
            }
            // 日付ごとにグループ化して見出し表示
            val groupedByDate = transactions.groupBy { dateFormatter.format(Date(it.date)) }
            groupedByDate.forEach { (dateLabel, txList) ->
                // 日付見出し
                Text(
                    text = dateLabel,
                    color = NotionTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
                // その日の取引をまとめて1ブロックで表示
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .border(1.dp, NotionBorder, RoundedCornerShape(12.dp))
                ) {
                    Column {
                        txList.forEachIndexed { idx, tx ->
                            AssetHistoryItem(
                                name = tx.name,
                                amount = "¥ ${String.format("%,d", tx.amount)}",
                                memo = tx.category,
                                balanceAfter = tx.assetName,
                                isExpense = tx.isExpense,
                                icon = historyIconMap[tx.category] ?: Icons.Default.MoreHoriz,
                                onLongClick = { transactionToDelete = tx }
                            )
                            if (idx < txList.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    thickness = 0.5.dp,
                                    color = NotionBorder
                                )
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(40.dp))
    }

    if (showGroupSheet) {
        ModalBottomSheet(
            onDismissRequest = { showGroupSheet = false },
            containerColor = Color.White,
            dragHandle = { BottomSheetDefaults.DragHandle(color = NotionBorder) }
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                Text("追加する資産カテゴリを選択", modifier = Modifier.padding(16.dp), color = NotionTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                assetGroups.forEach { group ->
                    AssetGroupItemRow(group) {
                        selectedGroupTitle = group
                        editNameText = ""
                        editAmountText = ""
                        showGroupSheet = false
                        showAddItemDialog = true
                    }
                }
            }
        }
    }

    if (showAddItemDialog) {
        AlertDialog(
            onDismissRequest = { showAddItemDialog = false },
            title = { Text("$selectedGroupTitle に追加", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(value = editNameText, onValueChange = { editNameText = it }, label = { Text("名称（例: 楽天銀行）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = editAmountText, 
                        onValueChange = { editAmountText = it }, 
                        label = { Text("金額（マイナス可）") }, 
                        singleLine = true, 
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val amount = editAmountText.replace("−", "-").toIntOrNull() ?: 0
                    scope.launch {
                        dao.insertAsset(AssetEntity(
                            id = UUID.randomUUID().toString(),
                            name = editNameText.ifBlank { "新規項目" },
                            amount = amount,
                            category = selectedGroupTitle,
                            lastUpdated = System.currentTimeMillis()
                        ))
                    }
                    showAddItemDialog = false
                }) { Text("追加", color = NotionSafeGreen) }
            },
            dismissButton = { TextButton(onClick = { showAddItemDialog = false }) { Text("キャンセル") } },
            containerColor = Color.White,
            shape = RoundedCornerShape(12.dp)
        )
    }

    editingCategory?.let { category ->
        NameEditDialog("カテゴリ名の変更", editNameText, onValueChange = { editNameText = it }, onDismiss = { editingCategory = null }) {
            scope.launch {
                dao.updateCategoryName(category.title, editNameText)
            }
            editingCategory = null
        }
    }

    editingItem?.let { (category, item) ->
        AlertDialog(
            onDismissRequest = { editingItem = null },
            title = { Text("項目の編集", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(value = editNameText, onValueChange = { editNameText = it }, label = { Text("名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = editAmountText, 
                        onValueChange = { editAmountText = it }, 
                        label = { Text("金額") }, 
                        singleLine = true, 
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val amount = editAmountText.replace("−", "-").toIntOrNull() ?: item.amount
                    scope.launch {
                        dao.updateAsset(AssetEntity(
                            id = item.id,
                            name = editNameText,
                            amount = amount,
                            category = category.title,
                            lastUpdated = System.currentTimeMillis()
                        ))
                    }
                    editingItem = null
                }) { Text("保存", color = NotionSafeGreen) }
            },
            dismissButton = { TextButton(onClick = { editingItem = null }) { Text("キャンセル") } },
            containerColor = Color.White,
            shape = RoundedCornerShape(12.dp)
        )
    }

    itemToDelete?.let { (category, item) ->
        DeleteConfirmDialog("${item.name} を削除しますか？", onDismiss = { itemToDelete = null }) {
            scope.launch {
                dao.deleteAsset(AssetEntity(item.id, item.name, item.amount, category.title, item.lastUpdated))
            }
            itemToDelete = null
        }
    }

    categoryToDelete?.let { category ->
        DeleteConfirmDialog("カテゴリ「${category.title}」と内の全資産を削除しますか？", onDismiss = { categoryToDelete = null }) {
            scope.launch {
                dao.deleteAssetsByCategory(category.title)
            }
            categoryToDelete = null
        }
    }

    transactionToDelete?.let { tx ->
        DeleteConfirmDialog(
            text = "この履歴「${tx.name}」を削除し、残高を取り消しますか？",
            onDismiss = { transactionToDelete = null }
        ) {
            scope.launch {
                val asset = dao.getAssetByName(tx.assetName)
                if (asset != null) {
                    val restoredAsset = asset.copy(
                        amount = asset.amount + tx.amount,
                        lastUpdated = System.currentTimeMillis()
                    )
                    dao.updateAsset(restoredAsset)
                }
                dao.deleteTransaction(tx)
                transactionToDelete = null
            }
        }
    }
}

// --- 4. AI相談画面 ---

@Composable
fun ConsultationScreen(
    messages: SnapshotStateList<ChatMessage>,
    initialTransaction: Transaction? = null,
    onClearConsultation: () -> Unit = {},
    gemma: GemmaModel? = null
) {
    var inputText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // --- 準備状態と進捗を取得 ---
    val isReady by (gemma?.isReady?.collectAsState() ?: remember { mutableStateOf(false) })
    val progress by (gemma?.copyProgress?.collectAsState() ?: remember { mutableIntStateOf(0) })
    val errorMessage by (gemma?.errorMessage?.collectAsState() ?: remember { mutableStateOf<String?>(null) })

    // 初期相談データの処理
    LaunchedEffect(initialTransaction) {
        if (initialTransaction != null) {
            val tx = initialTransaction
            val alreadyAsked = messages.any { it.text.contains(tx.id) || (it.text.contains(tx.name) && it.isUser) }
            if (!alreadyAsked) {
                messages.add(ChatMessage(text = "支出「${tx.name}」(¥${String.format("%,d", tx.amount)})について相談したいです。", isUser = true))
                messages.add(ChatMessage(text = "「${tx.name}」ですね。${tx.category}カテゴリの支出ですが、これは未来の自分への投資になりそうですか？それとも単なる浪費でしたか？", isUser = false))
            }
            onClearConsultation()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "AI相談",
            modifier = Modifier.padding(top = 24.dp, start = 24.dp, end = 24.dp, bottom = 12.dp),
            color = NotionTextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        // --- モデルの準備（ダウンロード）状況を表示 ---
        if (!isReady) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                color = NotionBackground,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, NotionBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (errorMessage != null) {
                        // エラー表示
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFE57373), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(errorMessage ?: "", fontSize = 12.sp, color = Color(0xFFE57373))
                        }
                    } else if (progress > 0) {
                        // ダウンロード中
                        Text("AIモデルをダウンロード中... $progress%", fontSize = 12.sp, color = NotionTextSecondary)
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = NotionSafeGreen
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("別のメニューに移動してもダウンロードは継続されます。", fontSize = 11.sp, color = NotionTextSecondary)
                    } else {
                        // 未開始（ダウンロード許可待ち）
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = NotionTextSecondary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("AIモデルのダウンロードが必要です。上のダイアログで「ダウンロード開始」を選択してください。", fontSize = 12.sp, color = NotionTextSecondary)
                        }
                    }
                }
            }
        }

        val listState = rememberLazyListState()

        LaunchedEffect(messages.size) {
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.size - 1)
            }
        }

        // チャット履歴
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(messages) { msg ->
                ChatBubble(msg)
            }
        }

        // 入力フォーム
        Surface(
            modifier = Modifier.fillMaxWidth().imePadding(),
            color = Color.White,
            tonalElevation = 2.dp,
            border = BorderStroke(1.dp, NotionBorder)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { /* レシート撮影ロジック */ }) {
                    Icon(Icons.Default.AddCircle, contentDescription = "レシート撮影", tint = NotionSafeGreen)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(NotionBackground, RoundedCornerShape(20.dp))
                        .border(1.dp, NotionBorder, RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    if (inputText.isEmpty()) {
                        Text("相談内容を入力...", color = NotionTextSecondary, fontSize = 14.sp)
                    }
                    androidx.compose.foundation.text.BasicTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))

                // 送信ボタン
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank() && gemma != null) {
                            val userMsg = inputText
                            messages.add(ChatMessage(text = userMsg, isUser = true))
                            inputText = ""

                            scope.launch {
                                val aiMsgId = "ai_reply_${System.currentTimeMillis()}"
                                messages.add(ChatMessage(id = aiMsgId, text = "...", isUser = false))

                                // ここで gemma を参照
                                val response = gemma.generateResponse(userMsg)

                                val index = messages.indexOfFirst { it.id == aiMsgId }
                                if (index != -1) {
                                    messages[index] = ChatMessage(id = aiMsgId, text = response, isUser = false)
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .background(if (inputText.isNotBlank()) NotionSafeGreen else NotionBorder, RoundedCornerShape(50.dp))
                        .size(36.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    if (message.isUser) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.Top
        ) {
            val bubbleColor = NotionSafeGreen
            val textColor = Color.White
            val shape = RoundedCornerShape(16.dp, 16.dp, 2.dp, 16.dp)

            Column(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .background(bubbleColor, shape)
                    .padding(12.dp)
            ) {
                if (message.imageUri != null) {
                    Text("[添付画像]", color = textColor, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(text = message.text, color = textColor, fontSize = 15.sp, lineHeight = 20.sp)
            }
        }
    } else {
        // AI Gemini Style
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, NotionBorder, CircleShape)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.nozokima),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "覗き魔 AI",
                    color = NotionTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = message.text,
                    color = NotionTextPrimary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
            }
        }
    }
}

// --- 5. 設定画面（貯金目標シミュレーション） ---

val currencyVisualTransformation = androidx.compose.ui.text.input.VisualTransformation { text ->
    val original = text.text
    if (original.isEmpty()) {
        return@VisualTransformation androidx.compose.ui.text.input.TransformedText(
            androidx.compose.ui.text.AnnotatedString("¥ 0"),
            object : androidx.compose.ui.text.input.OffsetMapping {
                override fun originalToTransformed(offset: Int) = 3
                override fun transformedToOriginal(offset: Int) = 0
            }
        )
    }

    val formatted = try {
        "¥ " + String.format(Locale.JAPAN, "%,d", original.toLong())
    } catch (e: Exception) {
        "¥ $original"
    }

    val offsetMapping = object : androidx.compose.ui.text.input.OffsetMapping {
        override fun originalToTransformed(offset: Int): Int {
            if (offset <= 0) return 2
            var digitCount = 0
            var i = 0
            while (digitCount < offset && i < formatted.length) {
                if (formatted[i].isDigit()) digitCount++
                i++
            }
            return maxOf(2, i)
        }

        override fun transformedToOriginal(offset: Int): Int {
            var digitCount = 0
            for (i in 0 until minOf(offset, formatted.length)) {
                if (formatted[i].isDigit()) digitCount++
            }
            return digitCount
        }
    }
    androidx.compose.ui.text.input.TransformedText(androidx.compose.ui.text.AnnotatedString(formatted), offsetMapping)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetSettingsScreen(dao: FinanceDao) {
    val assets by dao.getAllAssets().collectAsState(initial = emptyList())
    val goalSetting by dao.getGoalSetting().collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    val defaultDateMillis = remember { Calendar.getInstance().apply { add(Calendar.MONTH, 6) }.timeInMillis }

    var titleText by rememberSaveable { mutableStateOf("") }
    var targetAmountText by rememberSaveable { mutableStateOf("") }
    var targetDateMillis by rememberSaveable { mutableLongStateOf(defaultDateMillis) }
    var startDateMillis by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    val actualTotalAssets = assets.sumOf { it.amount }.toLong()
    var monthlyIncomeText by rememberSaveable { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showResults by rememberSaveable { mutableStateOf(false) }

    // DBから読み込み（初回のみ）
    var loadedFromDb by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(goalSetting) {
        if (!loadedFromDb && goalSetting != null) {
            val g = goalSetting!!
            if (g.title.isNotEmpty()) titleText = g.title
            if (g.targetAmount > 0) targetAmountText = g.targetAmount.toString()
            if (g.monthlyIncome > 0) monthlyIncomeText = g.monthlyIncome.toString()
            if (g.targetDateMillis > 0) targetDateMillis = g.targetDateMillis
            if (g.startDateMillis > 0) startDateMillis = g.startDateMillis
            showResults = g.showResults
            loadedFromDb = true
        }
    }

    // 変更をDBに保存するヘルパー
    fun saveGoal() {
        scope.launch {
            dao.upsertGoalSetting(
                GoalSettingEntity(
                    title = titleText,
                    targetAmount = targetAmountText.toLongOrNull() ?: 0L,
                    monthlyIncome = monthlyIncomeText.toLongOrNull() ?: 0L,
                    targetDateMillis = targetDateMillis,
                    showResults = showResults,
                    startDateMillis = startDateMillis
                )
            )
        }
    }

    // 計算
    val remainingDays = ((targetDateMillis - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(1)
    val remainingMonths = (remainingDays / 30.0).coerceAtLeast(0.1)
    val targetAmount = targetAmountText.toLongOrNull() ?: 0L
    val monthlyIncome = monthlyIncomeText.toLongOrNull() ?: 0L
    val totalExpectedIncome = (monthlyIncome * remainingMonths).toLong()
    val totalSpendable = (actualTotalAssets + totalExpectedIncome - targetAmount).coerceAtLeast(0L)
    val monthlyBudget = if (remainingMonths > 0) (totalSpendable / remainingMonths).toLong() else 0L
    val dailyLimit = if (remainingDays > 0) totalSpendable / remainingDays else 0L

    // 難易度診断
    val difficulty = when {
        targetAmount == 0L -> "—"
        dailyLimit < 1000 -> "スパルタ"
        dailyLimit < 3000 -> "普通"
        else -> "余裕"
    }
    val difficultyColor = when (difficulty) {
        "スパルタ" -> Color(0xFFE57373); "普通" -> Color(0xFFFFB74D); "余裕" -> NotionSafeGreen
        else -> NotionTextSecondary
    }
    val aiAdvice = when (difficulty) {
        "スパルタ" -> "今のままだと明日から水だけで生活することになるよ？もう少し現実的な目標にしてみない？"
        "普通" -> "なかなかいい目標だね。でも油断するとすぐ超えちゃうから、僕がしっかり覗いてあげる。"
        "余裕" -> "もっと高い目標にしても、僕は覗き続けてあげるよ。余裕があるうちにもっと貯めよう！"
        else -> "まずは目標貯金額と達成日を入力してみて。"
    }

    val animatedMonthly by animateIntAsState(targetValue = monthlyBudget.toInt(), label = "monthly")
    val animatedDaily by animateIntAsState(targetValue = dailyLimit.toInt(), label = "daily")
    val dateFormatter = remember { SimpleDateFormat("yyyy/MM/dd", Locale.JAPAN) }

    val goalAchieved = actualTotalAssets >= targetAmount && targetAmount > 0L
    val currentStep = when { goalAchieved -> 2; showResults -> 1; else -> 0 }
    val canStart = targetAmount > 0L && monthlyIncomeText.isNotEmpty()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // タイトル行
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when {
                    goalAchieved -> "目標達成"
                    showResults -> "目標経過"
                    else -> "目標設定"
                },
                color = NotionTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp, modifier = Modifier.weight(1f)
            )
            if (showResults && !goalAchieved) {
                OutlinedButton(
                    onClick = { showResults = false; saveGoal() },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, NotionBorder),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp), tint = NotionTextSecondary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("設定を変更", fontSize = 12.sp, color = NotionTextSecondary)
                }
            }
        }

        GoalStepper(currentStep = currentStep)

        Spacer(Modifier.height(4.dp))

        // ━━━━━━━━━━━ 設定画面 ━━━━━━━━━━━
        if (!showResults) {
            SectionLabel("目標の定義")
            GoalInputCard {
                // 目標タイトル
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).background(NotionSafeGreen.copy(alpha = 0.1f), RoundedCornerShape(8.dp)), Alignment.Center) {
                        Icon(Icons.Default.Flag, null, tint = NotionSafeGreen, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("目標タイトル", color = NotionTextSecondary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Surface(modifier = Modifier.width(120.dp), color = NotionSafeGreen.copy(alpha = 0.1f), shape = RoundedCornerShape(20.dp)) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = titleText,
                            onValueChange = { titleText = it; saveGoal() },
                            textStyle = androidx.compose.ui.text.TextStyle(color = NotionSafeGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                            singleLine = true,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).widthIn(min = 40.dp),
                            decorationBox = { inner ->
                                if (titleText.isEmpty()) Text("例: サイドFIRE", color = NotionSafeGreen.copy(alpha = 0.5f), fontSize = 12.sp, textAlign = TextAlign.Center)
                                inner()
                            }
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), 0.5.dp, NotionBorder)
                // 目標貯金額
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).background(NotionSafeGreen.copy(alpha = 0.1f), RoundedCornerShape(8.dp)), Alignment.Center) {
                        Icon(Icons.Default.Star, null, tint = NotionSafeGreen, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("目標貯金額", color = NotionTextSecondary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Surface(modifier = Modifier.width(100.dp), color = NotionSafeGreen.copy(alpha = 0.1f), shape = RoundedCornerShape(20.dp)) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = targetAmountText,
                            onValueChange = { targetAmountText = it.filter(Char::isDigit); saveGoal() },
                            textStyle = androidx.compose.ui.text.TextStyle(color = NotionSafeGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                            visualTransformation = currencyVisualTransformation,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).widthIn(min = 40.dp),
                            decorationBox = { inner -> inner() }
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), 0.5.dp, NotionBorder)
                // 月収
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).background(NotionSafeGreen.copy(alpha = 0.1f), RoundedCornerShape(8.dp)), Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = NotionSafeGreen, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("月収", color = NotionTextSecondary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Surface(modifier = Modifier.width(100.dp), color = NotionSafeGreen.copy(alpha = 0.1f), shape = RoundedCornerShape(20.dp)) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = monthlyIncomeText,
                            onValueChange = { monthlyIncomeText = it.filter(Char::isDigit); saveGoal() },
                            textStyle = androidx.compose.ui.text.TextStyle(color = NotionSafeGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                            visualTransformation = currencyVisualTransformation,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).widthIn(min = 40.dp),
                            decorationBox = { inner -> inner() }
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), 0.5.dp, NotionBorder)
                // 目標達成日
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true }.padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(36.dp).background(NotionSafeGreen.copy(alpha = 0.15f), RoundedCornerShape(8.dp)), Alignment.Center) {
                        Icon(Icons.Default.DateRange, null, tint = NotionSafeGreen, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("目標達成日", color = NotionTextSecondary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Surface(modifier = Modifier.width(100.dp), color = NotionSafeGreen.copy(alpha = 0.15f), shape = RoundedCornerShape(20.dp)) {
                        Text(dateFormatter.format(Date(targetDateMillis)), color = NotionSafeGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // シミュレーション結果ブロック
            SectionLabel("シミュレーション結果")
            Column(
                modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(12.dp)).border(1.dp, NotionBorder, RoundedCornerShape(12.dp))
            ) {
                // 現在の保有資産
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).background(Color(0xFFFFB74D).copy(alpha = 0.1f), RoundedCornerShape(8.dp)), Alignment.Center) {
                        Icon(Icons.Default.AccountBalance, null, tint = Color(0xFFFFB74D), modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("現在の保有資産", color = NotionTextSecondary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Surface(modifier = Modifier.width(100.dp), color = Color(0xFFFFB74D).copy(alpha = 0.1f), shape = RoundedCornerShape(20.dp)) {
                        Text("¥ ${String.format("%,d", actualTotalAssets)}", color = Color(0xFFFFB74D), fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
                    }
                }

                if (canStart) {
                    HorizontalDivider(thickness = 0.5.dp, color = NotionBorder, modifier = Modifier.padding(horizontal = 16.dp))
                    // 許容支出（合計）
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).background(Color(0xFF2196F3).copy(alpha = 0.1f), RoundedCornerShape(8.dp)), Alignment.Center) {
                            Icon(Icons.Default.Wallet, null, tint = Color(0xFF2196F3), modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text("合計の許容支出", color = NotionTextSecondary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Surface(modifier = Modifier.width(100.dp), color = Color(0xFF2196F3).copy(alpha = 0.1f), shape = RoundedCornerShape(20.dp)) {
                            Text("¥ ${String.format("%,d", totalSpendable)}", color = Color(0xFF2196F3), fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
                        }
                    }
                    
                    HorizontalDivider(thickness = 0.5.dp, color = NotionBorder, modifier = Modifier.padding(horizontal = 16.dp))
                    // 月の予算
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).background(Color(0xFF2196F3).copy(alpha = 0.1f), RoundedCornerShape(8.dp)), Alignment.Center) {
                            Icon(Icons.Default.CalendarMonth, null, tint = Color(0xFF2196F3), modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text("月の予算", color = NotionTextSecondary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Surface(modifier = Modifier.width(100.dp), color = Color(0xFF2196F3).copy(alpha = 0.1f), shape = RoundedCornerShape(20.dp)) {
                            Text("¥ ${String.format("%,d", monthlyBudget)}", color = Color(0xFF2196F3), fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // 貯蓄を開始するボタン
            Button(
                onClick = {
                    showResults = true
                    if (startDateMillis == 0L || goalSetting?.startDateMillis?.let { it == 0L } == true) {
                        startDateMillis = System.currentTimeMillis()
                    }
                    saveGoal()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = canStart,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NotionSafeGreen, disabledContainerColor = NotionBorder)
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("貯蓄を開始する", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            if (!canStart) {
                Text("※ 目標貯金額と月収を入力してください", color = NotionTextSecondary, fontSize = 11.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }

        } else if (goalAchieved) {
            // ━━━━━━━━━━━ 達成画面 ━━━━━━━━━━━
            val totalGoalDays = ((targetDateMillis - startDateMillis) / (1000 * 60 * 60 * 24)).coerceAtLeast(1L)
            val passedDays = ((System.currentTimeMillis() - startDateMillis) / (1000 * 60 * 60 * 24)).coerceAtLeast(0L)
            val daysSaved = totalGoalDays - passedDays

            // 1: お祝いカード
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFF9E6), RoundedCornerShape(16.dp))
                    .border(1.5.dp, Color(0xFFFFD54F), RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("🎉 Congratulations! 🎉", color = Color(0xFFF57F17), fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(8.dp))
                    val achievementTitle = if (titleText.isNotEmpty()) "「${titleText}」達成おめでとうございます" else "目標達成おめでとうございます"
                    Text(achievementTitle, color = NotionTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Text("¥ ${String.format("%,d", targetAmount)}", color = Color(0xFFF57F17), fontSize = 40.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp)
                }
            }

            Spacer(Modifier.height(12.dp))

            // 2: 実績サマリー
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, NotionBorder, RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    // 上段
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                            Text("目標達成", color = NotionTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Complete!", color = Color(0xFF2196F3), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        LinearProgressIndicator(
                            progress = { 1f },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = Color(0xFF2196F3),
                            trackColor = Color(0xFF2196F3).copy(alpha = 0.12f),
                            strokeCap = StrokeCap.Round
                        )
                    }

                    HorizontalDivider(thickness = 0.5.dp, color = NotionBorder)

                    // 下段
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                            Text("実績期間", color = NotionTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("${if (totalGoalDays > 0) (passedDays * 100 / totalGoalDays).toInt() else 100}%", color = NotionSafeGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        LinearProgressIndicator(
                            progress = { if (totalGoalDays > 0) (passedDays.toFloat() / totalGoalDays.toFloat()).coerceIn(0f, 1f) else 1f },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = NotionSafeGreen,
                            trackColor = NotionSafeGreen.copy(alpha = 0.12f),
                            strokeCap = StrokeCap.Round
                        )
                        if (daysSaved > 0) {
                            Text("予定より ${daysSaved} 日早くゴールしました！", color = NotionSafeGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Text("目標の期限通りに達成しました！", color = NotionTextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 3: AIフィードバック
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, NotionBorder, RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFFFF9E6)).border(1.dp, Color(0xFFFFD54F), CircleShape).padding(6.dp), Alignment.Center) {
                        Image(painterResource(R.drawable.nozokima), null, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("覗き魔 AI", color = NotionTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("君の努力をずっと見ていたよ。本当にお疲れ様！これからはもっと大きな目標に挑戦できるね！", color = NotionTextPrimary, fontSize = 14.sp, lineHeight = 22.sp)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    targetAmountText = ""
                    monthlyIncomeText = ""
                    showResults = false
                    startDateMillis = System.currentTimeMillis()
                    targetDateMillis = defaultDateMillis
                    saveGoal()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NotionSafeGreen)
            ) {
                Text("新しい目標を設定する", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { /* TODO: シェア */ },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, NotionSafeGreen),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NotionSafeGreen)
            ) {
                Text("結果をシェアする", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

        } else {
            // ━━━━━━━━━━━ 経過画面 ━━━━━━━━━━━

            val gap = targetAmount - actualTotalAssets
            val progressRatio = if (targetAmount > 0) (actualTotalAssets.toFloat() / targetAmount.toFloat()).coerceIn(0f, 1f) else 0f

            // ─── Group 1: 今月のアクション（ヒーロー） ──────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NotionSafeGreen.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
                    .border(1.5.dp, NotionSafeGreen.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 32.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    val displayGoalTitle = if (titleText.isNotEmpty()) "「${titleText}」のための今月の予算" else "今月使っていいのはこれだけ"
                    Text(displayGoalTitle, color = NotionSafeGreen, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "¥ ${String.format("%,d", animatedMonthly)}",
                            color = NotionSafeGreen, fontSize = 46.sp, fontWeight = FontWeight.Black,
                            letterSpacing = (-1.5).sp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("/ 月", color = NotionTextSecondary, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("1日の推奨予算：¥ ${String.format("%,d", animatedDaily)}", color = NotionTextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(12.dp))

            // ─── Group 2: 進捗・期限比較ブロック ──────────────
            val totalGoalDays = ((targetDateMillis - startDateMillis) / (1000 * 60 * 60 * 24)).coerceAtLeast(1L)
            val passedDays = ((System.currentTimeMillis() - startDateMillis) / (1000 * 60 * 60 * 24)).coerceAtLeast(0L)
            val timeProgressRatio = if (totalGoalDays > 0) (passedDays.toFloat() / totalGoalDays.toFloat()).coerceIn(0f, 1f) else 0f

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, NotionBorder, RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    // 上段：お金の進捗（達成率）
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                            Text("達成率", color = NotionTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("${if (progressRatio.isNaN()) 0 else (progressRatio * 100).toInt()}%", color = Color(0xFF2196F3), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        LinearProgressIndicator(
                            progress = { if (progressRatio.isNaN()) 0f else progressRatio },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = Color(0xFF2196F3),
                            trackColor = Color(0xFF2196F3).copy(alpha = 0.12f),
                            strokeCap = StrokeCap.Round
                        )
                        if (gap > 0) {
                            Text("目標まであと ¥ ${String.format("%,d", gap)}", color = NotionTextSecondary, fontSize = 12.sp)
                        } else {
                            Text("目標達成済み 🎉", color = NotionSafeGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    HorizontalDivider(thickness = 0.5.dp, color = NotionBorder)

                    // 下段：時間の進捗（期限）
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                            Text("期限", color = NotionTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("${if (timeProgressRatio.isNaN()) 0 else (timeProgressRatio * 100).toInt()}%", color = NotionSafeGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        LinearProgressIndicator(
                            progress = { if (timeProgressRatio.isNaN()) 0f else timeProgressRatio },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = NotionSafeGreen,
                            trackColor = NotionSafeGreen.copy(alpha = 0.12f),
                            strokeCap = StrokeCap.Round
                        )
                        Text("期限まであと ${remainingDays} 日", color = NotionTextSecondary, fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ─── Group 4: AI診断 ──────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, NotionBorder, RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White).border(1.dp, NotionBorder, CircleShape).padding(6.dp), Alignment.Center) {
                        Image(painterResource(R.drawable.nozokima), null, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("覗き魔 AI", color = NotionTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(12.dp))
                            Surface(color = difficultyColor.copy(alpha = 0.15f), shape = RoundedCornerShape(20.dp)) {
                                Text("難易度：$difficulty", color = difficultyColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(aiAdvice, color = NotionTextPrimary, fontSize = 14.sp, lineHeight = 22.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = targetDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    targetDateMillis = datePickerState.selectedDateMillis ?: targetDateMillis
                    showDatePicker = false
                    saveGoal()
                }) {
                    Text("OK", color = NotionSafeGreen)
                }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("キャンセル") } }
        ) { DatePicker(state = datePickerState) }
    }
}

@Composable
private fun SettingSummaryChip(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.background(color.copy(alpha = 0.08f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = NotionTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(value, color = NotionTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1)
    }
}

// ─── ヘルパー Composable ─────────────────────────────────────────────

@Composable
private fun GoalStepper(currentStep: Int) {
    val steps = listOf("設定", "経過", "達成")
    val stepColors = listOf(NotionSafeGreen, Color(0xFF2196F3), Color(0xFFFFB74D))

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, label ->
            val isActive = index == currentStep
            val isDone = index < currentStep
            val color = when {
                isActive || isDone -> stepColors[index]
                else -> NotionBorder
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(20.dp).background(color.copy(alpha = if (isActive) 0.15f else 0.05f), CircleShape).border(1.5.dp, color, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDone) {
                        Icon(Icons.Default.Check, null, tint = color, modifier = Modifier.size(10.dp))
                    } else {
                        Text("${index + 1}", color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(4.dp))
                Text(label, color = if (isActive) color else NotionTextSecondary, fontSize = 11.sp, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal)
            }
            if (index < steps.size - 1) {
                Box(modifier = Modifier.weight(1f).height(1.dp).padding(horizontal = 6.dp).background(if (index < currentStep) stepColors[index] else NotionBorder))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = NotionTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
}

@Composable
private fun GoalInputCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).border(1.dp, NotionBorder, RoundedCornerShape(16.dp)),
        content = content
    )
}

@Composable
private fun AiStatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    subLabel: String? = null,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.background(Color.White, RoundedCornerShape(12.dp)).border(1.dp, NotionBorder, RoundedCornerShape(12.dp)).padding(16.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = NotionSafeGreen, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, color = NotionTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(8.dp))
            Text(value, color = NotionTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Black)
            if (subLabel != null) Text(subLabel, color = NotionTextSecondary, fontSize = 10.sp)
        }
    }
}

@Composable
fun SummaryItemMini(label: String, amount: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = NotionTextSecondary, fontSize = 11.sp)
        Text("¥ ${String.format(Locale.JAPAN, "%,d", amount)}", color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AssetListItemRow(item: AssetItemData, onClick: () -> Unit, onLongClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = onClick,
            onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick() }
        ).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(item.name, color = NotionTextPrimary, fontSize = 14.sp)
        Text(
            "¥ ${String.format(Locale.JAPAN, "%,d", item.amount)}",
            color = if (item.amount >= 0) NotionTextPrimary else Color(0xFFE57373),
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun AssetHistoryItem(
    name: String,
    amount: String,
    memo: String,
    balanceAfter: String,
    isExpense: Boolean = true,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.MoreHoriz,
    onLongClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick() }
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        if (isExpense) Color(0xFFFFEBEE) else NotionSafeGreen.copy(alpha = 0.08f),
                        RoundedCornerShape(10.dp)
                    )
                    .border(1.dp, NotionBorder, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = memo,
                    tint = if (isExpense) Color(0xFFE57373) else NotionSafeGreen,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(name, color = NotionTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("$memo　$balanceAfter", color = NotionTextSecondary, fontSize = 12.sp)
            }
        }
        Text(amount, color = if (isExpense) Color(0xFFE57373) else NotionSafeGreen, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AssetGroupItemRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = NotionTextPrimary, fontSize = 15.sp)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = NotionTextSecondary)
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = NotionBorder)
}

@Composable
fun NameEditDialog(title: String, value: String, onValueChange: (String) -> Unit, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = { OutlinedTextField(value = value, onValueChange = onValueChange, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("保存", color = NotionSafeGreen) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
        containerColor = Color.White, shape = RoundedCornerShape(12.dp)
    )
}

@Composable
fun DeleteConfirmDialog(text: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("削除の確認", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = { Text(text, color = NotionTextSecondary) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("削除", color = Color(0xFFE57373)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
        containerColor = Color.White, shape = RoundedCornerShape(12.dp)
    )
}


