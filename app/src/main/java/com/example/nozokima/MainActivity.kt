@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.nozokima

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
                            0 -> HomeScreen(dao, onConsultClick = { tx ->
                                consultingTransaction = tx
                                selectedTab = 3
                            })
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
fun HomeScreen(dao: FinanceDao, onConsultClick: (Transaction) -> Unit = {}) {
    val transactions by dao.getAllTransactions().collectAsState(initial = emptyList())
    val assets by dao.getAllAssets().collectAsState(initial = emptyList())
    val budgets by dao.getAllBudgets().collectAsState(initial = emptyList())

    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..10 -> "おはようございます"
            in 11..17 -> "こんにちは"
            else -> "こんばんは"
        }
    }

    val currentAssets = assets.sumOf { it.amount }.let { if (it == 0) 158000 else it }
    
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
        .filter { it.date >= startOfMonth }
        .sumOf { it.amount }

    // 予算ゲージ用
    val monthlyBudget = budgets.sumOf { it.monthlyAmount }.let { if (it == 0) 100000 else it }
    val remainingBudget = monthlyBudget - spentThisMonth
    val remainingProgress = if (monthlyBudget > 0) (remainingBudget.toFloat() / monthlyBudget.toFloat()).coerceIn(0f, 1f) else 0f

    val goalName = "サイドFIRE"
    val goalProgress = 0.45f
    val remainingDays = 450
    val acceleration = "+ 1.2 日"
    
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

                // 予算進捗を強調
                val progressColor = when {
                    remainingProgress < 0.25f -> Color(0xFFE57373) // 赤
                    remainingProgress < 0.5f -> Color(0xFFFFB74D)  // 黄色
                    else -> NotionSafeGreen
                }
                
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text("あといくら使える？", color = NotionTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text(
                            text = "¥ ${String.format("%,d", remainingBudget)} / ¥ ${String.format("%,d", monthlyBudget)}",
                            color = NotionTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { remainingProgress },
                        modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
                        color = progressColor,
                        trackColor = NotionBorder,
                        strokeCap = StrokeCap.Round
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text("今月の統計", color = NotionTextSecondary, style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(16.dp))

        // 支出内訳グラフ（ドーナツ型）
        val categoryGroups = transactions.filter { it.date >= startOfMonth }
            .groupBy { it.category }
            .mapValues { it.value.sumOf { t -> t.amount } }
        
        val totalSpentForChart = categoryGroups.values.sum().toFloat()
        val sortedCategories = categoryGroups.toList().sortedByDescending { it.second }.take(4)
        val chartColors = listOf(NotionSafeGreen, Color(0xFF2196F3), Color(0xFFFFB74D), Color(0xFFE57373))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NotionBorder, RoundedCornerShape(12.dp))
                .background(Color.White, RoundedCornerShape(12.dp))
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DonutChart(
                    data = if (totalSpentForChart > 0) sortedCategories.map { it.second / totalSpentForChart } else listOf(1f),
                    colors = if (totalSpentForChart > 0) chartColors else listOf(NotionBorder),
                    centerLabel = sortedCategories.firstOrNull()?.first ?: "なし",
                    centerValue = if (totalSpentForChart > 0) "${((sortedCategories.firstOrNull()?.second ?: 0) / totalSpentForChart * 100).toInt()}%" else "0%",
                    modifier = Modifier.size(100.dp)
                )
                Spacer(modifier = Modifier.width(24.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (totalSpentForChart > 0) {
                        sortedCategories.forEachIndexed { index, (cat, amt) ->
                            ChartLegend(cat, "${(amt / totalSpentForChart * 100).toInt()}%", chartColors[index % chartColors.size])
                        }
                    } else {
                        Text("データなし", color = NotionTextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val maxTransaction = transactions.filter { it.date >= startOfMonth }.maxByOrNull { it.amount }
            SummaryCard(title = "今月の合計", value = "¥ ${String.format("%,d", spentThisMonth)}", comparison = null, modifier = Modifier.weight(1f).fillMaxHeight())
            SummaryCard(title = "最大支出", value = "¥ ${String.format("%,d", maxTransaction?.amount ?: 0)}", subValue = maxTransaction?.name ?: "なし", comparison = null, modifier = Modifier.weight(1f).fillMaxHeight())
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 最近の支出
        Text("最近の支出", color = NotionTextSecondary, style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(12.dp))
        
        if (transactions.isEmpty()) {
            Text("支出の記録がまだありません", color = NotionTextSecondary, fontSize = 14.sp, modifier = Modifier.padding(vertical = 16.dp))
        } else {
            transactions.take(5).forEach { entity ->
                val tx = Transaction(
                    id = entity.id,
                    name = entity.name,
                    amount = entity.amount,
                    category = entity.category,
                    date = entity.date
                )
                TransactionRow(tx, onConsultClick)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text("目標", color = NotionTextSecondary, style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NotionBorder, RoundedCornerShape(12.dp))
                .background(Color.White, RoundedCornerShape(12.dp))
                .padding(24.dp)
        ) {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = goalName, color = NotionTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "残り $remainingDays 日", color = NotionTextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = "${(goalProgress * 100).toInt()}%", color = NotionSafeGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { goalProgress },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = NotionSafeGreen,
                    trackColor = NotionBorder,
                    strokeCap = StrokeCap.Round
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("目標達成までの加速", color = NotionTextSecondary, style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = acceleration, color = NotionSafeGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun TransactionRow(tx: Transaction, onConsultClick: (Transaction) -> Unit) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, NotionBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(tx.name, color = NotionTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(tx.category, color = NotionTextSecondary, fontSize = 12.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("¥ ${String.format("%,d", tx.amount)}", color = NotionTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(12.dp))
                IconButton(onClick = { onConsultClick(tx) }, modifier = Modifier.size(32.dp).background(NotionBackground, CircleShape)) {
                    Icon(Icons.Default.Face, contentDescription = "AI相談", tint = NotionSafeGreen, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun DonutChart(data: List<Float>, colors: List<Color>, centerLabel: String = "", centerValue: String = "", modifier: Modifier = Modifier) {
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            var startAngle = -90f
            data.forEachIndexed { index, value ->
                val sweepAngle = value * 360f
                drawArc(
                    color = colors[index],
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 24f, cap = StrokeCap.Round)
                )
                startAngle += sweepAngle
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = centerLabel, color = NotionTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            Text(text = centerValue, color = NotionTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ChartLegend(label: String, percent: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, color = NotionTextSecondary, fontSize = 12.sp, modifier = Modifier.width(50.dp))
        Text(percent, color = NotionTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SummaryCard(title: String, value: String, subValue: String? = null, comparison: String? = null, modifier: Modifier = Modifier) {
    Box(modifier = modifier.border(1.dp, NotionBorder, RoundedCornerShape(12.dp)).background(Color.White, RoundedCornerShape(12.dp)).padding(20.dp)) {
        Column {
            Text(text = title, color = NotionTextSecondary, style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = value, color = NotionTextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (subValue != null) {
                Text(text = subValue, color = NotionTextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (comparison != null) {
            val isIncrease = comparison.startsWith("+")
            val badgeBgColor = if (isIncrease) Color(0xFFFFF1F1) else Color(0xFFF0F9F4)
            val badgeTextColor = if (isIncrease) Color(0xFFE57373) else NotionSafeGreen
            Surface(modifier = Modifier.align(Alignment.TopEnd).widthIn(min = 56.dp), color = badgeBgColor, shape = RoundedCornerShape(50.dp)) {
                Text(text = comparison, color = badgeTextColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
    }
}

// --- 2. 記録画面 ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputScreen(dao: FinanceDao) {
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

    val categories = listOf(
        CategoryData("食費", Icons.Default.ShoppingCart),
        CategoryData("日用品", Icons.Default.Build),
        CategoryData("交通費", Icons.Default.Place),
        CategoryData("交際費", Icons.Default.Favorite),
        CategoryData("娯楽", Icons.Default.Star),
        CategoryData("美容", Icons.Default.Face),
        CategoryData("健康", Icons.Default.Info),
        CategoryData("その他", Icons.Default.MoreHoriz)
    )

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
                assetName = asset.name
            )
            
            scope.launch {
                // 1. トランザクションを挿入
                dao.insertTransaction(transaction)
                // 2. 資産残高を更新
                val updatedAsset = asset.copy(
                    amount = asset.amount - amountValue,
                    lastUpdated = System.currentTimeMillis()
                )
                dao.updateAsset(updatedAsset)
                
                // 状態をリセット
                amountText = ""
                memoText = ""
                selectedCategory = null
                selectedAssetEntity = null
                showKeypad = false
                snackbarHostState.showSnackbar("記録を保存しました")
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Text("消費の記録", color = NotionTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            
            Spacer(modifier = Modifier.height(16.dp))

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
                        color = if (amountText.isEmpty()) NotionBorder else NotionTextPrimary,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // OCRボタン
                IconButton(
                    onClick = { /* OCR処理のモック */ },
                    modifier = Modifier
                        .size(64.dp)
                        .background(NotionBackground, RoundedCornerShape(12.dp))
                        .border(1.dp, NotionBorder, RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = "レシートを撮影", tint = NotionTextPrimary)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("カテゴリ", color = NotionTextSecondary, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(12.dp))
            
            Box(
                modifier = Modifier.fillMaxWidth().border(1.dp, NotionBorder, RoundedCornerShape(12.dp)).background(Color.White, RoundedCornerShape(12.dp)).padding(12.dp)
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.height(160.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    userScrollEnabled = false
                ) {
                    items(categories) { category ->
                        val isSelected = selectedCategory == category
                        Column(
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (isSelected) NotionSafeGreen.copy(alpha = 0.1f) else Color.Transparent).clickable { selectedCategory = category }.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(imageVector = category.icon, contentDescription = category.name, tint = if (isSelected) NotionSafeGreen else NotionTextSecondary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(category.name, fontSize = 10.sp, color = if (isSelected) NotionSafeGreen else NotionTextSecondary, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("品目", color = NotionTextSecondary, style = MaterialTheme.typography.titleSmall)
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
                            Text("何にお金を使いましたか？（例: カフェ代）", color = NotionTextSecondary, fontSize = 15.sp)
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
                    Text("支払い元", color = NotionTextPrimary, fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = selectedAssetEntity?.name ?: "未選択", color = if (selectedAssetEntity == null) NotionTextSecondary else Color(0xFF2196F3), fontWeight = FontWeight.Bold)
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
                        Text(text = dateFormatter.format(Date(selectedDate)), color = Color(0xFF2196F3), fontWeight = FontWeight.Bold)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = NotionTextSecondary)
                    }
                }
            }

            if (!showKeypad) {
                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NotionSafeGreen, disabledContainerColor = NotionBorder),
                    shape = RoundedCornerShape(12.dp),
                    enabled = isSaveEnabled
                ) {
                    Text("記録を保存する", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val dateFormatter = SimpleDateFormat("yyyy/MM/dd", Locale.JAPAN)
                if (transactions.isEmpty()) {
                    Text("記録がありません", modifier = Modifier.padding(16.dp).fillMaxWidth(), textAlign = TextAlign.Center, color = NotionTextSecondary)
                }
                transactions.forEach { tx ->
                    AssetHistoryItem(
                        date = dateFormatter.format(Date(tx.date)),
                        name = tx.name,
                        amount = "- ¥ ${String.format("%,d", tx.amount)}",
                        memo = tx.category,
                        balanceAfter = tx.assetName,
                        onLongClick = { transactionToDelete = tx }
                    )
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

// --- 5. 設定画面（予算設定） ---

@Composable
fun BudgetSettingsScreen(dao: FinanceDao) {
    var budgets by remember { mutableStateOf(emptyList<BudgetEntity>()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        dao.getAllBudgets().collectLatest { budgets = it }
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
        Text("予算の設定", color = NotionTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))

        val categories = listOf("食費", "日用品", "交通費", "交際費", "娯楽", "美容", "健康", "その他")
        
        categories.forEach { category ->
            val budget = budgets.find { it.category == category }
            var amountText by remember(budget) { mutableStateOf(budget?.monthlyAmount?.toString() ?: "") }

            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                Text(category, color = NotionTextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("¥ 0", color = NotionBorder) },
                    trailingIcon = {
                        IconButton(onClick = {
                            val amount = amountText.toIntOrNull() ?: 0
                            scope.launch {
                                dao.insertBudget(BudgetEntity(category, amount))
                            }
                        }) {
                            Icon(Icons.Default.Check, contentDescription = "保存", tint = NotionSafeGreen)
                        }
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    }
}

// --- 共通コンポーネント ---

@Composable
fun AssetBreakdownBar(categories: List<AssetCategoryData>, totalAssets: Int) {
    if (totalAssets <= 0) return
    
    val colors = listOf(NotionSafeGreen, Color(0xFF2196F3), Color(0xFFFFB74D), Color(0xFFE57373), Color(0xFF9575CD))
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("資産構成比", color = NotionTextSecondary, style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))
        ) {
            categories.forEachIndexed { index, category ->
                val amount = category.items.sumOf { if (it.amount > 0) it.amount else 0 }
                if (amount > 0) {
                    val weight = amount.toFloat() / totalAssets
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(weight)
                            .background(colors[index % colors.size])
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            categories.take(4).forEachIndexed { index, category ->
                val amount = category.items.sumOf { if (it.amount > 0) it.amount else 0 }
                if (amount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(colors[index % colors.size]))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(category.title, fontSize = 10.sp, color = NotionTextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
fun RecentAssetHistoryMock() {
    val historyData = listOf(
        HistoryItemData("楽天銀行", "+ ¥ 120,000", "給与振込", "¥ 515,046"),
        HistoryItemData("現金", "- ¥ 5,000", "ATM引き出し", "¥ 1,500"),
        HistoryItemData("PayPay", "- ¥ 1,200", "セブンイレブン", "¥ 394")
    )
    
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        historyData.forEach { data ->
            AssetHistoryItem(
                date = "本日",
                name = data.name,
                amount = data.amount,
                memo = data.memo,
                balanceAfter = data.balanceAfter
            )
        }
    }
}

data class HistoryItemData(val name: String, val amount: String, val memo: String, val balanceAfter: String)

@Composable
fun AssetHistoryItem(date: String, name: String, amount: String, memo: String, balanceAfter: String, onLongClick: () -> Unit = {}) {
    Box(modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(12.dp)).border(1.dp, NotionBorder, RoundedCornerShape(12.dp)).combinedClickable(onClick = {}, onLongClick = onLongClick)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(date, color = NotionTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                // 残高を少し目立たせない位置へ（右端）
                Surface(color = NotionBackground, shape = RoundedCornerShape(4.dp)) {
                    Text(
                        text = "残高: $balanceAfter", 
                        color = NotionTextSecondary, 
                        fontSize = 10.sp, 
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(name, color = NotionTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(memo, color = NotionTextSecondary, fontSize = 12.sp)
                }
                Text(
                    amount, 
                    color = if (amount.startsWith("+")) Color(0xFF2196F3) else Color(0xFFE57373), 
                    fontSize = 18.sp, 
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
fun SummaryItemMini(label: String, amount: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = NotionTextSecondary, fontSize = 10.sp)
        Text("¥ ${String.format("%,d", amount)}", color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AssetGroupItemRow(name: String, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Text(text = name, modifier = Modifier.padding(16.dp).fillMaxWidth(), color = NotionTextPrimary, fontSize = 16.sp)
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = NotionBorder)
    }
}

@Composable
fun SummaryItemSmall(label: String, amount: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = NotionTextSecondary, fontSize = 12.sp)
        Text("¥ ${String.format("%,d", amount)}", color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AssetListItemRow(item: AssetItemData, onClick: () -> Unit, onLongClick: () -> Unit) {
    val daysAgo = ((System.currentTimeMillis() - item.lastUpdated) / (1000 * 60 * 60 * 24)).toInt()
    val updatedText = when {
        daysAgo == 0 -> "今日"
        daysAgo < 7 -> "${daysAgo}日前"
        else -> "${daysAgo / 7}週間前"
    }

    Column {
        Row(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, color = NotionTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text("${updatedText}に更新", color = NotionTextSecondary, fontSize = 11.sp)
            }
            Text(text = "¥ ${String.format("%,d", item.amount)}", color = if (item.amount >= 0) Color(0xFF2196F3) else Color(0xFFE57373), fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = NotionBorder)
    }
}

@Composable
fun NameEditDialog(title: String, value: String, onValueChange: (String) -> Unit, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold) }, text = { OutlinedTextField(value = value, onValueChange = onValueChange, singleLine = true, modifier = Modifier.fillMaxWidth()) }, confirmButton = { TextButton(onClick = onConfirm) { Text("保存", color = NotionSafeGreen) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル", color = NotionTextSecondary) } }, containerColor = Color.White, shape = RoundedCornerShape(12.dp))
}

@Composable
fun DeleteConfirmDialog(text: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("確認", fontSize = 18.sp, fontWeight = FontWeight.Bold) }, text = { Text(text) }, confirmButton = { TextButton(onClick = onConfirm) { Text("削除", color = Color(0xFFE57373)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル", color = NotionTextSecondary) } }, containerColor = Color.White, shape = RoundedCornerShape(12.dp))
}
